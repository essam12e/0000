package main

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// طابور التحميلات: تحميلات متوازية (حسب الإعدادات) مع إيقاف مؤقت/استكمال/إلغاء/إعادة محاولة.
// الحالة تُحفظ على القرص حتى لا تضيع المهام المتوقفة عند إغلاق البرنامج.

type manager struct {
	mu      sync.Mutex
	tasks   []*Task
	cancels map[string]context.CancelFunc
	reason  map[string]string // "pause" عند الإيقاف المؤقت؛ غير موجود = إلغاء
	st      store[[]*Task]
	lastPub time.Time
}

var mgr = &manager{cancels: map[string]context.CancelFunc{}, reason: map[string]string{}}

func (m *manager) init() {
	m.st.path = filepath.Join(dataDir, "tasks.json")
	m.st.load(&m.tasks)
	for _, t := range m.tasks {
		if t.State == "running" || t.State == "queued" {
			t.State, t.Speed, t.ETA = "paused", "", 0
		}
	}
}

func (m *manager) snapshot() []Task {
	m.mu.Lock()
	defer m.mu.Unlock()
	out := make([]Task, 0, len(m.tasks))
	for _, t := range m.tasks {
		out = append(out, *t)
	}
	return out
}

// publish (والقفل ممسوك): يحفظ ويبثّ الحالة للواجهة.
func (m *manager) publishLocked(save bool) {
	out := make([]Task, 0, len(m.tasks))
	for _, t := range m.tasks {
		out = append(out, *t)
	}
	if save {
		cp := make([]*Task, len(m.tasks))
		copy(cp, m.tasks)
		m.st.save(cp)
	}
	m.lastPub = time.Now()
	go hub.publish("tasks", out)
}

func (m *manager) find(id string) *Task {
	for _, t := range m.tasks {
		if t.ID == id {
			return t
		}
	}
	return nil
}

func (m *manager) taskDir(id string) string { return filepath.Join(dataDir, "library", id) }

func (m *manager) enqueue(spec Spec) string {
	m.mu.Lock()
	t := &Task{ID: newID(), Spec: spec, State: "queued", CreatedAt: time.Now().UnixMilli()}
	m.tasks = append(m.tasks, t)
	m.publishLocked(true)
	m.mu.Unlock()
	m.pump()
	return t.ID
}

func (m *manager) pause(id string) {
	m.mu.Lock()
	defer m.mu.Unlock()
	t := m.find(id)
	if t == nil {
		return
	}
	switch t.State {
	case "running":
		m.reason[id] = "pause"
		if c := m.cancels[id]; c != nil {
			c()
		}
	case "queued":
		t.State = "paused"
		m.publishLocked(true)
	}
}

func (m *manager) resume(id string) {
	m.mu.Lock()
	if t := m.find(id); t != nil && (t.State == "paused" || t.State == "failed") {
		t.State, t.Error, t.Stage = "queued", "", ""
		m.publishLocked(true)
	}
	m.mu.Unlock()
	m.pump()
}

func (m *manager) cancel(id string) {
	m.mu.Lock()
	t := m.find(id)
	if t == nil {
		m.mu.Unlock()
		return
	}
	running := t.State == "running"
	for i, x := range m.tasks {
		if x.ID == id {
			m.tasks = append(m.tasks[:i], m.tasks[i+1:]...)
			break
		}
	}
	delete(m.reason, id)
	if c := m.cancels[id]; c != nil {
		c()
	}
	m.publishLocked(true)
	m.mu.Unlock()
	if !running {
		m.cleanup(id)
	}
}

// cleanup يحذف الملفات الجزئية لمهمة ملغاة (لا يمسّ ملفات المكتبة المكتملة).
func (m *manager) cleanup(id string) {
	dir := m.taskDir(id)
	keep := map[string]bool{}
	for _, it := range library.list() {
		keep[it.Path] = true
	}
	entries, _ := os.ReadDir(dir)
	for _, e := range entries {
		if p := filepath.Join(dir, e.Name()); !keep[p] {
			os.RemoveAll(p)
		}
	}
	if left, _ := os.ReadDir(dir); len(left) == 0 {
		os.Remove(dir)
	}
}

func (m *manager) active() bool {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, t := range m.tasks {
		if t.State == "running" || t.State == "queued" {
			return true
		}
	}
	return false
}

func (m *manager) pump() {
	m.mu.Lock()
	defer m.mu.Unlock()
	running := 0
	for _, t := range m.tasks {
		if t.State == "running" {
			running++
		}
	}
	slots := settings.get().MaxParallel - running
	for _, t := range m.tasks {
		if slots <= 0 {
			break
		}
		if t.State == "queued" {
			t.State, t.Stage, t.Error, t.Progress = "running", "جاري التحضير", "", 0
			ctx, cancel := context.WithCancel(context.Background())
			m.cancels[t.ID] = cancel
			delete(m.reason, t.ID)
			go m.run(ctx, t.ID, t.Spec)
			slots--
		}
	}
	m.publishLocked(true)
}

func (m *manager) run(ctx context.Context, id string, spec Spec) {
	dir := m.taskDir(id)
	onProgress := func(p Progress) {
		m.mu.Lock()
		defer m.mu.Unlock()
		t := m.find(id)
		if t == nil || t.State != "running" {
			return
		}
		if p.Fraction < 0 {
			t.Progress = -1
		} else if p.Fraction > t.Progress || t.Progress < 0 {
			t.Progress = p.Fraction // لا نرجع بالنسبة للخلف (yt-dlp يعيد العدّ لكل مرحلة)
		}
		if p.Speed != "" {
			t.Speed = p.Speed
		}
		t.ETA = p.ETA
		if p.Stage != "" {
			t.Stage = p.Stage
		}
		if time.Since(m.lastPub) > 250*time.Millisecond {
			m.publishLocked(false)
		}
	}
	var err error
	notice := ""
	switch spec.Mode {
	case "direct":
		os.MkdirAll(dir, 0o755)
		err = directDownload(ctx, spec.URL, filepath.Join(dir, safeName(firstNonEmpty(spec.DirectName, "file"))), onProgress)
	case "gallery":
		err = galleryDownload(ctx, spec, dir, onProgress)
	default:
		if err = waitEngine(ctx); err == nil {
			err = runDownload(ctx, spec, dir, onProgress)
		}
		if err == nil && spec.SubLang != "" && spec.Mode == "video" {
			r := finalizeSubs(ctx, spec, dir, func(stage string) { onProgress(Progress{Fraction: 0.99, Stage: stage}) })
			notice = r.notice(spec.SubLang)
		}
	}

	var files []string
	if err == nil {
		for _, f := range outputFiles(dir) {
			ext := extOf(f)
			leftoverCover := spec.Mode == "audio" && kindOfMime(mimeOfExt(ext)) == "image"
			leftoverSub := spec.SubLang != "" && spec.SubEmbed && subExts[ext]
			if !leftoverCover && !leftoverSub {
				files = append(files, f)
			} else {
				os.Remove(f)
			}
		}
		if len(files) == 0 {
			err = &AppError{Type: "generic", Detail: "لم ينتج عن التحميل أي ملف"}
		}
	}

	m.mu.Lock()
	delete(m.cancels, id)
	t := m.find(id)
	reason := m.reason[id]
	delete(m.reason, id)
	switch {
	case t == nil: // أُلغيت
		m.mu.Unlock()
		m.cleanup(id)
	case err != nil && (reason == "pause" || errors.Is(err, context.Canceled)):
		t.State, t.Speed, t.ETA, t.Stage = "paused", "", 0, ""
		m.publishLocked(true)
		m.mu.Unlock()
	case err != nil:
		var ae *AppError
		if !errors.As(err, &ae) {
			ae = &AppError{Type: "generic", Detail: err.Error()}
		}
		t.State, t.Speed, t.ETA, t.Stage = "failed", "", 0, ""
		t.Error = strings.TrimSpace(errorTitle(ae.Type) + "\n" + ae.Detail)
		m.publishLocked(true)
		m.mu.Unlock()
	default:
		for i, x := range m.tasks {
			if x.ID == id {
				m.tasks = append(m.tasks[:i], m.tasks[i+1:]...)
				break
			}
		}
		m.publishLocked(true)
		m.mu.Unlock()
		m.finish(spec, files, notice)
	}
	m.pump()
}

func errorTitle(t string) string {
	switch t {
	case "unsupported":
		return "هذا الرابط غير مدعوم"
	case "private":
		return "محتوى خاص يحتاج تسجيل الدخول"
	case "offline":
		return "لا يوجد اتصال بالإنترنت"
	case "engine":
		return "تعذّر تشغيل محرك التحميل"
	}
	return "تعذّر إتمام العملية"
}

// finish يضيف الملفات إلى المكتبة (مع صورة مصغّرة للفيديو) ويحفظها تلقائياً إن فُعّل ذلك.
func (m *manager) finish(spec Spec, files []string, notice string) {
	var items []LibItem
	for _, f := range files {
		ext := extOf(f)
		mime := mimeOfExt(ext)
		kind := kindOfMime(mime)
		if kind == "video" && spec.Mode == "audio" {
			kind, mime = "audio", "audio/mp4"
		}
		it := LibItem{ID: newID(), Path: f, Kind: kind, Mime: mime, Ext: ext, Size: fileSize(f), SourceURL: spec.URL,
			AddedAt: time.Now().UnixMilli(), Title: strings.TrimSuffix(filepath.Base(f), filepath.Ext(f))}
		if len(files) == 1 {
			it.Title, it.Duration = spec.Title, spec.Duration
		}
		switch kind {
		case "video":
			if makeThumb(f, filepath.Join(dataDir, "thumbs", it.ID+".jpg")) {
				it.Thumb = "local"
			}
		case "image":
			it.Thumb = "file"
		case "audio":
			it.Thumb = spec.Thumbnail
		}
		items = append(items, it)
	}
	library.add(items)
	msg := "اكتمل التحميل: " + spec.Title
	if settings.get().AutoSave {
		saved := 0
		for _, it := range items {
			if _, err := library.saveToDevice(it.ID); err == nil {
				saved++
			}
		}
		if saved == len(items) {
			msg += " — حُفظ في " + saveRoot()
		}
	}
	if notice != "" {
		msg = notice
	}
	hub.publish("notice", map[string]any{"text": msg, "done": true})
}

// shutdown يوقف التحميلات الجارية مؤقتاً قبل إغلاق البرنامج (حتى لا تبقى عمليات yt-dlp/FFmpeg تعمل بلا واجهة).
// المهام تبقى محفوظة كـ«متوقفة» وتُستكمل من حيث توقفت عند الفتح التالي.
func (m *manager) shutdown() {
	m.mu.Lock()
	for _, t := range m.tasks {
		if t.State == "running" {
			m.reason[t.ID] = "pause"
			if c := m.cancels[t.ID]; c != nil {
				c()
			}
		} else if t.State == "queued" {
			t.State = "paused"
		}
	}
	m.publishLocked(true)
	m.mu.Unlock()
	for i := 0; i < 40; i++ { // حتى ثانيتين لتنتهي العمليات
		m.mu.Lock()
		n := len(m.cancels)
		m.mu.Unlock()
		if n == 0 {
			return
		}
		time.Sleep(50 * time.Millisecond)
	}
}

// orphanDirs مجلدات جزئية لمهام لم تعد موجودة (تُمسح مع الكاش).
func (m *manager) orphanDirs() []string {
	known := map[string]bool{}
	m.mu.Lock()
	for _, t := range m.tasks {
		known[t.ID] = true
	}
	m.mu.Unlock()
	for _, it := range library.list() {
		known[filepath.Base(filepath.Dir(it.Path))] = true
	}
	var out []string
	entries, _ := os.ReadDir(filepath.Join(dataDir, "library"))
	for _, e := range entries {
		if e.IsDir() && !known[e.Name()] {
			out = append(out, filepath.Join(dataDir, "library", e.Name()))
		}
	}
	return out
}

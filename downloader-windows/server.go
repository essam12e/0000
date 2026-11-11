package main

import (
	"context"
	"embed"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

//go:embed web
var webFS embed.FS

// ---------------------------------------------------------------- بثّ الأحداث للواجهة (SSE)

type eventHub struct {
	mu   sync.Mutex
	subs map[chan []byte]bool
	last time.Time // آخر مرة كانت فيها واجهة متصلة
}

var hub = &eventHub{subs: map[chan []byte]bool{}, last: time.Now()}

func (h *eventHub) publish(kind string, data any) {
	b, _ := json.Marshal(map[string]any{"type": kind, "data": data})
	h.mu.Lock()
	defer h.mu.Unlock()
	for ch := range h.subs {
		select {
		case ch <- b:
		default: // واجهة بطيئة: نتخطى (الحالة الكاملة تُرسل في الحدث التالي)
		}
	}
}

func (h *eventHub) clients() int {
	h.mu.Lock()
	defer h.mu.Unlock()
	return len(h.subs)
}

// ---------------------------------------------------------------- الخادم المحلي

type server struct {
	token      string
	analyzeMu  sync.Mutex
	analyzeCtx context.CancelFunc
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

func writeErr(w http.ResponseWriter, err error) {
	var ae *AppError
	if !errors.As(err, &ae) {
		ae = &AppError{Type: "generic", Detail: err.Error()}
	}
	writeJSON(w, 400, map[string]any{"error": ae.Type, "title": errorTitle(ae.Type), "hint": errorHint(ae.Type), "detail": ae.Detail})
}

func errorHint(t string) string {
	switch t {
	case "unsupported":
		return "تأكد أن الرابط يشير مباشرة إلى فيديو أو صورة أو مقطع صوتي، ثم حاول مجدداً. قد يفيد تحديث المحرك من الإعدادات."
	case "private":
		return "هذا المحتوى لا يظهر إلا لصاحب حساب مسجّل. اختر متصفحك المسجَّل الدخول من الإعدادات ← الحسابات ثم أعد المحاولة."
	case "offline":
		return "تحقق من اتصال الإنترنت ثم أعد المحاولة."
	case "engine":
		return "أعد تشغيل البرنامج، وإن استمرت المشكلة حدّث المحرك من الإعدادات."
	}
	return "حاول مرة أخرى بعد قليل. إن تكرر الخطأ فحدّث المحرك من الإعدادات."
}

func readJSON(r *http.Request, v any) error {
	return json.NewDecoder(http.MaxBytesReader(nil, r.Body, 4<<20)).Decode(v)
}

// الحماية: الخادم يستمع على 127.0.0.1 فقط، وكل طلب API يحتاج الرمز السري الذي يُولَّد عند كل تشغيل،
// حتى لا تستطيع صفحة ويب أخرى على الجهاز استدعاءه.
func (s *server) guard(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		host, _, _ := net.SplitHostPort(r.Host)
		if host != "127.0.0.1" && host != "localhost" {
			http.Error(w, "forbidden", 403)
			return
		}
		tok := r.Header.Get("X-Token")
		if tok == "" {
			tok = r.URL.Query().Get("t")
		}
		if tok != s.token {
			http.Error(w, "forbidden", 403)
			return
		}
		next(w, r)
	}
}

func (s *server) routes() http.Handler {
	mux := http.NewServeMux()
	sub, _ := fs.Sub(webFS, "web")
	static := http.FileServer(http.FS(sub))
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		static.ServeHTTP(w, r)
	})
	api := func(path string, h http.HandlerFunc) { mux.HandleFunc(path, s.guard(h)) }

	api("/api/state", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]any{"setup": getSetup(), "settings": settingsView(), "tasks": mgr.snapshot(),
			"library": library.list(), "history": history.list(), "version": appVersion, "cacheBytes": cacheBytes()})
	})
	api("/api/events", s.events)
	api("/api/setup/retry", func(w http.ResponseWriter, r *http.Request) { go ensureEngine(); writeJSON(w, 200, true) })
	api("/api/analyze", s.analyze)
	api("/api/analyze/cancel", func(w http.ResponseWriter, r *http.Request) {
		s.analyzeMu.Lock()
		if s.analyzeCtx != nil {
			s.analyzeCtx()
		}
		s.analyzeMu.Unlock()
		writeJSON(w, 200, true)
	})
	api("/api/download", s.download)
	api("/api/task", func(w http.ResponseWriter, r *http.Request) {
		var b struct{ ID, Action string }
		if readJSON(r, &b) != nil {
			return
		}
		switch b.Action {
		case "pause":
			mgr.pause(b.ID)
		case "resume":
			mgr.resume(b.ID)
		case "cancel":
			mgr.cancel(b.ID)
		}
		writeJSON(w, 200, true)
	})
	api("/api/library", s.libraryAction)
	api("/api/media", s.media)
	api("/api/thumb", func(w http.ResponseWriter, r *http.Request) {
		id := filepath.Base(r.URL.Query().Get("id"))
		it, ok := library.get(id)
		if !ok {
			http.NotFound(w, r)
			return
		}
		if it.Thumb == "file" {
			http.ServeFile(w, r, it.Path)
			return
		}
		http.ServeFile(w, r, filepath.Join(dataDir, "thumbs", id+".jpg"))
	})
	api("/api/history", func(w http.ResponseWriter, r *http.Request) {
		var b struct{ ID, Action string }
		if readJSON(r, &b) != nil {
			return
		}
		if b.Action == "clear" {
			history.clear()
		} else {
			history.remove(b.ID)
		}
		writeJSON(w, 200, true)
	})
	api("/api/settings", func(w http.ResponseWriter, r *http.Request) {
		var patch map[string]json.RawMessage
		if readJSON(r, &patch) != nil {
			return
		}
		settings.update(func(st *Settings) {
			set := func(k string, into any) {
				if raw, ok := patch[k]; ok {
					json.Unmarshal(raw, into)
				}
			}
			set("defaultQuality", &st.DefaultQuality)
			set("audioFormat", &st.AudioFormat)
			set("maxParallel", &st.MaxParallel)
			set("saveDir", &st.SaveDir)
			set("autoSave", &st.AutoSave)
			set("cookiesBrowser", &st.CookiesBrowser)
			set("cookiesFile", &st.CookiesFile)
		})
		mgr.pump()
		writeJSON(w, 200, settingsView())
	})
	api("/api/pickfolder", func(w http.ResponseWriter, r *http.Request) {
		dir := pickFolder("اختر مجلد الحفظ")
		if dir != "" {
			if err := os.MkdirAll(dir, 0o755); err != nil {
				writeErr(w, &AppError{Type: "generic", Detail: "تعذّر استخدام هذا المجلد"})
				return
			}
			settings.update(func(st *Settings) { st.SaveDir = dir })
		}
		writeJSON(w, 200, settingsView())
	})
	api("/api/opensave", func(w http.ResponseWriter, r *http.Request) {
		os.MkdirAll(saveRoot(), 0o755)
		openPath(saveRoot())
		writeJSON(w, 200, true)
	})
	api("/api/engine/update", func(w http.ResponseWriter, r *http.Request) {
		updated, v, err := updateEngine()
		if err != nil {
			writeErr(w, err)
			return
		}
		settings.update(func(st *Settings) { st.LastEngineUpd = time.Now().Unix() })
		writeJSON(w, 200, map[string]any{"updated": updated, "version": v})
	})
	api("/api/cache/clear", func(w http.ResponseWriter, r *http.Request) {
		if mgr.active() {
			writeErr(w, &AppError{Type: "generic", Detail: "أوقف التحميلات الجارية أولاً ثم امسح الكاش"})
			return
		}
		before := cacheBytes()
		entries, _ := os.ReadDir(filepath.Join(dataDir, "cache"))
		for _, e := range entries {
			os.RemoveAll(filepath.Join(dataDir, "cache", e.Name()))
		}
		for _, d := range mgr.orphanDirs() {
			os.RemoveAll(d)
		}
		writeJSON(w, 200, map[string]any{"freed": before - cacheBytes(), "cacheBytes": cacheBytes()})
	})
	api("/api/clipboard", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]string{"url": extractURL(clipboardText())})
	})
	api("/api/openurl", func(w http.ResponseWriter, r *http.Request) {
		var b struct{ URL string }
		if readJSON(r, &b) == nil && strings.HasPrefix(b.URL, "http") {
			openURL(b.URL)
		}
		writeJSON(w, 200, true)
	})
	return mux
}

func cacheBytes() int64 {
	n := dirSize(filepath.Join(dataDir, "cache"))
	for _, d := range mgr.orphanDirs() {
		n += dirSize(d)
	}
	return n
}

func (s *server) events(w http.ResponseWriter, r *http.Request) {
	fl, ok := w.(http.Flusher)
	if !ok {
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-store")
	ch := make(chan []byte, 64)
	hub.mu.Lock()
	hub.subs[ch] = true
	hub.mu.Unlock()
	defer func() {
		hub.mu.Lock()
		delete(hub.subs, ch)
		hub.last = time.Now()
		hub.mu.Unlock()
	}()
	fmt.Fprint(w, ": ok\n\n")
	fl.Flush()
	ping := time.NewTicker(20 * time.Second)
	defer ping.Stop()
	for {
		select {
		case <-r.Context().Done():
			return
		case b := <-ch:
			fmt.Fprintf(w, "data: %s\n\n", b)
			fl.Flush()
		case <-ping.C:
			fmt.Fprint(w, ": ping\n\n")
			fl.Flush()
		}
	}
}

func (s *server) analyze(w http.ResponseWriter, r *http.Request) {
	var b struct{ URL string }
	if readJSON(r, &b) != nil {
		return
	}
	raw := strings.TrimSpace(b.URL)
	u := extractURL(raw)
	if u == "" && raw != "" && !strings.Contains(raw, " ") && strings.Contains(raw, ".") {
		u = "https://" + raw
	}
	if u == "" {
		writeErr(w, &AppError{Type: "unsupported", Detail: "ألصق رابطاً صحيحاً يبدأ بـ https://"})
		return
	}
	ctx, cancel := context.WithCancel(r.Context())
	s.analyzeMu.Lock()
	if s.analyzeCtx != nil {
		s.analyzeCtx() // تحليل جديد يلغي السابق
	}
	s.analyzeCtx = cancel
	s.analyzeMu.Unlock()
	defer cancel()

	info := probeDirect(ctx, u)
	var err error
	if info == nil {
		info, err = analyze(ctx, u)
		var ae *AppError
		if err != nil && errors.As(err, &ae) && (ae.Type == "unsupported" || ae.Type == "generic") && ctx.Err() == nil {
			// 1) gallery-dl للصور والكاروسيل  2) وسوم الصفحة كحل أخير.
			if g, gerr := galleryResolve(ctx, u); g != nil {
				info, err = g, nil
			} else if errors.As(gerr, &ae) && ae.Type == "private" {
				err = gerr
			} else if og := scrapeOpenGraph(ctx, u); og != nil {
				info, err = og, nil
			}
		}
	}
	if ctx.Err() != nil {
		writeJSON(w, 499, map[string]any{"error": "cancelled"})
		return
	}
	if err != nil {
		writeErr(w, err)
		return
	}
	info.Duplicate = history.find(info.SourceURL, info.MediaKey)
	writeJSON(w, 200, info)
}

func (s *server) download(w http.ResponseWriter, r *http.Request) {
	var b struct{ Specs []Spec }
	if readJSON(r, &b) != nil || len(b.Specs) == 0 {
		writeErr(w, &AppError{Type: "generic", Detail: "طلب غير صالح"})
		return
	}
	for _, spec := range b.Specs {
		if spec.AudioFormat == "" {
			spec.AudioFormat = settings.get().AudioFormat
		}
		mgr.enqueue(spec)
	}
	history.record(b.Specs[0])
	writeJSON(w, 200, true)
}

func (s *server) libraryAction(w http.ResponseWriter, r *http.Request) {
	var b struct {
		Action string
		IDs    []string
		Title  string
	}
	if readJSON(r, &b) != nil {
		return
	}
	switch b.Action {
	case "save":
		ok, lastDir := 0, ""
		for _, id := range b.IDs {
			if dest, err := library.saveToDevice(id); err == nil {
				ok++
				lastDir = filepath.Dir(dest)
			}
		}
		writeJSON(w, 200, map[string]any{"saved": ok, "total": len(b.IDs), "dir": lastDir, "root": saveRoot()})
	case "delete":
		for _, id := range b.IDs {
			library.remove(id, true)
		}
		writeJSON(w, 200, true)
	case "rename":
		if len(b.IDs) == 1 && library.rename(b.IDs[0], b.Title) {
			writeJSON(w, 200, true)
		} else {
			writeErr(w, &AppError{Type: "generic", Detail: "تعذّرت إعادة التسمية"})
		}
	case "open", "reveal":
		if len(b.IDs) == 1 {
			if it, ok := library.get(b.IDs[0]); ok {
				if b.Action == "open" {
					openPath(it.Path)
				} else {
					revealPath(it.Path)
				}
			}
		}
		writeJSON(w, 200, true)
	default:
		writeErr(w, &AppError{Type: "generic", Detail: "إجراء غير معروف"})
	}
}

// media يقدّم ملف المكتبة للمعاينة داخل البرنامج (مع دعم Range للتقديم في الفيديو).
func (s *server) media(w http.ResponseWriter, r *http.Request) {
	it, ok := library.get(r.URL.Query().Get("id"))
	if !ok {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", it.Mime)
	http.ServeFile(w, r, it.Path)
}

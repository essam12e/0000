package main

import (
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// ---------------------------------------------------------------- الإعدادات

type settingsStore struct {
	mu sync.RWMutex
	v  Settings
	st store[Settings]
}

var settings = &settingsStore{}

func (s *settingsStore) init() {
	s.st.path = filepath.Join(dataDir, "settings.json")
	s.v = Settings{AudioFormat: "mp3", MaxParallel: 3}
	s.st.load(&s.v)
	if s.v.MaxParallel < 1 || s.v.MaxParallel > 5 {
		s.v.MaxParallel = 3
	}
	if s.v.AudioFormat != "m4a" {
		s.v.AudioFormat = "mp3"
	}
}

func (s *settingsStore) get() Settings {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.v
}

func (s *settingsStore) update(f func(*Settings)) Settings {
	s.mu.Lock()
	f(&s.v)
	if s.v.MaxParallel < 1 {
		s.v.MaxParallel = 1
	}
	if s.v.MaxParallel > 5 {
		s.v.MaxParallel = 5
	}
	v := s.v
	s.mu.Unlock()
	s.st.save(v)
	hub.publish("settings", settingsView())
	return v
}

// saveRoot مجلد الحفظ الفعلي: المخصص أو «التنزيلات\داونلودر».
func saveRoot() string {
	if d := settings.get().SaveDir; d != "" {
		return d
	}
	return filepath.Join(defaultDownloads(), "داونلودر")
}

func folderOf(item LibItem) string {
	if subExts[item.Ext] {
		return "فيديو" // ملف الترجمة يُحفظ بجانب الفيديو حتى تلتقطه المشغّلات
	}
	switch item.Kind {
	case "video":
		return "فيديو"
	case "audio":
		return "صوتيات"
	case "image":
		return "صور"
	}
	return "أخرى"
}

func settingsView() map[string]any {
	s := settings.get()
	return map[string]any{"settings": s, "saveRoot": saveRoot(), "customSave": s.SaveDir != ""}
}

// ---------------------------------------------------------------- المكتبة

type libraryStore struct {
	mu    sync.RWMutex
	items []LibItem
	st    store[[]LibItem]
}

var library = &libraryStore{}

func (l *libraryStore) init() {
	l.st.path = filepath.Join(dataDir, "library.json")
	var loaded []LibItem
	l.st.load(&loaded)
	for _, it := range loaded {
		if fileSize(it.Path) > 0 {
			l.items = append(l.items, it)
		}
	}
}

func (l *libraryStore) list() []LibItem {
	l.mu.RLock()
	defer l.mu.RUnlock()
	out := make([]LibItem, len(l.items))
	copy(out, l.items)
	return out
}

func (l *libraryStore) get(id string) (LibItem, bool) {
	l.mu.RLock()
	defer l.mu.RUnlock()
	for _, it := range l.items {
		if it.ID == id {
			return it, true
		}
	}
	return LibItem{}, false
}

// changed يُستدعى والقفل ممسوك: يحفظ ويبثّ نسخة من القائمة (بلا إعادة قفل).
func (l *libraryStore) changed() {
	snap := append([]LibItem{}, l.items...)
	l.st.save(snap)
	go hub.publish("library", snap)
}

func (l *libraryStore) add(items []LibItem) {
	l.mu.Lock()
	l.items = append(items, l.items...)
	l.changed()
	l.mu.Unlock()
}

// remove يحذف العنصر من الفهرس، ومع deleteFile يحذف الملف من مساحة البرنامج.
func (l *libraryStore) remove(id string, deleteFile bool) {
	l.mu.Lock()
	defer l.mu.Unlock()
	for i, it := range l.items {
		if it.ID == id {
			if deleteFile {
				os.Remove(it.Path)
			}
			os.Remove(filepath.Join(dataDir, "thumbs", it.ID+".jpg"))
			if dir := filepath.Dir(it.Path); filepath.Dir(dir) == filepath.Join(dataDir, "library") {
				if left, _ := os.ReadDir(dir); len(left) == 0 {
					os.Remove(dir)
				}
			}
			l.items = append(l.items[:i], l.items[i+1:]...)
			l.changed()
			return
		}
	}
}

func (l *libraryStore) rename(id, title string) bool {
	title = strings.TrimSpace(title)
	if title == "" {
		return false
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	for i, it := range l.items {
		if it.ID != id {
			continue
		}
		dir, ext := filepath.Dir(it.Path), filepath.Ext(it.Path)
		dest := filepath.Join(dir, safeName(title)+ext)
		for n := 2; dest != it.Path && fileSize(dest) > 0; n++ {
			dest = filepath.Join(dir, safeName(title)+" ("+itoa(n)+")"+ext)
		}
		if dest != it.Path && os.Rename(it.Path, dest) != nil {
			return false
		}
		l.items[i].Title, l.items[i].Path = title, dest
		l.changed()
		return true
	}
	return false
}

func itoa(n int) string {
	if n == 0 {
		return "0"
	}
	var b []byte
	for n > 0 {
		b = append([]byte{byte('0' + n%10)}, b...)
		n /= 10
	}
	return string(b)
}

// saveToDevice ينقل الملف إلى مجلد الحفظ (فيديو/صوتيات/صور/أخرى) ثم يزيله من البرنامج.
// على القرص نفسه النقل فوري وذرّي؛ وبين قرصين يُنسخ ويُتحقق من الحجم قبل حذف الأصل. أي فشل يُبقي الأصل كما هو.
func (l *libraryStore) saveToDevice(id string) (string, error) {
	it, ok := l.get(id)
	if !ok {
		return "", os.ErrNotExist
	}
	dir := filepath.Join(saveRoot(), folderOf(it))
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", err
	}
	name := safeName(filepath.Base(it.Path))
	dest := filepath.Join(dir, name)
	for n := 1; exists(dest); n++ {
		dest = filepath.Join(dir, strings.TrimSuffix(name, filepath.Ext(name))+" ("+itoa(n)+")"+filepath.Ext(name))
	}
	size := fileSize(it.Path)
	if os.Rename(it.Path, dest) != nil {
		if err := copyFile(it.Path, dest); err != nil || fileSize(dest) != size {
			os.Remove(dest)
			if err == nil {
				err = os.ErrInvalid
			}
			return "", err
		}
	}
	if fileSize(dest) != size {
		return "", os.ErrInvalid
	}
	l.remove(id, true) // يحذف الأصل إن بقي (حالة النسخ) ويزيل العنصر من الفهرس
	return dest, nil
}

func exists(p string) bool { _, err := os.Stat(p); return err == nil }

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, in); err != nil {
		out.Close()
		return err
	}
	return out.Close()
}

// ---------------------------------------------------------------- السجلّ

type historyStore struct {
	mu      sync.RWMutex
	entries []HistoryEntry
	st      store[[]HistoryEntry]
}

var history = &historyStore{}

func (h *historyStore) init() {
	h.st.path = filepath.Join(dataDir, "history.json")
	h.st.load(&h.entries)
}

func (h *historyStore) list() []HistoryEntry {
	h.mu.RLock()
	defer h.mu.RUnlock()
	out := make([]HistoryEntry, len(h.entries))
	copy(out, h.entries)
	return out
}

// changed يُستدعى والقفل ممسوك.
func (h *historyStore) changed() {
	snap := append([]HistoryEntry{}, h.entries...)
	h.st.save(snap)
	go hub.publish("history", snap)
}

func (h *historyStore) record(spec Spec) {
	h.mu.Lock()
	e := HistoryEntry{ID: newID(), URL: spec.URL, Key: spec.MediaKey, Title: spec.Title, Thumbnail: spec.Thumbnail,
		Label: spec.Label, Platform: spec.Platform, At: time.Now().UnixMilli()}
	h.entries = append([]HistoryEntry{e}, h.entries...)
	if len(h.entries) > 500 {
		h.entries = h.entries[:500]
	}
	h.changed()
	h.mu.Unlock()
}

// find آخر مرة حُمّل فيها هذا المحتوى (بمعرّف المنصة أو بالرابط الموحّد).
func (h *historyStore) find(url, key string) *HistoryEntry {
	n := normalizeURL(url)
	h.mu.RLock()
	defer h.mu.RUnlock()
	for i := range h.entries {
		e := h.entries[i]
		if (key != "" && e.Key == key) || normalizeURL(e.URL) == n {
			return &e
		}
	}
	return nil
}

func (h *historyStore) remove(id string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	for i, e := range h.entries {
		if e.ID == id {
			h.entries = append(h.entries[:i], h.entries[i+1:]...)
			break
		}
	}
	h.changed()
}

func (h *historyStore) clear() {
	h.mu.Lock()
	h.entries = nil
	h.changed()
	h.mu.Unlock()
}

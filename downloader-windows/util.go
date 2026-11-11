package main

import (
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"mime"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"sync"
)

func newID() string {
	b := make([]byte, 6)
	rand.Read(b)
	return hex.EncodeToString(b)
}

// ---- تخزين JSON بسيط وآمن (كتابة ذرّية)

type store[T any] struct {
	mu   sync.Mutex
	path string
}

func (s *store[T]) load(into *T) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if b, err := os.ReadFile(s.path); err == nil {
		json.Unmarshal(b, into)
	}
}

func (s *store[T]) save(v T) {
	s.mu.Lock()
	defer s.mu.Unlock()
	b, _ := json.MarshalIndent(v, "", " ")
	tmp := s.path + ".tmp"
	if os.WriteFile(tmp, b, 0o644) == nil {
		os.Rename(tmp, s.path)
	}
}

// ---- أنواع الملفات

var extMime = map[string]string{
	"m4a": "audio/mp4", "mp3": "audio/mpeg", "opus": "audio/ogg", "ogg": "audio/ogg", "aac": "audio/aac",
	"wav": "audio/wav", "flac": "audio/flac", "weba": "audio/webm",
	"mp4": "video/mp4", "m4v": "video/mp4", "mkv": "video/x-matroska", "webm": "video/webm", "mov": "video/quicktime",
	"3gp": "video/3gpp", "ts": "video/mp2t", "flv": "video/x-flv",
	"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png", "webp": "image/webp", "gif": "image/gif",
	"heic": "image/heic", "avif": "image/avif", "bmp": "image/bmp",
	"srt": "application/x-subrip", "vtt": "text/vtt", "ass": "text/x-ssa", "lrc": "text/plain",
}

var subExts = map[string]bool{"srt": true, "vtt": true, "ass": true, "srv3": true, "ttml": true, "json3": true, "lrc": true}

func extOf(name string) string { return strings.ToLower(strings.TrimPrefix(filepath.Ext(name), ".")) }

func mimeOfExt(ext string) string {
	if m, ok := extMime[strings.ToLower(ext)]; ok {
		return m
	}
	if m := mime.TypeByExtension("." + ext); m != "" {
		return strings.Split(m, ";")[0]
	}
	return "application/octet-stream"
}

func kindOfMime(m string) string {
	switch {
	case strings.HasPrefix(m, "video/"):
		return "video"
	case strings.HasPrefix(m, "audio/"):
		return "audio"
	case strings.HasPrefix(m, "image/"):
		return "image"
	}
	return "other"
}

func extOfMime(m string) string {
	m = strings.ToLower(strings.TrimSpace(strings.Split(m, ";")[0]))
	keys := make([]string, 0, len(extMime))
	for k := range extMime {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for _, k := range []string{"mp4", "mp3", "jpg", "m4a", "webm", "png"} { // امتدادات مفضّلة أولاً
		if extMime[k] == m {
			return k
		}
	}
	for _, k := range keys {
		if extMime[k] == m {
			return k
		}
	}
	return ""
}

var badName = regexp.MustCompile(`[\\/:*?"<>|\x00-\x1f]`)

// safeName اسم ملف صالح لويندوز.
func safeName(name string) string {
	n := strings.Trim(strings.TrimSpace(badName.ReplaceAllString(name, "_")), ".")
	if r := []rune(n); len(r) > 120 {
		n = string(r[:120])
	}
	if n == "" {
		n = "file"
	}
	return n
}

var urlRe = regexp.MustCompile(`(?i)https?://[^\s<>"']+`)

func extractURL(text string) string {
	return strings.TrimRight(urlRe.FindString(text), ".,)]!؟،")
}

var trackingParams = map[string]bool{"si": true, "igshid": true, "igsh": true, "fbclid": true, "feature": true,
	"share": true, "ref": true, "ref_src": true, "s": true, "t": true, "pp": true}

// normalizeURL صيغة موحّدة للرابط لكشف التكرار (بلا وسوم تتبّع).
func normalizeURL(raw string) string {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil {
		return strings.ToLower(raw)
	}
	host := strings.TrimPrefix(strings.TrimPrefix(strings.ToLower(u.Host), "www."), "m.")
	p := strings.TrimRight(u.Path, "/")
	if host == "youtu.be" {
		return "youtube.com/watch?v=" + strings.TrimPrefix(p, "/")
	}
	var kept []string
	for k, vs := range u.Query() {
		lk := strings.ToLower(k)
		if strings.HasPrefix(lk, "utm_") || trackingParams[lk] {
			continue
		}
		for _, v := range vs {
			kept = append(kept, k+"="+v)
		}
	}
	sort.Strings(kept)
	out := host + strings.ToLower(p)
	if len(kept) > 0 {
		out += "?" + strings.Join(kept, "&")
	}
	return out
}

func fileSize(p string) int64 {
	if st, err := os.Stat(p); err == nil {
		return st.Size()
	}
	return 0
}

func dirSize(p string) int64 {
	var n int64
	filepath.Walk(p, func(_ string, info os.FileInfo, err error) error {
		if err == nil && !info.IsDir() {
			n += info.Size()
		}
		return nil
	})
	return n
}

package main

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// التحميل المباشر للروابط التي تشير إلى ملف + استخراج وسائط الصفحة كحل أخير.

const browserUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

var httpClient = &http.Client{Timeout: 0}

var platformHosts = []string{"youtube.com", "youtu.be", "instagram.com", "tiktok.com", "twitter.com", "x.com", "facebook.com",
	"fb.watch", "fb.com", "snapchat.com", "pinterest.", "pin.it", "vimeo.com", "dailymotion.com", "twitch.tv", "reddit.com",
	"soundcloud.com", "threads.net", "threads.com", "t.co"}

func isPlatform(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil {
		return false
	}
	host := strings.ToLower(u.Hostname())
	for _, h := range platformHosts {
		if host == h || strings.HasSuffix(host, "."+h) || strings.HasSuffix(h, ".") && strings.Contains(host, h) {
			return true
		}
	}
	return false
}

var reCD = regexp.MustCompile(`(?i)filename\*?=(?:UTF-8'')?"?([^";]+)"?`)

func nameFor(raw string, resp *http.Response, mime string) string {
	name := ""
	if resp != nil {
		if m := reCD.FindStringSubmatch(resp.Header.Get("Content-Disposition")); m != nil {
			name = m[1]
		}
	}
	if name == "" {
		if u, err := url.Parse(raw); err == nil {
			name = path.Base(u.Path)
		}
	}
	if d, err := url.PathUnescape(name); err == nil {
		name = d
	}
	name = safeName(name)
	if name == "file" || name == "_" {
		name = fmt.Sprintf("file-%d", time.Now().Unix())
	}
	if e := extOf(name); e == "" || len(e) > 5 {
		if x := extOfMime(mime); x != "" {
			name += "." + x
		}
	}
	return name
}

// probeDirect يفحص إن كان الرابط ملفاً مباشراً. يعيد nil إن كان صفحة ويب.
func probeDirect(ctx context.Context, raw string) *MediaInfo {
	if isPlatform(raw) {
		return nil
	}
	ctx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()
	do := func(method string, rng bool) *http.Response {
		req, err := http.NewRequestWithContext(ctx, method, raw, nil)
		if err != nil {
			return nil
		}
		req.Header.Set("User-Agent", browserUA)
		if rng {
			req.Header.Set("Range", "bytes=0-0")
		}
		resp, err := httpClient.Do(req)
		if err != nil {
			return nil
		}
		return resp
	}
	resp := do("HEAD", false)
	if resp == nil || resp.StatusCode >= 400 {
		if resp != nil {
			resp.Body.Close()
		}
		resp = do("GET", true)
	}
	if resp == nil {
		return nil
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		return nil
	}
	u, _ := url.Parse(raw)
	ext := extOf(u.Path)
	mime := strings.ToLower(strings.TrimSpace(strings.Split(resp.Header.Get("Content-Type"), ";")[0]))
	_, byExt := extMime[ext]
	kind := kindOfMime(mime)
	if kind == "other" && byExt {
		kind = kindOfMime(mimeOfExt(ext))
	}
	isFile := kind != "other" || strings.Contains(strings.ToLower(resp.Header.Get("Content-Disposition")), "attachment") ||
		mime == "application/octet-stream" || mime == "application/pdf" || mime == "application/zip"
	if !isFile || strings.Contains(mime, "mpegurl") || ext == "m3u8" || ext == "mpd" || subExts[ext] && kind == "other" && mime == "text/html" {
		return nil
	}
	var size int64
	if cr := resp.Header.Get("Content-Range"); cr != "" {
		size, _ = strconv.ParseInt(cr[strings.LastIndex(cr, "/")+1:], 10, 64)
	} else if resp.ContentLength > 1 {
		size = resp.ContentLength
	}
	if kindOfMime(mime) == "other" && byExt {
		mime = mimeOfExt(ext)
	}
	name := nameFor(resp.Request.URL.String(), resp, mime)
	info := &MediaInfo{SourceURL: raw, Title: name, Uploader: u.Hostname(), Platform: "رابط مباشر",
		Options: []Quality{}, Items: []PlaylistItem{}, AudioTracks: []AudioTrack{}, Subtitles: []SubTrack{},
		Direct: []DirectFile{{URL: raw, Name: name, Mime: mime, Size: size, Kind: kind}}}
	if kind == "image" {
		info.Thumbnail = raw
	}
	return info
}

// scrapeOpenGraph حل أخير: وسوم og:video / og:image من الصفحة.
func scrapeOpenGraph(ctx context.Context, raw string) *MediaInfo {
	ctx, cancel := context.WithTimeout(ctx, 25*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, "GET", raw, nil)
	if err != nil {
		return nil
	}
	req.Header.Set("User-Agent", browserUA)
	req.Header.Set("Accept", "text/html")
	resp, err := httpClient.Do(req)
	if err != nil {
		return nil
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 || !strings.Contains(resp.Header.Get("Content-Type"), "html") {
		return nil
	}
	b, _ := io.ReadAll(io.LimitReader(resp.Body, 3<<20))
	html := string(b)
	meta := func(prop string) []string {
		p := regexp.QuoteMeta(prop)
		var out []string
		for _, re := range []*regexp.Regexp{
			regexp.MustCompile(`(?i)<meta[^>]+(?:property|name)=["']` + p + `["'][^>]*?content=["']([^"']+)["']`),
			regexp.MustCompile(`(?i)<meta[^>]+content=["']([^"']+)["'][^>]*?(?:property|name)=["']` + p + `["']`),
		} {
			for _, m := range re.FindAllStringSubmatch(html, -1) {
				out = append(out, strings.ReplaceAll(m[1], "&amp;", "&"))
			}
		}
		return out
	}
	pick := func(props ...string) string {
		for _, p := range props {
			for _, v := range meta(p) {
				if strings.HasPrefix(v, "http") && !strings.Contains(v, "/embed") {
					return v
				}
			}
		}
		return ""
	}
	u, _ := url.Parse(raw)
	title := u.Hostname()
	if t := meta("og:title"); len(t) > 0 {
		title = t[0]
	}
	image := pick("og:image:secure_url", "og:image")
	info := &MediaInfo{SourceURL: raw, Title: title, Uploader: u.Hostname(), Thumbnail: image, Platform: "وسائط الصفحة",
		Options: []Quality{}, Items: []PlaylistItem{}, AudioTracks: []AudioTrack{}, Subtitles: []SubTrack{}}
	if v := pick("og:video:secure_url", "og:video:url", "og:video"); v != "" {
		info.Direct = []DirectFile{{URL: v, Name: nameFor(v, nil, "video/mp4"), Mime: "video/mp4", Kind: "video"}}
	} else if image != "" {
		info.Direct = []DirectFile{{URL: image, Name: nameFor(image, nil, "image/jpeg"), Mime: "image/jpeg", Kind: "image"}}
	} else {
		return nil
	}
	return info
}

// directDownload تحميل مباشر مع استكمال عبر Range.
func directDownload(ctx context.Context, raw, dest string, onProgress func(Progress)) error {
	part := dest + ".part"
	existing := fileSize(part)
	req, err := http.NewRequestWithContext(ctx, "GET", raw, nil)
	if err != nil {
		return &AppError{Type: "unsupported", Detail: err.Error()}
	}
	req.Header.Set("User-Agent", browserUA)
	if existing > 0 {
		req.Header.Set("Range", fmt.Sprintf("bytes=%d-", existing))
	}
	resp, err := httpClient.Do(req)
	if err != nil {
		if ctx.Err() != nil {
			return ctx.Err()
		}
		if isOffline(err) {
			return &AppError{Type: "offline", Detail: err.Error()}
		}
		return &AppError{Type: "generic", Detail: err.Error()}
	}
	defer resp.Body.Close()
	if resp.StatusCode == 416 {
		os.Remove(part)
		return &AppError{Type: "generic", Detail: "أعد المحاولة"}
	}
	if resp.StatusCode >= 400 {
		t := "generic"
		if resp.StatusCode == 401 || resp.StatusCode == 403 {
			t = "private"
		}
		return &AppError{Type: t, Detail: fmt.Sprintf("HTTP %d", resp.StatusCode)}
	}
	flags := os.O_CREATE | os.O_WRONLY
	if resp.StatusCode == 206 {
		flags |= os.O_APPEND
	} else {
		flags |= os.O_TRUNC
		existing = 0
	}
	f, err := os.OpenFile(part, flags, 0o644)
	if err != nil {
		return &AppError{Type: "generic", Detail: err.Error()}
	}
	total := int64(-1)
	if resp.ContentLength > 0 {
		total = resp.ContentLength + existing
	}
	done := existing
	buf := make([]byte, 128*1024)
	winStart, winBytes := time.Now(), int64(0)
	speed := ""
	var eta int64
	for {
		n, rerr := resp.Body.Read(buf)
		if n > 0 {
			if _, werr := f.Write(buf[:n]); werr != nil {
				f.Close()
				return &AppError{Type: "generic", Detail: werr.Error()}
			}
			done += int64(n)
			winBytes += int64(n)
			if dt := time.Since(winStart).Seconds(); dt >= 0.5 {
				bps := float64(winBytes) / dt
				if bps >= 1<<20 {
					speed = fmt.Sprintf("%.1f م.ب/ث", bps/(1<<20))
				} else {
					speed = fmt.Sprintf("%.0f ك.ب/ث", bps/1024)
				}
				if total > 0 && bps > 0 {
					eta = int64(float64(total-done) / bps)
				}
				winStart, winBytes = time.Now(), 0
			}
			p := Progress{Fraction: -1, Speed: speed, ETA: eta, Stage: "تحميل مباشر"}
			if total > 0 {
				p.Fraction = float64(done) / float64(total)
			}
			onProgress(p)
		}
		if rerr == io.EOF {
			break
		}
		if rerr != nil {
			f.Close()
			if ctx.Err() != nil {
				return ctx.Err()
			}
			return &AppError{Type: "offline", Detail: rerr.Error()}
		}
	}
	f.Close()
	return os.Rename(part, dest)
}

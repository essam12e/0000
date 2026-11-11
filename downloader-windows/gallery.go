package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os/exec"
	"strconv"
	"strings"
	"time"
)

// gallery-dl: يكمّل yt-dlp في منشورات الصور والكاروسيل (انستجرام، تويتر/X، بنترست...).

func galleryCmd(ctx context.Context, tool string, args ...string) *exec.Cmd {
	s := settings.get()
	base := []string{"--config-ignore", "--no-colors", "--no-input"}
	if s.CookiesBrowser != "" {
		base = append(base, "--cookies-from-browser", s.CookiesBrowser)
	} else if s.CookiesFile != "" {
		base = append(base, "--cookies", s.CookiesFile)
	}
	cmd := exec.CommandContext(ctx, tool, append(base, args...)...)
	hideWindow(cmd)
	cmd.Cancel = func() error { killTree(cmd); return nil }
	return cmd
}

// galleryResolve يعيد nil إن لم يجد شيئاً؛ وخطأ مصنّفاً إن كان السبب واضحاً.
func galleryResolve(ctx context.Context, url string) (*MediaInfo, error) {
	tool := ensureGalleryDl(ctx)
	if tool == "" {
		return nil, nil
	}
	ctx, cancel := context.WithTimeout(ctx, 3*time.Minute)
	defer cancel()
	cmd := galleryCmd(ctx, tool, "-j", "--range", "1-200", "-R", "2", "--http-timeout", "30", url)
	var out, errb bytes.Buffer
	cmd.Stdout, cmd.Stderr = &out, &errb
	cmd.Run()
	if ctx.Err() != nil {
		return nil, nil
	}
	errText := strings.ReplaceAll(errb.String(), "[error]", "ERROR:")
	var msgs [][]any
	if json.Unmarshal(bytes.TrimSpace(out.Bytes()), &msgs) != nil {
		return nil, classify(errText)
	}
	info := &MediaInfo{SourceURL: url, IsPlaylist: true, ViaGallery: true, Options: []Quality{}, Direct: []DirectFile{},
		AudioTracks: []AudioTrack{}, Subtitles: []SubTrack{}}
	var meta map[string]any
	text := func(m map[string]any, keys ...string) string {
		for _, k := range keys {
			switch v := m[k].(type) {
			case string:
				if strings.TrimSpace(v) != "" {
					return strings.TrimSpace(v)
				}
			case map[string]any:
				for _, kk := range []string{"name", "nick", "username", "full_name"} {
					if s, ok := v[kk].(string); ok && s != "" {
						return s
					}
				}
			}
		}
		return ""
	}
	for _, m := range msgs {
		if len(m) < 2 {
			continue
		}
		code, _ := m[0].(float64)
		switch int(code) {
		case 2:
			if meta == nil {
				meta, _ = m[1].(map[string]any)
			}
		case 3:
			fileURL, _ := m[1].(string)
			var kw map[string]any
			if len(m) > 2 {
				kw, _ = m[2].(map[string]any)
			}
			if meta == nil {
				meta = kw
			}
			ext := text(kw, "extension")
			if ext == "" {
				ext = extOf(strings.SplitN(fileURL, "?", 2)[0])
			}
			kind := kindOfMime(mimeOfExt(ext))
			n := len(info.Items) + 1
			label := map[string]string{"video": "فيديو", "audio": "صوت", "image": "صورة", "other": "ملف"}[kind]
			it := PlaylistItem{Index: n, Title: fmt.Sprintf("%s %d", label, n)}
			if ext != "" && len(ext) <= 5 {
				it.Title += "  ·  " + strings.ToUpper(ext)
			}
			if kind == "image" && strings.HasPrefix(fileURL, "http") {
				it.Thumbnail = fileURL
				if info.Thumbnail == "" {
					info.Thumbnail = fileURL
				}
			}
			info.Items = append(info.Items, it)
		case -1:
			if e, ok := m[1].(map[string]any); ok {
				errText = "ERROR: " + text(e, "error") + ": " + text(e, "message") + "\n" + errText
			}
		}
	}
	if len(info.Items) == 0 {
		if strings.Contains(errText, "ERROR") {
			return nil, classify(errText)
		}
		return nil, nil
	}
	if meta != nil {
		info.Title = strings.SplitN(text(meta, "title", "description", "content", "caption"), "\n", 2)[0]
		info.Uploader = text(meta, "username", "author", "user", "owner", "uploader", "fullname")
		if c := text(meta, "category"); c != "" {
			info.Platform = strings.ToUpper(c[:1]) + c[1:]
		}
	}
	if info.Title == "" {
		info.Title = info.Items[0].Title
	}
	if r := []rune(info.Title); len(r) > 90 {
		info.Title = string(r[:90])
	}
	return info, nil
}

func galleryDownload(ctx context.Context, spec Spec, dir string, onProgress func(Progress)) error {
	tool := ensureGalleryDl(ctx)
	if tool == "" {
		return &AppError{Type: "engine", Detail: "تعذّر تجهيز gallery-dl"}
	}
	args := []string{"-D", dir, "--no-mtime", "-R", "3", "--http-timeout", "30"}
	if len(spec.Items) > 0 {
		var idx []string
		for _, i := range spec.Items {
			idx = append(idx, strconv.Itoa(i))
		}
		args = append(args, "--range", strings.Join(idx, ","))
	}
	cmd := galleryCmd(ctx, tool, append(args, spec.URL)...)
	stdout, _ := cmd.StdoutPipe()
	var errb bytes.Buffer
	cmd.Stderr = &errb
	if err := cmd.Start(); err != nil {
		return &AppError{Type: "engine", Detail: err.Error()}
	}
	total, done := len(spec.Items), 0
	if total == 0 {
		total = 1
	}
	onProgress(Progress{Stage: "جاري التحضير"})
	sc := bufio.NewScanner(stdout)
	for sc.Scan() {
		if strings.TrimSpace(sc.Text()) != "" {
			done++
			f := float64(done) / float64(total)
			if f > 0.99 {
				f = 0.99
			}
			onProgress(Progress{Fraction: f, Stage: fmt.Sprintf("تحميل (%d/%d)", done, total)})
		}
	}
	cmd.Wait()
	if ctx.Err() != nil {
		return ctx.Err()
	}
	if len(outputFiles(dir)) == 0 {
		return classify(strings.ReplaceAll(errb.String(), "[error]", "ERROR:"))
	}
	return nil
}

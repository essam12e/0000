package main

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
)

// إنهاء الترجمة بعد التحميل: yt-dlp ينزّل ملفات SRT بجانب كل فيديو (المطلوبة إن أتاحتها المنصة،
// والأصلية كبديل). هنا نختار الأنسب، ننظّفه، ثم ندمجه داخل الفيديو أو نتركه ملف SRT باسم الفيديو.

var videoExts = map[string]bool{"mp4": true, "mkv": true, "webm": true, "mov": true, "m4v": true}

type cue struct {
	start, end int64
	text       string
}

type subResult struct {
	videos, wanted, original, none int
	origLang                       string
}

// notice نص التنبيه للمستخدم، أو فارغ إذا جاءت كل الترجمات كما طُلبت.
func (r subResult) notice(want string) string {
	switch {
	case r.videos == 0 || r.wanted == r.videos:
		return ""
	case r.videos == 1 && r.original == 1:
		return "المنصة رفضت ترجمة " + langName(want) + " الآلية؛ أُرفقت الترجمة الأصلية (" + langName(r.origLang) + ")"
	case r.videos == 1:
		return "اكتمل التحميل، لكن لا توجد ترجمة متاحة لهذا الفيديو"
	}
	s := fmt.Sprintf("الترجمة: %d بـ%s", r.wanted, langName(want))
	if r.original > 0 {
		s += fmt.Sprintf("، %d بلغتها الأصلية", r.original)
	}
	if r.none > 0 {
		s += fmt.Sprintf("، %d بلا ترجمة", r.none)
	}
	return s
}

func finalizeSubs(ctx context.Context, spec Spec, dir string, onStage func(string)) subResult {
	want := spec.SubLang
	wantBase := strings.SplitN(want, "-", 2)[0]
	var res subResult
	for _, video := range outputFiles(dir) {
		if !videoExts[extOf(video)] {
			continue
		}
		res.videos++
		prefix := strings.TrimSuffix(filepath.Base(video), filepath.Ext(video)) + "."
		subs := map[string]string{} // رمز اللغة → المسار
		entries, _ := os.ReadDir(dir)
		for _, e := range entries {
			n := e.Name()
			if strings.HasPrefix(n, prefix) && extOf(n) == "srt" && fileSize(filepath.Join(dir, n)) > 0 {
				subs[strings.TrimSuffix(strings.TrimPrefix(n, prefix), ".srt")] = filepath.Join(dir, n)
			}
		}
		pick, lang, rolling := "", wantBase, false
		if p, ok := subs[want]; ok {
			pick = p
		} else {
			for k, p := range subs {
				if k == wantBase || strings.HasPrefix(k, wantBase+"-") {
					pick = p
					break
				}
			}
		}
		if pick != "" {
			res.wanted++
		} else {
			// البديل: الترجمة الأصلية (المصدر المحدد، ثم الإنجليزية، ثم أي ترجمة).
			key := ""
			if _, ok := subs[spec.SubSrc]; ok && spec.SubSrc != "" {
				key = spec.SubSrc
			} else {
				for k := range subs {
					if key == "" || strings.HasPrefix(k, "en") && !strings.HasPrefix(key, "en") {
						key = k
					}
				}
			}
			if key == "" {
				res.none++
				continue
			}
			pick, lang, rolling = subs[key], strings.SplitN(key, "-", 2)[0], strings.HasSuffix(key, "-orig")
			res.original++
			res.origLang = key
		}
		raw, _ := os.ReadFile(pick)
		final := filepath.Join(dir, prefix+lang+".final.srt")
		writeSrt(final, cleanCues(parseSrt(string(raw)), rolling))
		for _, p := range subs {
			os.Remove(p)
		}
		if spec.SubEmbed {
			onStage("دمج الترجمة في الفيديو")
			embedSub(ctx, video, final, lang)
			os.Remove(final)
		} else {
			os.Rename(final, filepath.Join(dir, prefix+lang+".srt"))
		}
	}
	res.none = res.videos - res.wanted - res.original
	return res
}

var reStamp = regexp.MustCompile(`(\d+):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d+):(\d{2}):(\d{2})[,.](\d{3})`)
var reTag = regexp.MustCompile(`<[^>]+>`)

func parseSrt(text string) []cue {
	var cues []cue
	var lines []string
	start, end := int64(-1), int64(0)
	flush := func() {
		if start >= 0 && len(lines) > 0 {
			cues = append(cues, cue{start, end, strings.Join(lines, "\n")})
		}
		start, lines = -1, nil
	}
	text = strings.TrimPrefix(strings.ReplaceAll(text, "\r", ""), string(rune(0xFEFF)))
	for _, raw := range strings.Split(text, "\n") {
		if m := reStamp.FindStringSubmatch(raw); m != nil {
			flush()
			g := make([]int64, 8)
			for i := range g {
				g[i], _ = strconv.ParseInt(m[i+1], 10, 64)
			}
			start = ((g[0]*60+g[1])*60+g[2])*1000 + g[3]
			end = ((g[4]*60+g[5])*60+g[6])*1000 + g[7]
		} else if strings.TrimSpace(raw) == "" {
			flush()
		} else if start >= 0 {
			t := strings.TrimSpace(reTag.ReplaceAllString(raw, ""))
			t = strings.NewReplacer("&amp;", "&", "&nbsp;", " ", "&lt;", "<", "&gt;", ">").Replace(t)
			if t != "" {
				lines = append(lines, t)
			}
		}
	}
	flush()
	return cues
}

// cleanCues: التفريغ الآلي ليوتيوب (rolling) يكرر السطر السابق في كل مقطع؛ نأخذ السطر الأخير ونحذف المكرر والمقاطع اللحظية.
func cleanCues(cues []cue, rolling bool) []cue {
	var out []cue
	for _, c := range cues {
		text := strings.TrimSpace(c.text)
		if rolling {
			parts := strings.Split(text, "\n")
			text = strings.TrimSpace(parts[len(parts)-1])
			if c.end-c.start < 100 {
				continue
			}
		}
		if text == "" {
			continue
		}
		if n := len(out); n > 0 && out[n-1].text == text {
			if c.end > out[n-1].end {
				out[n-1].end = c.end
			}
			continue
		}
		out = append(out, cue{c.start, c.end, text})
	}
	for i := 0; i+1 < len(out); i++ {
		if out[i].end > out[i+1].start {
			out[i].end = out[i+1].start
		}
	}
	return out
}

func writeSrt(path string, cues []cue) {
	t := func(ms int64) string {
		return fmt.Sprintf("%02d:%02d:%02d,%03d", ms/3600000, ms/60000%60, ms/1000%60, ms%1000)
	}
	var b strings.Builder
	for i, c := range cues {
		fmt.Fprintf(&b, "%d\n%s --> %s\n%s\n\n", i+1, t(c.start), t(c.end), c.text)
	}
	os.WriteFile(path, []byte(b.String()), 0o644)
}

var iso3 = map[string]string{"ar": "ara", "en": "eng", "fr": "fra", "es": "spa", "de": "deu", "tr": "tur", "ru": "rus"}

// embedSub يدمج الترجمة كمسار داخل MP4 بدون إعادة ترميز.
func embedSub(ctx context.Context, video, srt, lang string) bool {
	ff := toolPath("ffmpeg")
	if ff == "" {
		return false
	}
	code := iso3[lang]
	if code == "" {
		code = lang
	}
	out := strings.TrimSuffix(video, filepath.Ext(video)) + ".subs.tmp.mp4"
	cmd := exec.CommandContext(ctx, ff, "-y", "-loglevel", "error", "-i", video, "-i", srt,
		"-map", "0:v", "-map", "0:a?", "-map", "1", "-c", "copy", "-c:s", "mov_text",
		"-metadata:s:s:0", "language="+code, "-disposition:s:0", "default", out)
	hideWindow(cmd)
	if err := cmd.Run(); err != nil || fileSize(out) < fileSize(video)/2 {
		os.Remove(out)
		return false
	}
	os.Remove(video)
	return os.Rename(out, strings.TrimSuffix(video, filepath.Ext(video))+".mp4") == nil
}

// makeThumb صورة مصغّرة لفيديو محمَّل (لعرضها في «تحميلاتي»).
func makeThumb(video, dest string) bool {
	ff := toolPath("ffmpeg")
	if ff == "" {
		return false
	}
	for _, at := range []string{"1", "0"} {
		cmd := exec.Command(ff, "-y", "-loglevel", "error", "-ss", at, "-i", video, "-frames:v", "1", "-vf", "scale=480:-2", dest)
		hideWindow(cmd)
		if cmd.Run() == nil && fileSize(dest) > 0 {
			return true
		}
	}
	return false
}

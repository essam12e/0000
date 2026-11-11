package main

import (
	"bufio"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

// غلاف حول yt-dlp: تحليل الرابط واستخراج الجودات، ثم التحميل مع متابعة التقدّم.

const analyzeTimeout = 4 * time.Minute

func ytdlpCmd(ctx context.Context, args ...string) *exec.Cmd {
	s := settings.get()
	base := []string{"--no-warnings", "--socket-timeout", "30", "--encoding", "utf-8"}
	if ff := toolPath("ffmpeg"); ff != "" {
		base = append(base, "--ffmpeg-location", filepath.Dir(ff))
	}
	if s.CookiesBrowser != "" {
		base = append(base, "--cookies-from-browser", s.CookiesBrowser)
	} else if s.CookiesFile != "" {
		base = append(base, "--cookies", s.CookiesFile)
	}
	cmd := exec.CommandContext(ctx, toolPath("yt-dlp"), append(base, args...)...)
	cmd.Env = append(os.Environ(), "PYTHONIOENCODING=utf-8", "PYTHONUTF8=1")
	hideWindow(cmd)
	cmd.Cancel = func() error { killTree(cmd); return nil }
	return cmd
}

// ---------------------------------------------------------------- تحليل الرابط

func analyze(ctx context.Context, url string) (*MediaInfo, error) {
	if err := waitEngine(ctx); err != nil {
		return nil, err
	}
	ctx, cancel := context.WithTimeout(ctx, analyzeTimeout)
	defer cancel()
	// --write-auto-subs: بدونه لا تُدرج الترجمات المترجمة آلياً من ترجمة يدوية.
	cmd := ytdlpCmd(ctx, "--dump-single-json", "--flat-playlist", "--no-playlist", "--write-auto-subs", url)
	var out, errb bytes.Buffer
	cmd.Stdout, cmd.Stderr = &out, &errb
	err := cmd.Run()
	if ctx.Err() == context.DeadlineExceeded {
		return nil, &AppError{Type: "generic", Detail: "استغرق تحليل الرابط وقتاً أطول من المتوقع"}
	}
	if ctx.Err() != nil {
		return nil, ctx.Err()
	}
	var root map[string]any
	if json.Unmarshal(bytes.TrimSpace(out.Bytes()), &root) != nil || root == nil {
		_ = err
		return nil, classify(errb.String())
	}
	return parseInfo(url, root)
}

func str(m map[string]any, k string) string {
	if v, ok := m[k].(string); ok {
		return strings.TrimSpace(v)
	}
	return ""
}

func num(m map[string]any, k string) (float64, bool) {
	v, ok := m[k].(float64)
	return v, ok
}

func objs(v any) []map[string]any {
	arr, _ := v.([]any)
	out := make([]map[string]any, 0, len(arr))
	for _, x := range arr {
		if m, ok := x.(map[string]any); ok {
			out = append(out, m)
		}
	}
	return out
}

func thumbOf(m map[string]any) string {
	if t := str(m, "thumbnail"); t != "" {
		return t
	}
	th := objs(m["thumbnails"])
	for i := len(th) - 1; i >= 0; i-- {
		if u := str(th[i], "url"); u != "" {
			return u
		}
	}
	return ""
}

func parseInfo(url string, root map[string]any) (*MediaInfo, error) {
	entries := objs(root["entries"])
	if str(root, "_type") == "playlist" && root["entries"] != nil {
		if len(entries) == 1 && entries[0]["formats"] != nil {
			return parseSingle(url, entries[0]), nil
		}
		if len(entries) == 0 {
			return nil, &AppError{Type: "unsupported", Detail: "القائمة فارغة"}
		}
		info := &MediaInfo{SourceURL: url, IsPlaylist: true, HasAudio: true, Platform: str(root, "extractor_key"),
			Title: str(root, "title"), Uploader: firstNonEmpty(str(root, "uploader"), str(root, "channel")), Thumbnail: thumbOf(root)}
		maxSide := 0
		for i, e := range entries {
			d, _ := num(e, "duration")
			title := str(e, "title")
			if title == "" {
				title = fmt.Sprintf("عنصر %d", i+1)
			}
			info.Items = append(info.Items, PlaylistItem{Index: i + 1, Title: title, Thumbnail: thumbOf(e), Duration: int64(d)})
			for _, f := range objs(e["formats"]) {
				if s := shortSide(f); s > maxSide {
					maxSide = s
				}
			}
			if info.Thumbnail == "" {
				info.Thumbnail = thumbOf(e)
			}
		}
		if info.Title == "" {
			info.Title = info.Items[0].Title
		}
		info.Options = presetOptions(maxSide)
		if id := str(root, "id"); id != "" && info.Platform != "Generic" {
			info.MediaKey = info.Platform + ":" + id
		}
		return info, nil
	}
	return parseSingle(url, root), nil
}

func firstNonEmpty(v ...string) string {
	for _, s := range v {
		if s != "" {
			return s
		}
	}
	return ""
}

func shortSide(f map[string]any) int {
	h, ok := num(f, "height")
	if !ok {
		return 0
	}
	if w, ok := num(f, "width"); ok && w > 0 && w < h {
		return int(w)
	}
	return int(h)
}

func sizeOf(f map[string]any, duration float64) (int64, bool, bool) {
	if v, ok := num(f, "filesize"); ok && v > 0 {
		return int64(v), false, true
	}
	if v, ok := num(f, "filesize_approx"); ok && v > 0 {
		return int64(v), true, true
	}
	for _, k := range []string{"tbr", "vbr", "abr"} {
		if v, ok := num(f, k); ok && v > 0 && duration > 0 {
			return int64(v * 125 * duration), true, true
		}
	}
	return 0, true, false
}

func codecRank(v string) int {
	switch {
	case strings.HasPrefix(v, "avc"), strings.HasPrefix(v, "h264"):
		return 3
	case strings.HasPrefix(v, "vp9"), strings.HasPrefix(v, "vp09"), strings.HasPrefix(v, "hev"), strings.HasPrefix(v, "hvc"):
		return 2
	}
	return 1
}

func codecName(v string) string {
	switch {
	case strings.HasPrefix(v, "avc"), strings.HasPrefix(v, "h264"):
		return "H.264"
	case strings.HasPrefix(v, "vp9"), strings.HasPrefix(v, "vp09"):
		return "VP9"
	case strings.HasPrefix(v, "av01"):
		return "AV1"
	case strings.HasPrefix(v, "hev"), strings.HasPrefix(v, "hvc"):
		return "HEVC"
	}
	return ""
}

func stdLabel(side int) int {
	for _, s := range []int{4320, 2160, 1440, 1080, 720, 480, 360, 240, 144} {
		if float64(side) >= float64(s)*0.93 {
			return s
		}
	}
	return side
}

func badgeFor(side int) string {
	switch {
	case side >= 4000:
		return "8K"
	case side >= 2000:
		return "4K"
	case side >= 1340:
		return "2K"
	case side >= 1000:
		return "FHD"
	case side >= 670:
		return "HD"
	}
	return ""
}

func parseSingle(url string, o map[string]any) *MediaInfo {
	duration, _ := num(o, "duration")
	var formats []map[string]any
	for _, f := range objs(o["formats"]) {
		if str(f, "ext") != "mhtml" && str(f, "format_note") != "storyboard" {
			formats = append(formats, f)
		}
	}
	hasVideo := func(f map[string]any) bool {
		v := str(f, "vcodec")
		_, hasH := num(f, "height")
		return (v != "" && v != "none") || (v == "" && hasH)
	}
	hasAudio := func(f map[string]any) bool { return str(f, "acodec") != "none" }
	isAudio := func(f map[string]any) bool {
		a := str(f, "acodec")
		abr, _ := num(f, "abr")
		return str(f, "vcodec") == "none" || (a != "" && a != "none") || str(f, "resolution") == "audio only" || abr > 0
	}
	var audioOnly []map[string]any
	for _, f := range formats {
		if !hasVideo(f) && isAudio(f) {
			audioOnly = append(audioOnly, f)
		}
	}
	rate := func(f map[string]any) float64 {
		if v, ok := num(f, "abr"); ok {
			return v
		}
		v, _ := num(f, "tbr")
		return v
	}
	var bestAudio map[string]any
	for _, f := range audioOnly {
		if bestAudio == nil || (str(f, "ext") == "m4a") != (str(bestAudio, "ext") == "m4a") && str(f, "ext") == "m4a" ||
			(str(f, "ext") == "m4a") == (str(bestAudio, "ext") == "m4a") && rate(f) > rate(bestAudio) {
			bestAudio = f
		}
	}
	var bestAudioSize int64
	bestAudioApprox, haveAudioSize := true, false
	if bestAudio != nil {
		bestAudioSize, bestAudioApprox, haveAudioSize = sizeOf(bestAudio, duration)
	}

	// مجموعة لكل جودة قياسية؛ نختار داخلها الأنسب (H.264 للتوافق، ثم الإطارات، ثم معدل البت).
	groups := map[int][]map[string]any{}
	for _, f := range formats {
		if _, ok := num(f, "height"); ok && hasVideo(f) {
			groups[stdLabel(shortSide(f))] = append(groups[stdLabel(shortSide(f))], f)
		}
	}
	info := &MediaInfo{SourceURL: url, Options: []Quality{}}
	for _, group := range groups {
		best := group[0]
		score := func(f map[string]any) [3]float64 {
			fps, _ := num(f, "fps")
			tbr, _ := num(f, "tbr")
			return [3]float64{float64(codecRank(str(f, "vcodec"))), fps, tbr}
		}
		for _, f := range group[1:] {
			a, b := score(f), score(best)
			if a[0] > b[0] || a[0] == b[0] && (a[1] > b[1] || a[1] == b[1] && a[2] > b[2]) {
				best = f
			}
		}
		side := shortSide(best)
		hf, _ := num(best, "height")
		h := int(hf)
		id := firstNonEmpty(str(best, "format_id"), "best")
		combined := hasAudio(best)
		size, approx, ok := sizeOf(best, duration)
		if ok && !combined && bestAudio != nil {
			if haveAudioSize {
				size += bestAudioSize
				approx = approx || bestAudioApprox
			} else {
				approx = true
			}
		}
		// {A} يُستبدل وقت التحميل بفلتر لغة الصوت المختارة.
		sel := fmt.Sprintf("%s/b[height<=%d]/bv*[height<=%d]+ba/b", id, h, h)
		if !combined && bestAudio != nil {
			sel = fmt.Sprintf("%s+ba{A}[ext=m4a]/%s+ba{A}/%s+ba/bv*[height<=%d]+ba/b[height<=%d]/b", id, id, id, h, h)
		}
		label := fmt.Sprintf("%dp", stdLabel(side))
		if fps, _ := num(best, "fps"); fps > 30 {
			label += strconv.Itoa(int(fps))
		}
		detail := "MP4"
		if c := codecName(str(best, "vcodec")); c != "" {
			detail += " · " + c
		}
		q := Quality{ID: fmt.Sprintf("v%d-%s", side, id), Label: label, Badge: badgeFor(side), Detail: detail, Selector: sel, Side: side, Approx: approx}
		if ok {
			q.Size = size
		}
		info.Options = append(info.Options, q)
	}
	sort.Slice(info.Options, func(i, j int) bool { return info.Options[i].Side > info.Options[j].Side })

	anyVideo := false
	for _, f := range formats {
		if hasVideo(f) {
			anyVideo = true
		}
	}
	audioOnlyMedia := (len(formats) > 0 && !anyVideo) || (len(formats) == 0 && str(o, "vcodec") == "none")
	if len(info.Options) == 0 && !audioOnlyMedia {
		q := Quality{ID: "best", Label: "أفضل جودة", Detail: "MP4", Selector: "bv*+ba/b", Approx: true}
		if v, ok := num(o, "filesize"); ok {
			q.Size = int64(v)
		} else if v, ok := num(o, "filesize_approx"); ok {
			q.Size = int64(v)
		}
		info.Options = append(info.Options, q)
	}

	info.Title = firstNonEmpty(str(o, "title"), str(o, "fulltitle"), "بدون عنوان")
	info.Uploader = firstNonEmpty(str(o, "uploader"), str(o, "channel"), str(o, "creator"))
	info.Thumbnail = thumbOf(o)
	info.Duration = int64(duration)
	info.Platform = str(o, "extractor_key")
	info.HasAudio = audioOnlyMedia || bestAudio != nil || len(formats) == 0
	for _, f := range formats {
		if hasVideo(f) && hasAudio(f) {
			info.HasAudio = true
		}
	}
	if haveAudioSize {
		info.AudioSize = bestAudioSize
	} else if duration > 0 {
		info.AudioSize = int64(duration * 128 * 125)
	}
	if id := str(o, "id"); id != "" && info.Platform != "Generic" {
		info.MediaKey = firstNonEmpty(info.Platform, "x") + ":" + id
	}

	// مسارات الصوت (الدبلجات): تُعرض فقط إذا وُجدت أكثر من لغة.
	seen := map[string]*AudioTrack{}
	for _, f := range audioOnly {
		lang := str(f, "language")
		if lang == "" {
			continue
		}
		pref, _ := num(f, "language_preference")
		orig := strings.Contains(strings.ToLower(str(f, "format_note")), "original") || pref >= 10
		if t, ok := seen[lang]; ok {
			t.Original = t.Original || orig
		} else {
			seen[lang] = &AudioTrack{Lang: lang, Name: langName(lang), Original: orig}
		}
	}
	if len(seen) > 1 {
		for _, t := range seen {
			info.AudioTracks = append(info.AudioTracks, *t)
		}
		rank := func(t AudioTrack) int {
			switch {
			case t.Original:
				return 0
			case strings.HasPrefix(t.Lang, "ar"):
				return 1
			case strings.HasPrefix(t.Lang, "en"):
				return 2
			}
			return 3
		}
		sort.Slice(info.AudioTracks, func(i, j int) bool {
			a, b := info.AudioTracks[i], info.AudioTracks[j]
			if rank(a) != rank(b) {
				return rank(a) < rank(b)
			}
			return a.Lang < b.Lang
		})
	}
	if info.AudioTracks == nil {
		info.AudioTracks = []AudioTrack{}
	}
	if len(info.Options) > 0 {
		info.Subtitles = parseSubs(o)
	}
	if info.Subtitles == nil {
		info.Subtitles = []SubTrack{}
	}
	info.Items, info.Direct = []PlaylistItem{}, []DirectFile{}
	return info
}

// parseSubs قائمة الترجمات مرتبة بحيث تتصدر العربية بأدق مصدر متاح.
func parseSubs(o map[string]any) []SubTrack {
	manualObj, _ := o["subtitles"].(map[string]any)
	autoObj, _ := o["automatic_captions"].(map[string]any)
	var manual []string
	for k := range manualObj {
		if k != "live_chat" && k != "comments" {
			manual = append(manual, k)
		}
	}
	sort.Strings(manual)
	isManual := map[string]bool{}
	for _, m := range manual {
		isManual[m] = true
	}
	common := map[string]bool{}
	for _, c := range []string{"ar", "en", "fr", "es", "de", "tr", "ur", "fa", "id", "hi", "ru", "it", "pt", "ja", "ko", "zh-Hans"} {
		common[c] = true
	}
	origAuto := ""
	var autoKeys []string
	for k := range autoObj {
		autoKeys = append(autoKeys, k)
		if strings.HasSuffix(k, "-orig") && (origAuto == "" || k < origAuto) {
			origAuto = k
		}
	}
	sort.Strings(autoKeys)

	var subs []SubTrack
	for _, m := range manual {
		subs = append(subs, SubTrack{Lang: m, Name: langName(m), Note: "من صاحب الفيديو"})
	}
	for _, k := range autoKeys {
		if isManual[k] {
			continue
		}
		src := ""
		for _, m := range manual {
			if strings.HasSuffix(k, "-"+m) && len(k) > len(m)+1 {
				src = m
				break
			}
		}
		switch {
		case src != "":
			// «ar-en» = مترجمة آلياً من الترجمة اليدوية؛ نعرض هذا النوع للعربية والإنجليزية فقط.
			if base := strings.TrimSuffix(k, "-"+src); base == "ar" || base == "en" {
				subs = append(subs, SubTrack{Lang: k, Name: langName(base), Auto: true, From: src, Src: src, Note: "مترجمة من " + langName(src)})
			}
		case strings.HasSuffix(k, "-orig"):
			subs = append(subs, SubTrack{Lang: k, Name: langName(k), Auto: true, Note: "تفريغ آلي للكلام"})
		case common[k]:
			t := SubTrack{Lang: k, Name: langName(k), Auto: true, Note: "ترجمة آلية"}
			if origAuto != "" && strings.TrimSuffix(origAuto, "-orig") != k {
				t.Src = origAuto
			}
			subs = append(subs, t)
		}
	}
	rank := func(t SubTrack) int {
		r := 20
		if t.Lang == "ar" || strings.HasPrefix(t.Lang, "ar-") {
			r = 0
		} else if t.Lang == "en" || strings.HasPrefix(t.Lang, "en-") {
			r = 10
		}
		switch {
		case !t.Auto:
		case t.From != "":
			r++
		default:
			r += 2
		}
		return r
	}
	sort.SliceStable(subs, func(i, j int) bool {
		if rank(subs[i]) != rank(subs[j]) {
			return rank(subs[i]) < rank(subs[j])
		}
		return subs[i].Lang < subs[j].Lang
	})
	return subs
}

// presetOptions جودات عامة للقوائم (صيغ كل عنصر تُعرف وقت التحميل).
func presetOptions(maxSide int) []Quality {
	out := []Quality{{ID: "p-best", Label: "الأعلى المتاح", Detail: "MP4", Approx: true, Selector: "bv*+ba/b", Side: 100000}}
	for _, h := range []int{2160, 1440, 1080, 720, 480, 360} {
		if maxSide == 0 || float64(h) <= float64(maxSide)*1.07 {
			out = append(out, Quality{ID: fmt.Sprintf("p-%d", h), Label: fmt.Sprintf("%dp", h), Badge: badgeFor(h), Detail: "MP4", Approx: true, Selector: "bv*+ba/b", Side: h})
		}
	}
	if maxSide != 0 && len(out) <= 2 {
		return out[:1]
	}
	return out
}

var langNames = map[string]string{"ar": "العربية", "en": "الإنجليزية", "fr": "الفرنسية", "es": "الإسبانية", "de": "الألمانية",
	"tr": "التركية", "ur": "الأردية", "fa": "الفارسية", "id": "الإندونيسية", "hi": "الهندية", "ru": "الروسية", "it": "الإيطالية",
	"pt": "البرتغالية", "ja": "اليابانية", "ko": "الكورية", "zh": "الصينية", "nl": "الهولندية", "sv": "السويدية", "pl": "البولندية",
	"uk": "الأوكرانية", "he": "العبرية", "bn": "البنغالية", "ms": "الملايوية", "th": "التايلاندية", "vi": "الفيتنامية", "el": "اليونانية"}

// langName اسم اللغة بالعربية من رمزها (مع تجاهل لواحق يوتيوب مثل -orig).
func langName(code string) string {
	base := strings.ToLower(strings.SplitN(strings.TrimSuffix(code, "-orig"), "-", 2)[0])
	if n, ok := langNames[base]; ok {
		return n
	}
	return strings.ToUpper(base)
}

// ---------------------------------------------------------------- التحميل

type Progress struct {
	Fraction float64
	Speed    string
	ETA      int64
	Stage    string
}

var (
	reItem  = regexp.MustCompile(`Downloading item (\d+) of (\d+)`)
	rePct   = regexp.MustCompile(`\[download\]\s+([\d.]+)%`)
	reSpeed = regexp.MustCompile(`at\s+([\d.]+)\s*([KMG]?)i?B/s`)
	reETA   = regexp.MustCompile(`ETA\s+(?:(\d+):)?(\d+):(\d+)`)
	reCap   = regexp.MustCompile(`^(\d{3,4})p`)
	reFrag  = regexp.MustCompile(`\.f[\w-]+\.\w+$`)
)

// arabicSpeed «1.20MiB/s» → «1.2 م.ب/ث».
func arabicSpeed(line string) string {
	m := reSpeed.FindStringSubmatch(line)
	if m == nil {
		return ""
	}
	unit := map[string]string{"G": "ج.ب", "M": "م.ب", "K": "ك.ب", "": "بايت"}[m[2]]
	v, _ := strconv.ParseFloat(m[1], 64)
	return fmt.Sprintf("%.1f %s/ث", v, unit)
}

// runDownload ينفّذ التحميل داخل dir. الإلغاء عبر ctx؛ الملفات الجزئية تبقى للاستكمال.
func runDownload(ctx context.Context, spec Spec, dir string, onProgress func(Progress)) error {
	os.MkdirAll(dir, 0o755)
	playlist := len(spec.Items) > 0
	args := []string{"-P", dir, "--no-mtime", "--newline", "--retries", "5", "--fragment-retries", "5",
		"--concurrent-fragments", "4", "--windows-filenames"}
	if playlist {
		var idx []string
		for _, i := range spec.Items {
			idx = append(idx, strconv.Itoa(i))
		}
		args = append(args, "--yes-playlist", "--playlist-items", strings.Join(idx, ","), "--ignore-errors",
			"-o", "%(playlist_index)02d - %(title).70B.%(ext)s")
	} else {
		args = append(args, "--no-playlist", "-o", "%(title).80B.%(ext)s")
	}
	langFilter := ""
	if spec.AudioLang != "" {
		langFilter = "[language=" + spec.AudioLang + "]"
	}
	if spec.Mode == "audio" {
		f := "ba/b"
		if langFilter != "" {
			f = "ba" + langFilter + "/ba/b"
		}
		args = append(args, "-f", f, "-x", "--audio-format", firstNonEmpty(spec.AudioFormat, "mp3"), "--audio-quality", "0",
			"--embed-metadata", "--embed-thumbnail", "--convert-thumbnails", "jpg")
	} else {
		args = append(args, "-f", strings.ReplaceAll(firstNonEmpty(spec.Selector, "bv*+ba/b"), "{A}", langFilter))
		// الترجمة: ننزّل ملفات SRT فقط، والبرنامج يختار ويدمج بعد التحميل (subtitles.go).
		// --ignore-errors: يوتيوب يرفض كثيراً الترجمات المترجمة آلياً (HTTP 429)، ولا يجوز أن يُفشل ذلك الفيديو.
		if spec.SubLang != "" {
			langs := []string{spec.SubLang}
			if playlist {
				langs = append(langs, spec.SubLang+"-.*", "en", ".*-orig")
			} else if spec.SubSrc != "" {
				langs = append(langs, spec.SubSrc)
			}
			args = append(args, "--sub-langs", strings.Join(langs, ","), "--write-subs", "--write-auto-subs", "--convert-subs", "srt")
			if !playlist {
				args = append(args, "--ignore-errors")
			}
		}
		if m := reCap.FindStringSubmatch(spec.Label); playlist && m != nil {
			args = append(args, "-S", "res:"+m[1]+",vcodec:h264,acodec:m4a")
		}
		args = append(args, "--merge-output-format", "mp4")
	}
	trimmed := spec.TrimStart != nil && spec.TrimEnd != nil && !playlist
	if trimmed {
		args = append(args, "--download-sections", fmt.Sprintf("*%d-%d", *spec.TrimStart, *spec.TrimEnd))
	}
	args = append(args, spec.URL)

	cmd := ytdlpCmd(ctx, args...)
	stdout, _ := cmd.StdoutPipe()
	var errb bytes.Buffer
	cmd.Stderr = &errb
	if err := cmd.Start(); err != nil {
		return &AppError{Type: "engine", Detail: err.Error()}
	}
	total := 1
	if playlist {
		total = len(spec.Items)
	}
	item, phase := 1, 0
	stage := "جاري التحضير"
	sc := bufio.NewScanner(stdout)
	sc.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for sc.Scan() {
		line := sc.Text()
		if m := reItem.FindStringSubmatch(line); m != nil {
			item, _ = strconv.Atoi(m[1])
			phase = 0
		}
		post := false
		switch {
		case strings.HasPrefix(line, "[download] Destination:"):
			phase++
			stage = "تحميل الفيديو"
			if spec.Mode == "audio" || phase >= 2 {
				stage = "تحميل الصوت"
			}
			if trimmed {
				stage = "تحميل المقطع المحدد"
			}
		case strings.HasPrefix(line, "[Merger]"):
			stage, post = "دمج الفيديو والصوت", true
		case strings.HasPrefix(line, "[ExtractAudio]"):
			stage, post = "تحويل الصوت", true
		case strings.HasPrefix(line, "[EmbedThumbnail]"), strings.HasPrefix(line, "[Metadata]"), strings.HasPrefix(line, "[ThumbnailsConvertor]"):
			stage, post = "إضافة الغلاف والبيانات", true
		case strings.HasPrefix(line, "[SubtitlesConvertor]"):
			stage, post = "تحويل الترجمة", true
		case strings.HasPrefix(line, "[VideoRemuxer]"), strings.HasPrefix(line, "[FixupM"):
			stage, post = "معالجة الملف", true
		}
		pct := -1.0
		if m := rePct.FindStringSubmatch(line); m != nil {
			pct, _ = strconv.ParseFloat(m[1], 64)
			pct /= 100
		}
		if pct < 0 && !post {
			if !trimmed {
				continue
			}
		}
		within := 0.99
		switch {
		case post:
		case spec.Mode != "video":
			within = pct * 0.97
		case phase <= 1:
			within = pct * 0.88
		default:
			within = 0.88 + pct*0.10
		}
		p := Progress{Speed: arabicSpeed(line), Stage: stage}
		if total > 1 {
			p.Stage = fmt.Sprintf("%s (%d/%d)", stage, item, total)
		}
		if trimmed && !post && pct < 0 {
			p.Fraction = -1 // FFmpeg يتولى التحميل عند القصّ ولا يعطي نسبة
		} else {
			p.Fraction = (float64(item-1) + within) / float64(total)
		}
		if m := reETA.FindStringSubmatch(line); m != nil && !post {
			h, _ := strconv.Atoi(m[1])
			mm, _ := strconv.Atoi(m[2])
			ss, _ := strconv.Atoi(m[3])
			p.ETA = int64(h*3600 + mm*60 + ss)
		}
		onProgress(p)
	}
	err := cmd.Wait()
	if ctx.Err() != nil {
		return ctx.Err()
	}
	if err != nil {
		// القوائم وطلب الترجمة: فشل عنصر أو ملف ترجمة لا يُفشل ما اكتمل.
		mediaDone := false
		for _, f := range outputFiles(dir) {
			if !subExts[extOf(f)] {
				mediaDone = true
			}
		}
		if (playlist || spec.SubLang != "") && mediaDone {
			return nil
		}
		return classify(errb.String())
	}
	return nil
}

// outputFiles الملفات النهائية داخل مجلد المهمة (بلا الأجزاء المؤقتة).
func outputFiles(dir string) []string {
	entries, _ := os.ReadDir(dir)
	var out []string
	for _, e := range entries {
		name := e.Name()
		ext := extOf(name)
		if e.IsDir() || ext == "part" || ext == "ytdl" || ext == "tmp" || ext == "temp" || strings.Contains(name, ".part-Frag") ||
			reFrag.MatchString(name) && ext != "srt" {
			continue
		}
		if fileSize(filepath.Join(dir, name)) > 0 {
			out = append(out, filepath.Join(dir, name))
		}
	}
	sort.Strings(out)
	return out
}

// ---------------------------------------------------------------- تصنيف الأخطاء

var reHTTP404 = regexp.MustCompile(`http error 4(04|10)`)

func classify(raw string) *AppError {
	low := strings.ToLower(raw)
	detail := ""
	for _, l := range strings.Split(raw, "\n") {
		l = strings.TrimSpace(l)
		if strings.HasPrefix(l, "ERROR") {
			detail = strings.TrimSpace(strings.TrimPrefix(l, "ERROR:"))
		}
	}
	if detail == "" {
		detail = lastLine(raw)
	}
	if r := []rune(detail); len(r) > 240 {
		detail = string(r[:240])
	}
	has := func(keys ...string) bool {
		for _, k := range keys {
			if strings.Contains(low, k) {
				return true
			}
		}
		return false
	}
	t := "generic"
	switch {
	case has("login required", "log in", "sign in", "private", "cookies", "registered users", "not available to you", "authentication",
		"members-only", "age-restricted", "rate-limit reached", "empty media response", "this content isn't available"):
		t = "private"
	case reHTTP404.MatchString(low), has("unsupported url", "is not a valid url", "no video formats", "no media found",
		"no video could be found", "there is no video", "no video in this", "unable to extract"):
		t = "unsupported"
	case has("name resolution", "getaddrinfo", "network is unreachable", "timed out", "connection refused", "connection reset",
		"unable to download webpage", "no address associated", "failed to resolve", "transporterror"):
		t = "offline"
	}
	return &AppError{Type: t, Detail: detail}
}

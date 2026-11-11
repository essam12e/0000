package main

import (
	"archive/zip"
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

// الأدوات الخارجية: yt-dlp و FFmpeg (و gallery-dl عند الحاجة). تُبحث أولاً بجانب البرنامج (مجلد bin)
// ثم في مجلد بيانات البرنامج، وما ينقص يُنزَّل من الإصدارات الرسمية على GitHub عند أول تشغيل.

const (
	urlYtDlp     = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
	urlFFmpegZip = "https://github.com/yt-dlp/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-win64-gpl.zip"
	urlGalleryDl = "https://github.com/mikf/gallery-dl/releases/latest/download/gallery-dl.exe"
)

var (
	dataDir string
	exeDir  string
)

func initDirs() {
	if d := os.Getenv("DL_DATA"); d != "" {
		dataDir = d
	} else {
		base, err := os.UserCacheDir() // ويندوز: %LOCALAPPDATA%
		if err != nil {
			base = os.TempDir()
		}
		dataDir = filepath.Join(base, "Downloader")
	}
	for _, d := range []string{"bin", "library", "thumbs", "cache"} {
		os.MkdirAll(filepath.Join(dataDir, d), 0o755)
	}
	if exe, err := os.Executable(); err == nil {
		exeDir = filepath.Dir(exe)
	}
}

// toolPath يعيد مسار الأداة أو نصاً فارغاً إن لم توجد.
func toolPath(name string) string {
	if p := os.Getenv("DL_" + strings.ToUpper(strings.ReplaceAll(name, "-", ""))); p != "" {
		return p
	}
	file := name + exeSuffix
	for _, dir := range []string{filepath.Join(dataDir, "bin"), filepath.Join(exeDir, "bin"), exeDir} {
		p := filepath.Join(dir, file)
		if st, err := os.Stat(p); err == nil && !st.IsDir() && st.Size() > 0 {
			return p
		}
	}
	if exeSuffix == "" { // تطوير على لينكس: من PATH
		if p, err := exec.LookPath(name); err == nil {
			return p
		}
	}
	return ""
}

// SetupState حالة تجهيز المحرك (تُعرض في الواجهة عند أول تشغيل).
type SetupState struct {
	Ready    bool    `json:"ready"`
	Stage    string  `json:"stage"`
	Progress float64 `json:"progress"` // 0..1 أو -1
	Error    string  `json:"error,omitempty"`
	Version  string  `json:"version,omitempty"`
}

var (
	setupMu sync.Mutex
	setup   = SetupState{Stage: "جاري التحقق من المحرك"}
	setupCh = make(chan struct{})
	setupOK sync.Once
)

func getSetup() SetupState {
	setupMu.Lock()
	defer setupMu.Unlock()
	return setup
}

func setSetup(f func(*SetupState)) {
	setupMu.Lock()
	f(&setup)
	s := setup
	setupMu.Unlock()
	hub.publish("setup", s)
}

// waitEngine ينتظر جاهزية المحرك أو يعيد خطأ.
func waitEngine(ctx context.Context) error {
	select {
	case <-setupCh:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	case <-time.After(10 * time.Minute):
		return &AppError{Type: "engine", Detail: getSetup().Error}
	}
}

func download(ctx context.Context, url, dest string, onProgress func(float64)) error {
	req, _ := http.NewRequestWithContext(ctx, "GET", url, nil)
	req.Header.Set("User-Agent", "Downloader/1.0")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	tmp := dest + ".part"
	f, err := os.Create(tmp)
	if err != nil {
		return err
	}
	var done int64
	buf := make([]byte, 256*1024)
	last := time.Now()
	for {
		n, rerr := resp.Body.Read(buf)
		if n > 0 {
			if _, werr := f.Write(buf[:n]); werr != nil {
				f.Close()
				return werr
			}
			done += int64(n)
			if onProgress != nil && time.Since(last) > 200*time.Millisecond {
				last = time.Now()
				if resp.ContentLength > 0 {
					onProgress(float64(done) / float64(resp.ContentLength))
				} else {
					onProgress(-1)
				}
			}
		}
		if rerr == io.EOF {
			break
		}
		if rerr != nil {
			f.Close()
			os.Remove(tmp)
			return rerr
		}
	}
	f.Close()
	return os.Rename(tmp, dest)
}

// ensureEngine يجهّز yt-dlp و FFmpeg. يُستدعى مرة عند الإقلاع، ويمكن إعادة المحاولة.
func ensureEngine() {
	ctx := context.Background()
	bin := filepath.Join(dataDir, "bin")
	fail := func(what string, err error) {
		msg := what + ": " + err.Error()
		if isOffline(err) {
			msg = "لا يوجد اتصال بالإنترنت لتنزيل المحرك. اتصل ثم اضغط «إعادة المحاولة»."
		}
		setSetup(func(s *SetupState) { s.Error = msg; s.Stage = "تعذّر تجهيز المحرك" })
	}
	setSetup(func(s *SetupState) {
		s.Error = ""
		s.Ready = false
		s.Stage = "جاري التحقق من المحرك"
		s.Progress = -1
	})

	if toolPath("yt-dlp") == "" {
		if exeSuffix == "" {
			fail("yt-dlp", errors.New("غير مثبّت (نسخة التطوير تتوقعه في PATH)"))
			return
		}
		setSetup(func(s *SetupState) {
			s.Stage = "تنزيل محرك التحميل yt-dlp (مرة واحدة)"
			s.Progress = 0
		})
		if err := download(ctx, urlYtDlp, filepath.Join(bin, "yt-dlp.exe"), func(p float64) {
			setSetup(func(s *SetupState) { s.Progress = p })
		}); err != nil {
			fail("تنزيل yt-dlp", err)
			return
		}
	}
	if toolPath("ffmpeg") == "" {
		if exeSuffix == "" {
			fail("ffmpeg", errors.New("غير مثبّت (نسخة التطوير تتوقعه في PATH أو DL_FFMPEG)"))
			return
		}
		setSetup(func(s *SetupState) {
			s.Stage = "تنزيل FFmpeg للدمج والتحويل (مرة واحدة)"
			s.Progress = 0
		})
		zipPath := filepath.Join(dataDir, "cache", "ffmpeg.zip")
		if err := download(ctx, urlFFmpegZip, zipPath, func(p float64) {
			setSetup(func(s *SetupState) { s.Progress = p })
		}); err != nil {
			fail("تنزيل FFmpeg", err)
			return
		}
		setSetup(func(s *SetupState) { s.Stage = "فكّ FFmpeg"; s.Progress = -1 })
		if err := unzipExes(zipPath, bin, "ffmpeg.exe", "ffprobe.exe"); err != nil {
			fail("فكّ FFmpeg", err)
			return
		}
		os.Remove(zipPath)
	}
	v := engineVersion()
	setSetup(func(s *SetupState) { s.Ready = true; s.Stage = ""; s.Progress = 1; s.Version = v })
	setupOK.Do(func() { close(setupCh) })
	go autoUpdateEngine()
}

func unzipExes(zipPath, destDir string, names ...string) error {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer r.Close()
	found := 0
	for _, f := range r.File {
		base := filepath.Base(f.Name)
		for _, want := range names {
			if strings.EqualFold(base, want) {
				src, err := f.Open()
				if err != nil {
					return err
				}
				out, err := os.Create(filepath.Join(destDir, want))
				if err != nil {
					src.Close()
					return err
				}
				_, err = io.Copy(out, src)
				out.Close()
				src.Close()
				if err != nil {
					return err
				}
				found++
			}
		}
	}
	if found == 0 {
		return errors.New("الملف المطلوب غير موجود داخل الأرشيف")
	}
	return nil
}

func engineVersion() string {
	p := toolPath("yt-dlp")
	if p == "" {
		return ""
	}
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	cmd := exec.CommandContext(ctx, p, "--version")
	hideWindow(cmd)
	out, _ := cmd.Output()
	return strings.TrimSpace(string(out))
}

// updateEngine يحدّث yt-dlp من داخل البرنامج. يعيد (حُدّث؟، الإصدار).
func updateEngine() (bool, string, error) {
	p := toolPath("yt-dlp")
	if p == "" {
		return false, "", &AppError{Type: "engine"}
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Minute)
	defer cancel()
	cmd := exec.CommandContext(ctx, p, "-U")
	hideWindow(cmd)
	out, err := cmd.CombinedOutput()
	text := string(out)
	v := engineVersion()
	setSetup(func(s *SetupState) { s.Version = v })
	if err != nil && !strings.Contains(text, "up to date") {
		// نسخ التطوير (pip) لا تحدّث نفسها؛ على ويندوز الخطأ غالباً شبكة.
		return false, v, &AppError{Type: "generic", Detail: lastLine(text)}
	}
	return strings.Contains(text, "Updated yt-dlp") || strings.Contains(text, "Updating to"), v, nil
}

// التحديث التلقائي الصامت: المواقع تتغير باستمرار، فنحدّث المحرك كل 3 أيام.
func autoUpdateEngine() {
	s := settings.get()
	if time.Now().Unix()-s.LastEngineUpd < 3*24*3600 || exeSuffix == "" {
		return
	}
	if _, _, err := updateEngine(); err == nil {
		settings.update(func(s *Settings) { s.LastEngineUpd = time.Now().Unix() })
	}
}

// ensureGalleryDl ينزّل gallery-dl عند أول حاجة (منشورات الصور). يعيد المسار أو فارغاً.
func ensureGalleryDl(ctx context.Context) string {
	if p := toolPath("gallery-dl"); p != "" {
		return p
	}
	if exeSuffix == "" {
		return ""
	}
	dest := filepath.Join(dataDir, "bin", "gallery-dl.exe")
	if download(ctx, urlGalleryDl, dest, nil) != nil {
		return ""
	}
	return dest
}

func lastLine(s string) string {
	lines := strings.Split(strings.TrimSpace(s), "\n")
	l := strings.TrimSpace(lines[len(lines)-1])
	if r := []rune(l); len(r) > 220 {
		l = string(r[:220])
	}
	return l
}

func isOffline(err error) bool {
	if err == nil {
		return false
	}
	m := strings.ToLower(err.Error())
	for _, k := range []string{"no such host", "network is unreachable", "dial tcp", "timeout", "connection refused", "no route"} {
		if strings.Contains(m, k) {
			return true
		}
	}
	return false
}

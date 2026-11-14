package main

import (
	"flag"
	"fmt"
	"net"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

const appVersion = "1.2"

func main() {
	port := flag.Int("port", 0, "منفذ ثابت (للتطوير)")
	token := flag.String("token", "", "رمز ثابت (للتطوير)")
	browser := flag.Bool("browser", false, "افتح الواجهة في المتصفح بدل النافذة")
	flag.Parse()

	initDirs()
	settings.init()
	library.init()
	history.init()
	mgr.init()

	srv := &server{token: *token}
	if srv.token == "" {
		srv.token = newID() + newID()
	}
	ln, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", *port))
	if err != nil {
		fmt.Fprintln(os.Stderr, "تعذّر بدء الخادم المحلي:", err)
		os.Exit(1)
	}
	url := fmt.Sprintf("http://127.0.0.1:%d/?t=%s", ln.Addr().(*net.TCPAddr).Port, srv.token)
	go http.Serve(ln, srv.routes())
	go ensureEngine()

	defer mgr.shutdown()
	// إنهاء من النظام (Ctrl+C / إيقاف الجلسة): نوقف التحميلات مؤقتاً ونحفظ حالتها قبل الخروج.
	sig := make(chan os.Signal, 1)
	signal.Notify(sig, os.Interrupt, syscall.SIGTERM)
	go func() { <-sig; mgr.shutdown(); os.Exit(0) }()
	if *browser || !runWindow(url) {
		// بلا نافذة أصلية (WebView2 غير متوفر أو وضع المتصفح): نفتح المتصفح ونبقى حتى تُغلق الواجهة.
		fmt.Println(url)
		if !*browser || os.Getenv("DL_NO_OPEN") == "" {
			openURL(url)
		}
		for {
			time.Sleep(5 * time.Second)
			hub.mu.Lock()
			idle := len(hub.subs) == 0 && time.Since(hub.last) > 90*time.Second
			hub.mu.Unlock()
			if idle && !mgr.active() && os.Getenv("DL_KEEP") == "" {
				return
			}
		}
	}
}

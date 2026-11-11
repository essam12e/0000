//go:build windows

package main

import (
	webview2 "github.com/jchv/go-webview2"
)

// runWindow يفتح نافذة ويندوز أصلية تعرض الواجهة عبر WebView2 (مضمَّن في ويندوز 10/11).
// يعيد false إن لم يتوفر WebView2، فيُفتح المتصفح بديلاً.
func runWindow(url string) bool {
	w := webview2.NewWithOptions(webview2.WebViewOptions{
		Debug:     false,
		AutoFocus: true,
		DataPath:  dataDir + `\webview`,
		WindowOptions: webview2.WindowOptions{
			Title:  "داونلودر",
			Width:  1180,
			Height: 800,
			IconId: 2,
			Center: true,
		},
	})
	if w == nil {
		return false
	}
	defer w.Destroy()
	w.SetSize(960, 640, webview2.HintMin)
	w.Navigate(url)
	w.Run()
	return true
}

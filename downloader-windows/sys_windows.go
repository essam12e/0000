//go:build windows

package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"syscall"
	"unsafe"
)

const exeSuffix = ".exe"

// hideWindow يمنع ظهور نافذة سوداء (Console) عند تشغيل yt-dlp و FFmpeg.
func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: 0x08000000} // CREATE_NO_WINDOW
}

// killTree ينهي العملية وكل ما شغّلته (yt-dlp يشغّل FFmpeg).
func killTree(cmd *exec.Cmd) {
	if cmd == nil || cmd.Process == nil {
		return
	}
	k := exec.Command("taskkill", "/T", "/F", "/PID", strconv.Itoa(cmd.Process.Pid))
	hideWindow(k)
	k.Run()
}

func defaultDownloads() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "Downloads")
}

func openPath(p string) error {
	c := exec.Command("rundll32", "url.dll,FileProtocolHandler", p)
	hideWindow(c)
	return c.Start()
}

// revealPath يفتح المجلد في المستكشف مع تحديد الملف.
func revealPath(p string) error {
	c := exec.Command("explorer")
	c.SysProcAttr = &syscall.SysProcAttr{CmdLine: `explorer /select,"` + p + `"`}
	return c.Start()
}

func openURL(u string) error { return openPath(u) }

// pickFolder يفتح نافذة اختيار مجلد من ويندوز نفسه.
func pickFolder(title string) string {
	script := `Add-Type -AssemblyName System.Windows.Forms; ` +
		`$d = New-Object System.Windows.Forms.FolderBrowserDialog; $d.Description = '` + strings.ReplaceAll(title, "'", "''") + `'; ` +
		`$d.ShowNewFolderButton = $true; [Console]::OutputEncoding = [Text.Encoding]::UTF8; ` +
		// نافذة مالكة شفافة في المقدمة حتى لا تظهر نافذة الاختيار خلف البرنامج.
		`$o = New-Object System.Windows.Forms.Form; $o.TopMost = $true; $o.ShowInTaskbar = $false; $o.Opacity = 0; $o.Show(); ` +
		`if ($d.ShowDialog($o) -eq 'OK') { Write-Output $d.SelectedPath }; $o.Close()`
	c := exec.Command("powershell", "-NoProfile", "-STA", "-Command", script)
	hideWindow(c)
	out, _ := c.Output()
	return strings.TrimSpace(string(out))
}

var (
	user32           = syscall.NewLazyDLL("user32.dll")
	kernel32         = syscall.NewLazyDLL("kernel32.dll")
	openClipboard    = user32.NewProc("OpenClipboard")
	closeClipboard   = user32.NewProc("CloseClipboard")
	getClipboardData = user32.NewProc("GetClipboardData")
	globalLock       = kernel32.NewProc("GlobalLock")
	globalUnlock     = kernel32.NewProc("GlobalUnlock")
)

// clipboardText يقرأ نص الحافظة (لاكتشاف الرابط المنسوخ).
func clipboardText() string {
	if r, _, _ := openClipboard.Call(0); r == 0 {
		return ""
	}
	defer closeClipboard.Call()
	h, _, _ := getClipboardData.Call(13) // CF_UNICODETEXT
	if h == 0 {
		return ""
	}
	p, _, _ := globalLock.Call(h)
	if p == 0 {
		return ""
	}
	defer globalUnlock.Call(h)
	// p يشير إلى ذاكرة يملكها النظام (مقفلة بـ GlobalLock)، لا إلى ذاكرة Go.
	text := unsafe.Slice((*uint16)(unsafe.Pointer(p)), 8192) //nolint:govet
	n := 0
	for n < len(text) && text[n] != 0 {
		n++
	}
	return syscall.UTF16ToString(text[:n])
}

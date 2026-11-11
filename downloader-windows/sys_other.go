//go:build !windows

package main

// نسخة التطوير والاختبار (لينكس/ماك): الدوال الخاصة بويندوز لها بدائل بسيطة هنا.

import (
	"os"
	"os/exec"
	"path/filepath"
	"syscall"
)

const exeSuffix = ""

func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
}

func killTree(cmd *exec.Cmd) {
	if cmd == nil || cmd.Process == nil {
		return
	}
	syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL)
	cmd.Process.Kill()
}

func defaultDownloads() string {
	if d := os.Getenv("DL_DOWNLOADS"); d != "" {
		return d
	}
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "Downloads")
}

func openPath(p string) error   { return exec.Command("xdg-open", p).Start() }
func revealPath(p string) error { return exec.Command("xdg-open", filepath.Dir(p)).Start() }
func openURL(u string) error    { return exec.Command("xdg-open", u).Start() }
func pickFolder(string) string  { return os.Getenv("DL_PICK_FOLDER") }
func clipboardText() string     { return os.Getenv("DL_CLIPBOARD") }

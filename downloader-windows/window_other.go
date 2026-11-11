//go:build !windows

package main

// على غير ويندوز لا توجد نافذة أصلية؛ الواجهة تُفتح في المتصفح (للتطوير والاختبار).
func runWindow(string) bool { return false }

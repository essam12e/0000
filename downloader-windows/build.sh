#!/bin/bash
# يبني Downloader.exe لويندوز 64-بت من أي نظام (لينكس/ماك/ويندوز). المتطلب الوحيد: Go 1.24 أو أحدث.
# ملف الموارد (الأيقونة وبيان التوافق) rsrc_windows_amd64.syso محفوظ في المستودع؛
# لإعادة توليده بعد تغيير الأيقونة: python3 tools/mkres.py
set -e
cd "$(dirname "$0")"
mkdir -p dist
GOOS=windows GOARCH=amd64 go build -trimpath -ldflags "-s -w -H windowsgui" -o dist/Downloader.exe .
echo "تم: dist/Downloader.exe"

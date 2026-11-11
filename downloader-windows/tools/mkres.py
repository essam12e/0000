#!/usr/bin/env python3
"""يولّد rsrc_windows_amd64.syso: موارد ويندوز (أيقونة البرنامج + بيان التوافق) التي يربطها Go تلقائياً بملف exe.

الاستخدام:  python3 tools/mkres.py            (يحتاج Pillow)
لا حاجة لتشغيله إلا إذا تغيّرت الأيقونة أو البيان؛ الملف الناتج محفوظ في المستودع.
"""
import io
import struct
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ICON_SRC = ROOT / "web" / "icon.png"
OUT = ROOT / "rsrc_windows_amd64.syso"
ICON_GROUP_ID = 2  # يطابق IconId في window_windows.go

MANIFEST = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
  <assemblyIdentity type="win32" name="Downloader" version="1.0.0.0" processorArchitecture="amd64"/>
  <description>Downloader</description>
  <dependency>
    <dependentAssembly>
      <assemblyIdentity type="win32" name="Microsoft.Windows.Common-Controls" version="6.0.0.0"
        processorArchitecture="*" publicKeyToken="6595b64144ccf1df" language="*"/>
    </dependentAssembly>
  </dependency>
  <trustInfo xmlns="urn:schemas-microsoft-com:asm.v3">
    <security><requestedPrivileges><requestedExecutionLevel level="asInvoker" uiAccess="false"/></requestedPrivileges></security>
  </trustInfo>
  <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1">
    <application>
      <supportedOS Id="{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}"/>
      <supportedOS Id="{1f676c76-80e1-4239-95bb-83d0f6d0da78}"/>
    </application>
  </compatibility>
  <application xmlns="urn:schemas-microsoft-com:asm.v3">
    <windowsSettings>
      <dpiAware xmlns="http://schemas.microsoft.com/SMI/2005/WindowsSettings">true/pm</dpiAware>
      <dpiAwareness xmlns="http://schemas.microsoft.com/SMI/2016/WindowsSettings">PerMonitorV2</dpiAwareness>
      <longPathAware xmlns="http://schemas.microsoft.com/SMI/2016/WindowsSettings">true</longPathAware>
    </windowsSettings>
  </application>
</assembly>
""".encode("utf-8")

RT_ICON, RT_GROUP_ICON, RT_MANIFEST = 3, 14, 24


def icon_image(src: Image.Image, size: int) -> bytes:
    """صورة أيقونة واحدة: PNG للحجم 256، وDIB 32-بت (مع قناع AND) للأحجام الأصغر."""
    im = src.resize((size, size), Image.LANCZOS)
    if size >= 256:
        buf = io.BytesIO()
        im.save(buf, "PNG")
        return buf.getvalue()
    header = struct.pack("<IiiHHIIiiII", 40, size, size * 2, 1, 32, 0, 0, 0, 0, 0, 0)
    pixels = bytearray()
    for y in range(size - 1, -1, -1):  # من الأسفل للأعلى، BGRA
        for x in range(size):
            r, g, b, a = im.getpixel((x, y))
            pixels += bytes((b, g, r, a))
    mask_row = ((size + 31) // 32) * 4
    return header + bytes(pixels) + bytes(mask_row * size)


def build() -> bytes:
    src = Image.open(ICON_SRC).convert("RGBA")
    sizes = [16, 24, 32, 48, 64, 256]
    images = [icon_image(src, s) for s in sizes]

    group = struct.pack("<HHH", 0, 1, len(sizes))
    for i, (s, data) in enumerate(zip(sizes, images)):
        group += struct.pack("<BBBBHHIH", s % 256, s % 256, 0, 0, 1, 32, len(data), i + 1)

    # (النوع، المعرّف، اللغة، البيانات) — مرتبة تصاعدياً كما تتطلب صيغة PE
    resources = [(RT_ICON, i + 1, 1033, d) for i, d in enumerate(images)]
    resources.append((RT_GROUP_ICON, ICON_GROUP_ID, 1033, group))
    resources.append((RT_MANIFEST, 1, 1033, MANIFEST))

    types = sorted({t for t, *_ in resources})
    by_type = {t: [r for r in resources if r[0] == t] for t in types}

    # حساب المواضع: جذر ← مجلد لكل نوع ← مجلد لغة لكل معرّف ← مدخلات البيانات ← البيانات
    dir_size = lambda n: 16 + 8 * n
    off = dir_size(len(types))
    type_dir_off = {}
    for t in types:
        type_dir_off[t] = off
        off += dir_size(len(by_type[t]))
    lang_dir_off = {}
    for r in resources:
        lang_dir_off[(r[0], r[1])] = off
        off += dir_size(1)
    entry_off = {}
    for r in resources:
        entry_off[(r[0], r[1])] = off
        off += 16
    data_off = {}
    for r in resources:
        data_off[(r[0], r[1])] = off
        off += (len(r[3]) + 7) & ~7

    sec = bytearray(off)

    def put_dir(at, ids_and_targets, subdir):
        struct.pack_into("<IIHHHH", sec, at, 0, 0, 0, 0, 0, len(ids_and_targets))
        for i, (ident, target) in enumerate(ids_and_targets):
            struct.pack_into("<II", sec, at + 16 + 8 * i, ident, target | (0x80000000 if subdir else 0))

    put_dir(0, [(t, type_dir_off[t]) for t in types], True)
    for t in types:
        put_dir(type_dir_off[t], [(r[1], lang_dir_off[(t, r[1])]) for r in by_type[t]], True)
    relocs = []
    for t, ident, lang, data in resources:
        key = (t, ident)
        put_dir(lang_dir_off[key], [(lang, entry_off[key])], False)
        # OffsetToData يحمل الإزاحة داخل القسم؛ الرابط يحوّلها إلى RVA عبر إعادة التوطين
        struct.pack_into("<IIII", sec, entry_off[key], data_off[key], len(data), 0, 0)
        relocs.append(entry_off[key])
        sec[data_off[key]:data_off[key] + len(data)] = data

    # ملف COFF: ترويسة + قسم .rsrc واحد + إعادات التوطين + جدول الرموز
    IMAGE_FILE_MACHINE_AMD64, IMAGE_REL_AMD64_ADDR32NB = 0x8664, 3
    raw_ptr = 20 + 40
    reloc_ptr = raw_ptr + len(sec)
    sym_ptr = reloc_ptr + 10 * len(relocs)
    out = struct.pack("<HHIIIHH", IMAGE_FILE_MACHINE_AMD64, 1, 0, sym_ptr, 1, 0, 0)
    out += struct.pack("<8sIIIIIIHHI", b".rsrc", 0, 0, len(sec), raw_ptr, reloc_ptr, 0, len(relocs), 0, 0x40000040)
    out += bytes(sec)
    for r in relocs:
        out += struct.pack("<IIH", r, 0, IMAGE_REL_AMD64_ADDR32NB)
    out += struct.pack("<8sIhHBB", b".rsrc", 0, 1, 0, 3, 0)  # رمز ثابت يشير إلى القسم
    out += struct.pack("<I", 4)  # جدول نصوص فارغ
    return out


if __name__ == "__main__":
    data = build()
    OUT.write_bytes(data)
    print(f"{OUT.name}: {len(data)} bytes", file=sys.stderr)

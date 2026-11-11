#!/usr/bin/env python3
"""يتحقق من بنية Downloader.exe بدون تشغيله: نوع الملف، النظام الفرعي، والموارد (الأيقونة والبيان).

الاستخدام:  python3 tools/checkexe.py dist/Downloader.exe
"""
import struct
import sys

data = open(sys.argv[1], "rb").read()
assert data[:2] == b"MZ", "ليس ملف تنفيذي لويندوز"
pe = struct.unpack_from("<I", data, 0x3C)[0]
assert data[pe:pe + 4] == b"PE\0\0"
machine, nsec, _, _, _, opt_size, _ = struct.unpack_from("<HHIIIHH", data, pe + 4)
opt = pe + 24
magic = struct.unpack_from("<H", data, opt)[0]
subsystem = struct.unpack_from("<H", data, opt + 68)[0]
rsrc_rva, rsrc_size = struct.unpack_from("<II", data, opt + 112 + 8 * 2)  # DataDirectory[2] = الموارد
print(f"machine=0x{machine:x} (0x8664 = x64)  pe32+={magic == 0x20b}  subsystem={subsystem} (2 = واجهة رسومية بلا Console)")

sections = []
for i in range(nsec):
    name, vsize, va, rsize, rptr = struct.unpack_from("<8sIIII", data, opt + opt_size + 40 * i)
    sections.append((name.rstrip(b"\0").decode(), va, max(vsize, rsize), rptr))


def rva_to_off(rva):
    for _, va, size, rptr in sections:
        if va <= rva < va + size:
            return rptr + rva - va
    raise ValueError(f"RVA خارج الأقسام: {rva:#x}")


base = rva_to_off(rsrc_rva)
NAMES = {3: "ICON", 14: "GROUP_ICON", 16: "VERSION", 24: "MANIFEST"}


def walk(off, path):
    named, ids = struct.unpack_from("<HH", data, base + off + 12)
    for i in range(named + ids):
        ident, target = struct.unpack_from("<II", data, base + off + 16 + 8 * i)
        if target & 0x80000000:
            yield from walk(target & 0x7FFFFFFF, path + [ident])
        else:
            rva, size, _, _ = struct.unpack_from("<IIII", data, base + target)
            yield path + [ident], data[rva_to_off(rva):rva_to_off(rva) + size]


found = {}
for (rtype, rid, lang), blob in walk(0, []):
    found.setdefault(rtype, []).append((rid, blob))
    extra = ""
    if rtype == 3:
        extra = "PNG" if blob[:4] == b"\x89PNG" else f"DIB {struct.unpack_from('<i', blob, 4)[0]}px"
    elif rtype == 14:
        count = struct.unpack_from("<H", blob, 4)[0]
        sizes = [blob[6 + 14 * k] or 256 for k in range(count)]
        extra = f"sizes={sizes}"
    elif rtype == 24:
        extra = "dpiAwareness=PerMonitorV2" if b"PerMonitorV2" in blob else "!! بلا DPI"
    print(f"  {NAMES.get(rtype, rtype)} id={rid} lang={lang} size={len(blob)} {extra}")

assert machine == 0x8664 and magic == 0x20B and subsystem == 2
assert 14 in found and found[14][0][0] == 2, "أيقونة المجموعة رقم 2 مفقودة"
assert 24 in found and found[24][0][1].startswith(b"<?xml"), "البيان مفقود"
assert len(found[3]) == struct.unpack_from("<H", found[14][0][1], 4)[0], "عدد صور الأيقونة لا يطابق المجموعة"
print("OK: البنية سليمة")

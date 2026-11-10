"""Builds rsrc_windows_amd64.syso: a COFF object with the installer's Windows resources.

Contains the application icon (RT_ICON/RT_GROUP_ICON), an application manifest (RT_MANIFEST) and
version information (RT_VERSION). The Go linker links any *.syso in the package into the .exe.

Usage: python3 tools/make_syso.py <icon.png> <output.syso> <version> [<description> <file name> <identity>]
"""
import io
import struct
import sys

from PIL import Image

RT_ICON, RT_GROUP_ICON, RT_VERSION, RT_MANIFEST = 3, 14, 16, 24
LANG_EN_US = 0x0409


def icon_resources(png_path):
    """Returns ([(id, image bytes)], group icon bytes) built from a PNG via Pillow's ICO writer."""
    img = Image.open(png_path).convert("RGBA")
    buf = io.BytesIO()
    img.save(buf, format="ICO", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    ico = buf.getvalue()
    _, _, count = struct.unpack_from("<HHH", ico, 0)
    images, group = [], struct.pack("<HHH", 0, 1, count)
    for i in range(count):
        w, h, colors, res, planes, bits, size, offset = struct.unpack_from("<BBBBHHII", ico, 6 + i * 16)
        images.append((i + 1, ico[offset:offset + size]))
        group += struct.pack("<BBBBHHIH", w, h, colors, res, planes, bits, size, i + 1)
    return images, group


MANIFEST = b"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
  <assemblyIdentity type="win32" name="AnnoyingClient.Installer" version="1.0.0.0" processorArchitecture="amd64"/>
  <description>Annoying Client Installer</description>
  <trustInfo xmlns="urn:schemas-microsoft-com:asm.v3">
    <security><requestedPrivileges><requestedExecutionLevel level="asInvoker" uiAccess="false"/></requestedPrivileges></security>
  </trustInfo>
  <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1">
    <application>
      <supportedOS Id="{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}"/>
      <supportedOS Id="{1f676c76-80e1-4239-95bb-83d0f6d0da78}"/>
      <supportedOS Id="{4a2f28e3-53b9-4441-ba9c-d69d4a4a6e38}"/>
      <supportedOS Id="{35138b9a-5d96-4fbd-8e2d-a2440225f93a}"/>
    </application>
  </compatibility>
  <application xmlns="urn:schemas-microsoft-com:asm.v3">
    <windowsSettings><activeCodePage xmlns="http://schemas.microsoft.com/SMI/2019/WindowsSettings">UTF-8</activeCodePage></windowsSettings>
  </application>
</assembly>
"""


def pad4(b):
    return b + b"\0" * ((4 - len(b) % 4) % 4)


def ver_node(key, value=b"", is_text=False, children=(), value_len_words=None):
    """One VS_VERSIONINFO-style block: wLength, wValueLength, wType, szKey, padding, Value, padding, Children."""
    b = bytearray(6) + (key + "\0").encode("utf-16-le")
    b = bytearray(pad4(bytes(b)))
    b += value
    for child in children:
        b = bytearray(pad4(bytes(b)))  # every child starts on a 32-bit boundary
        b += child
    if value_len_words is None:
        value_len_words = len(value) // 2 if is_text else len(value)
    struct.pack_into("<HHH", b, 0, len(b), value_len_words, 1 if is_text else 0)
    return bytes(b)


APP = {"description": "Annoying Client Installer", "file": "AnnoyingClient-Installer.exe", "identity": "AnnoyingClient.Installer"}


def version_resource(version):
    parts = [int(p) for p in version.split(".")] + [0] * 4
    ms = (parts[0] << 16) | parts[1]
    ls = (parts[2] << 16) | parts[3]
    fixed = struct.pack("<13I", 0xFEEF04BD, 0x00010000, ms, ls, ms, ls, 0x3F, 0, 0x00040004, 1, 0, 0, 0)
    strings = {
        "CompanyName": "Annoying Client Team",
        "FileDescription": APP["description"],
        "FileVersion": version,
        "InternalName": APP["file"].rsplit(".", 1)[0],
        "LegalCopyright": "MIT License, Annoying Client Team",
        "OriginalFilename": APP["file"],
        "ProductName": "Annoying Client",
        "ProductVersion": version,
    }
    string_nodes = [ver_node(k, (v + "\0").encode("utf-16-le"), is_text=True) for k, v in strings.items()]
    table = ver_node("040904B0", is_text=True, children=string_nodes)
    sfi = ver_node("StringFileInfo", is_text=True, children=[table])
    var = ver_node("Translation", struct.pack("<HH", 0x0409, 0x04B0))
    vfi = ver_node("VarFileInfo", is_text=True, children=[var])
    return ver_node("VS_VERSION_INFO", fixed, children=[sfi, vfi])


def build_rsrc(resources):
    """resources: {type_id: {name_id: bytes}} -> (section bytes, relocation offsets)."""
    types = sorted(resources)
    # Sizes: root dir + one name dir per type + one language dir per (type, name) + data entries.
    dir_size = lambda n: 16 + 8 * n
    offset = dir_size(len(types))
    name_dir_off = {}
    for t in types:
        name_dir_off[t] = offset
        offset += dir_size(len(resources[t]))
    lang_dir_off = {}
    for t in types:
        for n in sorted(resources[t]):
            lang_dir_off[(t, n)] = offset
            offset += dir_size(1)
    entry_off = {}
    for t in types:
        for n in sorted(resources[t]):
            entry_off[(t, n)] = offset
            offset += 16
    data_off = {}
    for t in types:
        for n in sorted(resources[t]):
            offset = (offset + 7) & ~7
            data_off[(t, n)] = offset
            offset += len(resources[t][n])
    out = bytearray(offset)

    def write_dir(at, entries):
        struct.pack_into("<IIHHHH", out, at, 0, 0, 0, 0, 0, len(entries))
        for i, (ident, target, is_dir) in enumerate(entries):
            struct.pack_into("<II", out, at + 16 + i * 8, ident, target | (0x80000000 if is_dir else 0))

    write_dir(0, [(t, name_dir_off[t], True) for t in types])
    relocs = []
    for t in types:
        write_dir(name_dir_off[t], [(n, lang_dir_off[(t, n)], True) for n in sorted(resources[t])])
        for n in sorted(resources[t]):
            write_dir(lang_dir_off[(t, n)], [(LANG_EN_US, entry_off[(t, n)], False)])
            data = resources[t][n]
            struct.pack_into("<IIII", out, entry_off[(t, n)], data_off[(t, n)], len(data), 0, 0)
            relocs.append(entry_off[(t, n)])
            out[data_off[(t, n)]:data_off[(t, n)] + len(data)] = data
    return bytes(out), relocs


def write_coff(path, section, relocs):
    header_size = 20 + 40
    raw_ptr = header_size
    reloc_ptr = raw_ptr + len(section)
    sym_ptr = reloc_ptr + 10 * len(relocs)
    coff = struct.pack("<HHIIIHH", 0x8664, 1, 0, sym_ptr, 1, 0, 0)
    sect = struct.pack("<8sIIIIIIHHI", b".rsrc", 0, 0, len(section), raw_ptr, reloc_ptr, 0, len(relocs), 0, 0x40000040)
    rel = b"".join(struct.pack("<IIH", r, 0, 3) for r in relocs)  # IMAGE_REL_AMD64_ADDR32NB against symbol 0
    sym = struct.pack("<8sIhHBB", b".rsrc", 0, 1, 0, 3, 0)  # section symbol, IMAGE_SYM_CLASS_STATIC
    with open(path, "wb") as f:
        f.write(coff + sect + section + rel + sym + struct.pack("<I", 4))


def main():
    png, out, version = sys.argv[1], sys.argv[2], sys.argv[3]
    if len(sys.argv) >= 7:
        APP.update(description=sys.argv[4], file=sys.argv[5], identity=sys.argv[6])
    manifest = MANIFEST.replace(b"AnnoyingClient.Installer", APP["identity"].encode()).replace(
        b"<description>Annoying Client Installer</description>", ("<description>" + APP["description"] + "</description>").encode())
    images, group = icon_resources(png)
    resources = {
        RT_ICON: {i: data for i, data in images},
        RT_GROUP_ICON: {1: group},
        RT_VERSION: {1: version_resource(version)},
        RT_MANIFEST: {1: manifest},
    }
    section, relocs = build_rsrc(resources)
    write_coff(out, section, relocs)
    print(f"wrote {out}: {len(images)} icon sizes, manifest, version {version} ({len(section)} bytes)")


if __name__ == "__main__":
    main()

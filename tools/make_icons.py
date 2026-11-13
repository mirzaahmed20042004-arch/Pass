#!/usr/bin/env python3
"""Generate NoFilter AI launcher icons (legacy mipmaps + adaptive icon) from the concept logo."""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGO = os.path.join(ROOT, "assets-src", "logo_pick.png")
RES = os.path.join(ROOT, "app/src/main/res")

BG = (11, 15, 26, 255)          # #0B0F1A deep navy
RADIUS_FRAC = 0.22               # rounded-square corner radius (legacy icons)

logo = Image.open(LOGO).convert("RGBA")
W, H = logo.size
print("logo size:", W, H)

# --- crop the emblem (brain + aperture ring, without wordmark) ---
# emblem occupies roughly the upper 72% of the artwork
# ring spans roughly x 0.19W..0.81W, y 0.178H..0.72H (wordmark starts ~0.74H)
box = (int(W * 0.19), int(H * 0.178), int(W * 0.81), int(H * 0.72))
emblem = logo.crop(box)
print("emblem box:", box, emblem.size)
os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
logo.resize((512, 512), Image.LANCZOS).save(os.path.join(ROOT, "docs/logo.png"), optimize=True)


def rounded_square(img, radius):
    """Apply rounded-corner alpha mask to an RGBA image."""
    mask = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], radius=radius, fill=255)
    out = img.copy()
    out.putalpha(mask)
    return out


def legacy_icon(size):
    """Full logo on rounded square (logo bg already dark navy)."""
    im = logo.resize((size, size), Image.LANCZOS)
    return rounded_square(im, int(size * RADIUS_FRAC))


def adaptive_foreground(size=432):
    """Transparent canvas with emblem centered at ~60% (inside 66dp safe zone)."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    e = emblem.resize((int(size * 0.62), int(size * 0.62)), Image.LANCZOS)
    canvas.alpha_composite(e, ((size - e.size[0]) // 2, (size - e.size[1]) // 2))
    return canvas


DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

for folder, size in DENSITIES.items():
    d = os.path.join(RES, folder)
    os.makedirs(d, exist_ok=True)
    ic = legacy_icon(size)
    ic.save(os.path.join(d, "ic_launcher.png"))
    ic.save(os.path.join(d, "ic_launcher_round.png"))

os.makedirs(os.path.join(RES, "mipmap-anydpi-v26"), exist_ok=True)
fg = adaptive_foreground(432)
fg.save(os.path.join(RES, "drawable", "ic_launcher_foreground.png"))

# adaptive icon XML
with open(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), "w") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background"/>\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '</adaptive-icon>\n')
with open(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher_round.xml"), "w") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n'
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '    <background android:drawable="@color/ic_launcher_background"/>\n'
            '    <foreground android:drawable="@drawable/ic_launcher_foreground"/>\n'
            '</adaptive-icon>\n')

print("icons written:")
for r, _, files in os.walk(RES):
    for fn in sorted(files):
        if fn.endswith(".png") or fn.endswith(".xml"):
            p = os.path.join(r, fn)
            print("  ", os.path.relpath(p, RES), os.path.getsize(p))

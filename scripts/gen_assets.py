#!/usr/bin/env python3
"""50 iPhone-style wallpapers + launcher icons generate karta hai (Pillow only)."""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WP_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "wallpapers")
RES = os.path.join(ROOT, "app", "src", "main", "res")

# iOS jaisi palettes (base_top, base_bottom, blob1, blob2)
PALETTES = [
    ((10, 20, 60), (90, 40, 160), (255, 90, 120), (60, 200, 255)),
    ((20, 10, 40), (150, 30, 120), (255, 160, 60), (80, 120, 255)),
    ((0, 30, 60), (0, 120, 180), (120, 255, 220), (255, 255, 255)),
    ((40, 0, 60), (200, 40, 90), (255, 200, 80), (140, 80, 255)),
    ((5, 5, 15), (30, 30, 70), (90, 140, 255), (200, 90, 255)),
    ((120, 20, 40), (40, 10, 80), (255, 120, 80), (255, 220, 120)),
    ((0, 60, 50), (0, 140, 120), (180, 255, 160), (120, 220, 255)),
    ((60, 30, 0), (180, 80, 20), (255, 200, 100), (255, 120, 120)),
    ((15, 15, 35), (70, 40, 140), (255, 80, 160), (100, 200, 255)),
    ((0, 40, 90), (120, 60, 160), (255, 150, 200), (130, 180, 255)),
    ((30, 0, 30), (110, 20, 110), (255, 110, 200), (150, 120, 255)),
    ((10, 50, 30), (30, 120, 80), (200, 255, 150), (120, 255, 220)),
]

W, H = 720, 1560


def vgradient(top, bottom):
    img = Image.new("RGB", (W, H))
    px = img.load()
    for y in range(H):
        t = y / max(1, H - 1)
        r = int(top[0] + (bottom[0] - top[0]) * t)
        g = int(top[1] + (bottom[1] - top[1]) * t)
        b = int(top[2] + (bottom[2] - top[2]) * t)
        for x in range(0, W, 4):
            px[x, y] = (r, g, b)
            if x + 1 < W:
                px[x + 1, y] = (r, g, b)
            if x + 2 < W:
                px[x + 2, y] = (r, g, b)
            if x + 3 < W:
                px[x + 3, y] = (r, g, b)
    return img


def blobs(base, rng, colors, count=5, alpha=150, blur=90, small=False):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(count):
        col = rng.choice(colors)
        rw = rng.randint(W // (5 if small else 3), W // (2 if small else 1))
        rh = rng.randint(H // 8, H // 3)
        x = rng.randint(-rw // 2, W - rw // 2)
        y = rng.randint(-rh // 2, H - rh // 2)
        d.ellipse([x, y, x + rw, y + rh], fill=col + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(blur))
    return Image.alpha_composite(base.convert("RGBA"), layer)


def bokeh(img, rng, count=40):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(count):
        r = rng.randint(6, 46)
        x = rng.randint(0, W)
        y = rng.randint(0, H)
        a = rng.randint(25, 90)
        d.ellipse([x - r, y - r, x + r, y + r], outline=(255, 255, 255, a), width=max(2, r // 8))
    layer = layer.filter(ImageFilter.GaussianBlur(2))
    return Image.alpha_composite(img.convert("RGBA"), layer)


def streak(img, rng):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    x0 = rng.randint(0, W)
    d.line([x0, -50, x0 + rng.randint(-260, 260), H + 50], fill=(255, 255, 255, 70), width=rng.randint(30, 90))
    layer = layer.filter(ImageFilter.GaussianBlur(40))
    return Image.alpha_composite(img.convert("RGBA"), layer)


def vignette(img, strength=90):
    layer = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(layer)
    d.ellipse([-W // 2, -H // 2, W + W // 2, H + H // 2], fill=255)
    layer = layer.filter(ImageFilter.GaussianBlur(220))
    black = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    inv = Image.eval(layer, lambda v: 255 - int(v * strength / 255))
    black.putalpha(inv)
    return Image.alpha_composite(img.convert("RGBA"), black)


def make_wallpaper(i):
    rng = random.Random(1000 + i)
    pal = PALETTES[i % len(PALETTES)]
    style = i % 5
    if style == 0:
        img = vgradient(pal[0], pal[1])
        img = blobs(img, rng, [pal[2], pal[3]], count=5)
    elif style == 1:
        img = vgradient(pal[1], pal[0])
        img = blobs(img, rng, [pal[2], pal[3]], count=3, alpha=200, blur=60)
        img = bokeh(img, rng, count=34)
    elif style == 2:
        img = vgradient(pal[2], pal[1])
        img = blobs(img, rng, [pal[0], pal[3]], count=6, alpha=170)
        img = streak(img, rng)
    elif style == 3:
        img = vgradient((8, 8, 18), (25, 25, 55))
        img = blobs(img, rng, [pal[2], pal[3]], count=4, alpha=220, blur=70)
        img = bokeh(img, rng, count=50)
    else:
        img = vgradient(pal[3], pal[0])
        img = blobs(img, rng, [pal[1], pal[2]], count=7, alpha=140, blur=110, small=True)
    img = vignette(img)
    return img.convert("RGB")


def rounded_rect(d, box, radius, fill):
    x0, y0, x1, y1 = box
    d.rounded_rectangle(box, radius=radius, fill=fill)


def make_launcher(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # gradient background
    grad = Image.new("RGB", (size, size))
    px = grad.load()
    top = (94, 92, 230)
    bot = (10, 132, 255)
    for y in range(size):
        t = y / max(1, size - 1)
        col = (int(top[0] + (bot[0] - top[0]) * t), int(top[1] + (bot[1] - top[1]) * t), int(top[2] + (bot[2] - top[2]) * t))
        for x in range(size):
            px[x, y] = col
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size, size], radius=int(size * 0.22), fill=255)
    img.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(img)
    # white phone glyph
    m = size * 0.28
    w = size * 0.16
    d.rounded_rectangle([m, m * 0.72, size - m, size - m * 0.72], radius=int(size * 0.07),
                        outline=(255, 255, 255, 255), width=max(2, int(size * 0.035)))
    cx = size / 2
    cy = size * 0.44
    s = size * 0.075
    d.polygon([(cx - s, cy - s), (cx - s, cy + s), (cx + s, cy)], fill=(255, 255, 255, 255))
    d.rounded_rectangle([cx - w / 2, size * 0.68, cx + w / 2, size * 0.68 + max(2, size * 0.03)],
                        radius=max(2, int(size * 0.015)), fill=(255, 255, 255, 255))
    return img


def main():
    os.makedirs(WP_DIR, exist_ok=True)
    print("Wallpapers...")
    for i in range(1, 51):
        img = make_wallpaper(i)
        img.save(os.path.join(WP_DIR, "wp%03d.jpg" % i), quality=82)
        if i % 10 == 0:
            print("  %d/50" % i)
    print("Launcher icons...")
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, size in densities.items():
        folder = os.path.join(RES, "mipmap-" + name)
        os.makedirs(folder, exist_ok=True)
        icon = make_launcher(size)
        icon.save(os.path.join(folder, "ic_launcher.png"))
        # round version
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size, size], fill=255)
        bg = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        bg.paste(icon, (0, 0), mask)
        bg.save(os.path.join(folder, "ic_launcher_round.png"))
    print("Done.")


if __name__ == "__main__":
    main()

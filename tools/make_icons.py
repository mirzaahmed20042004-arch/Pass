#!/usr/bin/env python3
"""Slice the generated 1024px icon into launcher densities and stage web assets."""
import os
import shutil

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "assets_src", "icon_1024.png")

DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


def main():
    img = Image.open(SRC).convert("RGBA")
    for folder, size in DENSITIES.items():
        d = os.path.join(ROOT, "app", "res", "mipmap-" + folder)
        os.makedirs(d, exist_ok=True)
        img.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))
        print("mipmap-%s %dpx" % (folder, size))
    static = os.path.join(ROOT, "backend", "static")
    os.makedirs(static, exist_ok=True)
    hero = os.path.join(ROOT, "assets_src", "hero_1200.png")
    if os.path.exists(hero):
        shutil.copy(hero, os.path.join(static, "hero.png"))
        print("hero copied")


if __name__ == "__main__":
    main()

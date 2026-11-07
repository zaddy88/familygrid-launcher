"""Renders the logo, the README banner and the Play feature graphic from the phone screenshots.

Run:  python docs/make_images.py   (needs Pillow; uses Windows Segoe UI fonts if present)
"""
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(HERE, "screenshots")
OUT = os.path.join(HERE, "images")
os.makedirs(OUT, exist_ok=True)

TILES = ["#42A5F5", "#25D366", "#FF5252", "#FFD54F", "#AB47BC", "#26A69A", "#FF8A65", "#EC407A", "#78909C"]
BG_TOP, BG_BOTTOM = (20, 27, 38), (11, 15, 22)


def font(bold, size):
    for name in (("segoeuib.ttf", "arialbd.ttf") if bold else ("segoeui.ttf", "arial.ttf")):
        p = os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts", name)
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def gradient(w, h, top=BG_TOP, bottom=BG_BOTTOM):
    g = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(g)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
    return g


def logo(size):
    """Same design as res/drawable/ic_launcher.xml: 3x3 coloured tiles on a dark rounded square."""
    s = size / 48.0
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([2 * s, 2 * s, 46 * s, 46 * s], radius=8 * s, fill=(20, 27, 38, 255))
    for i, c in enumerate(TILES):
        x, y = 7 + (i % 3) * 11, 8 + (i // 3) * 11
        d.rounded_rectangle([x * s, y * s, (x + 12) * s, (y + 12) * s], radius=2 * s, fill=c)
    return img


def rounded(img, r):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0] - 1, img.size[1] - 1], radius=r, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def phone(shot, height):
    """Screenshot cropped of the status bar, in a simple dark phone frame with a soft shadow."""
    im = Image.open(os.path.join(SHOTS, shot)).convert("RGB")
    im = im.crop((0, 80, im.width, im.height))  # drop the black status-bar strip
    w = int(im.width * height / im.height)
    im = rounded(im.resize((w, height), Image.LANCZOS), int(w * 0.07))
    pad = int(w * 0.035)
    frame = Image.new("RGBA", (w + 2 * pad, height + 2 * pad), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle([0, 0, frame.width - 1, frame.height - 1],
                                            radius=int(w * 0.1), fill=(40, 48, 60, 255))
    frame.alpha_composite(im, (pad, pad))
    shadow = Image.new("RGBA", (frame.width + 60, frame.height + 60), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([30, 40, frame.width + 30, frame.height + 40],
                                             radius=int(w * 0.1), fill=(0, 0, 0, 150))
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    shadow.alpha_composite(frame, (30, 30))
    return shadow


def banner(w, h, shots, path, title_size, tag_size):
    img = gradient(w, h).convert("RGBA")
    d = ImageDraw.Draw(img)
    lg = int(h * 0.22)
    left = int(w * 0.06)
    img.alpha_composite(logo(lg), (left, int(h * 0.12)))
    ty = int(h * 0.12) + lg + int(h * 0.04)
    d.text((left, ty), "FamilyGrid", font=font(True, title_size), fill="white")
    ty += int(title_size * 1.25)
    d.text((left, ty), "Made for parents and grandparents.", font=font(False, tag_size), fill=(143, 161, 179))
    ty += int(tag_size * 1.9)
    chips = ["Big tiles", "Wi-Fi status", "Speed dial", "6 languages"]
    cf = font(True, int(tag_size * 0.72))
    x = left
    chip_h = int(tag_size * 1.25)
    for c in chips:
        tw = d.textlength(c, font=cf)
        if x + tw + 28 > w * 0.50:          # wrap before running into the phones
            x, ty = left, ty + chip_h + 12
        d.rounded_rectangle([x, ty, x + tw + 28, ty + chip_h], radius=20, fill=(28, 37, 50))
        d.text((x + 14, ty + int(tag_size * 0.22)), c, font=cf, fill=(201, 212, 224))
        x += tw + 40
    ph = int(h * 0.76)
    x = w - int(w * 0.03)
    for i, s in enumerate(reversed(shots)):
        p = phone(s, ph)
        x -= p.width - 40
        img.alpha_composite(p, (x, int(h * 0.02) + (i % 2) * int(h * 0.05)))
    img.convert("RGB").save(path, quality=92)
    print("wrote", path)


logo(512).save(os.path.join(OUT, "logo.png"))
logo(128).save(os.path.join(OUT, "logo-128.png"))
print("wrote logo")
banner(1280, 640, ["home.png", "home-urdu.png"], os.path.join(OUT, "banner.png"), 84, 34)
banner(1024, 500, ["home.png", "settings.png"], os.path.join(OUT, "play-feature-graphic.png"), 64, 26)

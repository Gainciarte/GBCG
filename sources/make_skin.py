"""
Generates GBCG's skin artwork: the plastic texture of the console body, the circuit board
seen through the translucent skins, and realistic buttons (A/B, Start/Select and the
d-pad, with their pressed states) for the GBC, DMG and dark DMG skins.

Buttons are lit like real plastic: each shape gets a height map (a rounded bevel, plus a
dome or a dimple), its surface normals are lit from the top left (diffuse + specular),
its rim darkens where it sinks into the body, and a fine grain is added on top. Each
button fills its image edge to edge with the same shape as the original vector buttons,
so the buttons keep their size on screen (the views scale the image to fit them).

Writes PNGs to app/src/main/res/drawable-nodpi. Usage: python sources/make_skin.py
(needs numpy and Pillow). Previews of everything go to sources/skin_preview.png.
"""
import os
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
SS = 4  # supersampling
RNG = np.random.default_rng(1998)  # fixed seed: the same images on every run

LIGHT = np.array([-0.45, -0.6, 0.66])
LIGHT /= np.linalg.norm(LIGHT)
HALF = LIGHT + np.array([0, 0, 1.0])
HALF /= np.linalg.norm(HALF)


def hex_rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float) / 255


def box_blur(a, r):
    """Approximate gaussian blur (three box blurs) of a 2D array, radius r pixels."""
    r = max(1, int(r))
    for _ in range(3):
        for axis in (0, 1):
            pad = [(0, 0), (0, 0)]
            pad[axis] = (r + 1, r)
            p = np.pad(a, pad, mode="edge")
            c = np.cumsum(p, axis=axis)
            if axis == 0:
                a = (c[2 * r + 1:] - c[:-2 * r - 1]) / (2 * r + 1)
            else:
                a = (c[:, 2 * r + 1:] - c[:, :-2 * r - 1]) / (2 * r + 1)
    return a


def grid(w, h, margin=0):
    """Supersampled pixel centres of a w x h image, plus a margin around it (a button's
    bevel is worked out with room around the shape, then cropped by crop())."""
    y, x = np.mgrid[0:(h + 2 * margin) * SS, 0:(w + 2 * margin) * SS].astype(float)
    return (x + 0.5) / SS - margin, (y + 0.5) / SS - margin


def crop(rgba, w, h, margin):
    m = margin * SS
    return rgba[m:m + h * SS, m:m + w * SS]


def rounded_rect(x, y, x0, y0, x1, y1, r):
    """Mask (0..1) of a rounded rectangle, antialiased by the supersampling."""
    cx = np.clip(x, x0 + r, x1 - r)
    cy = np.clip(y, y0 + r, y1 - r)
    return ((x - cx) ** 2 + (y - cy) ** 2 <= r * r).astype(float)


def shade(mask, height, base, spec=0.3, shine=24, ambient=0.5, diffuse=0.62, grain=0.035,
          rim=0.0, rim_width=1.0):
    """Lights a height map; returns an RGBA image (still supersampled). rim darkens the
    edge of the shape, over rim_width pixels, where it would meet the console body."""
    gy, gx = np.gradient(height)
    n = np.stack([-gx, -gy, np.ones_like(height)], axis=-1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    dif = np.clip(n @ LIGHT, 0, 1)
    spc = np.clip(n @ HALF, 0, 1) ** shine * spec
    rgb = base[None, None, :] * (ambient + diffuse * dif)[..., None] + spc[..., None]
    if rim > 0:
        inner = np.clip(box_blur(mask, rim_width * SS) * 2 - 1, 0, 1)
        rgb *= (1 - rim * (1 - inner) ** 2)[..., None]
    rgb = np.clip(rgb, 0, 1)
    rgba = np.concatenate([rgb, mask[..., None]], axis=-1)
    return rgba, grain


def finish(rgba_grain, w, h):
    """Downsamples, adds grain and returns a PIL image."""
    rgba, grain = rgba_grain
    img = Image.fromarray((np.clip(rgba, 0, 1) * 255).astype(np.uint8), "RGBA")
    img = img.resize((w, h), Image.LANCZOS)
    a = np.asarray(img).astype(float) / 255
    noise = RNG.normal(0, grain, (h, w))
    a[..., :3] = np.clip(a[..., :3] * (1 + noise[..., None]), 0, 1)
    return Image.fromarray((a * 255).astype(np.uint8), "RGBA")


def round_button(color, pressed, size=256):
    """A/B: a circle as wide as the image, like the original ic_button_ab.xml."""
    margin = size // 8
    x, y = grid(size, size, margin)
    cx = cy = r = size * 0.5
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
    mask = (d <= r).astype(float)
    depth = (0.5 if pressed else 1.0) * size * 0.05
    height = box_blur(mask, size * 0.06 * SS) * depth * SS
    dome = np.clip(1 - (d / r) ** 2, 0, 1) * size * (0.03 if pressed else 0.075) * SS
    height = height + dome * mask
    base = hex_rgb(color) * (0.85 if pressed else 1.0)
    rgba, grain = shade(mask, height / SS, base, spec=0.22 if pressed else 0.42, shine=26, ambient=0.45,
                        diffuse=0.72, rim=0.45 if pressed else 0.35, rim_width=size * 0.03)
    return finish((crop(rgba, size, size, margin), grain), size, size)


def pill_button(color, pressed, w=384, h=128):
    """Start/Select: matte rubber, a 3:1 pill filling the image, like ic_button_startselect.xml."""
    margin = h // 4
    x, y = grid(w, h, margin)
    mask = rounded_rect(x, y, 0, 0, w, h, h / 2)
    depth = (0.4 if pressed else 1.0) * h * 0.1
    height = box_blur(mask, h * 0.12 * SS) * depth * SS
    base = hex_rgb(color) * (0.82 if pressed else 1.0)
    rgba, grain = shade(mask, height / SS, base, spec=0.06, shine=8, ambient=0.55, diffuse=0.55, grain=0.05,
                        rim=0.45 if pressed else 0.35, rim_width=h * 0.06)
    return finish((crop(rgba, w, h, margin), grain), w, h)


def cross_mask(x, y, s):
    """The d-pad's cross, as in ic_button_dpad_background.xml: arms a third as wide as the
    image, reaching its edges, with their ends rounded."""
    lo, hi = s * 0.3334, s * 0.6666
    rr = s * 0.1
    vertical = rounded_rect(x, y, lo, 0, hi, s, rr)
    horizontal = rounded_rect(x, y, 0, lo, s, hi, rr)
    return np.maximum(vertical, horizontal), lo, hi


def dpad(color, size=320):
    margin = size // 8
    x, y = grid(size, size, margin)
    mask, lo, hi = cross_mask(x, y, size)
    height = box_blur(mask, size * 0.035 * SS) * size * 0.045 * SS
    # The dimple in the middle
    cx = cy = size * 0.5
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
    rd = size * 0.12
    height -= np.clip(1 - (d / rd) ** 2, 0, 1) * size * 0.02 * SS
    rgba, grain = shade(mask, height / SS, hex_rgb(color), spec=0.32, shine=22, ambient=0.45, diffuse=0.7,
                        rim=0.35, rim_width=size * 0.025)
    return finish((crop(rgba, size, size, margin), grain), size, size)


def dpad_pressed_overlay(directions, size=320):
    """Darkens the pressed arms."""
    x, y = grid(size, size)
    mask, lo, hi = cross_mask(x, y, size)
    sel = np.zeros_like(mask)
    regions = {
        "up": y < lo, "down": y > hi, "left": x < lo, "right": x > hi,
    }
    for dname in directions:
        sel = np.maximum(sel, regions[dname].astype(float))
    alpha = box_blur(sel * mask, size * 0.02 * SS) * mask * 0.38
    rgba = np.zeros(mask.shape + (4,))
    rgba[..., 3] = alpha
    img = Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")
    return img.resize((size, size), Image.LANCZOS)


def plastic_texture(size=256):
    """Tileable grain for the console body: light and dark specks at low opacity."""
    n = RNG.normal(0, 1, (size, size))
    # Blur with wrap-around so the tile is seamless
    k = np.fft.fft2(n)
    fy = np.fft.fftfreq(size)[:, None]
    fx = np.fft.fftfreq(size)[None, :]
    k *= np.exp(-(fx ** 2 + fy ** 2) / (2 * 0.18 ** 2))
    n = np.real(np.fft.ifft2(k))
    n /= n.std()
    rgba = np.zeros((size, size, 4))
    light = n > 0
    rgba[..., :3] = np.where(light[..., None], 1.0, 0.0)
    rgba[..., 3] = np.clip(np.abs(n) * 0.05, 0, 0.16)
    return Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")


def circuit_board(size=512):
    """Tileable circuit board, seen through the translucent skins."""
    from PIL import ImageDraw
    big = Image.new("RGB", (size * SS, size * SS), (38, 66, 48))
    d = ImageDraw.Draw(big)
    s = SS

    def wrap(draw_fn):
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                draw_fn(ox * s, oy * s)

    copper = (150, 128, 70)
    for _ in range(70):
        x0, y0 = RNG.integers(0, size, 2)
        pts = [(x0, y0)]
        for _ in range(RNG.integers(2, 5)):
            px, py = pts[-1]
            step = int(RNG.integers(20, 90))
            dirn = RNG.integers(0, 6)
            dx, dy = [(1, 0), (0, 1), (1, 1), (-1, 1), (-1, 0), (0, -1)][dirn]
            pts.append((px + dx * step, py + dy * step))
        width = int(RNG.choice([2, 2, 3, 4]))
        def draw_trace(ox, oy, pts=pts, width=width):
            d.line([(px * s + ox, py * s + oy) for px, py in pts], fill=copper, width=width * s, joint="curve")
            for px, py in (pts[0], pts[-1]):
                rr = (width + 2) * s
                d.ellipse([px * s + ox - rr, py * s + oy - rr, px * s + ox + rr, py * s + oy + rr], fill=(180, 160, 95))
        wrap(draw_trace)
    for _ in range(7):
        x0, y0 = RNG.integers(0, size, 2)
        w, h = int(RNG.integers(40, 110)), int(RNG.integers(24, 70))
        def draw_chip(ox, oy, x0=x0, y0=y0, w=w, h=h):
            for i in range(4, w - 3, 8):
                for py in (y0 - 5, y0 + h + 1):
                    d.rectangle([(x0 + i) * s + ox, py * s + oy, (x0 + i + 3) * s + ox, (py + 4) * s + oy], fill=(170, 170, 165))
            d.rectangle([x0 * s + ox, y0 * s + oy, (x0 + w) * s + ox, (y0 + h) * s + oy], fill=(24, 24, 26))
            d.rectangle([(x0 + 3) * s + ox, (y0 + 3) * s + oy, (x0 + w - 3) * s + ox, (y0 + h - 3) * s + oy], fill=(32, 32, 35))
        wrap(draw_chip)
    img = big.resize((size, size), Image.LANCZOS)
    a = np.asarray(img).astype(float) / 255
    a = np.clip(a * (1 + RNG.normal(0, 0.04, a.shape[:2])[..., None]), 0, 1)
    return Image.fromarray((a * 255).astype(np.uint8), "RGB")


COLOURS = {
    "": {"ab": "#2a2a2a", "ss": "#333333", "dpad": "#2a2a2a"},          # GBC
    "_dmg": {"ab": "#9E265A", "ss": "#918E92", "dpad": "#2a2a2a"},     # DMG, light
    "_dmg_dark": {"ab": "#4e4c50", "ss": "#4e4c50", "dpad": "#4e4c50"},  # DMG, dark
}


def main():
    os.makedirs(OUT, exist_ok=True)
    images = {}
    for suffix, c in COLOURS.items():
        images[f"ic_button_ab{suffix}"] = round_button(c["ab"], False)
        images[f"ic_button_ab{suffix}_pressed"] = round_button(c["ab"], True)
        images[f"ic_button_startselect{suffix}"] = pill_button(c["ss"], False)
        images[f"ic_button_startselect{suffix}_pressed"] = pill_button(c["ss"], True)
    images["ic_button_dpad_background"] = dpad(COLOURS[""]["dpad"])
    images["ic_button_dpad_background_dmg_dark"] = dpad(COLOURS["_dmg_dark"]["dpad"])
    images["ic_button_dpad_highlight"] = Image.new("RGBA", (320, 320), (0, 0, 0, 0))
    for name, dirs in {
        "up": ["up"], "down": ["down"], "left": ["left"], "right": ["right"],
        "up_left": ["up", "left"], "up_right": ["up", "right"],
        "down_left": ["down", "left"], "down_right": ["down", "right"],
    }.items():
        images[f"ic_button_dpad_highlight_pressed_{name}"] = dpad_pressed_overlay(dirs)
    images["skin_texture"] = plastic_texture()
    images["skin_pcb"] = circuit_board()
    for name, img in images.items():
        img.save(os.path.join(OUT, name + ".png"), optimize=True)

    # Preview sheet
    sheet = Image.new("RGBA", (1100, 560), (0, 79, 80, 255))
    tex = images["skin_texture"]
    for tx in range(0, 1100, 256):
        for ty in range(0, 560, 256):
            sheet.alpha_composite(tex, (tx, ty))
    xpos = 20
    for suffix in COLOURS:
        sheet.alpha_composite(images[f"ic_button_ab{suffix}"].resize((128, 128), Image.LANCZOS), (xpos, 20))
        sheet.alpha_composite(images[f"ic_button_ab{suffix}_pressed"].resize((128, 128), Image.LANCZOS), (xpos + 140, 20))
        sheet.alpha_composite(images[f"ic_button_startselect{suffix}"].resize((150, 50), Image.LANCZOS), (xpos, 170))
        sheet.alpha_composite(images[f"ic_button_startselect{suffix}_pressed"].resize((150, 50), Image.LANCZOS), (xpos + 160, 170))
        xpos += 360
    sheet.alpha_composite(images["ic_button_dpad_background"].resize((200, 200), Image.LANCZOS), (20, 250))
    pressed = images["ic_button_dpad_background"].copy()
    pressed.alpha_composite(images["ic_button_dpad_highlight_pressed_up_right"])
    sheet.alpha_composite(pressed.resize((200, 200), Image.LANCZOS), (240, 250))
    pcb = images["skin_pcb"].convert("RGBA").resize((300, 300))
    # The same tints as the app's translucent skins (Skin.kt)
    purple = Image.new("RGBA", pcb.size, (91, 46, 145, 176))
    pcb_purple = pcb.copy(); pcb_purple.alpha_composite(purple)
    clear = Image.new("RGBA", pcb.size, (220, 227, 232, 110))
    pcb_clear = pcb.copy(); pcb_clear.alpha_composite(clear)
    sheet.alpha_composite(pcb_purple.crop((0, 0, 280, 280)), (480, 260))
    sheet.alpha_composite(pcb_clear.crop((0, 0, 280, 280)), (790, 260))
    sheet.convert("RGB").save(os.path.join(ROOT, "sources", "skin_preview.png"))


if __name__ == "__main__":
    main()

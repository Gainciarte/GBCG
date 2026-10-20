"""
Generates GBCG's skin artwork: realistic buttons (A/B, Start/Select and the d-pad, with
their pressed states) for the GBC, DMG and dark DMG skins, and the grain of the plastic
of the console body. The body itself (its colour, the speaker grille, the labels and the
inside of the translucent skins) is drawn by the app, in SkinDrawable.kt, because it
depends on where the buttons are.

Buttons are lit like real plastic: each shape gets a height map (a rounded bevel, plus a
dome or a dimple, the GBC's engraved letters and raised arrows), its surface normals are
lit from the top left (diffuse + specular), its rim darkens where it sinks into the body,
and a fine grain is added on top. Each button fills its image edge to edge with the same
shape as the original vector buttons, so the buttons keep their size on screen (the
views scale the image to fit them).

As on the real consoles, the GBC has the letters on its A and B buttons and arrows on
its d-pad; the DMG has plain buttons (its labels are printed on the body).

Writes PNGs to app/src/main/res/drawable-nodpi. Usage: python sources/make_skin.py
(needs numpy and Pillow). Previews of everything
go to sources/skin_preview.png.
"""
import os
import numpy as np
from PIL import Image, ImageDraw

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


def letter_mask(letter, w, h, margin, centre_x, centre_y, cap):
    """Supersampled mask of the GBC's A or B: sans serif letters drawn with a single stroke
    width (as on the real buttons), tall and narrow, of cap height cap, centred on
    (centre_x, centre_y)."""
    big = Image.new("L", ((w + 2 * margin) * SS, (h + 2 * margin) * SS), 0)
    d = ImageDraw.Draw(big)
    stroke = cap * 0.075
    sw = max(1, int(stroke * SS))

    def pt(x, y):
        return ((x + margin) * SS, (y + margin) * SS)

    def dot(x, y):  # round the stroke ends and joins
        r = stroke * SS / 2
        cx, cy = pt(x, y)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)

    top = centre_y - cap / 2 + stroke / 2
    bottom = centre_y + cap / 2 - stroke / 2
    if letter == "A":
        half = cap * 0.33
        bar = top + (bottom - top) * 0.64
        d.line([pt(centre_x - half, bottom), pt(centre_x, top), pt(centre_x + half, bottom)], fill=255, width=sw,
               joint="curve")
        f = (bar - top) / (bottom - top)
        d.line([pt(centre_x - half * f, bar), pt(centre_x + half * f, bar)], fill=255, width=sw)
        dot(centre_x, top)
    else:
        width = cap * 0.62
        left = centre_x - width / 2
        middle = top + (bottom - top) * 0.47
        d.line([pt(left, top), pt(left, bottom)], fill=255, width=sw)
        for y0, y1, right in ((top, middle, left + width * 0.8), (middle, bottom, left + width)):
            r = (y1 - y0) / 2
            d.line([pt(left, y0), pt(right - r, y0)], fill=255, width=sw)
            d.line([pt(left, y1), pt(right - r, y1)], fill=255, width=sw)
            box = [pt(right - 2 * r, y0)[0] - sw / 2, pt(0, y0)[1] - sw / 2,
                   pt(right, y1)[0] + sw / 2, pt(0, y1)[1] + sw / 2]
            d.arc(box, -90, 90, fill=255, width=sw)
        for x, y in ((left, top), (left, bottom)):
            dot(x, y)
    return np.asarray(big).astype(float) / 255


def round_button(color, pressed, size=256, letter=None):
    """A/B: a circle as wide as the image, like the original ic_button_ab.xml, nearly flat
    on top with a rounded edge. The GBC's have their letter engraved, as on the real buttons."""
    margin = size // 8
    x, y = grid(size, size, margin)
    cx = cy = r = size * 0.5
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
    mask = (d <= r).astype(float)
    depth = (0.5 if pressed else 1.0) * size * 0.05
    height = box_blur(mask, size * 0.06 * SS) * depth * SS
    dome = np.clip(1 - (d / r) ** 2, 0, 1) * size * (0.015 if pressed else 0.035) * SS
    height = height + dome * mask
    if letter:
        # Engraved into the button, not raised
        # A little left of the middle, as on the real buttons
        glyph = letter_mask(letter, size, size, margin, cx - size * 0.045, cy, size * 0.48)
        height = height - box_blur(glyph, size * 0.004 * SS) * size * 0.014 * SS
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


def triangle_mask(x, y, cx, cy, size, direction):
    """An arrow head (equilateral-ish triangle) pointing up, down, left or right."""
    # Work in the frame of an arrow pointing up
    u, v = {"up": (x - cx, y - cy), "down": (cx - x, cy - y),
            "left": (cy - y, x - cx), "right": (y - cy, cx - x)}[direction]
    half = size * 0.5
    inside = (v <= half * 0.75) & (v >= -half * 0.75 + np.abs(u) * 1.7)
    return inside.astype(float)


def dpad(color, size=320, style="gbc"):
    """The d-pad. The GBC's has a dimple in the middle and a raised arrow head near the end
    of each arm; the DMG's has three grooves across each arm and a small dome in the middle,
    sitting in a sunken ring."""
    margin = size // 8
    x, y = grid(size, size, margin)
    mask, lo, hi = cross_mask(x, y, size)
    height = box_blur(mask, size * 0.035 * SS) * size * 0.045 * SS
    cx = cy = size * 0.5
    d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2)
    if style == "gbc":
        rd = size * 0.12
        height -= np.clip(1 - (d / rd) ** 2, 0, 1) * size * 0.02 * SS
        t = size * 0.135
        far = lo * 0.48
        heads = np.zeros_like(mask)
        for direction, (ax, ay) in {"up": (cx, far), "down": (cx, size - far),
                                    "left": (far, cy), "right": (size - far, cy)}.items():
            heads = np.maximum(heads, triangle_mask(x, y, ax, ay, t, direction))
        height += box_blur(heads, size * 0.004 * SS) * size * 0.012 * SS
    else:
        # Sunken ring with a dome inside
        ring = ((d > size * 0.07) & (d < size * 0.105)).astype(float)
        height -= box_blur(ring, size * 0.006 * SS) * size * 0.012 * SS
        height += np.clip(1 - (d / (size * 0.07)) ** 2, 0, 1) ** 0.5 * size * 0.02 * SS
        # Three grooves across each arm, from near the middle towards the end
        arm = hi - lo
        grooves = np.zeros_like(mask)
        for k in range(3):
            along = lo * (0.2 + 0.24 * k)  # distance from the outer end of the arm
            gw, gl = size * 0.032, arm * 0.62
            for gx0, gy0, gx1, gy1 in (
                    (cx - gl / 2, along, cx + gl / 2, along + gw),                    # up
                    (cx - gl / 2, size - along - gw, cx + gl / 2, size - along),      # down
                    (along, cy - gl / 2, along + gw, cy + gl / 2),                    # left
                    (size - along - gw, cy - gl / 2, size - along, cy + gl / 2)):     # right
                grooves = np.maximum(grooves, rounded_rect(x, y, gx0, gy0, gx1, gy1, gw / 2))
        height -= box_blur(grooves, size * 0.004 * SS) * size * 0.01 * SS
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


def tileable_noise(size, sigma):
    """Gaussian-blurred white noise that tiles seamlessly (blurred in frequency space)."""
    n = RNG.normal(0, 1, (size, size))
    k = np.fft.fft2(n)
    fy = np.fft.fftfreq(size)[:, None]
    fx = np.fft.fftfreq(size)[None, :]
    k *= np.exp(-2 * (np.pi * sigma) ** 2 * (fx ** 2 + fy ** 2))
    n = np.real(np.fft.ifft2(k))
    return n / n.std()


def plastic_texture(size=256, bump=1.1, strength=0.2, coarse=0.35, sparkle=0.0):
    """Tileable overlay that gives the body the pebbled finish of moulded plastic: a field
    of tiny bumps, lit from the top left like the buttons. Lit slopes become white at low
    opacity, slopes in shadow black, so it works over any colour. bump is the size of the
    bumps in pixels, coarse adds a broader unevenness, and sparkle a few bright points."""
    h = tileable_noise(size, bump) + coarse * tileable_noise(size, bump * 4)
    gy = (np.roll(h, -1, 0) - np.roll(h, 1, 0)) / 2
    gx = (np.roll(h, -1, 1) - np.roll(h, 1, 1)) / 2
    n = np.stack([-gx, -gy, np.full_like(h, 2.2)], axis=-1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    lit = n @ LIGHT - LIGHT[2] * (2.2 / np.sqrt(2.2 ** 2))  # 0 on a flat surface
    lit /= np.abs(lit).max()
    if sparkle > 0:
        lit = np.maximum(lit, (RNG.random((size, size)) > 1 - sparkle) * 0.9)
    rgba = np.zeros((size, size, 4))
    rgba[..., :3] = np.where(lit[..., None] > 0, 1.0, 0.0)
    rgba[..., 3] = np.clip(np.abs(lit) * strength, 0, 1)
    return Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")


# The GBC's buttons are a different colour on each shell (measured on photos of real ones)
GBC_BUTTONS = {
    "berry": "#2C2A28",
    "dandelion": "#1E2030",
    "grape": "#3B4242",
    "kiwi": "#28292E",
    "teal": "#5E6463",
    "white": "#2F3033",
}
DMG = {"ab": "#982A5A", "ss": "#4A525A", "dpad": "#3A4045"}        # colours of a real DMG
DMG_DARK = {"ab": "#4e4c50", "ss": "#4e4c50", "dpad": "#4e4c50"}


def main():
    os.makedirs(OUT, exist_ok=True)
    # Everything in drawable-nodpi comes from here: clear what earlier runs left
    for old in os.listdir(OUT):
        if old.endswith(".png"):
            os.remove(os.path.join(OUT, old))
    # The realistic buttons are named real_*; the flat ones are the original vectors
    # (ic_button_*.xml in res/drawable), and a setting picks between them (ButtonArt.kt)
    images = {}
    for shell, colour in GBC_BUTTONS.items():
        for letter in ("a", "b"):
            images[f"real_button_{letter}_{shell}"] = round_button(colour, False, letter=letter.upper())
            images[f"real_button_{letter}_{shell}_pressed"] = round_button(colour, True, letter=letter.upper())
        images[f"real_button_startselect_{shell}"] = pill_button(colour, False)
        images[f"real_button_startselect_{shell}_pressed"] = pill_button(colour, True)
        images[f"real_dpad_{shell}"] = dpad(colour, style="gbc")
    for suffix, c in {"_dmg": DMG, "_dmg_dark": DMG_DARK}.items():
        images[f"real_button_ab{suffix}"] = round_button(c["ab"], False)
        images[f"real_button_ab{suffix}_pressed"] = round_button(c["ab"], True)
        images[f"real_button_startselect{suffix}"] = pill_button(c["ss"], False)
        images[f"real_button_startselect{suffix}_pressed"] = pill_button(c["ss"], True)
    images["real_dpad_dmg"] = dpad(DMG["dpad"], style="dmg")
    images["real_dpad_dmg_dark"] = dpad(DMG_DARK["dpad"], style="dmg")
    for name, dirs in {
        "up": ["up"], "down": ["down"], "left": ["left"], "right": ["right"],
        "up_left": ["up", "left"], "up_right": ["up", "right"],
        "down_left": ["down", "left"], "down_right": ["down", "right"],
    }.items():
        images[f"real_dpad_pressed_{name}"] = dpad_pressed_overlay(dirs)
    # The GBC's plastic is visibly pebbled; the DMG's is smoother
    images["skin_texture"] = plastic_texture(bump=1.1, strength=0.22, coarse=0.35, sparkle=0.004)
    images["skin_texture_dmg"] = plastic_texture(bump=0.8, strength=0.10, coarse=0.25)
    for name, img in images.items():
        img.save(os.path.join(OUT, name + ".png"), optimize=True)

    # Preview sheet, on the colours of a real Teal GBC and a DMG
    sheet = Image.new("RGBA", (1100, 600), (10, 144, 165, 255))
    dmg_side = Image.new("RGBA", (550, 600), (216, 209, 193, 255))
    sheet.alpha_composite(dmg_side, (550, 0))
    for tx in range(0, 550, 256):
        for ty in range(0, 600, 256):
            sheet.alpha_composite(images["skin_texture"].crop((0, 0, min(256, 550 - tx), min(256, 600 - ty))), (tx, ty))
            sheet.alpha_composite(images["skin_texture_dmg"].crop((0, 0, min(256, 550 - tx), min(256, 600 - ty))), (550 + tx, ty))
    def put(name, w, h, x, y):
        sheet.alpha_composite(images[name].resize((w, h), Image.LANCZOS), (x, y))
    put("real_dpad_teal", 200, 200, 20, 20)
    put("real_button_b_teal", 120, 120, 250, 90)
    put("real_button_a_teal", 120, 120, 390, 40)
    put("real_button_b_teal_pressed", 120, 120, 250, 240)
    put("real_button_a_teal_pressed", 120, 120, 390, 240)
    put("real_button_startselect_teal", 120, 40, 60, 420)
    put("real_button_startselect_teal_pressed", 120, 40, 200, 420)
    put("real_dpad_dmg", 200, 200, 570, 20)
    put("real_button_ab_dmg", 120, 120, 800, 90)
    put("real_button_ab_dmg_pressed", 120, 120, 940, 40)
    put("real_button_startselect_dmg", 120, 40, 610, 420)
    put("real_dpad_dmg_dark", 140, 140, 800, 380)
    put("real_button_ab_dmg_dark", 90, 90, 960, 400)
    sheet.convert("RGB").save(os.path.join(ROOT, "sources", "skin_preview.png"))


if __name__ == "__main__":
    main()

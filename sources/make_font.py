"""
Makes the app's font files from Nunito (SIL Open Font License, github.com/google/fonts,
ofl/nunito). Google publishes Nunito only as a variable font, whose default instance is
ExtraLight, and Android versions before 8 can't pick a weight from a variable font; so
this cuts fixed Regular, SemiBold and Bold instances out of it, keeping only the Latin
characters the app's six languages use (anything else falls back to the system font).

Usage: python sources/make_font.py <path to Nunito[wght].ttf>
Writes app/src/main/res/font/nunito_{regular,semibold,bold}.ttf (needs fontTools).
"""
import os
import sys

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app", "src", "main", "res", "font")
WEIGHTS = {"regular": 400, "semibold": 600, "bold": 700}
# Basic Latin, Latin-1, Latin Extended A/B and Additional, punctuation, currency, letterlike
# symbols and arrows
UNICODES = (list(range(0x20, 0x7F)) + list(range(0xA0, 0x250)) + list(range(0x1E00, 0x1F00)) +
            list(range(0x2000, 0x2070)) + list(range(0x20A0, 0x20C0)) + list(range(0x2100, 0x2150)) +
            list(range(0x2190, 0x2200)) + [0x2212, 0x2022, 0x2026])


def main():
    source = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    for name, weight in WEIGHTS.items():
        font = instancer.instantiateVariableFont(TTFont(source), {"wght": weight})
        options = subset.Options()
        options.layout_features = ["*"]
        options.name_IDs = ["*"]
        options.notdef_outline = True
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=UNICODES)
        subsetter.subset(font)
        font["OS/2"].usWeightClass = weight
        path = os.path.join(OUT, f"nunito_{name}.ttf")
        font.save(path)
        print(path, os.path.getsize(path))


if __name__ == "__main__":
    main()

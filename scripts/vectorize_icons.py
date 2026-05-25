"""
One-shot script: replaces <text> elements in plugin icons with vectorized
<path> elements extracted from JetBrains Mono Bold.

Why: SVG icons on the JetBrains Marketplace web preview render without access
to JetBrains Mono (it's bundled with IDEs, not browsers). Embedding glyph
paths makes rendering pixel-identical everywhere.

Re-run if the source <text> positions/contents change.
"""

import os
import re
import urllib.request
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen

FONT_URL  = "https://github.com/JetBrains/JetBrainsMono/raw/master/fonts/ttf/JetBrainsMono-Bold.ttf"
FONT_PATH = os.path.join(os.path.dirname(__file__), "JetBrainsMono-Bold.ttf")

ICON_LIGHT = os.path.normpath(os.path.join(os.path.dirname(__file__),
    "..", "src", "main", "resources", "META-INF", "pluginIcon.svg"))
ICON_DARK  = os.path.normpath(os.path.join(os.path.dirname(__file__),
    "..", "src", "main", "resources", "META-INF", "pluginIcon_dark.svg"))


if not os.path.exists(FONT_PATH):
    print(f"Downloading {FONT_URL}")
    urllib.request.urlretrieve(FONT_URL, FONT_PATH)

font  = TTFont(FONT_PATH)
upem  = font["head"].unitsPerEm
cmap  = font.getBestCmap()
gset  = font.getGlyphSet()


def glyph(char):
    name = cmap[ord(char)]  # getBestCmap() returns codepoint -> glyph name
    pen  = SVGPathPen(gset)
    gset[name].draw(pen)
    return pen.getCommands(), gset[name].width


def text_to_paths(text, x, y, font_size, anchor="start", fill="url(#g1)", fill_opacity=None):
    scale       = font_size / upem
    total_width = sum(glyph(c)[1] for c in text) * scale
    if anchor == "middle":
        start_x = x - total_width / 2
    elif anchor == "end":
        start_x = x - total_width
    else:
        start_x = x

    parts = []
    cur_x = start_x
    for c in text:
        path_d, adv = glyph(c)
        attrs = (
            f'd="{path_d}" '
            f'transform="translate({cur_x:.4f} {y}) scale({scale:.5f} -{scale:.5f})" '
            f'fill="{fill}"'
        )
        if fill_opacity is not None:
            attrs += f' fill-opacity="{fill_opacity}"'
        parts.append(f"<path {attrs}/>")
        cur_x += adv * scale
    return "\n  ".join(parts)


# Vectorize the two <text> runs from plugin_icon_{light,dark}.svg:
#   <text x="10"   y="23" font-size="6.5" font-weight=700 fill-opacity=0.35>0x</text>
#   <text x="24.5" y="24" font-size="11"  font-weight=700 text-anchor=middle>FF</text>
faded = text_to_paths("0x", 10.0,  23.0, 6.5, anchor="start",  fill_opacity=0.35)
bold  = text_to_paths("FF", 24.5,  24.0, 11.0, anchor="middle")

print("=== faded '0x' ===")
print(faded)
print()
print("=== bold 'FF' ===")
print(bold)


# Pattern that matches both <text> elements in either icon (light or dark).
TEXT_RE = re.compile(r"<text[^>]*>0x</text>\s*<text[^>]*>FF</text>", re.DOTALL)


def patch(svg_path):
    with open(svg_path, "r", encoding="utf-8") as f:
        svg = f.read()
    if "<text" not in svg:
        print(f"  (no <text> in {svg_path} — already vectorized)")
        return
    replacement = faded + "\n  " + bold
    new = TEXT_RE.sub(replacement, svg, count=1)
    if new == svg:
        print(f"  WARNING: no replacement made in {svg_path}")
        return
    with open(svg_path, "w", encoding="utf-8") as f:
        f.write(new)
    print(f"  patched {svg_path}")


print()
print("Patching SVGs:")
patch(ICON_LIGHT)
patch(ICON_DARK)

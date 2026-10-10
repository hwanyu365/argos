"""argos 워드마크를 108 x 108 adaptive icon 좌표의 Android vector pathData 로 만든다.

미리보기(typo3.html)의 word() 배치와 같은 규칙: Outfit 600, 높이 12 (g 는 1.45 배), 자간 보정, o 자리는 버건디 고리 + 동공.
사용: python tools/icon/gen_vectors.py <Outfit[wght].ttf> app/src/main/res/drawable
글꼴: https://github.com/google/fonts/tree/main/ofl/outfit (SIL OFL 1.1, 저장소에는 넣지 않는다)
출력: 디렉터리에 ic_launcher_foreground.xml, ic_launcher_monochrome.xml, ic_notification.xml, ic_launcher_background.xml, preview.svg
"""
import math
import sys
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

FONT = Path(sys.argv[1])
OUT = Path(sys.argv[2])
OUT.mkdir(parents=True, exist_ok=True)

font = instantiateVariableFont(TTFont(FONT), {"wght": 600})
gs = font.getGlyphSet()
cmap = font.getBestCmap()

INK = "#F4F1EA"
BUR = "#9E2A47"
BG0, BG1 = "#2B2E35", "#121316"
H, Y = 12.0, 48.0
SEQ = [("a", 0.0), ("r", -0.6), ("g", -0.4), ("o", -0.4), ("s", -0.4)]


def bounds(ch):
    bp = BoundsPen(gs)
    gs[cmap[ord(ch)]].draw(bp)
    return bp.bounds


def path(ch, x, top, h):
    """글자 윤곽을 상자(x, top, 높이 h)에 맞춘 pathData. 글꼴 좌표는 y 가 위로 증가하므로 뒤집는다."""
    x0, y0, x1, y1 = bounds(ch)
    s = h / (y1 - y0)
    pen = SVGPathPen(gs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
    gs[cmap[ord(ch)]].draw(TransformPen(pen, (s, 0, 0, -s, x - x0 * s, top + y1 * s)))
    return pen.getCommands(), (x1 - x0) * s


# 배치: 폭을 먼저 재고 54 에 가운데 맞춘다.
layout, x = [], 0.0
for ch, k in SEQ:
    x += k
    h = H * 1.45 if ch == "g" else H
    _, w = path(ch, 0, 0, h)
    layout.append((ch, x, w, h))
    x += w + 0.9
off = 54 - (x - 0.9) / 2

letters, eye = [], None
for ch, px, w, h in layout:
    if ch == "o":
        eye = (off + px + w / 2, Y + H / 2, H / 2)
    else:
        letters.append(path(ch, off + px, Y, h)[0])

cx, cy, r = eye


def circle(x, y, rr):
    return f"M{x - rr:.2f},{y:.2f}a{rr:.2f},{rr:.2f} 0 1,0 {2 * rr:.2f},0a{rr:.2f},{rr:.2f} 0 1,0 {-2 * rr:.2f},0Z"


ring = circle(cx, cy, r - 1.6)
# 동공 + 빛: 빛 자리를 evenOdd 로 비워 바탕이 그대로 보이게 한다 (저장소 소유자 요청).
pupil = circle(cx, cy, r * 0.36) + circle(cx + r * 0.16, cy - r * 0.16, r * 0.11)
word = "".join(letters)


def vector(body, w=108, h=108, vw=108, vh=108, vx=0, vy=0, extra_ns=""):
    group_open = f'\n    <group android:translateX="{-vx:.2f}" android:translateY="{-vy:.2f}">' if vx or vy else ""
    group_close = "\n    </group>" if vx or vy else ""
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 생성 파일: argos 워드마크 (Outfit SemiBold, SIL OFL 1.1). 글자 윤곽을 경로로 변환해 글꼴 파일은 포함하지 않는다. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"{extra_ns}
    android:width="{w}dp"
    android:height="{h}dp"
    android:viewportWidth="{vw:.2f}"
    android:viewportHeight="{vh:.2f}">{group_open}{body}{group_close}
</vector>
'''


def item(color, ring_color, pupil_color):
    return f'''
    <path android:fillColor="{color}" android:pathData="{word}" />
    <path android:strokeColor="{ring_color}" android:strokeWidth="3.2" android:pathData="{ring}" />
    <path android:fillColor="{pupil_color}" android:fillType="evenOdd" android:pathData="{pupil}" />'''


(OUT / "ic_launcher_foreground.xml").write_text(vector(item(INK, BUR, BUR)), encoding="utf-8")
(OUT / "ic_launcher_monochrome.xml").write_text(vector(item("#FFFFFF", "#FFFFFF", "#FFFFFF")), encoding="utf-8")
(OUT / "ic_launcher_background.xml").write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108"
                android:startColor="{BG0}"
                android:endColor="{BG1}" />
        </aapt:attr>
    </path>
</vector>
''', encoding="utf-8")

# 알림: 같은 워드마크를 흰색 단색으로, 글자 영역만 잘라 24dp 높이 안에서 최대한 크게.
x0 = off - 0.5
x1 = off + layout[-1][1] + layout[-1][2] + 0.5
y0, y1 = Y - 0.5, Y + H * 1.45 + 0.5
vw, vh = x1 - x0, y1 - y0
side = max(vw, vh)
pad_y = (side - vh) / 2
(OUT / "ic_notification.xml").write_text(
    vector(item("#FFFFFF", "#FFFFFF", "#FFFFFF"), w=24, h=24, vw=side, vh=side, vx=x0, vy=y0 - pad_y), encoding="utf-8"
)

(OUT / "preview.svg").write_text(f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="480" height="480">
<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="{BG0}"/><stop offset="1" stop-color="{BG1}"/></linearGradient></defs>
<rect width="108" height="108" fill="url(#bg)"/><path fill="{INK}" d="{word}"/><path fill="none" stroke="{BUR}" stroke-width="3.2" d="{ring}"/><path fill="{BUR}" fill-rule="evenodd" d="{pupil}"/></svg>''', encoding="utf-8")
print("eye", round(cx, 2), round(cy, 2), round(r, 2), "word box", round(x0, 1), round(x1, 1))

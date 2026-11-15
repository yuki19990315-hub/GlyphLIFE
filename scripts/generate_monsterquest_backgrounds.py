#!/usr/bin/env python3
"""Hand-coded, pixel-locked 16 px tiles and 32–64 px scenery objects.

Outputs four independently scrollable, seamless-ish 640x180 RGBA PNG layers
per zone.  The gameplay camera shows 320x180 at native scale.  No generated
bitmap or blur is used: every contour is explicitly drawn on a pixel grid.
Run: python3 scripts/generate_monsterquest_backgrounds.py
"""

from dataclasses import dataclass
from pathlib import Path
from PIL import Image, ImageDraw
import math
import random

WIDTH = 640
HEIGHT = 180
TILE = 16
FLOOR = 142
OUT = Path(__file__).resolve().parents[1] / "monsterquest/src/main/res/drawable-nodpi"


def rgb(hex_value):
    return tuple(int(hex_value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def blend(one, two, fraction):
    """Small fixed secondary-color set; sharp boundaries, never gradients."""
    a = rgb(one)
    b = rgb(two)
    return "".join(f"{round(a[i] * (1 - fraction) + b[i] * fraction):02X}"
                   for i in range(3))


@dataclass(frozen=True)
class Scheme:
    sky: str
    haze: str
    cloud: str
    far: str
    far_lit: str
    rear: str
    rear_light: str
    rear_dark: str
    earth: str
    earth_dark: str
    lip: str
    highlight: str
    accent: str
    bright: str


SCHEMES = {
    "meadow": Scheme("6EA9B6", "9AC5C4", "E2E7D4", "567F8E", "8EB9B7", "476A62", "69917D", "304F50", "806F55", "4D5046", "668963", "ADC77F", "ECD28C", "F8E4BA"),
    "forest": Scheme("304E67", "537B7A", "9BB4AE", "345C60", "66877A", "234D43", "416C52", "173B38", "4D5040", "283B36", "60814D", "8BA869", "E2C98A", "EDE5BF"),
    "cave": Scheme("1B3040", "334D5B", "51717C", "233A4B", "496575", "344858", "526F7C", "1B2A39", "5B5D60", "303E48", "71827F", "9FB0AD", "6AA5B7", "B6E6DD"),
    "ruins": Scheme("6E8B91", "B0B9A8", "DFD6AE", "6A7C78", "9AA7A0", "526667", "8A9484", "374F55", "777669", "454C4E", "A09E7D", "CFBD92", "D8B36C", "F8E2AE"),
    "volcano": Scheme("3C2937", "74413E", "B36A4F", "4E3943", "895148", "463D46", "6E5651", "282A35", "5C4B45", "302F37", "95664F", "C28A5C", "F08046", "FFCE75"),
    "ice": Scheme("739FBC", "B8D6D7", "F2F5E6", "729AB2", "C7DCE2", "4F7C94", "A2C5CE", "365E7A", "748F9B", "486879", "B3D6D9", "E6FBF2", "82C9DC", "F4FFFF"),
    "coast": Scheme("72B0BB", "B2D7C4", "F4E2B9", "668F99", "A3C2AD", "3D7886", "6EA2A5", "285567", "C4AB7B", "987B62", "E3CF9C", "F2E2B6", "4AB3C0", "DAF8ED"),
    "wasteland": Scheme("B09576", "D9C1A0", "F0DDAC", "93836E", "B7A182", "776F64", "988878", "534D4C", "9F785B", "664E47", "C5A078", "E6C392", "B96852", "F4D4A1"),
    "dark": Scheme("171E38", "293454", "535779", "252942", "515572", "353147", "55455F", "1D2438", "484151", "242837", "716379", "9B809E", "AD6DAE", "E5B1DD"),
    "boss": Scheme("142139", "283653", "A9B7C4", "24324A", "65738B", "344158", "596784", "161D31", "555869", "262E42", "8A8490", "B3A39D", "D06F70", "F0D6C3"),
}


class Painter:
    def __init__(self):
        self.image = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
        self.draw = ImageDraw.Draw(self.image)

    def rect(self, x, y, w, h, color):
        if w > 0 and h > 0:
            self.draw.rectangle((int(x), int(y), int(x + w - 1), int(y + h - 1)), fill=rgb(color))

    def polygon(self, points, color):
        self.draw.polygon([(int(x), int(y)) for x, y in points], fill=rgb(color))


def hill_y(x, zone, secondary=False):
    phase = (x / WIDTH) * 2 * math.pi
    level = 95 if secondary else 73
    height = (7 if secondary else 10) * math.sin(phase * 3 + (2 if secondary else 0))
    height += 4 * math.sin(phase * 7 + 1.6) + 2 * math.cos(phase * 11)
    if zone in ("ice", "volcano"):
        height *= 1.6
    if zone in ("cave", "dark"):
        level = 67 if secondary else 52
    return int(level + height) // 2 * 2


def clouds(p, sc, r, zone):
    if zone in ("cave", "dark"):
        for i in range(68):
            x = r.randrange(5, WIDTH - 5) // 2 * 2
            y = r.randrange(8, 60) // 2 * 2
            p.rect(x, y, 2 if i % 3 else 1, 2, sc.cloud)
        return
    if zone == "boss":
        # The moon is a stepped, clean pixel circle, not a blurred radial glow.
        for dy, half in [(-12, 7), (-10, 11), (-8, 14), (-4, 16), (0, 17),
                         (4, 16), (8, 14), (10, 11), (12, 7)]:
            p.rect(475 - half, 36 + dy, half * 2, 3, sc.cloud)
        p.rect(465, 30, 5, 4, sc.haze)
    for i in range(7):
        x = 24 + i * 91 + r.randrange(-12, 13)
        y = r.randrange(12, 48) // 2 * 2
        w = r.choice([24, 34, 43])
        p.rect(x + 4, y + 4, w, 5,
               blend(sc.cloud,sc.sky,(.08,.18,.27)[i%3]))
        p.rect(x + 11, y, w - 17, 5, sc.cloud)
        p.rect(x, y + 7, w + 8, 3, sc.cloud)


def make_far(zone, sc, seed):
    p, r = Painter(), random.Random(seed)
    p.rect(0, 0, WIDTH, HEIGHT, sc.sky)
    p.rect(0, 45, WIDTH, 33, blend(sc.sky, sc.haze, .37))
    p.rect(0, 78, WIDTH, HEIGHT - 78, sc.haze)
    clouds(p, sc, r, zone)
    for x in range(0, WIDTH, 2):
        top = hill_y(x, zone)
        if zone=="coast":
            top = 111 + (top-70)//4
        elif zone=="wasteland":
            top = 81 + (top-70)//2
        elif zone=="forest":
            top += 14
        p.rect(x, top, 2, 142 - top, sc.far)
        p.rect(x, top, 2, 3, sc.far_lit)
        nearer = hill_y(x, zone, True)
        if zone=="coast":
            nearer = 119 + (nearer-95)//5
        elif zone=="forest":
            nearer += 11
        p.rect(x, nearer, 2, 142 - nearer,
               blend(sc.far,sc.haze,.25))
        if x % 14 == 0 and zone not in ("coast", "boss", "dark"):
            p.rect(x, top + 6, 4, 2, blend(sc.far,sc.far_lit,.48))
    if zone == "coast":
        p.rect(0, 122, WIDTH, 21, sc.sky)
        for y in range(124, 142, 5):
            for x in range(y%21, WIDTH, 28):
                p.rect(x, y, 10 + (x//28)%6, 1,
                       blend(sc.sky,sc.bright,.35))
    if zone == "volcano":
        for x in (82,263,470):
            p.polygon([(x-60,118),(x-12,74),(x,46),
                (x+18,67),(x+67,118)],sc.far)
            p.polygon([(x-4,49),(x+3,56),(x+8,72),
                (x+3,87),(x-5,87)],sc.accent)
            p.rect(x+1,62,3,9,sc.bright)
    if zone == "ice":
        for x in (102,293,504):
            p.polygon([(x-49,120),(x,46),(x+47,120)],sc.far_lit)
            p.polygon([(x-13,65),(x,46),(x+10,66),
                (x+2,70),(x-6,64)],sc.cloud)
    if zone == "wasteland":
        for x in (75,245,422,575):
            p.rect(x-20,76,50,42,sc.far)
            p.rect(x-27,74,62,7,sc.far_lit)
            p.rect(x-14,82,8,24,blend(sc.far,sc.rear,.55))
    if zone in ("boss", "ruins"):
        for x in (85, 182, 325, 573):
            p.rect(x, 67, 7, 75, sc.far)
            p.rect(x - 6, 64, 20, 5, sc.far_lit)
            p.rect(x+3,73,1,26,blend(sc.far,sc.rear,.45))
    if zone == "boss":
        # The prominent moon belongs to the BACK sky, above everything else.
        for dy, half in [(-17,4),(-15,10),(-12,14),(-8,17),
                         (-3,19),(2,19),(7,16),(12,12),(16,6)]:
            p.rect(208-half,42+dy,half*2,4,sc.cloud)
        p.rect(201,34,7,4,blend(sc.cloud,sc.far,.34))
        p.rect(218,47,4,3,blend(sc.cloud,sc.far,.34))
    if zone == "meadow":
        for x in (60,205,419,581):
            p.rect(x,105,18,2,blend(sc.far,sc.rear_light,.4))
            p.rect(x+5,99,2,7,sc.far_lit)
    if zone=="dark":
        for x in (44,168,300,442,559):
            p.rect(x,69,16,58,sc.far)
            p.rect(x+3,76,4,36,sc.rear_dark)
    return p.image


def tree(p, x, y, sc, r, thin=False):
    height = r.choice([36, 45, 49])
    trunk = 5 if thin else 7
    p.rect(x, y - height, trunk, height, sc.rear_dark)
    p.rect(x, y - height, 2, height - 4, sc.rear)
    p.rect(x+trunk-1,y-height+5,1,height-10,
           blend(sc.rear,sc.rear_dark,.55))
    p.rect(x-4,y-4,6,3,sc.rear_dark)
    p.rect(x+trunk-1,y-3,7,3,sc.rear_dark)
    if thin:
        p.rect(x - 7, y - height + 8, 12, 3, sc.rear_dark)
        p.rect(x + 4, y - height + 14, 15, 2, sc.rear)
        p.rect(x-10,y-height+6,4,2,sc.rear_light)
        p.rect(x+15,y-height+13,3,2,sc.rear_light)
    else:
        canopy_y = y - height - 18
        p.rect(x - 12, canopy_y + 11, 32, 19, sc.rear_dark)
        p.rect(x - 19, canopy_y + 17, 44, 12, sc.rear)
        p.rect(x - 9, canopy_y + 2, 27, 22, sc.rear)
        p.rect(x - 1, canopy_y - 3, 18, 11, sc.rear_light)
        p.rect(x-15,canopy_y+11,18,5,blend(sc.rear,sc.rear_light,.43))
        p.rect(x+9,canopy_y+6,9,7,blend(sc.rear,sc.rear_light,.52))
        p.rect(x-11,canopy_y+25,18,3,blend(sc.rear,sc.rear_dark,.49))
        p.rect(x-3,canopy_y+9,8,4,sc.rear_light)
        p.rect(x+17,canopy_y+17,6,7,sc.rear_dark)
        for j in range(10):
            tx=x-15 + r.randrange(33)
            ty=canopy_y+4 + r.randrange(22)
            p.rect(tx,ty,3,2,
                   blend(sc.rear_light,sc.highlight,.28) if j%2
                   else blend(sc.rear_dark,sc.rear,.25))
        # A few overlapping leaf groups break the stamp silhouette.
        p.rect(x-23,canopy_y+21,8,5,sc.rear_dark)
        p.rect(x+19,canopy_y+12,8,7,sc.rear)


def column(p, x, y, sc, r, broken=False):
    h = r.randint(34, 60)
    if broken:
        h -= 12
    top = y - h
    p.rect(x + 3, top, 13, h, sc.rear_dark)
    p.rect(x + 5, top, 8, h, sc.rear)
    p.rect(x + 5, top, 2, h, sc.rear_light)
    p.rect(x + 11,top+3,2,h-8,blend(sc.rear,sc.rear_dark,.53))
    for offset in (12, 24, 39):
        if offset < h:
            p.rect(x + 4, top + offset, 11, 2, sc.rear_dark)
            p.rect(x + 6, top + offset + 2, 4, 1,
                   blend(sc.rear,sc.rear_light,.3))
    p.rect(x, top - 3, 18, 4, sc.rear_light)
    p.rect(x - 2, y - 4, 22, 5, sc.rear_dark)
    p.rect(x, y - 5, 18, 2, sc.rear_light)
    p.rect(x + 4, top - 3, 3, 3, sc.highlight)
    p.rect(x + 12, top - 3, 2, 3,sc.highlight)
    if broken:
        p.rect(x + 7, top - 7, 8, 5, sc.rear_dark)
        p.rect(x+9,top-8,3,2,sc.rear_light)


def crystal(p, x, y, sc, r):
    w = r.choice([9, 13, 16])
    h = r.choice([22, 33, 42])
    p.polygon([(x, y), (x - w // 2, y - h + 8),
               (x + 3, y - h), (x + w, y - h + 13),
               (x + w + 4, y)], sc.accent)
    p.polygon([(x, y), (x + 3, y - h), (x + 5, y)], sc.rear_light)
    p.rect(x + 5, y - h + 12, 2, 6, sc.bright)
    p.rect(x+7,y-13,3,6,blend(sc.accent,sc.bright,.55))
    p.rect(x-2,y-7,3,4,blend(sc.accent,sc.rear_light,.5))


def rock(p, x, y, sc, r):
    w = r.randrange(23, 46)
    h = r.randrange(21, 42)
    p.polygon([(x, y), (x + 3, y - h + 9), (x + 14, y - h),
               (x + w - 8, y - h + 6), (x + w, y)], sc.rear_dark)
    p.polygon([(x + 4, y - 8), (x + 6, y - h + 9),
               (x + 14, y - h), (x + 19, y - 6)], sc.rear)
    p.rect(x + 12, y - h + 9, 5, 2, sc.rear_light)
    p.rect(x + 8, y - h + 12, 7, 3,
           blend(sc.rear_light,sc.rear,.55))
    p.rect(x + 21, y - 9, 7, 3,sc.rear_dark)
    p.rect(x + 14, y - 5, 9, 2,blend(sc.rear_dark,sc.earth,.4))


def palm(p, x, y, sc):
    p.rect(x, y - 42, 5, 42, sc.rear_dark)
    p.rect(x, y - 41, 2, 35, sc.rear_light)
    for side in (-1, 1):
        for i in range(4):
            p.rect(x + side * (8 + 5 * i), y - 46 + i * 3, 10, 3, sc.rear)
    p.rect(x - 10, y - 51, 26, 4, sc.rear_light)
    p.rect(x - 4, y - 55, 16, 5, sc.rear_light)


def torch(p, x, y, sc, r):
    p.rect(x, y - 28, 5, 28, sc.rear_dark)
    p.rect(x - 4, y - 31, 14, 3, sc.rear_light)
    p.rect(x - 2, y - 38, 10, 10, sc.accent)
    p.rect(x + 1, y - 43, 5, 12, sc.bright)
    p.rect(x + 2, y - 39, 2, 6, sc.highlight)


def make_mid(zone, sc, seed):
    p, r = Painter(), random.Random(seed * 3 + 7)
    # Objects are independently authored shapes with a stable vertical unit.
    for i, x in enumerate(range(66, WIDTH - 63, 60)):
        xx = x + r.randrange(-5, 6)
        if zone in ("meadow", "forest"):
            tree(p, xx, FLOOR, sc, r, thin=(zone == "meadow" and i % 3 == 0))
        elif zone in ("ruins", "boss", "dark"):
            if zone != "boss" or xx < 148 or xx > 520:
                column(p, xx, FLOOR, sc, r, broken=i % 3 == 1)
        elif zone == "cave":
            rock(p, xx, FLOOR, sc, r)
            crystal(p, xx + 24, FLOOR, sc, r)
        elif zone == "volcano":
            rock(p, xx, FLOOR, sc, r)
            if i % 2 == 0:
                p.rect(xx + 12, FLOOR - 16, 4, 15, sc.accent)
        elif zone == "ice":
            crystal(p, xx + 6, FLOOR, sc, r)
            rock(p, xx + 12, FLOOR, sc, r)
        elif zone == "coast":
            if i % 2 == 0:
                palm(p, xx, FLOOR, sc)
            else:
                rock(p, xx, FLOOR, sc, r)
        elif zone == "wasteland":
            tree(p, xx, FLOOR, sc, r, thin=True)
            if i % 2:
                rock(p, xx + 8, FLOOR, sc, r)

    if zone in ("cave", "dark"):
        for i in range(16):
            x = 25 + i * 38
            depth = 19 + (i * 13) % 23
            p.polygon([(x - 13, 0), (x + 16, 0),
                       (x + 8, depth), (x + 4, depth + 7),
                       (x - 4, depth - 7)], sc.rear_dark)
            p.rect(x - 4, 0, 5, 12, sc.rear)
    if zone=="meadow":
        # Rustic fence and bushes create a second, smaller scale of detail.
        for start in (80,375):
            p.rect(start,120,116,3,sc.rear_dark)
            p.rect(start,132,116,2,sc.rear_dark)
            for x in range(start, start+117,19):
                p.rect(x,116,4,26,sc.rear)
                p.rect(x,116,2,22,sc.rear_light)
        for x in (18,171,306,501):
            p.rect(x,132,25,8,blend(sc.rear,sc.rear_light,.35))
            p.rect(x+5,128,13,6,sc.rear_light)
            p.rect(x+9,126,6,3,sc.highlight)
    if zone=="forest":
        for x in (110,280,459,580):
            p.rect(x,138,25,4,sc.rear_dark)
            p.rect(x+2,135,23,4,sc.rear)
            p.rect(x+3,133,5,3,sc.rear_light)
            for mushroom in (x+4,x+16):
                p.rect(mushroom,133,2,6,sc.highlight)
                p.rect(mushroom-2,131,7,3,sc.accent)
                p.rect(mushroom,131,2,1,sc.bright)
    if zone=="ruins":
        for x in (115,373):
            p.rect(x,76,103,7,sc.rear_dark)
            p.rect(x+3,75,98,3,sc.rear_light)
            for notch in range(x+9,x+93,22):
                p.rect(notch,79,3,3,sc.rear_light)
            p.rect(x+2,84,6,30,blend(sc.rear,sc.rear_dark,.5))
            p.rect(x+96,82,6,24,sc.rear_dark)
        for x in (170,410,555):
            p.rect(x,124,2,16,sc.rear_dark)
            p.rect(x-4,120,10,7,sc.accent)
            p.rect(x-2,118,5,6,sc.bright)
    if zone=="cave":
        for x in (125,348,548):
            p.rect(x,125,3,18,sc.rear_dark)
            p.rect(x-4,123,12,4,sc.rear)
            p.rect(x-2,117,8,9,sc.accent)
            p.rect(x,113,4,8,sc.bright)
    if zone=="volcano":
        for x in (162,422):
            p.rect(x,81,18,62,sc.rear_dark)
            p.rect(x+5,83,8,52,sc.accent)
            p.rect(x+8,88,3,42,sc.bright)
            p.rect(x+3,121,12,4,blend(sc.accent,sc.bright,.5))
    if zone=="ice":
        for x in (95,268,473):
            p.rect(x,98,6,45,sc.rear_light)
            p.rect(x-4,96,14,5,sc.bright)
            p.rect(x+4,99,3,33,blend(sc.rear_light,sc.rear_dark,.45))
            p.rect(x+5,133,9,6,sc.accent)
    if zone=="coast":
        for x in (121,347,553):
            p.rect(x,123,2,17,sc.rear_dark)
            p.polygon([(x,122),(x+20,128),(x,128)],sc.cloud)
            p.rect(x-4,133,28,3,sc.rear_dark)
            p.rect(x+2,136,13,2,sc.rear)
    if zone=="wasteland":
        for x in (94,300,507):
            p.rect(x,135,26,5,sc.rear_light)
            p.rect(x+4,130,4,8,sc.bright)
            p.rect(x+18,128,4,9,sc.bright)
            p.rect(x+3,127,20,2,sc.rear)
    if zone=="dark":
        for x in (104,272,455,593):
            p.rect(x,67,2,60,sc.rear_dark)
            p.rect(x-4,126,10,4,sc.rear)
            p.rect(x-3,129,8,11,sc.accent)
            p.rect(x-1,132,4,4,sc.bright)
    if zone == "boss":
        # Central arena is kept empty: readable boss, hero and bullet silhouettes.
        for x in (6, 590):
            p.rect(x, 40, 25, 102, sc.rear_dark)
            p.rect(x + 4, 42, 6, 92, sc.rear_light)
            p.rect(x - 4, 38, 37, 7, sc.rear_light)
        p.rect(180,62,266,9,sc.rear_dark)
        p.rect(185,60,256,3,sc.rear_light)
        for x in (192,296,405):
            p.rect(x,72,7,19,sc.rear_dark)
            p.rect(x+2,74,2,15,sc.rear_light)
    return p.image


def ground_tile(p, x, y, sc, variant, zone, top_row):
    # All 16x16 tile edges share the same base fill, so horizontal seams vanish.
    p.rect(x, y, TILE, TILE, sc.earth)
    if top_row:
        p.rect(x, y, TILE, 2, sc.lip)
        p.rect(x, y + 2, TILE, 1,
               blend(sc.earth,sc.lip,.52))
    if variant % 3 != 0:
        p.rect(x + (variant * 3) % 10, y + 9, 5, 2,
               blend(sc.earth,sc.earth_dark,.65))
        p.rect(x + (variant * 7) % 12, y + 13, 3, 2,
               blend(sc.highlight,sc.earth,.55))
        p.rect(x + (variant * 5) % 11, y + 5, 3, 2,
               blend(sc.earth,sc.lip,.4))
    # Deliberate 2–4 pixel shading clusters, five stable stone tones.
    tone=(.17,.28,.39,.5,.61)[variant%5]
    p.rect(x + (variant*5)%10, y + 3 + variant%3, 4, 2,
           blend(sc.earth,sc.earth_dark,tone))
    p.rect(x + (variant*4)%11, y + 11, 3, 2,
           blend(sc.earth,sc.lip,(.14,.28,.42,.56)[variant%4]))
    p.rect(x + (variant*7)%10, y + 7, 2, 2,
           blend(sc.earth,sc.highlight,(.2,.33,.46,.59)[variant%4]))
    if top_row:
        p.rect(x+(variant*3)%9,y,6,1,
               blend(sc.lip,sc.highlight,(.14,.28,.43,.57)[variant%4]))
    if zone in ("meadow", "forest"):
        if top_row:
            p.rect(x + 3, y - 2, 2, 4, sc.highlight)
            p.rect(x + 8, y - 3, 2, 5, sc.lip)
            p.rect(x + 11, y - 1, 3, 2,
                   blend(sc.lip,sc.rear_dark,.35))
        p.rect(x+9,y+8,5,2,blend(sc.earth,sc.rear_dark,.34))
    elif zone in ("volcano", "dark"):
        if variant % 4 == 1:
            p.rect(x + 5, y + 7, 4, 2, sc.accent)
            p.rect(x + 6, y + 7, 2, 1,sc.bright)
        p.rect(x+11,y+2,3,4,blend(sc.earth,sc.earth_dark,.5))
    elif zone in ("ice", "cave"):
        if variant % 3 == 1:
            p.rect(x + 5, y + 7, 6, 2, sc.rear_light)
        if top_row:
            p.rect(x + 6, y - 2, 9, 3,
                   blend(sc.lip,sc.bright,.55))
    elif zone == "coast":
        if variant % 4 == 0:
            p.rect(x + 3, y + 6, 7, 2, sc.highlight)
            p.rect(x + 4, y + 6, 3, 1,sc.bright)
    elif zone in ("ruins", "boss"):
        p.rect(x + 1, y + 5, 13, 1,
               blend(sc.earth,sc.earth_dark,.48))
        if variant%2==0:
            p.rect(x+7,y+6,1,5,sc.earth_dark)
        p.rect(x+2,y+8,5,1,blend(sc.earth,sc.highlight,.47))


def make_ground(zone, sc, seed):
    p, r = Painter(), random.Random(seed * 11)
    for tx in range(WIDTH // TILE):
        for ty in range(3):
            ground_tile(p, tx * TILE, FLOOR + ty * TILE, sc,
                        (tx * 11 + ty * 7 + r.randrange(4)) % 17, zone,
                        top_row=ty==0)
    p.rect(0, FLOOR, WIDTH, 1, sc.highlight)
    if zone == "coast":
        for x in range(11, WIDTH, 91):
            p.rect(x, FLOOR + 11, 20, 2, sc.bright)
    if zone in ("ruins", "boss"):
        for x in range(0, WIDTH, 48):
            p.rect(x+9,FLOOR+24,15,2,blend(sc.earth,sc.earth_dark,.35))
            p.rect(x+14,FLOOR+26,4,2,sc.highlight)
    if zone=="volcano":
        for x in (92,229,433,586):
            p.rect(x,FLOOR+24,28,3,sc.accent)
            p.rect(x+7,FLOOR+24,12,1,sc.bright)
    if zone == "boss":
        # Sparse engraved floor plates; no tall obstacle in the dodge arena.
        for x in range(0, WIDTH, 32):
            p.rect(x + 3, FLOOR + 5, 24, 1, sc.lip)
            p.rect(x + 8, FLOOR + 12, 13, 1, sc.earth_dark)
    return p.image


def make_front(zone, sc, seed):
    p, r = Painter(), random.Random(seed * 19 + 3)
    for i, x in enumerate(range(24, WIDTH - 10, 72)):
        xx = x + r.randrange(-10, 11)
        y = 167 + (i % 3) * 4
        if zone in ("meadow", "forest", "coast"):
            p.rect(xx - 4, y, 21, 5, sc.rear_dark)
            p.rect(xx, y - 7, 7, 10, sc.lip)
            p.rect(xx + 8, y - 11, 6, 14, sc.rear)
            p.rect(xx + 1, y - 4, 3, 3, sc.highlight)
        elif zone in ("ruins", "boss", "dark", "wasteland"):
            p.rect(xx - 3, y - 5, 20, 10, sc.rear_dark)
            p.rect(xx, y - 8, 12, 7, sc.rear)
            p.rect(xx + 2, y - 8, 6, 2, sc.rear_light)
        elif zone in ("cave", "ice"):
            # Short front stones, never concealing a full-size monster.
            p.polygon([(xx - 7, 179), (xx - 3, 161),
                       (xx + 4, 155), (xx + 13, 179)], sc.accent)
            p.rect(xx + 3, 161, 2, 9, sc.bright)
        elif zone == "volcano":
            p.rect(xx, y, 17, 6, sc.rear_dark)
            p.rect(xx + 5, y - 5, 4, 8, sc.accent)
            p.rect(xx + 5, y - 7, 2, 3, sc.bright)
    return p.image


def generate():
    OUT.mkdir(parents=True, exist_ok=True)
    for index, (zone, sc) in enumerate(SCHEMES.items()):
        seed = 700 + index * 41
        layers = {
            "far": make_far(zone, sc, seed),
            "mid": make_mid(zone, sc, seed),
            "ground": make_ground(zone, sc, seed),
            "front": make_front(zone, sc, seed),
        }
        for name, im in layers.items():
            im.save(OUT / f"mq_bg_{zone}_{name}.png", optimize=True)
        print(f"{zone}: 4 × {WIDTH}×{HEIGHT} RGBA pixel-locked layers")


if __name__ == "__main__":
    generate()

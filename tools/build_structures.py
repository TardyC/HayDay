#!/usr/bin/env python3
"""
Tegner alle HayDays bygninger og gårdens pynt som "blueprints" og skriver dem til
src/main/resources/structures.yml.

Kør:  python3 tools/build_structures.py

Format i structures.yml (kan også rettes i hånden):
  * layers: lag nedefra og op. Hvert lag er rækker fra nord (bagsiden) mod syd (forsiden),
    og hvert tegn er én blok fra vest mod øst.
  * palette: "tegn=blok" - hvilket tegn er hvilken blok. ' ' = rør ikke, '.' = luft.
  * '@' er ankeret: for bygninger er det kerne-blokken man klikker på (laget under er jorden),
    for gårdens pynt er det punktet strukturen placeres ud fra.
  * 'F' (kun på marken) er pladser til marker.
Bygningerne drejes automatisk, så forsiden vender mod spilleren der placerer dem.
"""
import math
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "structures.yml")

random.seed(7)

# Fælles palette - kan overskrives pr. struktur
COMMON = {
    ".": "air",
    "G": "grass_block",
    "d": "coarse_dirt",
    "D": "dirt_path",
    "~": "water",
    "c": "cobblestone",
    "m": "mossy_cobblestone",
    "S": "stone_bricks",
    "h": "hay_block[axis=y]",
    "H": "hay_block[axis=x]",
    "b": "barrel[facing=up]",
    "f": "spruce_fence",
    "o": "oak_fence",
    "n": "dark_oak_fence",
    "*": "lantern[hanging=false]",
    "^": "lantern[hanging=true]",
    "p": "spruce_planks",
    "P": "oak_planks",
    "g": "glass_pane",
    "w": "water_cauldron[level=3]",
}


class Blueprint:
    def __init__(self, width, height, depth):
        self.w, self.h, self.d = width, height, depth
        self.cells = [[[" "] * width for _ in range(depth)] for _ in range(height)]
        self.palette = {}

    def set(self, x, y, z, ch):
        if 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d:
            self.cells[y][z][x] = ch

    def get(self, x, y, z):
        return self.cells[y][z][x]

    def fill(self, x0, y0, z0, x1, y1, z1, ch):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    self.set(x, y, z, ch)

    def ring(self, x0, y0, z0, x1, y1, z1, ch):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    if x in (x0, x1) or z in (z0, z1):
                        self.set(x, y, z, ch)

    def pal(self, **entries):
        for key, value in entries.items():
            self.palette[key] = value

    def chars(self, mapping):
        for ch, block in mapping.items():
            self.palette[ch] = block


def gable_roof_x(bp, x0, x1, z0, z1, y, stair, slab, fill=None, gable_x=None):
    """Sadeltag med rygning langs x. Tagfladerne vender mod nord og syd."""
    zn, zs = z0, z1
    layer = 0
    while zn < zs:
        for x in range(x0, x1 + 1):
            bp.set(x, y + layer, zn, stair + "s")
            bp.set(x, y + layer, zs, stair + "n")
        if fill and gable_x:
            for gx in gable_x:
                for z in range(zn + 1, zs):
                    bp.set(gx, y + layer, z, fill)
        zn += 1
        zs -= 1
        layer += 1
    if zn == zs:
        for x in range(x0, x1 + 1):
            bp.set(x, y + layer, zn, slab)
    return y + layer


# ---------------------------------------------------------------------------
# Gårdens bygninger (pynt på øen)
# ---------------------------------------------------------------------------

def farmhouse():
    """Stuehus i to etager med veranda, bindingsværk, skodder, skorsten og blomsterbed."""
    bp = Blueprint(11, 15, 11)
    bp.chars({
        "W": "birch_planks",
        "L": "stripped_dark_oak_log[axis=y]",
        "X": "stripped_dark_oak_log[axis=x]",
        "Z": "stripped_dark_oak_log[axis=z]",
        "Rs": "mangrove_stairs[facing=south]",
        "Rn": "mangrove_stairs[facing=north]",
        "r": "mangrove_slab[type=bottom]",
        "B": "bricks",
        "C": "campfire[lit=true,signal_fire=false]",
        "q": "spruce_slab[type=bottom]",
        "1": "spruce_door[facing=south,half=lower,hinge=left]",
        "2": "spruce_door[facing=south,half=upper,hinge=left]",
        "t": "spruce_trapdoor[facing=south,half=bottom,open=true]",
        "T": "spruce_trapdoor[facing=north,half=bottom,open=true]",
        "e": "spruce_trapdoor[facing=east,half=bottom,open=true]",
        "v": "spruce_trapdoor[facing=west,half=bottom,open=true]",
        "@": "spruce_planks",
        "k": "crafting_table",
        "u": "furnace[facing=south]",
        "K": "bookshelf",
        "a": "ladder[facing=west]",
        "Q": "red_bed[facing=north,part=head]",
        "q2": "red_bed[facing=north,part=foot]",
        "x": "chest[facing=south]",
        "y": "red_carpet",
        "j": "oak_pressure_plate",
        "l": "spruce_stairs[facing=west]",
        "R": "rooted_dirt",
        "1f": "red_tulip",
        "2f": "oxeye_daisy",
        "3f": "azure_bluet",
        "4f": "poppy",
        "A": "azalea_leaves[persistent=true]",
        "Y": "flowering_azalea_leaves[persistent=true]",
        "N": "potted_red_tulip",
    })
    # Fundament og gulv (y0 = jordhøjde)
    bp.fill(1, 0, 1, 9, 0, 7, "p")
    bp.ring(1, 0, 1, 9, 0, 7, "c")
    bp.fill(1, 0, 8, 9, 0, 9, "p")
    # Stueetagen
    bp.ring(1, 1, 1, 9, 3, 7, "W")
    for x, z in ((1, 1), (9, 1), (1, 7), (9, 7), (5, 1)):
        bp.fill(x, 1, z, x, 3, z, "L")
    for x in (3, 7):
        bp.set(x, 2, 7, "g")
        bp.set(x, 2, 1, "g")
        bp.set(x - 1, 2, 8, "t")
        bp.set(x + 1, 2, 8, "t")
    for z in (3, 5):
        bp.set(1, 2, z, "g")
        bp.set(9, 2, z, "g")
    bp.set(5, 1, 7, "1")
    bp.set(5, 2, 7, "2")
    # Indbo
    bp.set(2, 1, 2, "k")
    bp.set(3, 1, 2, "u")
    bp.set(2, 1, 3, "b")
    bp.fill(8, 1, 2, 8, 2, 3, "K")
    bp.set(6, 1, 4, "o")
    bp.set(6, 2, 4, "j")
    bp.set(5, 1, 4, "l")
    bp.fill(3, 1, 5, 4, 1, 6, "y")
    bp.fill(8, 1, 6, 8, 4, 6, "a")
    bp.set(5, 3, 4, "^")
    # Bjælkelag mellem etagerne
    bp.fill(2, 4, 2, 8, 4, 6, "p")
    bp.set(8, 4, 6, "a")
    for x in range(1, 10):
        bp.set(x, 4, 1, "X")
        bp.set(x, 4, 7, "X")
    for z in range(2, 7):
        bp.set(1, 4, z, "Z")
        bp.set(9, 4, z, "Z")
    for x, z in ((1, 1), (9, 1), (1, 7), (9, 7)):
        bp.set(x, 4, z, "L")
    # Verandaen: tag, stolper, rækværk og lygter
    bp.fill(0, 4, 8, 10, 4, 9, "r")
    for x in (1, 9):
        bp.fill(x, 1, 9, x, 3, 9, "f")
    for x in (2, 3, 7, 8):
        bp.set(x, 1, 9, "f")
    bp.set(3, 3, 8, "^")
    bp.set(7, 3, 8, "^")
    # 1. sal
    bp.ring(1, 5, 1, 9, 7, 7, "W")
    for x, z in ((1, 1), (9, 1), (1, 7), (9, 7)):
        bp.fill(x, 5, z, x, 7, z, "L")
    for x in (3, 5, 7):
        bp.set(x, 6, 7, "g")
    for x in (3, 7):
        bp.set(x, 6, 1, "g")
    bp.set(1, 6, 4, "g")
    bp.set(9, 6, 4, "g")
    bp.set(2, 5, 2, "Q")
    bp.set(2, 5, 3, "q2")
    bp.set(3, 5, 2, "x")
    bp.fill(4, 5, 3, 6, 5, 5, "y")
    bp.set(7, 5, 2, "N")
    bp.set(5, 7, 4, "^")
    # Tag
    for x in range(0, 11):
        for layer in range(4):
            bp.set(x, 8 + layer, 0 + layer, "Rs")
            bp.set(x, 8 + layer, 8 - layer, "Rn")
        bp.set(x, 12, 4, "r")
    for layer in range(4):
        for gx in (1, 9):
            for z in range(1 + layer, 8 - layer):
                bp.set(gx, 8 + layer, z, "W")
    bp.set(1, 9, 4, "g")
    bp.set(9, 9, 4, "g")
    # Skorsten med røg
    bp.fill(8, 8, 2, 8, 13, 2, "B")
    bp.set(8, 14, 2, "C")
    # Blomsterbed og buske foran
    for x in range(1, 10):
        if x in (4, 5, 6):
            continue
        bp.set(x, 0, 10, "R")
        bp.set(x, 1, 10, random.choice(["1f", "2f", "3f", "4f"]))
    bp.set(0, 1, 9, "A")
    bp.set(10, 1, 9, "Y")
    bp.set(0, 1, 8, "Y")
    bp.set(10, 1, 8, "A")
    bp.set(5, 0, 9, "@")
    return bp, {"hologram-height": 13.5}


def barn():
    """Den røde lade med hvide hjørner, mansardtag, stor port og hølem."""
    bp = Blueprint(11, 13, 12)
    bp.chars({
        "R": "red_terracotta",
        "L": "stripped_birch_log[axis=y]",
        "X": "stripped_birch_log[axis=x]",
        "W": "birch_planks",
        "E": "dark_oak_stairs[facing=east]",
        "V": "dark_oak_stairs[facing=west]",
        "K": "dark_oak_planks",
        "s": "dark_oak_slab[type=bottom]",
        "@": "coarse_dirt",
        "i": "lightning_rod",
        "A": "azalea_leaves[persistent=true]",
    })
    # Fundament og gulv
    bp.fill(1, 0, 0, 9, 0, 10, "p")
    bp.ring(1, 0, 0, 9, 0, 10, "S")
    # Vægge
    bp.ring(1, 1, 0, 9, 5, 10, "R")
    for x in (1, 9):
        for z in (0, 10):
            bp.fill(x, 1, z, x, 5, z, "L")
    for x in range(2, 9):
        bp.set(x, 5, 10, "X")
        bp.set(x, 5, 0, "X")
    # Vinduer i siderne
    for z in (3, 7):
        bp.set(1, 3, z, "g")
        bp.set(9, 3, z, "g")
    # Den store port: hvid ramme om røde låger
    for y in range(1, 5):
        for x in range(3, 8):
            edge = x in (3, 5, 7) or y == 4
            bp.set(x, y, 10, "W" if edge else "R")
    # Mansardtag (rygning langs z)
    profile = [(6, 1, "E", 9, "V"), (7, 1, "K", 9, "K"), (8, 2, "E", 8, "V"), (9, 3, "E", 7, "V"), (10, 4, "E", 6, "V")]
    for z in range(0, 11):
        bp.set(0, 6, z, "E")
        bp.set(10, 6, z, "V")
        for y, xl, cl, xr, cr in profile:
            bp.set(xl, y, z, cl)
            bp.set(xr, y, z, cr)
        bp.set(5, 11, z, "s")
    # Gavle for og bag
    gable = {6: range(2, 9), 7: range(2, 9), 8: range(3, 8), 9: range(4, 7), 10: range(5, 6)}
    for y, xs in gable.items():
        for x in xs:
            bp.set(x, y, 0, "R")
            bp.set(x, y, 10, "R")
    # Hølem med høballe
    bp.set(4, 7, 10, "W")
    bp.set(6, 7, 10, "W")
    bp.set(5, 8, 10, "W")
    bp.set(5, 7, 10, "h")
    bp.set(5, 12, 5, "i")
    # Inde: høballer
    bp.fill(2, 1, 1, 3, 2, 2, "h")
    bp.fill(7, 1, 1, 8, 1, 3, "H")
    # Foran porten
    bp.fill(2, 0, 11, 8, 0, 11, "d")
    bp.set(5, 0, 11, "@")
    bp.set(1, 1, 11, "h")
    bp.set(1, 2, 11, "h")
    bp.set(2, 1, 11, "H")
    bp.set(9, 1, 11, "b")
    bp.set(10, 1, 11, "A")
    return bp, {"hologram-height": 13.0}


def silo():
    """Høj rund silo med striber, kuppel, stige og lynafleder."""
    size = 7
    bp = Blueprint(size, 15, size + 1)
    bp.chars({
        "w": "white_concrete",
        "l": "light_gray_concrete",
        "r": "red_concrete",
        "a": "ladder[facing=south]",
        "i": "lightning_rod",
        "@": "stone_bricks",
    })
    c = 3
    for z in range(size):
        for x in range(size):
            dist = math.hypot(x - c, z - c)
            if dist <= 3.4:
                bp.set(x, 0, z, "S")
            if 2.4 < dist <= 3.4:
                for y in range(1, 11):
                    bp.set(x, y, z, "l" if y in (3, 6, 9) else "w")
            if dist <= 2.9:
                bp.set(x, 11, z, "r")
            if dist <= 1.9:
                bp.set(x, 12, z, "r")
            if dist <= 0.9:
                bp.set(x, 13, z, "r")
    bp.set(c, 14, c, "i")
    for y in range(1, 11):
        bp.set(c, y, size, "a")
    bp.set(c, 0, c, "@")
    return bp, {"hologram-height": 15.0}


def orderboard():
    """Ordretavlen: en træ-opslagstavle med sedler under et lille tag."""
    bp = Blueprint(5, 6, 2)
    bp.chars({
        "L": "dark_oak_log[axis=y]",
        "B": "birch_trapdoor[facing=south,half=bottom,open=true]",
        "s": "spruce_slab[type=bottom]",
        "@": "coarse_dirt",
    })
    bp.set(0, 1, 0, "n")
    bp.set(4, 1, 0, "n")
    for y in (2, 3, 4):
        bp.set(0, y, 0, "L")
        bp.set(4, y, 0, "L")
        for x in (1, 2, 3):
            bp.set(x, y, 0, "p")
    bp.set(1, 3, 1, "B")
    bp.set(3, 3, 1, "B")
    bp.set(2, 2, 1, "B")
    bp.fill(0, 5, 0, 4, 5, 1, "s")
    bp.set(2, 4, 1, "^")
    bp.set(4, 1, 1, "b")
    bp.set(2, 0, 1, "@")
    return bp, {"hologram-height": 6.2}


def mailbox():
    """Postkassen ved vejen (avisen)."""
    bp = Blueprint(2, 3, 1)
    bp.chars({"x": "barrel[facing=south]", "v": "lever[face=wall,facing=east,powered=false]", "@": "coarse_dirt"})
    bp.set(0, 0, 0, "@")
    bp.set(0, 1, 0, "f")
    bp.set(0, 2, 0, "x")
    bp.set(1, 2, 0, "v")
    return bp, {"hologram-height": 3.2}


def fieldpatch():
    """Den indhegnede mark: pløjet jord i rækker med vandkanaler, fugleskræmsel og kompost."""
    w, d = 15, 11
    bp = Blueprint(w, 4, d)
    bp.chars({
        "F": "farmland[moisture=7]",
        "@": "coarse_dirt",
        "e": "spruce_fence_gate[facing=east,open=false]",
        "k": "composter",
        "P": "carved_pumpkin[facing=south]",
    })
    for x in range(1, w - 1):
        for z in range(1, d - 1):
            bp.set(x, 0, z, "d")
    for z in (2, 3, 5, 6, 8):
        for x in range(2, w - 2):
            bp.set(x, 0, z, "F")
    for z in (4, 7):
        for x in range(2, w - 2):
            bp.set(x, 0, z, "~")
    bp.ring(0, 1, 0, w - 1, 1, d - 1, "f")
    for x, z in ((0, 0), (w - 1, 0), (0, d - 1), (w - 1, d - 1)):
        bp.set(x, 2, z, "*")
    bp.set(w - 1, 1, 5, "e")
    bp.set(w - 1, 0, 5, "@")
    # Fugleskræmsel og kompost
    bp.set(w - 2, 1, 9, "f")
    bp.set(w - 2, 2, 9, "h")
    bp.set(w - 2, 3, 9, "P")
    bp.set(1, 1, 9, "k")
    bp.set(1, 1, 1, "b")
    return bp, {"hologram-height": 3.0}


def pond():
    """En lille dam med åkander, siv og en bænk."""
    w, d = 13, 11
    bp = Blueprint(w, 3, d)
    bp.chars({
        "~": "water",
        "s": "seagrass",
        "L": "lily_pad",
        "C": "sugar_cane",
        "a": "sand",
        "Y": "clay",
        "@": "water",
    })
    cx, cz = 6, 5
    for z in range(d):
        for x in range(w):
            n = ((x - cx) / 5.2) ** 2 + ((z - cz) / 4.2) ** 2
            if n <= 1.0:
                bp.set(x, 1, z, "~")
                bp.set(x, 0, z, "~" if n <= 0.55 else "Y")
                if n <= 0.55 and random.random() < 0.35:
                    bp.set(x, 0, z, "s")
                if 0.2 < n < 0.85 and random.random() < 0.18:
                    bp.set(x, 2, z, "L")
            elif n <= 1.35:
                bp.set(x, 1, z, "a" if random.random() < 0.4 else "G")
                if random.random() < 0.3:
                    bp.set(x, 2, z, "C")
    bp.set(cx, 1, cz, "@")
    return bp, {"hologram-height": 2.0}


def dock():
    """Anløbsbroen ved stranden, hvor man ankommer."""
    length = 10
    bp = Blueprint(3, 9, length)
    bp.chars({"l": "spruce_log[axis=y]", "@": "spruce_planks", "s": "spruce_slab[type=bottom]"})
    base = 6
    for z in range(length):
        for x in range(3):
            bp.set(x, base, z, "p")
    for z in (3, 6, 9):
        for x in (0, 2):
            for y in range(0, base):
                bp.set(x, y, z, "l")
    for z in range(2, length):
        bp.set(0, base + 1, z, "f")
        bp.set(2, base + 1, z, "f")
    bp.set(0, base + 2, length - 1, "*")
    bp.set(2, base + 2, length - 1, "*")
    bp.set(0, base + 2, 2, "*")
    bp.set(2, base + 2, 2, "*")
    bp.set(1, base, 0, "@")
    return bp, {"hologram-height": 2.0}


def fountain():
    """Springvandet midt på torvet."""
    bp = Blueprint(7, 4, 7)
    bp.chars({
        "S": "stone_bricks",
        "C": "chiseled_stone_bricks",
        "s": "stone_brick_slab[type=bottom]",
        "~": "water",
        "l": "sea_lantern",
        "@": "chiseled_stone_bricks",
    })
    bp.fill(0, 0, 0, 6, 0, 6, "S")
    bp.ring(0, 1, 0, 6, 1, 6, "s")
    bp.fill(1, 1, 1, 5, 1, 5, "~")
    bp.ring(1, 1, 1, 5, 1, 5, "S")
    bp.fill(2, 1, 2, 4, 1, 4, "~")
    bp.set(3, 1, 3, "C")
    bp.set(3, 2, 3, "C")
    bp.set(3, 3, 3, "l")
    bp.set(3, 0, 3, "@")
    return bp, {"hologram-height": 5.0}


# ---------------------------------------------------------------------------
# Produktionsbygninger (pladseres af spillerne - '@' er bygningens blok)
# ---------------------------------------------------------------------------

def shed(bp, x0, x1, z0, z1, wall, corner, stair, slab, top):
    """Lille hus: vægge fra y1 til top, sadeltag langs x og gavle."""
    bp.ring(x0, 1, z0, x1, top, z1, wall)
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        bp.fill(x, 1, z, x, top, z, corner)
    zn, zs, y = z0 - 1, z1 + 1, top + 1
    while zn < zs:
        for x in range(x0 - 1, x1 + 2):
            bp.set(x, y, zn, stair + "s")
            bp.set(x, y, zs, stair + "n")
        for z in range(zn + 1, zs):
            if z0 <= z <= z1:
                bp.set(x0, y, z, wall)
                bp.set(x1, y, z, wall)
        zn += 1
        zs -= 1
        y += 1
    if zn == zs:
        for x in range(x0 - 1, x1 + 2):
            bp.set(x, y, zn, slab)
    return y


def pen(bp, w, d, gate_x):
    """Hegn hele vejen rundt med en låge i midten foran."""
    bp.ring(0, 1, 0, w - 1, 1, d - 1, "f")
    bp.set(gate_x, 1, d - 1, "e")
    for x, z in ((0, 0), (w - 1, 0), (0, d - 1), (w - 1, d - 1)):
        bp.set(x, 2, z, "*")


def feed_mill():
    bp = Blueprint(7, 9, 7)
    bp.chars({"Rs": "spruce_stairs[facing=south]", "Rn": "spruce_stairs[facing=north]", "r": "spruce_slab[type=bottom]",
              "L": "stripped_spruce_log[axis=y]", "x": "grindstone[face=floor,facing=south]", "@": "barrel"})
    bp.fill(1, 0, 1, 5, 0, 5, "p")
    shed(bp, 1, 5, 1, 4, "p", "L", "R", "r", 3)
    for y in (1, 2, 3):
        for x in (2, 3, 4):
            bp.set(x, y, 4, ".")
    bp.set(3, 1, 4, ".")
    bp.set(2, 1, 2, "b")
    bp.set(2, 2, 2, "b")
    bp.set(4, 1, 2, "h")
    bp.set(4, 2, 2, "h")
    bp.set(3, 1, 2, "x")
    bp.set(3, 3, 4, "^")
    bp.set(3, 1, 5, "@")
    bp.set(1, 1, 5, "h")
    bp.set(5, 1, 5, "b")
    return bp


def chicken_coop():
    bp = Blueprint(7, 6, 7)
    bp.chars({"e": "oak_fence_gate[facing=south,open=false]", "R": "red_terracotta", "L": "stripped_birch_log[axis=y]",
              "Rs": "dark_oak_stairs[facing=south]", "Rn": "dark_oak_stairs[facing=north]", "r": "dark_oak_slab[type=bottom]",
              "@": "hay_block", "k": "composter"})
    for x in range(1, 6):
        for z in range(1, 6):
            bp.set(x, 0, z, "d")
    pen(bp, 7, 7, 3)
    bp.fill(1, 0, 0, 5, 0, 1, "p")
    shed(bp, 1, 5, 0, 1, "R", "L", "R", "r", 2)
    bp.set(3, 1, 1, ".")
    bp.set(3, 2, 1, ".")
    bp.set(2, 1, 0, "h")
    bp.set(4, 1, 0, "h")
    bp.set(3, 1, 4, "@")
    bp.set(1, 1, 5, "k")
    bp.set(5, 1, 5, "w")
    return bp


def bakery():
    bp = Blueprint(7, 9, 7)
    bp.chars({"B": "bricks", "L": "stripped_oak_log[axis=y]", "Rs": "deepslate_tile_stairs[facing=south]",
              "Rn": "deepslate_tile_stairs[facing=north]", "r": "deepslate_tile_slab[type=bottom]",
              "C": "campfire[lit=true,signal_fire=false]", "t": "spruce_trapdoor[facing=south,half=top,open=false]",
              "@": "smoker"})
    bp.fill(1, 0, 1, 5, 0, 4, "S")
    top = shed(bp, 1, 5, 1, 4, "B", "L", "R", "r", 3)
    bp.set(2, 2, 4, "g")
    bp.set(4, 2, 4, "g")
    bp.set(3, 1, 4, "@")
    bp.fill(5, 4, 1, 5, top + 1, 1, "B")
    bp.set(5, top + 2, 1, "C")
    bp.fill(1, 3, 5, 5, 3, 5, "t")
    bp.set(1, 1, 5, "b")
    bp.set(5, 1, 5, "h")
    return bp


def cow_barn():
    bp = Blueprint(7, 5, 7)
    bp.chars({"e": "oak_fence_gate[facing=south,open=false]", "r": "dark_oak_slab[type=bottom]", "@": "moss_block"})
    pen(bp, 7, 7, 3)
    for x in range(1, 6):
        bp.set(x, 1, 0, "P")
        bp.set(x, 2, 0, "P")
    bp.set(3, 1, 0, "h")
    bp.set(1, 1, 1, "n")
    bp.set(1, 2, 1, "n")
    bp.set(5, 1, 1, "n")
    bp.set(5, 2, 1, "n")
    bp.fill(0, 3, 0, 6, 3, 2, "r")
    bp.set(3, 1, 4, "@")
    bp.set(5, 1, 5, "w")
    bp.set(1, 1, 5, "h")
    return bp


def sugar_mill():
    bp = Blueprint(7, 9, 7)
    bp.chars({"M": "mossy_stone_bricks", "L": "stripped_oak_log[axis=y]", "Rs": "stone_brick_stairs[facing=south]",
              "Rn": "stone_brick_stairs[facing=north]", "r": "stone_brick_slab[type=bottom]", "C": "sugar_cane",
              "@": "grindstone"})
    bp.fill(1, 0, 1, 5, 0, 4, "c")
    shed(bp, 1, 5, 1, 4, "S", "M", "R", "r", 3)
    for y in (1, 2):
        for x in (2, 3, 4):
            bp.set(x, y, 4, ".")
    bp.set(3, 1, 4, "@")
    bp.set(2, 1, 2, "b")
    bp.set(4, 1, 2, "b")
    bp.set(3, 3, 3, "^")
    bp.set(1, 1, 5, "b")
    bp.set(5, 1, 5, "b")
    return bp


def dairy():
    bp = Blueprint(7, 9, 7)
    bp.chars({"W": "white_concrete", "L": "stripped_birch_log[axis=y]", "Rs": "prismarine_brick_stairs[facing=south]",
              "Rn": "prismarine_brick_stairs[facing=north]", "r": "prismarine_brick_slab[type=bottom]",
              "@": "cauldron", "x": "white_glazed_terracotta"})
    bp.fill(1, 0, 1, 5, 0, 4, "S")
    shed(bp, 1, 5, 1, 4, "W", "L", "R", "r", 3)
    for y in (1, 2):
        for x in (2, 3, 4):
            bp.set(x, y, 4, ".")
    bp.set(2, 2, 1, "g")
    bp.set(4, 2, 1, "g")
    bp.set(3, 1, 4, "@")
    bp.set(2, 1, 2, "b")
    bp.set(4, 1, 2, "x")
    bp.set(3, 3, 3, "^")
    bp.set(1, 1, 5, "b")
    return bp


def pigsty():
    bp = Blueprint(7, 5, 7)
    bp.chars({"e": "oak_fence_gate[facing=south,open=false]", "M": "mud", "r": "spruce_slab[type=bottom]",
              "@": "mud", "t": "spruce_trapdoor[facing=north,half=bottom,open=false]"})
    pen(bp, 7, 7, 3)
    for x in range(1, 6):
        for z in range(1, 6):
            bp.set(x, 0, z, "M" if (x + z) % 3 else "d")
    bp.fill(1, 1, 0, 5, 2, 0, "p")
    bp.set(1, 1, 1, "f")
    bp.set(1, 2, 1, "f")
    bp.set(5, 1, 1, "f")
    bp.set(5, 2, 1, "f")
    bp.fill(0, 3, 0, 6, 3, 2, "r")
    bp.set(3, 1, 4, "@")
    bp.fill(1, 1, 5, 2, 1, 5, "t")
    bp.set(5, 1, 5, "w")
    return bp


def sheep_fold():
    bp = Blueprint(7, 5, 7)
    bp.chars({"e": "oak_fence_gate[facing=south,open=false]", "r": "spruce_slab[type=bottom]", "@": "grass_block",
              "y": "white_wool"})
    pen(bp, 7, 7, 3)
    bp.fill(1, 1, 0, 5, 2, 0, "p")
    bp.set(2, 1, 0, "H")
    bp.set(4, 1, 0, "y")
    bp.set(1, 1, 1, "f")
    bp.set(1, 2, 1, "f")
    bp.set(5, 1, 1, "f")
    bp.set(5, 2, 1, "f")
    bp.fill(0, 3, 0, 6, 3, 2, "r")
    bp.set(3, 1, 4, "@")
    bp.set(5, 1, 5, "w")
    bp.set(1, 1, 5, "h")
    return bp


def weaver():
    bp = Blueprint(7, 9, 7)
    bp.chars({"L": "stripped_spruce_log[axis=y]", "Rs": "spruce_stairs[facing=south]", "Rn": "spruce_stairs[facing=north]",
              "r": "spruce_slab[type=bottom]", "1": "white_wool", "2": "pink_wool", "3": "light_blue_wool",
              "@": "loom"})
    bp.fill(1, 0, 1, 5, 0, 4, "p")
    shed(bp, 1, 5, 1, 4, "P", "L", "R", "r", 3)
    for y in (1, 2):
        for x in (2, 3, 4):
            bp.set(x, y, 4, ".")
    bp.set(3, 1, 4, "@")
    bp.set(2, 1, 2, "1")
    bp.set(2, 2, 2, "2")
    bp.set(4, 1, 2, "3")
    bp.set(3, 3, 3, "^")
    bp.set(5, 1, 5, "1")
    bp.set(1, 1, 5, "b")
    return bp


def juice_press():
    bp = Blueprint(7, 5, 5)
    bp.chars({"1": "orange_wool", "2": "white_wool", "u": "pumpkin", "M": "melon", "@": "brewing_stand",
              "c": "spruce_slab[type=top]"})
    for x in (1, 5):
        bp.fill(x, 1, 1, x, 3, 1, "f")
        bp.fill(x, 1, 3, x, 3, 3, "f")
    for x in range(0, 7):
        for z in range(0, 5):
            bp.set(x, 4, z, "1" if x % 2 == 0 else "2")
    bp.set(2, 1, 3, "c")
    bp.set(4, 1, 3, "c")
    bp.set(3, 1, 3, "@")
    bp.set(2, 1, 1, "b")
    bp.set(3, 1, 1, "b")
    bp.set(4, 1, 1, "b")
    bp.set(2, 2, 1, "u")
    bp.set(4, 2, 1, "M")
    bp.set(0, 1, 4, "u")
    bp.set(6, 1, 4, "M")
    return bp


def roadside():
    bp = Blueprint(5, 5, 3)
    bp.chars({"1": "red_wool", "2": "white_wool", "c": "spruce_slab[type=top]", "u": "pumpkin", "M": "melon",
              "@": "chest[facing=south]"})
    for x in (0, 4):
        bp.fill(x, 1, 0, x, 3, 0, "f")
        bp.fill(x, 1, 2, x, 3, 2, "f")
    for x in range(0, 5):
        for z in range(0, 3):
            bp.set(x, 4, z, "1" if x % 2 == 0 else "2")
    bp.set(1, 1, 2, "c")
    bp.set(3, 1, 2, "c")
    bp.set(2, 1, 2, "@")
    bp.set(1, 1, 0, "h")
    bp.set(2, 1, 0, "b")
    bp.set(3, 1, 0, "u")
    bp.set(3, 2, 0, "M")
    return bp


# ---------------------------------------------------------------------------
# YAML
# ---------------------------------------------------------------------------

def stair_aliases(bp):
    """'Rs'/'Rn' er 2-tegns nøgler i Python - erstat dem med ledige enkelt-tegn."""
    free = [ch for ch in "ABCEIJKMNOQTUVXYZ0123456789!$%&+,-/:;<=>?" if ch not in bp.palette and ch not in COMMON]
    mapping = {}
    used = set()
    for y in range(bp.h):
        for z in range(bp.d):
            for x in range(bp.w):
                used.add(bp.cells[y][z][x])
    for key in list(used):
        if len(key) > 1:
            if key not in mapping:
                ch = free.pop(0)
                mapping[key] = ch
    return mapping


def resolve(bp):
    mapping = stair_aliases(bp)
    used = set()
    layers = []
    for y in range(bp.h):
        rows = []
        for z in range(bp.d):
            row = ""
            for x in range(bp.w):
                ch = bp.cells[y][z][x]
                ch = mapping.get(ch, ch)
                used.add(ch)
                row += ch
            rows.append(row)
        layers.append(rows)
    palette = {}
    for key, value in list(bp.palette.items()) + list(COMMON.items()):
        ch = mapping.get(key, key)
        if len(ch) == 1 and ch in used and ch != " " and ch not in palette:
            palette[ch] = value
    # Stair-nøgler som "Rs" findes kun via mapping
    for key, ch in mapping.items():
        if ch not in palette:
            palette[ch] = bp.palette.get(key) or bp.palette.get(key[0] + key[1:])
    missing = [ch for ch in used if ch not in palette and ch != " "]
    if missing:
        raise SystemExit("Mangler i paletten: %s" % missing)
    return layers, palette


def yaml_str(value):
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def main():
    structures = {}
    for name, fn in (("farmhouse", farmhouse), ("barn", barn), ("silo", silo), ("orderboard", orderboard),
                     ("mailbox", mailbox), ("fieldpatch", fieldpatch), ("pond", pond), ("dock", dock),
                     ("fountain", fountain)):
        bp, extra = fn()
        structures[name] = (bp, extra)
    # 3D-modeller ved husene: "model dx dy dz [drejning] [størrelse] [grader/sek]" - relativt til bygningens blok
    props = {
        "foderfabrik": ["feed_sack -1 0 0 15 0.9", "feed_sack 1 0 0 -20 0.9", "windmill 0 4.4 0.7 0 1.6 40"],
        "hoensehus": ["feed_sack 2 0 -1 10 0.8"],
        "bageri": ["bread_basket -1 0 1 0 0.8"],
        "kostald": ["hay_stack -1 0 -2 90 1.0", "milk_churn 2 0 -1 0 0.8"],
        "sukkermoelle": ["windmill 0 4.4 1.7 0 1.6 40"],
        "mejeri": ["milk_churn -1 0 1 0 0.8", "milk_churn 1 0 1 30 0.8", "cheese_wheel -1 1 -2 0 0.7"],
        "svinesti": ["wheelbarrow 2 0 -1 -30 0.9"],
        "faarefold": ["hay_stack -2 0 -1 90 1.0"],
        "vaeveri": ["crate_produce 1 0 1 0 0.8"],
        "saftpresse": ["crate_produce -2 0 1 0 0.8"],
        "vejbod": ["crate_produce 2.7 0 0.2 15 0.8"],
    }
    buildings = {
        "foderfabrik": (feed_mill, 6.4),
        "hoensehus": (chicken_coop, 3.3),
        "bageri": (bakery, 6.2),
        "kostald": (cow_barn, 3.3),
        "sukkermoelle": (sugar_mill, 6.4),
        "mejeri": (dairy, 6.4),
        "svinesti": (pigsty, 3.3),
        "faarefold": (sheep_fold, 3.3),
        "vaeveri": (weaver, 6.4),
        "saftpresse": (juice_press, 4.0),
        "vejbod": (roadside, 4.0),
    }
    for name, (fn, height) in buildings.items():
        structures[name] = (fn(), {"hologram-height": height, "props": props.get(name, [])})

    lines = [
        "# ==============================================================",
        "#   HayDay - bygninger og gårdens pynt (genereret af tools/build_structures.py)",
        "#",
        "#   layers: lag nedefra og op. Hver række går fra nord (bagsiden) mod syd (forsiden),",
        "#           hvert tegn er én blok fra vest mod øst.",
        "#   palette: \"tegn=blok\".  ' ' = rør ikke,  '.' = luft.",
        "#   '@' er ankeret. For bygninger er det bygningens blok (laget under er jorden).",
        "#   Bygningerne drejes automatisk så forsiden vender mod spilleren der placerer dem.",
        "#   Navnet på en bygning her skal matche id'et i buildings.yml (eller 'structure:' der).",
        "# ==============================================================",
        "version: 2",
        "structures:",
    ]
    for name, (bp, extra) in structures.items():
        layers, palette = resolve(bp)
        lines.append("  %s:" % name)
        for key, value in extra.items():
            if isinstance(value, list):
                if value:
                    lines.append("    %s:" % key)
                    for entry in value:
                        lines.append("      - %s" % yaml_str(entry))
                continue
            lines.append("    %s: %s" % (key, value))
        lines.append("    palette:")
        for ch in sorted(palette):
            lines.append("      - %s" % yaml_str(ch + "=" + palette[ch]))
        lines.append("    layers:")
        for rows in layers:
            lines.append("      -")
            for row in rows:
                lines.append("        - %s" % yaml_str(row))
    with open(OUT, "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")
    print("Skrev %d strukturer -> %s" % (len(structures), os.path.normpath(OUT)))


if __name__ == "__main__":
    main()

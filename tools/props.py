"""
HayDays 3D-modeller ("props"): lastbilen, skibet, vindmøllevinger, runde høballer, sække, mælkejunger ...

Modellerne bygges af kasser (elements) med Minecrafts egne blok-teksturer + nogle få egne teksturer.
Pluginet viser dem som ItemDisplay-entities (item-model "hayday:prop_<navn>"), og ItemsAdder kan bruge
dem som møbler (configs/hayday_props.yml).

Koordinater er i pixels: 16 = én blok. Modellens bund er y=0, og forsiden vender mod syd (+z).
"""
import json
import os
import random

from PIL import Image, ImageDraw

V = "minecraft:block/"

# Navn -> (dansk navn, ItemsAdder-hitbox (længde, bredde, højde) eller None, ingen møbel)
PROPS = {}


def box(frm, to, tex, up=None, down=None, south=None, north=None, east=None, west=None, rotation=None):
    faces = {}
    sizes = {
        "north": (to[0] - frm[0], to[1] - frm[1]), "south": (to[0] - frm[0], to[1] - frm[1]),
        "east": (to[2] - frm[2], to[1] - frm[1]), "west": (to[2] - frm[2], to[1] - frm[1]),
        "up": (to[0] - frm[0], to[2] - frm[2]), "down": (to[0] - frm[0], to[2] - frm[2]),
    }
    override = {"up": up, "down": down, "south": south, "north": north, "east": east, "west": west}
    for face, (w, h) in sizes.items():
        faces[face] = {"texture": "#" + (override[face] or tex), "uv": [0, 0, min(16, abs(w)), min(16, abs(h))]}
    element = {"from": list(frm), "to": list(to), "faces": faces}
    if rotation:
        element["rotation"] = rotation
    return element


def prop(name, label, textures, elements, furniture=None):
    PROPS[name] = (label, textures, elements, furniture)


# ---------------------------------------------------------------------------
# Modellerne
# ---------------------------------------------------------------------------

def define():
    prop("feed_sack", "Fodersæk", {"b": "hayday:item/props/burlap", "t": V + "hay_block_side",
                                   "l": "hayday:item/props/sack_label"}, [
        box((4, 0, 4), (12, 10, 12), "b", south="l"),
        box((5, 10, 5), (11, 12, 11), "b"),
        box((6.5, 12, 6.5), (9.5, 14, 9.5), "t"),
    ], furniture=(1, 1, 1))

    prop("hay_stack", "Rund høballe", {"s": V + "hay_block_side", "e": V + "hay_block_top"}, [
        box((0, 2.5, 2), (16, 14.5, 14), "s", east="e", west="e"),
        box((0, 2.5, 2), (16, 14.5, 14), "s", east="e", west="e",
            rotation={"angle": 45, "axis": "x", "origin": [8, 8.5, 8]}),
    ], furniture=(1, 1, 1))

    prop("milk_churn", "Mælkejunge", {"i": V + "iron_block", "g": V + "light_gray_concrete"}, [
        box((4, 0, 4), (12, 1, 12), "i"),
        box((4.5, 1, 4.5), (11.5, 10, 11.5), "i"),
        box((4.3, 6, 4.3), (11.7, 7, 11.7), "g"),
        box((6, 10, 6), (10, 13, 10), "i"),
        box((5.5, 13, 5.5), (10.5, 14, 10.5), "g"),
        box((3.5, 9, 7), (4.5, 11, 9), "i"),
        box((11.5, 9, 7), (12.5, 11, 9), "i"),
    ], furniture=(1, 1, 1))

    prop("crate_produce", "Kasse med grønt", {"p": V + "oak_planks", "u": V + "pumpkin_side", "t": V + "pumpkin_top",
                                               "m": V + "melon_side", "r": V + "red_wool", "c": V + "orange_wool"}, [
        box((1, 0, 1), (15, 7, 15), "p"),
        box((2, 7, 2), (7, 11, 7), "u", up="t"),
        box((8, 7, 3), (13, 11, 8), "m"),
        box((3, 7, 9), (6, 9.5, 12), "r"),
        box((6.5, 7, 10), (9, 9.5, 12.5), "r"),
        box((10, 7, 9.5), (13, 9, 13), "c"),
    ], furniture=(1, 1, 1))

    prop("wheelbarrow", "Trillebør", {"p": V + "spruce_planks", "l": V + "spruce_log", "w": V + "black_concrete",
                                      "h": V + "hay_block_top"}, [
        box((2, 5, 2), (14, 10, 12), "p"),
        box((3, 10, 3), (13, 11.5, 11), "h"),
        box((7, 0, 12), (9, 6, 15), "w"),
        box((3, 0, 3), (4, 5, 4), "l"),
        box((12, 0, 3), (13, 5, 4), "l"),
        box((3, 7, -5), (4, 8, 2), "l"),
        box((12, 7, -5), (13, 8, 2), "l"),
    ], furniture=(1, 1, 1))

    prop("scarecrow", "Fugleskræmsel", {"o": V + "dark_oak_log", "a": V + "oak_log", "r": V + "red_wool",
                                         "j": V + "blue_wool", "h": V + "hay_block_side", "k": V + "hay_block_top",
                                         "s": V + "pumpkin_side", "t": V + "pumpkin_top", "f": V + "carved_pumpkin"}, [
        box((7, 0, 7), (9, 22, 9), "o"),
        box((0, 16, 7.5), (16, 17.5, 8.5), "a"),
        box((4, 8, 6), (12, 13, 10), "j"),
        box((4, 13, 6), (12, 21, 10), "r"),
        box((0.5, 15, 6.5), (4, 19, 9.5), "r"),
        box((12, 15, 6.5), (15.5, 19, 9.5), "r"),
        box((-0.5, 14.5, 7), (0.5, 17.5, 9), "h"),
        box((15.5, 14.5, 7), (16.5, 17.5, 9), "h"),
        box((5, 21, 5), (11, 27, 11), "s", up="t", south="f"),
        box((3, 27, 3), (13, 28, 13), "k"),
        box((5, 28, 5), (11, 31, 11), "h", up="k"),
    ], furniture=(1, 1, 2))

    prop("bread_basket", "Brødkurv", {"w": V + "stripped_oak_log", "b": "hayday:item/props/crust"}, [
        box((2, 0, 4), (14, 4, 12), "w"),
        box((3, 4, 5), (7, 7, 11), "b"),
        box((7.5, 4, 5.5), (11.5, 6.5, 10.5), "b"),
        box((5, 6.5, 6), (10, 9, 10), "b"),
    ], furniture=(1, 1, 1))

    prop("cheese_wheel", "Ostehjul", {"c": "hayday:item/props/cheese", "r": V + "yellow_concrete"}, [
        box((3, 0, 3), (13, 5, 13), "c", up="r", down="r"),
        box((3, 0, 3), (13, 5, 13), "c", up="r", down="r", rotation={"angle": 45, "axis": "y", "origin": [8, 2.5, 8]}),
    ], furniture=(1, 1, 1))

    prop("windmill", "Vindmøllevinger", {"o": V + "dark_oak_planks", "l": V + "spruce_log", "w": V + "white_wool"}, [
        box((6, 6, 6), (10, 10, 10), "o"),
        box((7, 10, 7.5), (9, 24, 8.5), "l"),
        box((7, -8, 7.5), (9, 6, 8.5), "l"),
        box((-8, 7, 7.5), (6, 9, 8.5), "l"),
        box((10, 7, 7.5), (24, 9, 8.5), "l"),
        box((9, 12, 7.8), (12.5, 23, 8.2), "w"),
        box((3.5, -7, 7.8), (7, 4, 8.2), "w"),
        box((-7, 9, 7.8), (4, 12.5, 8.2), "w"),
        box((12, 3.5, 7.8), (23, 7, 8.2), "w"),
    ])

    prop("truck", "Lastbil", {"r": V + "red_concrete", "k": V + "black_concrete", "g": V + "glass",
                              "s": V + "light_gray_concrete", "p": V + "spruce_planks", "h": V + "hay_block_side",
                              "e": V + "hay_block_top", "x": V + "barrel_side", "y": V + "barrel_top",
                              "b": "hayday:item/props/burlap", "l": V + "sea_lantern"}, [
        box((1, 3, -8), (15, 5, 24), "k"),
        box((0, 0, -5), (2, 6, 1), "k"),
        box((14, 0, -5), (16, 6, 1), "k"),
        box((0, 0, 15), (2, 6, 21), "k"),
        box((14, 0, 15), (16, 6, 21), "k"),
        box((1, 5, 10), (15, 11, 24), "r"),
        box((1, 11, 18), (15, 13, 24), "r"),
        box((2, 11, 10), (14, 18, 18), "r"),
        box((2.5, 12, 17.9), (13.5, 17, 18.1), "g"),
        box((1.9, 12, 11), (2.1, 17, 17), "g"),
        box((13.9, 12, 11), (14.1, 17, 17), "g"),
        box((1, 4, 24), (15, 6, 25), "s"),
        box((2, 8, 24), (4, 10, 24.3), "l"),
        box((12, 8, 24), (14, 10, 24.3), "l"),
        box((1, 5, -8), (15, 6, 10), "p"),
        box((1, 6, -8), (2, 10, 10), "r"),
        box((14, 6, -8), (15, 10, 10), "r"),
        box((1, 6, -8), (15, 10, -7), "r"),
        box((3, 6, -6), (8, 10, -1), "h", up="e"),
        box((9, 6, -6), (13, 10, -2), "x", up="y"),
        box((3, 6, 1), (7, 10, 5), "b"),
    ], furniture=(2, 3, 2))

    prop("ship", "Fragtskib", {"r": V + "red_concrete", "w": V + "white_concrete", "p": V + "spruce_planks",
                               "u": V + "blue_concrete", "k": V + "black_concrete", "g": V + "glass",
                               "x": V + "barrel_side", "y": V + "barrel_top", "o": V + "oak_planks",
                               "h": V + "hay_block_side", "e": V + "hay_block_top", "l": V + "spruce_log",
                               "f": V + "red_wool"}, [
        box((2, 0, -14), (14, 4, 30), "r"),
        box((1, 4, -16), (15, 9, 32), "w"),
        box((2, 8.9, -14), (14, 9.4, 30), "p"),
        box((3, 9.4, -14), (13, 18, -4), "w"),
        box((2.9, 13, -12), (3.1, 16, -6), "g"),
        box((12.9, 13, -12), (13.1, 16, -6), "g"),
        box((4, 13, -4.1), (12, 16, -3.9), "g"),
        box((2, 18, -15), (14, 19, -3), "u"),
        box((6, 19, -11), (10, 26, -7), "r"),
        box((6, 26, -11), (10, 27, -7), "k"),
        box((3, 9.4, 0), (8, 14, 5), "x", up="y"),
        box((8, 9.4, 0), (13, 14, 5), "o"),
        box((3, 9.4, 8), (8, 13, 13), "h", up="e"),
        box((8, 9.4, 8), (13, 14, 13), "x", up="y"),
        box((4, 14, 1), (8, 17, 4), "o"),
        box((7.5, 9.4, 20), (8.5, 26, 21), "l"),
        box((8.5, 22, 20.4), (13, 25, 20.6), "f"),
    ])


# ---------------------------------------------------------------------------
# Egne teksturer
# ---------------------------------------------------------------------------

def paint_textures(texture_dir):
    rnd = random.Random(99)
    os.makedirs(texture_dir, exist_ok=True)

    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for y in range(16):
        for x in range(16):
            base = (200, 168, 112) if (x + y) % 2 == 0 else (186, 152, 98)
            n = rnd.randint(-10, 10)
            d.point((x, y), fill=(base[0] + n, base[1] + n, base[2] + n, 255))
    img.save(os.path.join(texture_dir, "burlap.png"))

    label = img.copy()
    d = ImageDraw.Draw(label)
    d.rectangle((3, 3, 12, 12), fill=(244, 232, 200, 255), outline=(150, 110, 60, 255))
    for i, x in enumerate((6, 8, 10)):
        d.line([(x, 11), (x, 6)], fill=(120, 150, 40, 255))
        d.point((x - 1, 6 + i % 2), fill=(230, 180, 40, 255))
        d.point((x + 1, 7), fill=(230, 180, 40, 255))
        d.point((x, 5), fill=(240, 196, 60, 255))
    label.save(os.path.join(texture_dir, "sack_label.png"))

    img = Image.new("RGBA", (16, 16), (246, 204, 76, 255))
    d = ImageDraw.Draw(img)
    for _ in range(9):
        x, y = rnd.randint(1, 13), rnd.randint(1, 13)
        d.ellipse((x, y, x + rnd.choice((1, 2)), y + rnd.choice((1, 2))), fill=(214, 168, 50, 255))
    img.save(os.path.join(texture_dir, "cheese.png"))

    img = Image.new("RGBA", (16, 16), (196, 128, 56, 255))
    d = ImageDraw.Draw(img)
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-12, 8)
            d.point((x, y), fill=(196 + n, 128 + n, 56 + n // 2, 255))
    for x in (3, 8, 13):
        d.line([(x, 2), (x + 2, 13)], fill=(232, 196, 120, 255))
    img.save(os.path.join(texture_dir, "crust.png"))


# ---------------------------------------------------------------------------
# Skriv filerne
# ---------------------------------------------------------------------------

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as out:
        json.dump(data, out, indent=2, ensure_ascii=True)
        out.write("\n")


def write(assets_dir, itemsadder_dir):
    """Skriver modeller, item-definitioner, teksturer og ItemsAdder-møbler. Returnerer listen af props."""
    PROPS.clear()
    define()
    paint_textures(os.path.join(assets_dir, "textures", "item", "props"))
    for name, (label, textures, elements, furniture) in PROPS.items():
        tex = dict(textures)
        tex["particle"] = list(textures.values())[0]
        write_json(os.path.join(assets_dir, "models", "item", "props", name + ".json"),
                   {"parent": "minecraft:block/block", "textures": tex, "elements": elements})
        write_json(os.path.join(assets_dir, "items", "prop_" + name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "hayday:item/props/" + name}})

    lines = [
        "# Genereret af tools/generate_pack.py - HayDays 3D-modeller som ItemsAdder-møbler",
        "# Få dem med /iaget hayday:<navn> - fx /iaget hayday:truck",
        "info:",
        "  namespace: hayday",
        "",
        "items:",
    ]
    for name, (label, textures, elements, furniture) in PROPS.items():
        if furniture is None:
            continue
        length, width, height = furniture
        lines += [
            "  %s:" % name,
            "    display_name: \"&f%s\"" % label,
            "    resource:",
            "      material: PAPER",
            "      generate: false",
            "      model_path: item/props/%s" % name,
            "    behaviours:",
            "      furniture:",
            "        entity: item_display",
            "        solid: true",
            "        hitbox:",
            "          length: %d" % length,
            "          width: %d" % width,
            "          height: %d" % height,
        ]
    path = os.path.join(itemsadder_dir, "hayday_props.yml")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")
    return list(PROPS.keys())

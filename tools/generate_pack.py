#!/usr/bin/env python3
"""
Laver HayDays komplette resourcepack: menu-baggrunde, egne item-ikoner, ikoner til hologrammer,
font-opsætning, item-modeller, pack.mcmeta og pack.png - samt ItemsAdder-config'en.

Al grafik er original og tegnes af dette script i en hyggelig farm-stil. Der bruges ingen grafik fra Hay Day.

Kør:  pip install pillow && python3 tools/generate_pack.py

Output:
  src/main/resources/resourcepack/      den selvstændige resourcepack (bygges til en zip af pluginet)
  src/main/resources/itemsadder/        ItemsAdder-config (bruges hvis ItemsAdder er installeret)
  src/main/resources/pack-glyphs.yml    hvilke tegn i fonten der viser hvilke billeder

Menu-geometri (samme som ItemsAdders egne menu-baggrunde):
  * Teksturen er 192 px bred og 52 + rækker*18 + 10 px høj, med ascent 47.
  * Den forskydes -16 px, så tekstur-pixel (16, 52) ligger præcis over menuens første slot.
  * Slot (række r, kolonne k) har sin 18x18 ramme ved x = 15 + 18k, y = 51 + 18r.
  * Pixel (191, 0) har alpha 1, så tegnets bredde altid er 192 (+1 = 193 px fremrykning).
"""
import json
import os
import random

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RESOURCES = os.path.join(HERE, "..", "src", "main", "resources")
PACK = os.path.join(RESOURCES, "resourcepack")
ASSETS = os.path.join(PACK, "assets", "hayday")
TEXTURES = os.path.join(ASSETS, "textures")
WIDTH = 192
GUI_ADVANCE = WIDTH + 1

GUI_BASE = 0xEB00
ICON_BASE = 0xEB80
NEGATIVE_BASE = 0xEBC0
POSITIVE_BASE = 0xEBD0
SPACES = [1, 2, 4, 8, 16, 32, 64, 128, 256]

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

# Farver
WOOD = (166, 107, 52)
WOOD_LIGHT = (196, 138, 74)
WOOD_DARK = (110, 66, 28)
OUTLINE = (66, 38, 14)
CREAM = (250, 236, 200)
CREAM_DARK = (226, 200, 150)
SLOT_FILL = (255, 247, 224)
SLOT_BORDER = (196, 152, 92)
SLOT_SHADOW = (232, 212, 170)

RIBBONS = {
    "red": ((214, 48, 49), (150, 28, 30), (240, 98, 92)),
    "green": ((96, 172, 58), (56, 112, 30), (140, 206, 98)),
    "yellow": ((236, 178, 40), (170, 118, 18), (250, 214, 104)),
    "blue": ((58, 132, 206), (30, 82, 140), (110, 172, 236)),
    "brown": ((160, 100, 50), (100, 60, 24), (200, 140, 84)),
}

BADGES = {
    "green": ((121, 193, 67), (63, 122, 30), (170, 226, 120)),
    "red": ((224, 69, 58), (140, 32, 26), (244, 128, 116)),
    "orange": ((242, 162, 58), (160, 94, 20), (250, 200, 120)),
    "gold": ((245, 197, 66), (166, 120, 20), (252, 228, 140)),
    "blue": ((74, 150, 220), (34, 88, 150), (140, 196, 244)),
}

random.seed(42)


def font(size):
    try:
        return ImageFont.truetype(FONT_BOLD, size)
    except OSError:
        return ImageFont.load_default()


def ensure_dir(path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def write_json(path, data):
    with open(ensure_dir(path), "w", encoding="utf-8") as out:
        json.dump(data, out, indent=2, ensure_ascii=True)
        out.write("\n")


# ===========================================================================
# Menu-baggrunde
# ===========================================================================

def height(rows):
    return 52 + rows * 18 + 10


def cell(slot):
    return 15 + 18 * (slot % 9), 51 + 18 * (slot // 9)


def wood_rect(draw, box, radius=6):
    x0, y0, x1, y1 = box
    draw.rounded_rectangle(box, radius=radius, fill=WOOD, outline=OUTLINE)
    for y in range(y0 + 3, y1 - 2, 3):
        shade = WOOD_DARK if (y // 3) % 2 == 0 else WOOD_LIGHT
        start = x0 + 3 + random.randint(0, 6)
        end = x1 - 3 - random.randint(0, 6)
        for x in range(start, end):
            if random.random() < 0.55:
                draw.point((x, y), fill=shade)
    draw.line([(x0 + radius, y0 + 1), (x1 - radius, y0 + 1)], fill=WOOD_LIGHT)
    draw.line([(x0 + 1, y0 + radius), (x0 + 1, y1 - radius)], fill=WOOD_LIGHT)
    draw.line([(x0 + radius, y1 - 1), (x1 - radius, y1 - 1)], fill=WOOD_DARK)
    draw.line([(x1 - 1, y0 + radius), (x1 - 1, y1 - radius)], fill=WOOD_DARK)


def panel(img, rows, inner=CREAM, inner_dark=CREAM_DARK):
    draw = ImageDraw.Draw(img)
    h = height(rows)
    wood_rect(draw, (4, 30, WIDTH - 5, h - 2), radius=7)
    draw.rounded_rectangle((10, 36, WIDTH - 11, h - 8), radius=4, fill=inner, outline=OUTLINE)
    draw.line([(12, 37), (WIDTH - 13, 37)], fill=inner_dark)
    draw.line([(14, 49), (WIDTH - 15, 49)], fill=inner_dark)
    for x, y in ((8, 34), (WIDTH - 9, 34), (8, h - 6), (WIDTH - 9, h - 6)):
        draw.point((x, y), fill=(230, 220, 200))
        draw.point((x + 1, y + 1), fill=OUTLINE)
    return draw


def ribbon(img, text, color="red"):
    draw = ImageDraw.Draw(img)
    main, dark, light = RIBBONS[color]
    f = font(13)
    tw = draw.textlength(text, font=f)
    w = int(tw) + 28
    x0 = (WIDTH - w) // 2
    x1 = x0 + w
    y0, y1 = 8, 28
    for side in (-1, 1):
        tx = x0 - 12 if side < 0 else x1 - 2
        tail = [(tx, y0 + 6), (tx + 14, y0 + 6), (tx + 14, y1 + 4), (tx, y1 + 4),
                (tx + (5 if side < 0 else 9), (y0 + y1) // 2 + 5)]
        draw.polygon(tail, fill=dark, outline=OUTLINE)
    draw.rounded_rectangle((x0, y0, x1, y1), radius=4, fill=main, outline=OUTLINE)
    draw.line([(x0 + 3, y0 + 2), (x1 - 3, y0 + 2)], fill=light)
    draw.line([(x0 + 3, y1 - 2), (x1 - 3, y1 - 2)], fill=dark)
    tx = (WIDTH - tw) / 2
    draw.text((tx, y0 + 3), text, font=f, fill=(255, 250, 235), stroke_width=1, stroke_fill=OUTLINE)


def slot(draw, slot_index, fill=SLOT_FILL, border=SLOT_BORDER, shadow=SLOT_SHADOW):
    x, y = cell(slot_index)
    draw.rounded_rectangle((x, y, x + 17, y + 17), radius=3, fill=fill, outline=border)
    draw.line([(x + 2, y + 1), (x + 15, y + 1)], fill=shadow)
    draw.line([(x + 1, y + 2), (x + 1, y + 15)], fill=shadow)


def slots(draw, indices, **kwargs):
    for index in indices:
        slot(draw, index, **kwargs)


def crate(draw, slot_index):
    x, y = cell(slot_index)
    draw.rectangle((x, y, x + 17, y + 17), fill=(197, 138, 74), outline=(110, 66, 28))
    draw.rectangle((x + 2, y + 2, x + 15, y + 15), outline=(150, 96, 44))
    draw.line([(x + 2, y + 2), (x + 15, y + 15)], fill=(150, 96, 44))
    draw.rectangle((x + 4, y + 4, x + 13, y + 13), fill=(232, 196, 140))


def soil(draw, slot_index):
    x, y = cell(slot_index)
    draw.rounded_rectangle((x, y, x + 17, y + 17), radius=3, fill=(132, 86, 46), outline=(80, 48, 20))
    for i in range(4):
        draw.line([(x + 2, y + 3 + i * 4), (x + 15, y + 3 + i * 4)], fill=(108, 68, 34))
    draw.line([(x + 2, y + 1), (x + 15, y + 1)], fill=(160, 112, 66))


def paper_note(draw, slot_index):
    x, y = cell(slot_index)
    draw.rectangle((x + 1, y + 2, x + 17, y + 17), fill=(150, 110, 70))
    draw.rectangle((x, y + 1, x + 16, y + 16), fill=(255, 252, 240), outline=(170, 150, 120))
    draw.ellipse((x + 6, y - 1, x + 10, y + 3), fill=(214, 48, 49), outline=(120, 20, 20))


def badge(draw, slot_index, color="green", symbol=None, label=None):
    x, y = cell(slot_index)
    main, dark, light = BADGES[color]
    draw.rounded_rectangle((x, y + 1, x + 17, y + 17), radius=6, fill=dark)
    draw.rounded_rectangle((x, y, x + 17, y + 15), radius=6, fill=main, outline=OUTLINE)
    draw.line([(x + 4, y + 2), (x + 13, y + 2)], fill=light)
    white = (255, 255, 255)
    if symbol == "x":
        draw.line([(x + 5, y + 4), (x + 12, y + 11)], fill=white, width=2)
        draw.line([(x + 12, y + 4), (x + 5, y + 11)], fill=white, width=2)
    elif symbol == "left":
        draw.polygon([(x + 4, y + 8), (x + 9, y + 3), (x + 9, y + 6), (x + 13, y + 6), (x + 13, y + 10), (x + 9, y + 10), (x + 9, y + 13)],
                     fill=white, outline=OUTLINE)
    elif symbol == "right":
        draw.polygon([(x + 13, y + 8), (x + 8, y + 3), (x + 8, y + 6), (x + 4, y + 6), (x + 4, y + 10), (x + 8, y + 10), (x + 8, y + 13)],
                     fill=white, outline=OUTLINE)
    if label:
        f = font(6 if len(label) > 2 else 7)
        tw = draw.textlength(label, font=f)
        draw.text((x + 9 - tw / 2, y + 4), label, font=f, fill=white, stroke_width=1, stroke_fill=OUTLINE)


def speckle(img, box, color, amount):
    draw = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    for _ in range(amount):
        draw.point((random.randint(x0, x1), random.randint(y0, y1)), fill=color)


def new_gui(rows):
    return Image.new("RGBA", (WIDTH, height(rows)), (0, 0, 0, 0))


GUIS = []


def save_gui(img, name):
    # Næsten usynlig pixel i øverste højre hjørne: giver tegnet en fast bredde i fonten
    img.putpixel((WIDTH - 1, 0), (0, 0, 0, 1))
    img.save(ensure_dir(os.path.join(TEXTURES, "gui", name + ".png")))
    GUIS.append((name, img.size[1]))


def gui_main():
    img = new_gui(4)
    draw = panel(img, 4)
    badge(draw, 4, "gold")
    for s in (10, 12, 14, 16, 19, 21, 23, 25):
        badge(draw, s, "green")
    badge(draw, 29, "gold")
    badge(draw, 31, "red", "x")
    badge(draw, 33, "blue")
    ribbon(img, "HAY DAY", "red")
    save_gui(img, "main_4")


def gui_storage(name, title, color):
    img = new_gui(6)
    draw = panel(img, 6)
    slots(draw, range(0, 45))
    badge(draw, 45, "orange", "left")
    badge(draw, 47, "gold")
    badge(draw, 49, "gold")
    badge(draw, 53, "green")
    ribbon(img, title, color)
    save_gui(img, name + "_6")


def gui_sell():
    img = new_gui(3)
    draw = panel(img, 3)
    badge(draw, 4, "gold")
    slots(draw, range(10, 15))
    badge(draw, 16, "gold")
    badge(draw, 18, "orange", "left")
    badge(draw, 26, "red", "x")
    ribbon(img, "SÆLG", "yellow")
    save_gui(img, "sell_3")


def gui_seed(rows):
    img = new_gui(rows)
    draw = panel(img, rows)
    for row in range(1, rows - 1):
        for column in range(1, 8):
            soil(draw, row * 9 + column)
    badge(draw, rows * 9 - 5, "red", "x")
    ribbon(img, "PLANT", "green")
    save_gui(img, "seed_" + str(rows))


def gui_building():
    img = new_gui(6)
    draw = panel(img, 6)
    badge(draw, 4, "gold")
    badge(draw, 8, "green")
    for s in range(9, 18):
        crate(draw, s)
    badge(draw, 22, "green")
    slots(draw, range(27, 45))
    badge(draw, 45, "orange", "left")
    badge(draw, 49, "red", "x")
    ribbon(img, "PRODUKTION", "brown")
    save_gui(img, "building_6")


def gui_orders():
    img = new_gui(3)
    draw = panel(img, 3, inner=(205, 152, 96), inner_dark=(170, 120, 70))
    speckle(img, (12, 50, WIDTH - 13, height(3) - 10), (176, 124, 72), 260)
    badge(draw, 4, "blue")
    for s in range(9, 18):
        paper_note(draw, s)
    badge(draw, 18, "orange", "left")
    badge(draw, 26, "red", "x")
    ribbon(img, "ORDRER", "blue")
    save_gui(img, "orders_3")


def gui_shop():
    img = new_gui(5)
    draw = panel(img, 5)
    badge(draw, 4, "gold")
    for row in range(1, 4):
        slots(draw, [row * 9 + c for c in range(1, 8)])
    badge(draw, 36, "orange", "left")
    badge(draw, 40, "red", "x")
    ribbon(img, "BUTIK", "green")
    save_gui(img, "shop_5")


def gui_roadside():
    img = new_gui(4)
    draw = panel(img, 4)
    for i, x in enumerate(range(12, WIDTH - 12, 12)):
        color = (214, 48, 49) if i % 2 == 0 else (250, 245, 235)
        draw.rectangle((x, 38, x + 11, 47), fill=color)
    draw.line([(12, 48), (WIDTH - 13, 48)], fill=OUTLINE)
    badge(draw, 4, "gold")
    for s in (10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25):
        crate(draw, s)
    badge(draw, 27, "orange", "left")
    badge(draw, 31, "blue")
    badge(draw, 35, "red", "x")
    ribbon(img, "VEJBOD", "red")
    save_gui(img, "roadside_4")


def gui_listing():
    img = new_gui(6)
    draw = panel(img, 6)
    slots(draw, range(0, 36))
    badge(draw, 37, "red", label="-5")
    badge(draw, 38, "red", label="-1")
    badge(draw, 40, "gold")
    badge(draw, 42, "green", label="+1")
    badge(draw, 43, "green", label="+5")
    badge(draw, 45, "orange", "left")
    badge(draw, 46, "red", label="-10")
    badge(draw, 47, "red", label="-1")
    badge(draw, 49, "green")
    badge(draw, 51, "green", label="+1")
    badge(draw, 52, "green", label="+10")
    badge(draw, 53, "gold", label="MAX")
    ribbon(img, "SÆLG VARE", "yellow")
    save_gui(img, "listing_6")


def gui_news():
    img = new_gui(6)
    draw = panel(img, 6, inner=(246, 242, 230), inner_dark=(210, 204, 190))
    slots(draw, range(0, 45), fill=(255, 255, 252), border=(170, 165, 150), shadow=(232, 228, 216))
    badge(draw, 45, "orange", "left")
    badge(draw, 48, "blue", "left")
    badge(draw, 49, "gold")
    badge(draw, 50, "blue", "right")
    badge(draw, 53, "red", "x")
    ribbon(img, "AVISEN", "brown")
    save_gui(img, "news_6")


def gui_ship():
    rows = 5
    img = new_gui(rows)
    water = (204, 230, 246)
    draw = panel(img, rows, inner=water, inner_dark=(160, 200, 228))
    # bølger
    for y in range(56, height(rows) - 12, 6):
        for x in range(14 + (y // 6 % 2) * 3, WIDTH - 16, 7):
            draw.arc((x, y, x + 5, y + 3), 180, 360, fill=(150, 196, 230))
    # kajen bag kasserne
    for row in range(1, 4):
        x0, y0 = cell(row * 9 + 2)
        draw.rectangle((x0 - 3, y0 + 2, x0 + 18 * 5 - 1, y0 + 17), fill=(150, 104, 58), outline=OUTLINE)
        for x in range(x0, x0 + 18 * 5 - 3, 9):
            draw.line([(x, y0 + 3), (x, y0 + 16)], fill=(120, 80, 40))
    badge(draw, 4, "blue")
    for row in range(1, 4):
        badge(draw, row * 9 + 1, "gold")
        for column in (3, 4, 5):
            crate(draw, row * 9 + column)
        badge(draw, row * 9 + 7, "green")
    badge(draw, 36, "orange", "left")
    badge(draw, 40, "green")
    badge(draw, 44, "red", "x")
    ribbon(img, "SKIBET", "blue")
    save_gui(img, "ship_5")


# ===========================================================================
# Små ikoner (16x16) til hologrammer og beskeder
# ===========================================================================

ICONS = []


def save_icon(name, painter):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    painter(ImageDraw.Draw(img))
    img.save(ensure_dir(os.path.join(TEXTURES, "icons", name + ".png")))
    ICONS.append(name)


def paint_clock(d):
    d.ellipse((1, 1, 14, 14), fill=(250, 245, 230), outline=(80, 60, 40))
    d.ellipse((2, 2, 13, 13), outline=(214, 48, 49))
    d.line([(8, 8), (8, 4)], fill=(40, 30, 20))
    d.line([(8, 8), (11, 9)], fill=(40, 30, 20))
    d.point((8, 8), fill=(214, 48, 49))


def paint_check(d):
    d.ellipse((0, 0, 15, 15), fill=(96, 172, 58), outline=(40, 90, 20))
    d.line([(4, 8), (7, 11), (12, 4)], fill=(255, 255, 255), width=2)


def paint_coin(d):
    d.ellipse((1, 1, 14, 14), fill=(245, 197, 66), outline=(150, 100, 10))
    d.ellipse((4, 4, 11, 11), outline=(200, 150, 30))
    d.line([(6, 4), (6, 11)], fill=(255, 236, 160))


def paint_star(color):
    def painter(d):
        points = [(8, 0), (10, 5), (15, 6), (11, 10), (12, 15), (8, 12), (4, 15), (5, 10), (1, 6), (6, 5)]
        d.polygon(points, fill=color, outline=(90, 60, 10))
    return painter


def paint_crate(d):
    d.rectangle((1, 2, 14, 14), fill=(197, 138, 74), outline=(100, 60, 24))
    d.line([(1, 6), (14, 6)], fill=(110, 66, 28))
    d.line([(1, 10), (14, 10)], fill=(110, 66, 28))
    d.line([(4, 2), (4, 14)], fill=(150, 96, 44))
    d.line([(11, 2), (11, 14)], fill=(150, 96, 44))


def paint_anchor(d):
    navy = (40, 70, 120)
    d.ellipse((6, 0, 9, 3), outline=navy)
    d.line([(7, 3), (7, 13)], fill=navy, width=2)
    d.line([(4, 5), (11, 5)], fill=navy)
    d.arc((1, 6, 14, 15), 20, 160, fill=navy, width=2)
    d.polygon([(1, 10), (3, 8), (4, 11)], fill=navy)
    d.polygon([(14, 10), (12, 8), (11, 11)], fill=navy)


# ===========================================================================
# Egne item-ikoner (16x16) i farm-stil
# ===========================================================================

ITEMS = []


def outline_pass(img, color):
    """Tegner en 1 px kontur rundt om alle synlige pixels (som Minecrafts egne items)."""
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3] > 0:
                    img.putpixel((x, y), color + (255,))
                    break


def save_item(name, painter, outline=(52, 34, 20)):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    painter(ImageDraw.Draw(img), img)
    if outline:
        outline_pass(img, outline)
    img.save(ensure_dir(os.path.join(TEXTURES, "item", name + ".png")))
    ITEMS.append(name)


def shade(color, amount):
    return tuple(max(0, min(255, c + amount)) for c in color)


def sack(label, accent):
    burlap = (214, 182, 128)

    def painter(d, img):
        d.rounded_rectangle((3, 5, 12, 14), radius=3, fill=burlap)
        d.polygon([(5, 2), (10, 2), (11, 5), (4, 5)], fill=shade(burlap, -10))
        d.line([(4, 4), (11, 4)], fill=(150, 100, 50))
        d.line([(4, 6), (4, 13)], fill=shade(burlap, 22))
        d.line([(12, 7), (12, 13)], fill=shade(burlap, -36))
        d.rectangle((5, 8, 10, 12), fill=label)
        d.line([(5, 8), (10, 8)], fill=shade(label, 30))
        for x, y in ((6, 10), (8, 11), (9, 9)):
            d.point((x, y), fill=accent)
    return painter


def paint_milk(d, img):
    glass = (236, 242, 246)
    d.rectangle((5, 6, 10, 14), fill=(250, 250, 250))
    d.rectangle((6, 3, 9, 6), fill=glass)
    d.rectangle((6, 1, 9, 3), fill=(60, 120, 210))
    d.rectangle((5, 9, 10, 11), fill=(70, 140, 220))
    d.line([(6, 7), (6, 13)], fill=(255, 255, 255))
    d.line([(10, 7), (10, 13)], fill=(214, 222, 230))


def paint_cream(d, img):
    d.rectangle((4, 7, 11, 14), fill=(232, 236, 240))
    d.line([(5, 8), (5, 13)], fill=(250, 250, 252))
    d.ellipse((3, 3, 12, 9), fill=(255, 252, 240))
    d.ellipse((6, 1, 10, 5), fill=(255, 255, 248))
    d.line([(4, 11), (11, 11)], fill=(210, 60, 60))
    d.point((8, 2), fill=(214, 48, 49))


def paint_butter(d, img):
    d.ellipse((1, 9, 14, 14), fill=(120, 170, 220))
    d.ellipse((2, 9, 13, 13), fill=(220, 236, 250))
    d.rectangle((4, 5, 11, 11), fill=(248, 214, 90))
    d.polygon([(4, 5), (6, 3), (13, 3), (11, 5)], fill=(255, 236, 140))
    d.polygon([(11, 5), (13, 3), (13, 9), (11, 11)], fill=(224, 182, 60))


def paint_cheese(d, img):
    d.polygon([(1, 8), (13, 3), (14, 12), (1, 13)], fill=(250, 204, 70))
    d.polygon([(1, 8), (13, 3), (14, 5), (2, 9)], fill=(255, 228, 120))
    for x, y in ((4, 11), (8, 9), (11, 11), (10, 7)):
        d.point((x, y), fill=(214, 160, 40))
        d.point((x + 1, y), fill=(214, 160, 40))


def paint_bacon(d, img):
    for offset in (0, 5):
        for i in range(12):
            x = 2 + i
            y = 4 + offset + (1 if (i // 2) % 2 == 0 else 0)
            d.point((x, y), fill=(196, 58, 58))
            d.point((x, y + 1), fill=(250, 214, 200))
            d.point((x, y + 2), fill=(170, 40, 40))


def paint_sugar(d, img):
    def cube(x, y):
        d.rectangle((x, y, x + 4, y + 4), fill=(252, 252, 255))
        d.line([(x, y), (x + 4, y)], fill=(255, 255, 255))
        d.line([(x + 4, y + 1), (x + 4, y + 4)], fill=(200, 206, 220))
        d.line([(x, y + 4), (x + 4, y + 4)], fill=(210, 214, 228))
    cube(2, 9)
    cube(8, 9)
    cube(5, 4)


def glass_drink(liquid, garnish):
    def painter(d, img):
        d.polygon([(3, 4), (12, 4), (11, 14), (4, 14)], fill=(220, 236, 244))
        d.polygon([(4, 7), (11, 7), (10, 13), (5, 13)], fill=liquid)
        d.line([(5, 8), (5, 12)], fill=shade(liquid, 40))
        d.line([(10, 1), (8, 7)], fill=(240, 80, 80))
        d.ellipse((10, 2, 14, 6), fill=garnish)
    return painter


def paint_berry_juice(d, img):
    red = (176, 16, 58)
    d.rectangle((5, 6, 10, 14), fill=red)
    d.line([(6, 7), (6, 13)], fill=shade(red, 50))
    d.rectangle((6, 2, 9, 6), fill=(200, 210, 220))
    d.rectangle((6, 1, 9, 2), fill=(150, 100, 60))
    d.rectangle((5, 9, 10, 11), fill=(250, 240, 220))
    d.point((7, 10), fill=red)
    d.point((8, 10), fill=red)


def paint_sweater(d, img):
    red = (192, 57, 43)
    d.polygon([(4, 2), (11, 2), (14, 5), (14, 9), (12, 9), (12, 14), (3, 14), (3, 9), (1, 9), (1, 5)], fill=red)
    d.rectangle((6, 2, 9, 3), fill=(150, 36, 28))
    d.line([(3, 8), (12, 8)], fill=(250, 240, 230))
    d.line([(3, 10), (12, 10)], fill=(250, 240, 230))
    for x in range(4, 12, 2):
        d.point((x, 9), fill=(250, 240, 230))
    d.line([(4, 3), (2, 5)], fill=shade(red, 40))


def paint_blanket(d, img):
    blue = (90, 170, 230)
    d.rounded_rectangle((1, 5, 12, 13), radius=3, fill=blue)
    for x in (4, 7, 10):
        d.line([(x, 5), (x, 13)], fill=(250, 250, 250))
    d.ellipse((10, 4, 15, 14), fill=shade(blue, -20))
    d.arc((11, 6, 14, 12), 0, 360, fill=(250, 250, 250))
    d.point((12, 9), fill=shade(blue, -50))


# ===========================================================================
# Pack-filer
# ===========================================================================

def paint_pack_icon():
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / size
        d.line([(0, y), (size, y)], fill=(int(120 + 80 * t), int(190 + 40 * t), 245))
    d.rectangle((0, 92, size, size), fill=(108, 176, 64))
    d.rectangle((0, 92, size, 96), fill=(132, 200, 80))
    # lade
    d.polygon([(24, 52), (64, 22), (104, 52)], fill=(150, 30, 30), outline=(70, 20, 20))
    d.rectangle((28, 52, 100, 100), fill=(196, 48, 48), outline=(90, 20, 20))
    d.rectangle((50, 66, 78, 100), fill=(250, 245, 235), outline=(90, 20, 20))
    d.line([(50, 66), (78, 100)], fill=(196, 48, 48), width=3)
    d.line([(78, 66), (50, 100)], fill=(196, 48, 48), width=3)
    d.rectangle((56, 38, 72, 50), fill=(250, 245, 235), outline=(90, 20, 20))
    # høballe
    d.rounded_rectangle((92, 86, 122, 108), radius=4, fill=(236, 196, 80), outline=(140, 100, 20))
    d.line([(100, 86), (100, 108)], fill=(200, 150, 40), width=2)
    d.line([(114, 86), (114, 108)], fill=(200, 150, 40), width=2)
    img.save(ensure_dir(os.path.join(PACK, "pack.png")))


def write_pack_files():
    write_json(os.path.join(PACK, "pack.mcmeta"), {
        "pack": {
            "description": "§a§lHay§e§lDay §7- farmspillet",
            "pack_format": 46,
            "supported_formats": {"min_inclusive": 46, "max_inclusive": 999},
            "min_format": 46,
            "max_format": 999,
        }
    })

    # Font: menu-baggrunde, ikoner og mellemrum (negative og positive) i standard-fonten
    providers = []
    advances = {}
    glyphs = {"gui-advance": GUI_ADVANCE, "gui": {}, "icons": {}, "negative": {}, "positive": {}}
    for i, amount in enumerate(SPACES):
        neg = chr(NEGATIVE_BASE + i)
        pos = chr(POSITIVE_BASE + i)
        advances[neg] = -amount
        advances[pos] = amount
        glyphs["negative"][amount] = "%04X" % (NEGATIVE_BASE + i)
        glyphs["positive"][amount] = "%04X" % (POSITIVE_BASE + i)
    providers.append({"type": "space", "advances": advances})
    for i, (name, h) in enumerate(GUIS):
        code = GUI_BASE + i
        providers.append({"type": "bitmap", "file": "hayday:gui/" + name + ".png", "ascent": 47, "height": h, "chars": [chr(code)]})
        glyphs["gui"][name] = "%04X" % code
    for i, name in enumerate(ICONS):
        code = ICON_BASE + i
        providers.append({"type": "bitmap", "file": "hayday:icons/" + name + ".png", "ascent": 8, "height": 9, "chars": [chr(code)]})
        glyphs["icons"][name] = "%04X" % code
    write_json(os.path.join(PACK, "assets", "minecraft", "font", "default.json"), {"providers": providers})

    # Item-modeller (1.21.4+ "items"-mappen)
    for name in ITEMS + ["invisible"]:
        write_json(os.path.join(ASSETS, "items", name + ".json"),
                   {"model": {"type": "minecraft:model", "model": "hayday:item/" + name}})
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": "hayday:item/" + name}})
    Image.new("RGBA", (16, 16), (0, 0, 0, 0)).save(ensure_dir(os.path.join(TEXTURES, "item", "invisible.png")))

    # Glyph-kort til pluginet
    lines = ["# Genereret af tools/generate_pack.py - tegn i resourcepackens standard-font", "gui-advance: %d" % GUI_ADVANCE]
    for section in ("gui", "icons", "negative", "positive"):
        lines.append(section + ":")
        for key, value in glyphs[section].items():
            lines.append("  '%s': '%s'" % (key, value))
    lines.append("items:")
    for name in ITEMS:
        lines.append("  - " + name)
    with open(ensure_dir(os.path.join(RESOURCES, "pack-glyphs.yml")), "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")


def write_itemsadder_config():
    lines = [
        "# Genereret af tools/generate_pack.py - HayDays menuer og ikoner til ItemsAdder",
        "# Teksturerne og item-modellerne ligger i resourcepack/assets/hayday/ ved siden af denne mappe.",
        "info:",
        "  namespace: hayday",
        "",
        "font_images:",
    ]
    for name, _ in GUIS:
        lines += ["  gui_" + name + ":", "    path: gui/" + name, "    suggest_in_command: false", "    y_position: 47"]
    for name in ICONS:
        lines += ["  icon_" + name + ":", "    path: icons/" + name, "    suggest_in_command: true",
                  "    scale_ratio: 9", "    y_position: 8"]
    path = os.path.join(RESOURCES, "itemsadder", "configs", "hayday.yml")
    with open(ensure_dir(path), "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")


def main():
    gui_main()
    gui_storage("silo", "SILO", "yellow")
    gui_storage("barn", "LADE", "red")
    gui_sell()
    gui_building()
    gui_orders()
    gui_shop()
    gui_roadside()
    gui_listing()
    gui_news()
    gui_ship()
    for rows in range(3, 7):
        gui_seed(rows)

    save_icon("clock", paint_clock)
    save_icon("check", paint_check)
    save_icon("coin", paint_coin)
    save_icon("xp", paint_star((120, 220, 90)))
    save_icon("star", paint_star((250, 214, 70)))
    save_icon("crate", paint_crate)
    save_icon("anchor", paint_anchor)

    save_item("kyllingefoder", sack((250, 214, 70), (160, 110, 20)))
    save_item("kofoder", sack((120, 190, 80), (60, 110, 30)))
    save_item("grisefoder", sack((240, 150, 180), (170, 70, 110)))
    save_item("faarefoder", sack((140, 196, 240), (50, 100, 160)))
    save_item("maelk", paint_milk)
    save_item("floede", paint_cream)
    save_item("smoer", paint_butter)
    save_item("ost", paint_cheese)
    save_item("bacon", paint_bacon)
    save_item("sukker", paint_sugar)
    save_item("gulerodsjuice", glass_drink((255, 140, 26), (255, 160, 60)))
    save_item("melonjuice", glass_drink((255, 96, 110), (120, 200, 90)))
    save_item("baersaft", paint_berry_juice)
    save_item("uldsweater", paint_sweater)
    save_item("uldtaeppe", paint_blanket)

    paint_pack_icon()
    write_pack_files()
    write_itemsadder_config()
    print("Resourcepack: %d menuer, %d ikoner, %d items -> %s" % (len(GUIS), len(ICONS), len(ITEMS), os.path.normpath(PACK)))


if __name__ == "__main__":
    main()

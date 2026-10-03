#!/usr/bin/env python3
"""
Tegner HayDays ItemsAdder-teksturer (menu-baggrunde og ikoner) og skriver ItemsAdder-config'en.

Al grafik er original og tegnet af dette script i en hyggelig "farm"-stil - der bruges ingen grafik fra Hay Day.

Kør:  pip install pillow && python3 tools/generate_textures.py
Output: src/main/resources/itemsadder/hayday/...

Geometri (samme som ItemsAdders egne menu-baggrunde):
  * Teksturen er 192 px bred og 52 + rækker*18 + 10 px høj.
  * Font-billedet har y_position 47 og forskydes -16 px, så tekstur-pixel (16, 52) ligger
    præcis over menuens første slot (række 0, kolonne 0).
  * Slot (række r, kolonne k) har sin 18x18 ramme ved x = 15 + 18k, y = 51 + 18r.
"""
import os
import random

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "itemsadder", "hayday")
TEXTURES = os.path.join(ROOT, "resourcepack", "assets", "hayday", "textures")
WIDTH = 192

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


def height(rows):
    return 52 + rows * 18 + 10


def cell(slot):
    return 15 + 18 * (slot % 9), 51 + 18 * (slot // 9)


# ---------------------------------------------------------------------------
# Grundelementer
# ---------------------------------------------------------------------------

def wood_rect(draw, box, radius=6):
    x0, y0, x1, y1 = box
    draw.rounded_rectangle(box, radius=radius, fill=WOOD, outline=OUTLINE)
    # årer i træet
    for y in range(y0 + 3, y1 - 2, 3):
        shade = WOOD_DARK if (y // 3) % 2 == 0 else WOOD_LIGHT
        start = x0 + 3 + random.randint(0, 6)
        end = x1 - 3 - random.randint(0, 6)
        for x in range(start, end):
            if random.random() < 0.55:
                draw.point((x, y), fill=shade)
    # lys kant foroven/venstre og mørk forneden/højre
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
    # skillelinje under titlen
    draw.line([(14, 49), (WIDTH - 15, 49)], fill=inner_dark)
    # små søm i hjørnerne
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
    # haler
    for side in (-1, 1):
        tx = x0 - 12 if side < 0 else x1 - 2
        tail = [(tx, y0 + 6), (tx + 14, y0 + 6), (tx + 14, y1 + 4), (tx, y1 + 4), (tx + (5 if side < 0 else 9), (y0 + y1) // 2 + 5)]
        draw.polygon(tail, fill=dark, outline=OUTLINE)
    # selve båndet
    draw.rounded_rectangle((x0, y0, x1, y1), radius=4, fill=main, outline=OUTLINE)
    draw.line([(x0 + 3, y0 + 2), (x1 - 3, y0 + 2)], fill=light)
    draw.line([(x0 + 3, y1 - 2), (x1 - 3, y1 - 2)], fill=dark)
    # tekst med kontur
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


def new(rows):
    return Image.new("RGBA", (WIDTH, height(rows)), (0, 0, 0, 0))


def save(img, name):
    path = os.path.join(TEXTURES, "gui", name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return name


# ---------------------------------------------------------------------------
# Menuerne
# ---------------------------------------------------------------------------

def gui_main():
    img = new(4)
    draw = panel(img, 4)
    badge(draw, 4, "gold")
    for s in (10, 12, 14, 16, 20, 22, 24):
        badge(draw, s, "green")
    badge(draw, 29, "gold")
    badge(draw, 31, "red", "x")
    badge(draw, 33, "blue")
    ribbon(img, "HAY DAY", "red")
    return save(img, "main_4")


def gui_storage(name, title, color):
    img = new(6)
    draw = panel(img, 6)
    slots(draw, range(0, 45))
    badge(draw, 45, "orange", "left")
    badge(draw, 47, "gold")
    badge(draw, 49, "gold")
    badge(draw, 53, "green")
    ribbon(img, title, color)
    return save(img, name + "_6")


def gui_sell():
    img = new(3)
    draw = panel(img, 3)
    badge(draw, 4, "gold")
    slots(draw, range(10, 15))
    badge(draw, 16, "gold")
    badge(draw, 18, "orange", "left")
    badge(draw, 26, "red", "x")
    ribbon(img, "SÆLG", "yellow")
    return save(img, "sell_3")


def gui_seed(rows):
    img = new(rows)
    draw = panel(img, rows)
    for row in range(1, rows - 1):
        for column in range(1, 8):
            soil(draw, row * 9 + column)
    badge(draw, rows * 9 - 5, "red", "x")
    ribbon(img, "PLANT", "green")
    return save(img, "seed_" + str(rows))


def gui_building():
    img = new(6)
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
    return save(img, "building_6")


def gui_orders():
    img = new(3)
    board = (205, 152, 96)
    draw = panel(img, 3, inner=board, inner_dark=(170, 120, 70))
    speckle(img, (12, 50, WIDTH - 13, height(3) - 10), (176, 124, 72), 260)
    badge(draw, 4, "blue")
    for s in range(9, 18):
        paper_note(draw, s)
    badge(draw, 18, "orange", "left")
    badge(draw, 26, "red", "x")
    ribbon(img, "ORDRER", "blue")
    return save(img, "orders_3")


def gui_shop():
    img = new(5)
    draw = panel(img, 5)
    badge(draw, 4, "gold")
    for row in range(1, 4):
        slots(draw, [row * 9 + c for c in range(1, 8)])
    badge(draw, 36, "orange", "left")
    badge(draw, 40, "red", "x")
    ribbon(img, "BUTIK", "green")
    return save(img, "shop_5")


def gui_roadside():
    img = new(4)
    draw = panel(img, 4)
    # en lille markise over boden
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
    return save(img, "roadside_4")


def gui_listing():
    img = new(6)
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
    return save(img, "listing_6")


def gui_news():
    img = new(6)
    paper = (246, 242, 230)
    draw = panel(img, 6, inner=paper, inner_dark=(210, 204, 190))
    slots(draw, range(0, 45), fill=(255, 255, 252), border=(170, 165, 150), shadow=(232, 228, 216))
    badge(draw, 45, "orange", "left")
    badge(draw, 48, "blue", "left")
    badge(draw, 49, "gold")
    badge(draw, 50, "blue", "right")
    badge(draw, 53, "red", "x")
    ribbon(img, "AVISEN", "brown")
    return save(img, "news_6")


# ---------------------------------------------------------------------------
# Ikoner (16x16) til hologrammer og beskeder
# ---------------------------------------------------------------------------

def icon(name, painter):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    painter(ImageDraw.Draw(img))
    path = os.path.join(TEXTURES, "icons", name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return name


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


def write_config(guis, icons):
    lines = [
        "# Genereret af tools/generate_textures.py - HayDays menuer og ikoner til ItemsAdder",
        "info:",
        "  namespace: hayday",
        "",
        "items:",
        "  # Usynligt item - bruges over knapper der er tegnet i menu-teksturen",
        "  invisible:",
        "    enabled: true",
        "    display_name: \" \"",
        "    resource:",
        "      material: PAPER",
        "      generate: true",
        "      textures:",
        "        - item/invisible",
        "",
        "font_images:",
    ]
    for name in guis:
        lines += [
            "  gui_" + name + ":",
            "    path: gui/" + name,
            "    suggest_in_command: false",
            "    y_position: 47",
        ]
    for name in icons:
        lines += [
            "  icon_" + name + ":",
            "    path: icons/" + name,
            "    suggest_in_command: true",
            "    scale_ratio: 9",
            "    y_position: 8",
        ]
    path = os.path.join(ROOT, "configs", "hayday.yml")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as out:
        out.write("\n".join(lines) + "\n")


def main():
    guis = [
        gui_main(),
        gui_storage("silo", "SILO", "yellow"),
        gui_storage("barn", "LADE", "red"),
        gui_sell(),
        gui_building(),
        gui_orders(),
        gui_shop(),
        gui_roadside(),
        gui_listing(),
        gui_news(),
    ]
    for rows in range(3, 7):
        guis.append(gui_seed(rows))
    icons = [
        icon("clock", paint_clock),
        icon("check", paint_check),
        icon("coin", paint_coin),
        icon("xp", paint_star((120, 220, 90))),
        icon("star", paint_star((250, 214, 70))),
        icon("crate", paint_crate),
    ]
    invisible = os.path.join(TEXTURES, "item", "invisible.png")
    os.makedirs(os.path.dirname(invisible), exist_ok=True)
    Image.new("RGBA", (16, 16), (0, 0, 0, 0)).save(invisible)
    write_config(guis, icons)
    print("Lavede %d menu-teksturer og %d ikoner i %s" % (len(guis), len(icons), ROOT))


if __name__ == "__main__":
    main()

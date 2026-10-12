"""Generates every MineGTA texture from scratch: 16x16 item icons, wanted stars and the mod icon.

Run from the repo root:  python tools/gen_textures.py
Binary-safe: writes PNGs under src/main/resources/assets/minegta/.
"""

import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "src", "main", "resources", "assets", "minegta")

GUNMETAL = (43, 47, 51, 255)
STEEL = (110, 122, 133, 255)
LIGHT = (170, 182, 191, 255)
WOOD = (138, 90, 43, 255)
WOOD_DARK = (99, 62, 29, 255)
BRASS = (216, 179, 74, 255)
BRASS_DARK = (158, 127, 45, 255)
BLACK = (0, 0, 0, 255)
WHITE = (255, 255, 255, 255)
RED = (200, 40, 40, 255)


def canvas(size=16):
    image = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    return image, ImageDraw.Draw(image)


def rect(draw, box, color):
    draw.rectangle(box, fill=color)


def pixels(image, points):
    """Paint a set of (x, y, color) pixels, for fine pixel art."""
    for point in points:
        x, y, color = point
        if 0 <= x < image.width and 0 <= y < image.height:
            image.putpixel((x, y), color)


def save(image, relative):
    path = os.path.join(ROOT, relative)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    return path


def star(size=16, fill=WHITE, outline=BLACK):
    image, draw = canvas(size)
    center = size / 2.0
    outer = size / 2.0 - 1.0
    inner = outer * 0.42

    points = []
    for index in range(10):
        angle = -math.pi / 2.0 + index * math.pi / 5.0
        radius = outer if index % 2 == 0 else inner
        points.append((center + math.cos(angle) * radius, center + math.sin(angle) * radius))

    draw.polygon(points, fill=fill, outline=outline)
    return image


def pistol():
    image, draw = canvas()
    rect(draw, (4, 6, 10, 9), GUNMETAL)          # slide
    rect(draw, (5, 5, 9, 6), STEEL)              # top of the slide
    rect(draw, (10, 7, 12, 8), LIGHT)            # muzzle
    rect(draw, (3, 9, 6, 14), GUNMETAL)          # grip
    rect(draw, (3, 9, 4, 14), WOOD_DARK)
    rect(draw, (6, 9, 6, 12), LIGHT)             # trigger guard hint
    rect(draw, (8, 4, 9, 5), STEEL)              # rear sight
    rect(draw, (5, 4, 6, 5), STEEL)              # front sight
    return image


def micro_smg():
    image, draw = canvas()
    rect(draw, (3, 6, 12, 9), GUNMETAL)          # receiver
    rect(draw, (12, 7, 15, 8), LIGHT)            # barrel
    rect(draw, (4, 9, 7, 12), STEEL)             # magazine
    rect(draw, (5, 12, 8, 15), GUNMETAL)         # grip
    rect(draw, (2, 8, 4, 12), GUNMETAL)          # stock
    rect(draw, (3, 5, 11, 6), STEEL)             # top rail
    rect(draw, (7, 6, 9, 8), BLACK)              # ejection port
    return image


def pump_shotgun():
    image, draw = canvas()
    rect(draw, (1, 7, 14, 9), STEEL)             # barrel
    rect(draw, (2, 6, 14, 7), LIGHT)             # barrel highlight
    rect(draw, (1, 9, 5, 12), WOOD)              # stock
    rect(draw, (5, 8, 6, 11), WOOD_DARK)         # handguard
    rect(draw, (6, 9, 8, 12), GUNMETAL)          # grip
    rect(draw, (13, 7, 16, 8), BLACK)            # muzzle
    return image


def carbine_rifle():
    image, draw = canvas()
    rect(draw, (2, 7, 13, 9), GUNMETAL)          # receiver
    rect(draw, (13, 8, 16, 9), STEEL)            # barrel
    rect(draw, (6, 5, 12, 6), STEEL)             # scope
    rect(draw, (7, 4, 11, 5), LIGHT)
    rect(draw, (1, 10, 6, 13), WOOD)             # stock
    rect(draw, (6, 9, 9, 12), GUNMETAL)          # grip
    rect(draw, (8, 9, 11, 11), STEEL)            # magazine
    return image


def rpg():
    image, draw = canvas()
    rect(draw, (2, 7, 14, 10), GUNMETAL)         # tube
    rect(draw, (14, 6, 16, 11), STEEL)           # warhead cone
    rect(draw, (15, 7, 16, 10), RED)
    rect(draw, (4, 4, 8, 7), STEEL)              # grip
    rect(draw, (5, 10, 7, 13), GUNMETAL)         # fore grip
    rect(draw, (1, 8, 3, 11), WOOD_DARK)         # rear
    return image


def ammo(rounds, casing=BRASS, tip=BRASS_DARK, layout="row"):
    image, draw = canvas()

    if layout == "row":
        for index in range(rounds):
            x = 2 + index * 3
            rect(draw, (x, 6, x + 1, 10), casing)
            rect(draw, (x, 4, x + 1, 6), tip)
    else:
        for index in range(3):
            x = 2 + index * 4
            rect(draw, (x, 5, x + 1, 9), casing)
            rect(draw, (x + 1, 6, x + 2, 9), tip)

    return image


def shells():
    return ammo(3, casing=RED, tip=BRASS_DARK, layout="stack")


def rockets():
    return ammo(2, casing=GUNMETAL, tip=RED, layout="stack")


def icon():
    image, draw = canvas(64)
    draw.rectangle([2, 2, 62, 62], fill=(20, 21, 26, 255))
    draw.rectangle([2, 2, 62, 12], fill=(200, 40, 40, 255))
    draw.text((16, 22), "$", fill=(255, 255, 255, 255), font_size=36)
    return image


def main():
    made = []

    made.append(save(star(), "textures/gui/star_on.png"))
    made.append(save(star(fill=(70, 70, 70, 255), outline=(20, 20, 20, 255)), "textures/gui/star_off.png"))

    items = {
        "pistol": pistol(),
        "micro_smg": micro_smg(),
        "pump_shotgun": pump_shotgun(),
        "carbine_rifle": carbine_rifle(),
        "rpg": rpg(),
        "ammo_pistol": ammo(4),
        "ammo_smg": ammo(4),
        "ammo_shell": shells(),
        "ammo_rifle": ammo(4),
        "ammo_rocket": rockets(),
    }

    for name, image in items.items():
        made.append(save(image, f"textures/item/{name}.png"))

    made.append(save(icon(), "icon.png"))

    for path in made:
        print(path)


if __name__ == "__main__":
    main()

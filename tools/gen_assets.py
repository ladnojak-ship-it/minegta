"""Writes item model definitions, item models and language files for MineGTA."""

import json
import os

ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "src", "main", "resources", "assets", "minegta")

GUNS = {
    "pistol": "Pistol",
    "micro_smg": "Micro SMG",
    "pump_shotgun": "Pump Shotgun",
    "carbine_rifle": "Carbine Rifle",
    "rpg": "RPG",
}

AMMO = {
    "ammo_pistol": {"en": "Pistol Ammo", "ru": "Патроны для пистолета"},
    "ammo_smg": {"en": "SMG Ammo", "ru": "Патроны для УП"},
    "ammo_shell": {"en": "Shotgun Shells", "ru": "Дробовые патроны"},
    "ammo_rifle": {"en": "Rifle Ammo", "ru": "Патроны для винтовки"},
    "ammo_rocket": {"en": "Rocket", "ru": "Ракета"},
}

GUN_NAMES = {
    "pistol": {"en": "Pistol", "ru": "Пистолет"},
    "micro_smg": {"en": "Micro SMG", "ru": "Микро-пистолет-пулемёт"},
    "pump_shotgun": {"en": "Pump Shotgun", "ru": "Дробовик"},
    "carbine_rifle": {"en": "Carbine Rifle", "ru": "Карабин"},
    "rpg": {"en": "RPG", "ru": "РПГ"},
}


def write(path, data):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    print(full)


def main():
    items_dir = os.path.join(ROOT, "items")
    models_dir = os.path.join(ROOT, "models", "item")

    for name in GUNS:
        write(os.path.join("items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"minegta:item/{name}"}})
        write(os.path.join("models", "item", f"{name}.json"), {"parent": "minecraft:item/handheld", "textures": {"layer0": f"minegta:item/{name}"}})

    for name in AMMO:
        write(os.path.join("items", f"{name}.json"), {"model": {"type": "minecraft:model", "model": f"minegta:item/{name}"}})
        write(os.path.join("models", "item", f"{name}.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"minegta:item/{name}"}})

    en = {}
    ru = {}

    for name, translations in {**GUN_NAMES, **AMMO}.items():
        en[f"item.minegta.{name}"] = translations["en"]
        ru[f"item.minegta.{name}"] = translations["ru"]

    write(os.path.join("lang", "en_us.json"), en)
    write(os.path.join("lang", "ru_ru.json"), ru)


if __name__ == "__main__":
    main()

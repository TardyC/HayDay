#!/usr/bin/env python3
"""
Laver HayDay-ItemsAdder.zip: mappen "hayday" der pakkes ud i plugins/ItemsAdder/contents/.

Kør:  python3 tools/make_itemsadder_zip.py [uddata.zip]
"""
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
RESOURCES = os.path.join(HERE, "..", "src", "main", "resources")

README = """HayDay - ItemsAdder-indhold
===========================

1. Pak mappen "hayday" ud i:   plugins/ItemsAdder/contents/
   (så du får plugins/ItemsAdder/contents/hayday/configs/hayday.yml osv.)
2. Skriv /iazip i spillet (eller konsollen).
3. Log ud og ind igen - så har alle HayDay-menuerne, ikonerne og 3D-modellerne.

Indhold:
  configs/hayday.yml        menu-baggrunde (font images) og ikoner til hologrammer og chat
  configs/hayday_props.yml  3D-modellerne som møbler, fx /iaget hayday:truck
                            (truck, feed_sack, hay_stack, milk_churn, crate_produce, wheelbarrow,
                             scarecrow, bread_basket, cheese_wheel)
  resourcepack/assets/hayday/  teksturer, item-ikoner og 3D-modeller

Tip: står der 127.0.0.1 eller 127.0.1.1 i ItemsAdders "URL:" efter /iazip, så skriv serverens rigtige
IP/domæne under "server: address:" i plugins/ItemsAdder/config.yml og kør /iazip igen.

HayDay-pluginet kopierer også selv indholdet ind når ItemsAdder er installeret - zip'en er til at gøre det i hånden.
"""


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "target", "HayDay-ItemsAdder.zip")
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    count = 0
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("hayday/LAES-MIG.txt", README)
        configs = os.path.join(RESOURCES, "itemsadder", "configs")
        for name in sorted(os.listdir(configs)):
            z.write(os.path.join(configs, name), "hayday/configs/" + name)
            count += 1
        base = os.path.join(RESOURCES, "resourcepack", "assets", "hayday")
        for dirpath, dirs, files in os.walk(base):
            dirs.sort()
            for name in sorted(files):
                full = os.path.join(dirpath, name)
                rel = os.path.relpath(full, base).replace(os.sep, "/")
                z.write(full, "hayday/resourcepack/assets/hayday/" + rel)
                count += 1
    print("%d filer -> %s" % (count, os.path.normpath(out)))


if __name__ == "__main__":
    main()

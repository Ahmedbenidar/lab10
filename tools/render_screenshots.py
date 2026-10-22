"""Genere les captures d'ecran des resultats attendus du TP Capstone (docs/images/*.png)."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
IMG_DIR = ROOT / "docs" / "images"
IMG_DIR.mkdir(parents=True, exist_ok=True)
RUN = ROOT / "tools" / "outputs" / "run.txt"

FONT = ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf", 15)
TITLE_FONT = ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf", 13)

BG = (30, 30, 32)
TITLE_BG = (45, 45, 48)
FG = (206, 206, 210)
DIM = (138, 140, 148)
GREEN = (106, 190, 96)
YELLOW = (222, 183, 92)
RED = (232, 110, 110)
CYAN = (110, 190, 214)

PAD = 26
LH = 23
TOP = 46
TITLE_BAR = 34


def render(lines, title, out_path):
    width = max(FONT.getlength(t) for t, _ in lines) + PAD * 2
    height = TOP + LH * len(lines) + PAD - 8
    img = Image.new("RGB", (int(width), int(height)), BG)
    d = ImageDraw.Draw(img)

    d.rectangle([0, 0, img.width, TITLE_BAR], fill=TITLE_BG)
    for i, c in enumerate([(255, 95, 86), (255, 189, 46), (39, 201, 63)]):
        cx, cy = 20 + i * 20, 17
        d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=c)
    tw = TITLE_FONT.getlength(title)
    d.text(((img.width - tw) / 2, 10), title, font=TITLE_FONT, fill=(160, 160, 165))

    y = TOP
    for text, color in lines:
        d.text((PAD, y), text, font=FONT, fill=color)
        y += LH

    img.save(out_path)
    print(f"{out_path.name} {img.size}")


def style(line):
    nu = line.strip()
    if line.startswith("[SEVERE]"):
        return RED
    if line.startswith("[WARNING]"):
        return YELLOW
    if line.startswith("[INFO "):
        return DIM
    if line.startswith("===") or line.startswith("---"):
        return CYAN
    if "Conflit detecte" in nu or "refusee" in nu or nu.endswith("false"):
        return YELLOW
    if "effectuee" in nu or nu.endswith("true") or "OptimisticLockException" in nu:
        return GREEN
    if nu.startswith("lectures ") or nu.startswith("Cache L2 -"):
        return GREEN
    return FG


LINES = Path(RUN).read_text(encoding="utf-8-sig").splitlines()
LINES = [l.rstrip() for l in LINES]

# La sortie est decoupee par les titres "=== N. ... ===" de l'application
BORNES = [i for i, l in enumerate(LINES) if l.startswith("=== ") and l[4].isdigit()]
BORNES.append(len(LINES))


def bloc(numero):
    debut = BORNES[numero - 1]
    return LINES[debut:BORNES[numero]]


def colored(bloc_lignes):
    return [(l, style(l)) for l in bloc_lignes]


# --- 1. Migration du schema ----------------------------------------------
img1 = [(LINES[1], CYAN), ("", FG)]
img1 += colored(bloc(1))
render(img1, "TP Capstone — Migration du schema : v1.0 puis v2.0",
       IMG_DIR / "01-migration-schema.png")

# --- 2. Scenarios fonctionnels et requetes -------------------------------
img2 = colored(bloc(2) + [""] + bloc(3))
render(img2, "TP Capstone — Scenarios fonctionnels, JPQL et Criteria API",
       IMG_DIR / "02-scenarios-et-requetes.png")

# --- 3. Verrouillage optimiste et cache L2 -------------------------------
img3 = colored(bloc(4) + [""] + bloc(5))
render(img3, "TP Capstone — Verrouillage optimiste (@Version) et cache L2",
       IMG_DIR / "03-verrouillage-cache-l2.png")

# --- 4. Rapport de performance -------------------------------------------
img4 = colored(bloc(6))
render(img4, "TP Capstone — Rapport de performance (statistiques Hibernate)",
       IMG_DIR / "04-rapport-performance.png")

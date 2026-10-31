#!/usr/bin/env python3
"""
Erzeugt das XuroSMP Resource-Pack (Minecraft 1.21.4, pack_format 46) komplett ohne Fremd-Bibliotheken.
Alle Texturen sind selbst gezeichnete Pixel-Art (keine Mojang-Assets).
Aufruf:  python3 make_pack.py   ->   XuroSMP-Pack.zip + SHA1.txt
"""
import hashlib, json, os, struct, zipfile, zlib

OUT = os.path.dirname(os.path.abspath(__file__))

# ------------------------------------------------------------------ PNG
class Img:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]

    def set(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def get(self, x, y):
        return self.px[y][x] if 0 <= x < self.w and 0 <= y < self.h else (0, 0, 0, 0)

    def blit(self, other, ox, oy):
        for y in range(other.h):
            for x in range(other.w):
                if other.px[y][x][3]:
                    self.set(ox + x, oy + y, other.px[y][x])

    def png(self):
        raw = b''.join(b'\x00' + b''.join(bytes(p) for p in row) for row in self.px)
        def chunk(t, d):
            c = struct.pack('>I', len(d)) + t + d
            return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
        return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', self.w, self.h, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

    def scaled(self, k):
        im = Img(self.w * k, self.h * k)
        for y in range(self.h):
            for x in range(self.w):
                for dy in range(k):
                    for dx in range(k):
                        im.px[y * k + dy][x * k + dx] = self.px[y][x]
        return im


def rgb(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def from_grid(rows, legend):
    im = Img(len(rows[0]), len(rows))
    for y, r in enumerate(rows):
        assert len(r) == im.w, (r, len(r), im.w)
        for x, ch in enumerate(r):
            if ch != '.':
                im.set(x, y, legend[ch])
    return im

# ------------------------------------------------------------------ Icons (8x8, Scoreboard / TAB)
ICONS = []

ICONS.append(from_grid([
    "..oooo..", ".oyyyyo.", "oyyddyyo", "oydyyyyo", "oyyddyyo", "oyyyydyo", ".oyddyo.", "..oooo.."],
    {'o': rgb('b06e14'), 'y': rgb('ffd73c'), 'd': rgb('c88c14')}))                         # money
ICONS.append(from_grid([
    "..pppp..", ".pPPPPp.", "pPwPPPPp", "pPPPPPPp", ".pPPPPp.", "..pPPp..", "...pp...", "........"],
    {'p': rgb('6e28aa'), 'P': rgb('be64ff'), 'w': rgb('fff0ff')}))                          # gems
ICONS.append(from_grid([
    "...yy...", "...yy...", "yyyyyyyy", ".yyyyyy.", "..yyyy..", ".yyyyyy.", ".yy..yy.", "yy....yy"],
    {'y': rgb('ffdc32')}))                                                                   # stars
ICONS.append(from_grid([
    "s......s", "ss....ss", ".ss..ss.", "..ssss..", "..ssss..", ".bs..sb.", "bb....bb", "b......b"],
    {'s': rgb('d2d7e1'), 'b': rgb('825028')}))                                               # kills
ICONS.append(from_grid([
    ".wwwwww.", "wwwwwwww", "wkkwwkkw", "wkkwwkkw", "wwwkkwww", ".wwwwww.", ".w.ww.w.", "........"],
    {'w': rgb('ebebf0'), 'k': rgb('282832')}))                                               # deaths
ICONS.append(from_grid([
    "gggggggg", "gYYYYYYg", "gYYggYYg", "gYYggYYg", ".gYYYYg.", ".gYYYYg.", "..gYYg..", "...gg..."],
    {'g': rgb('f0c83c'), 'Y': rgb('4682e6')}))                                               # clan
ICONS.append(from_grid([
    "..gggg..", ".g....g.", "g..gg..g", "..g..g..", "...gg...", "........", "...gg...", "...gg..."],
    {'g': rgb('50e678')}))                                                                   # ping
ICONS.append(from_grid([
    "..cccc..", ".c....c.", ".c....c.", "..cccc..", "...cc...", "...cc...", "...ccc..", "...cc..."],
    {'c': rgb('50dce6')}))                                                                   # keyall
ICONS.append(from_grid([
    "..gggg..", ".gwwwwg.", "gwwdwwwg", "gwwdwwwg", "gwwddwwg", "gwwwwwwg", ".gwwwwg.", "..gggg.."],
    {'g': rgb('50c864'), 'w': rgb('ebffeb'), 'd': rgb('1e5a28')}))                           # time

ICON_CHARS = [chr(0xE000 + i) for i in range(len(ICONS))]

# ------------------------------------------------------------------ Logo (XUROSMP)
LETTERS = {
    'X': ["X...X", "X...X", ".X.X.", "..X..", ".X.X.", "X...X", "X...X"],
    'U': ["X...X", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."],
    'R': ["XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"],
    'O': [".XXX.", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."],
    'S': [".XXXX", "X....", "X....", ".XXX.", "....X", "....X", "XXXX."],
    'M': ["X...X", "XX.XX", "X.X.X", "X.X.X", "X...X", "X...X", "X...X"],
    'P': ["XXXX.", "X...X", "X...X", "XXXX.", "X....", "X....", "X...."],
}


def make_logo(text="XUROSMP", scale=2):
    gw = len(text) * 6 * scale - scale          # Breite ohne letzten Abstand
    im = Img(gw + 2, 7 * scale + 2)
    c0, c1 = (150, 80, 255), (255, 90, 200)
    for ci, ch in enumerate(text):
        for ry, row in enumerate(LETTERS[ch]):
            for rx, v in enumerate(row):
                if v != 'X':
                    continue
                for dy in range(scale):
                    for dx in range(scale):
                        x = ci * 6 * scale + rx * scale + dx
                        y = ry * scale + dy
                        t = x / max(1, gw)
                        col = tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3)) + (255,)
                        im.set(x + 1, y + 1, (30, 10, 50, 255))   # Schatten
                        im.set(x, y, col)
    return im

# ------------------------------------------------------------------ Item-Texturen (16x16)
def palette(o, p, P, w):
    return {'o': rgb(o), 'p': rgb(p), 'P': rgb(P), 'w': rgb(w), 'h': rgb('a07a46'), 'H': rgb('6e4e28'), 'g': rgb(p)}

PURPLE = palette('2b0a3d', '7a2fbf', 'b565f0', 'f0d4ff')
RED = palette('3d0a0a', 'bf2f2f', 'f06565', 'ffd4d4')
ORANGE = palette('3d230a', 'bf7a2f', 'f0b565', 'fff0d4')


def outline(im, color):
    shape = [(x, y) for y in range(im.h) for x in range(im.w) if im.px[y][x][3]]
    for (x, y) in shape:
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if not im.get(x + dx, y + dy)[3]:
                    im.set(x + dx, y + dy, color)
    return im


def handle(im, P, x0, y0, x1, y1):
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        x = x0 + (x1 - x0) * i // n
        y = y0 + (y1 - y0) * i // n
        im.set(x, y, P['h'])
        im.set(x + 1, y, P['H'])


def pickaxe(P):
    im = Img(16, 16)
    handle(im, P, 2, 13, 10, 5)
    outer = [(4, 7), (4, 6), (5, 5), (6, 4), (7, 3), (8, 3), (9, 3), (10, 3), (11, 3), (12, 4), (13, 5), (14, 6), (14, 7)]
    inner = [(5, 6), (6, 5), (7, 4), (8, 4), (9, 4), (10, 4), (11, 4), (12, 5), (13, 6), (13, 7)]
    for (x, y) in inner:
        im.set(x, y, P['p'])
    for (x, y) in outer:
        im.set(x, y, P['P'])
    im.set(8, 3, P['w']); im.set(9, 3, P['w'])
    return outline(im, P['o'])


def axe(P):
    im = Img(16, 16)
    handle(im, P, 2, 14, 8, 8)
    rows = {1: (8, 10), 2: (8, 12), 3: (8, 13), 4: (8, 13), 5: (8, 12), 6: (8, 10)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            im.set(x, y, P['p'] if x <= x0 + 1 else P['P'])
    for y in (3, 4):
        im.set(13, y, P['w'])
    im.set(9, 1, P['w'])
    return outline(im, P['o'])


def sword(P):
    im = Img(16, 16)
    for i in range(9):
        x, y = 5 + i, 10 - i
        im.set(x, y, P['P'])
        im.set(x + 1, y, P['p'])
    im.set(14, 1, P['w']); im.set(13, 2, P['w'])
    for (x, y) in [(3, 9), (4, 10), (5, 11), (6, 12)]:
        im.set(x, y, P['p'])
    handle(im, P, 1, 14, 4, 11)
    return outline(im, P['o'])


def shovel(P):
    im = Img(16, 16)
    handle(im, P, 2, 14, 9, 7)
    for y in range(16):
        for x in range(16):
            if ((x - 11.5) / 3.4) ** 2 + ((y - 4.0) / 3.6) ** 2 <= 1:
                im.set(x, y, P['P'] if x + y < 16 else P['p'])
    im.set(10, 2, P['w']); im.set(10, 3, P['w'])
    return outline(im, P['o'])


def armor(rows, P):
    return from_grid(rows, {'o': P['o'], 'p': P['p'], 'P': P['P'], 'w': P['w']})


HELMET = ["................", "................", "....oooooooo....", "...oppppppppo...", "..oppPPPPPPppo..",
          "..opPPPPPPPPpo..", "..opPPPPPPPPpo..", "..opPP....PPpo..", "..opP......Ppo..", "..opP......Ppo..",
          "..oop......poo..", "...oo......oo...", "................", "................", "................", "................"]
CHEST = ["................", "..oooo....oooo..", ".oppppo..oppppo.", ".opPPpooooopPpo.", ".opPPppppppPPpo.",
         ".oopPPPPPPPPpoo.", "..opPPPwPPPPpo..", "..opPPPPPPPPpo..", "..opPPPPPPPPpo..", "..opPPPPPPPPpo..",
         "..oppPPPPPPppo..", "...oppppppppo...", "....oooooooo....", "................", "................", "................"]
LEGS = ["................", "...oooooooooo...", "..oppppppppppo..", "..opPPPPPPPPpo..", "..opPPPPPPPPpo..",
        "..opPPpoopPPpo..", "..opPPpoopPPpo..", "..opPPpoopPPpo..", "..opPPpoopPPpo..", "..opPPpoopPPpo..",
        "..oppPpoopPppo..", "..oooooooooooo..", "................", "................", "................", "................"]
BOOTS = ["................", "................", "................", "..oooo....oooo..", "..oppo....oppo..",
         "..opPo....oPpo..", "..opPo....oPpo..", "..opPo....oPpo..", ".oppPo....oPppo.", ".opPPoo..ooPPpo.",
         ".oppppo..oppppo.", "..oooo....oooo..", "................", "................", "................", "................"]

TEXTURES = {
    'crystal_pickaxe': pickaxe(PURPLE), 'crystal_axe': axe(PURPLE), 'crystal_sword': sword(PURPLE),
    'crystal_helmet': armor(HELMET, PURPLE), 'crystal_chestplate': armor(CHEST, PURPLE),
    'crystal_leggings': armor(LEGS, PURPLE), 'crystal_boots': armor(BOOTS, PURPLE),
    'hammer': pickaxe(RED), 'excavator': shovel(ORANGE),
}
HANDHELD = {'crystal_pickaxe', 'crystal_axe', 'crystal_sword', 'hammer', 'excavator'}

# base item -> [(custom_model_data, modelname)]
ITEM_MODELS = {
    'netherite_pickaxe': [(1, 'crystal_pickaxe'), (9, 'hammer')],
    'netherite_axe': [(2, 'crystal_axe')],
    'netherite_sword': [(3, 'crystal_sword')],
    'netherite_helmet': [(4, 'crystal_helmet')],
    'netherite_chestplate': [(5, 'crystal_chestplate')],
    'netherite_leggings': [(6, 'crystal_leggings')],
    'netherite_boots': [(7, 'crystal_boots')],
    'netherite_shovel': [(10, 'excavator')],
}

# ------------------------------------------------------------------ Pack zusammenbauen
def build():
    files = {}
    files['pack.mcmeta'] = json.dumps({"pack": {"pack_format": 46, "description": "§dXuroSMP §7Resource-Pack"}}, ensure_ascii=False).encode('utf-8')
    atlas = Img(8 * len(ICONS), 8)
    for i, ic in enumerate(ICONS):
        atlas.blit(ic, i * 8, 0)
    logo = make_logo()
    files['assets/smp/textures/font/icons.png'] = atlas.png()
    files['assets/smp/textures/font/logo.png'] = logo.png()
    font = {"providers": [
        {"type": "bitmap", "file": "smp:font/icons.png", "ascent": 7, "height": 8, "chars": ["".join(ICON_CHARS)]},
        {"type": "bitmap", "file": "smp:font/logo.png", "ascent": 12, "height": logo.h, "chars": [chr(0xE100)]},
    ]}
    files['assets/minecraft/font/default.json'] = json.dumps(font).encode('ascii')

    for name, img in TEXTURES.items():
        files[f'assets/smp/textures/item/{name}.png'] = img.png()
        parent = 'minecraft:item/handheld' if name in HANDHELD else 'minecraft:item/generated'
        files[f'assets/smp/models/item/{name}.json'] = json.dumps({"parent": parent, "textures": {"layer0": f"smp:item/{name}"}}).encode()
    for base, entries in ITEM_MODELS.items():
        d = {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                       "fallback": {"type": "minecraft:model", "model": f"minecraft:item/{base}"},
                       "entries": [{"threshold": t, "model": {"type": "minecraft:model", "model": f"smp:item/{m}"}} for t, m in entries]}}
        files[f'assets/minecraft/items/{base}.json'] = json.dumps(d).encode()
    files['pack.png'] = ICONS[1].scaled(8).png()

    zpath = os.path.join(OUT, 'XuroSMP-Pack.zip')
    with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as z:
        for name in sorted(files):
            zi = zipfile.ZipInfo(name, date_time=(2024, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, files[name])
    sha = hashlib.sha1(open(zpath, 'rb').read()).hexdigest()
    open(os.path.join(OUT, 'SHA1.txt'), 'w').write(sha + '\n')
    print('Pack erstellt:', zpath, '| SHA-1:', sha, '|', len(files), 'Dateien')

    # Vorschau (nur zur Kontrolle)
    sheet = Img(600, 330)
    for y in range(sheet.h):
        for x in range(sheet.w):
            sheet.px[y][x] = (48, 48, 56, 255)
    x = 8
    for name, img in TEXTURES.items():
        sheet.blit(img.scaled(4), x, 8)
        x += 66 if x < 540 else 0
    sheet.blit(atlas.scaled(6), 8, 90)
    sheet.blit(logo.scaled(4), 8, 160)
    open(os.path.join(OUT, 'preview.png'), 'wb').write(sheet.png())
    return sha


if __name__ == '__main__':
    build()

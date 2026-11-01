"""Assets, data and village buildings of the GambleCraft Sportsbook add-on (sportsbook/).

  python tools/gen_sportsbook.py          textures, icon font, item/block definitions, loot, recipe, lang, structures, Blockbench spec
  python tools/gen_sportsbook.py finish   after `python tools/bb_build.py betting_terminal`: fix the exported model's texture ids

The terminal's block model is built in Blockbench from blockbench/betting_terminal.json by blockbench/build_block.js.
"""
import json
import os
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_structures  # noqa: E402
import nbt  # noqa: E402

NS = "gamblecraft_sportsbook"
ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
RES = os.path.join(ROOT, "sportsbook", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", NS)
DATA = os.path.join(RES, "data", NS)
T = (0, 0, 0, 0)


def path(base, *parts):
    p = os.path.join(base, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def write_json(content, base, *parts):
    with open(path(base, *parts), "w", encoding="utf-8", newline="\n") as f:
        json.dump(content, f, indent=2, ensure_ascii=False)
        f.write("\n")


def img(w=16, h=16):
    return Image.new("RGBA", (w, h), T)


# ---- pixel letters for the sign --------------------------------------------------------------------------------
LETTERS = {
    "B": ["110", "101", "110", "101", "110"],
    "E": ["111", "100", "110", "100", "111"],
    "T": ["111", "010", "010", "010", "010"],
}


def letters(d, text, x, y, colour):
    for ch in text:
        for row, bits in enumerate(LETTERS[ch]):
            for col, bit in enumerate(bits):
                if bit == "1":
                    d.point((x + col, y + row), fill=colour)
        x += 4


def block_textures():
    graphite = (46, 50, 58, 255)
    edge = (30, 33, 40, 255)
    light = (70, 76, 88, 255)
    green = (38, 150, 70, 255)

    body = img()
    d = ImageDraw.Draw(body)
    d.rectangle([0, 0, 15, 15], fill=graphite)
    d.rectangle([0, 0, 15, 15], outline=edge)
    d.line([(1, 1), (14, 1)], fill=light)
    d.line([(0, 12), (15, 12)], fill=green)
    d.line([(0, 13), (15, 13)], fill=(28, 110, 52, 255))
    for x, y in ((2, 4), (13, 4), (2, 9), (13, 9)):
        d.point((x, y), fill=(110, 116, 128, 255))
    body.save(path(ASSETS, "textures", "block", "terminal_body.png"))

    front = img()
    d = ImageDraw.Draw(front)
    d.rectangle([0, 0, 15, 15], fill=graphite)
    d.rectangle([0, 0, 15, 15], outline=edge)
    # printer slot with a coupon sticking out
    d.rectangle([3, 3, 12, 5], fill=(12, 12, 14, 255))
    d.rectangle([5, 5, 10, 8], fill=(236, 232, 220, 255))
    d.line([(6, 6), (9, 6)], fill=(150, 150, 150, 255))
    d.line([(6, 7), (8, 7)], fill=(60, 160, 80, 255))
    # card reader and keys
    d.rectangle([2, 10, 5, 13], fill=(20, 22, 26, 255))
    d.point((3, 11), fill=(60, 220, 90, 255))
    for i, x in enumerate(range(8, 14, 2)):
        for y in (10, 12):
            d.point((x, y), fill=(180, 186, 196, 255))
    d.line([(0, 15), (15, 15)], fill=green)
    front.save(path(ASSETS, "textures", "block", "terminal_front.png"))

    screen = img()
    d = ImageDraw.Draw(screen)
    d.rectangle([0, 0, 15, 15], fill=edge)
    d.rectangle([1, 1, 14, 14], fill=(10, 22, 38, 255))
    d.line([(2, 2), (13, 2)], fill=(31, 122, 52, 255))
    for y in (5, 8, 11):
        d.line([(2, y), (8, y)], fill=(60, 80, 110, 255))
        d.line([(11, y), (13, y)], fill=(200, 160, 50, 255))
    screen.save(path(ASSETS, "textures", "block", "terminal_screen.png"))

    sign = img()
    d = ImageDraw.Draw(sign)
    d.rectangle([0, 0, 15, 15], fill=(30, 120, 56, 255))
    d.rectangle([0, 0, 15, 15], outline=(20, 80, 36, 255))
    d.line([(1, 4), (14, 4)], fill=(240, 200, 60, 255))
    letters(d, "BET", 3, 5, (255, 255, 255, 255))
    d.line([(1, 10), (14, 10)], fill=(240, 200, 60, 255))
    sign.save(path(ASSETS, "textures", "block", "terminal_sign.png"))


def item_textures():
    paper = (238, 234, 222, 255)
    shade = (196, 190, 176, 255)
    green = (40, 150, 70, 255)

    def slip():
        i = img()
        d = ImageDraw.Draw(i)
        d.polygon([(4, 1), (12, 1), (12, 14), (11, 15), (10, 14), (9, 15), (8, 14), (7, 15), (6, 14), (5, 15), (4, 14)], fill=paper)
        d.line([(12, 1), (12, 14)], fill=shade)
        d.rectangle([4, 1, 12, 3], fill=green)
        for y in (5, 7, 9):
            d.line([(5, y), (9, y)], fill=(120, 120, 120, 255))
            d.point((11, y), fill=(200, 150, 40, 255))
        for x in range(5, 12, 2):
            d.line([(x, 11), (x, 12)], fill=(40, 40, 40, 255))
        return i, d

    coupon, _ = slip()
    coupon.save(path(ASSETS, "textures", "item", "bet_coupon.png"))

    lost, d = slip()
    d.line([(3, 3), (13, 13)], fill=(200, 30, 30, 255), width=2)
    d.line([(13, 3), (3, 13)], fill=(200, 30, 30, 255), width=2)
    lost.save(path(ASSETS, "textures", "item", "bet_coupon_lost.png"))

    void, d = slip()
    d.rectangle([3, 6, 13, 10], fill=(150, 150, 156, 255), outline=(100, 100, 106, 255))
    d.line([(5, 8), (11, 8)], fill=(240, 240, 240, 255))
    void.save(path(ASSETS, "textures", "item", "bet_coupon_void.png"))

    # the pouch of winnings: a green drawstring purse, a gold coin and the coupon tucked in its neck
    pouch = img()
    d = ImageDraw.Draw(pouch)
    d.rectangle([6, 1, 9, 4], fill=paper)
    d.line([(6, 2), (9, 2)], fill=green)
    d.ellipse([2, 5, 13, 15], fill=(48, 140, 62, 255), outline=(24, 84, 36, 255))
    d.rectangle([5, 4, 10, 6], fill=(36, 110, 48, 255))
    d.line([(4, 6), (11, 6)], fill=(212, 172, 52, 255))
    d.point((4, 7), fill=(212, 172, 52, 255))
    d.ellipse([7, 9, 11, 13], fill=(240, 196, 60, 255), outline=(170, 120, 20, 255))
    d.point((9, 11), fill=(255, 240, 160, 255))
    d.point((4, 9), fill=(110, 200, 120, 255))
    d.point((5, 8), fill=(110, 200, 120, 255))
    pouch.save(path(ASSETS, "textures", "item", "bet_coupon_won.png"))

    icon = img()
    d = ImageDraw.Draw(icon)
    d.rectangle([3, 0, 12, 2], fill=(30, 120, 56, 255))
    d.rectangle([3, 3, 12, 8], fill=(46, 50, 58, 255))
    d.rectangle([4, 4, 11, 7], fill=(10, 22, 38, 255))
    d.line([(5, 5), (8, 5)], fill=(200, 200, 220, 255))
    d.line([(9, 6), (10, 6)], fill=(220, 170, 50, 255))
    d.rectangle([7, 9, 8, 9], fill=(40, 44, 50, 255))
    d.rectangle([4, 10, 11, 15], fill=(46, 50, 58, 255))
    d.rectangle([6, 11, 9, 12], fill=paper)
    d.line([(4, 14), (11, 14)], fill=(38, 150, 70, 255))
    icon.save(path(ASSETS, "textures", "item", "betting_terminal.png"))


def icon_font():
    """Five 8x8 marks for the coupon description: waiting, live, won, lost, void (Pick.Status order)."""
    sheet = img(40, 8)
    d = ImageDraw.Draw(sheet)
    # clock: grey face, dark hands
    d.ellipse([0, 0, 6, 6], fill=(220, 220, 220, 255), outline=(120, 120, 120, 255))
    d.line([(3, 1), (3, 3)], fill=(40, 40, 40, 255))
    d.line([(3, 3), (5, 3)], fill=(40, 40, 40, 255))
    # live: an orange ring
    d.ellipse([8, 0, 14, 6], outline=(255, 170, 30, 255), width=2)
    # won: a green tick
    d.line([(16, 3), (18, 5)], fill=(60, 210, 80, 255), width=2)
    d.line([(18, 5), (22, 0)], fill=(60, 210, 80, 255), width=2)
    # lost: a red cross
    d.line([(24, 0), (30, 6)], fill=(230, 50, 50, 255), width=2)
    d.line([(30, 0), (24, 6)], fill=(230, 50, 50, 255), width=2)
    # void: a grey bar
    d.rectangle([33, 2, 38, 4], fill=(160, 160, 160, 255))
    sheet.save(path(ASSETS, "textures", "font", "icons.png"))
    write_json({"providers": [{"type": "bitmap", "file": f"{NS}:font/icons.png", "ascent": 7, "height": 8,
                               "chars": [""]}]}, ASSETS, "font", "icons.json")


def models():
    for name in ("bet_coupon", "bet_coupon_won", "bet_coupon_lost", "bet_coupon_void", "betting_terminal"):
        write_json({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}}, ASSETS, "models", "item", f"{name}.json")

    def model(name):
        return {"type": "minecraft:model", "model": f"{NS}:item/{name}"}

    write_json({"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                          "cases": [{"when": "won", "model": model("bet_coupon_won")}, {"when": "lost", "model": model("bet_coupon_lost")},
                                    {"when": "void", "model": model("bet_coupon_void")}],
                          "fallback": model("bet_coupon")}}, ASSETS, "items", "bet_coupon.json")
    write_json({"model": model("betting_terminal")}, ASSETS, "items", "betting_terminal.json")
    states = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        lower = {"model": f"{NS}:block/betting_terminal"}
        if y:
            lower["y"] = y
        states[f"facing={facing},half=lower"] = lower
        states[f"facing={facing},half=upper"] = {"model": f"{NS}:block/betting_terminal_top"}
    write_json({"variants": states}, ASSETS, "blockstates", "betting_terminal.json")
    write_json({"textures": {"particle": f"{NS}:block/terminal_body"}}, ASSETS, "models", "block", "betting_terminal_top.json")


def data():
    write_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:betting_terminal",
               "condition": {"type": "minecraft:match_block", "blocks": f"{NS}:betting_terminal", "state": {"half": "lower"}}}],
               "condition": {"type": "minecraft:survives_explosion"}}]}, DATA, "loot_table", "blocks", "betting_terminal.json")
    write_json({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["IGI", "IRI", "IPI"],
                "key": {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone", "P": "minecraft:paper"},
                "result": {"id": f"{NS}:betting_terminal", "count": 1}}, DATA, "recipe", "betting_terminal.json")


def spec():
    cubes = [
        {"name": "plinth", "from": [1, 0, 2], "to": [15, 1, 15], "tex": "body", "faces": {}},
        {"name": "cabinet", "from": [2, 1, 3], "to": [14, 13, 14], "tex": "body", "faces": {"north": "front"}},
        {"name": "desk", "from": [1, 13, 0.5], "to": [15, 14, 9], "tex": "desk", "faces": {}},
        {"name": "coupon", "from": [6, 14, 1], "to": [10, 14.2, 4], "tex": "paper", "faces": {}},
        {"name": "neck", "from": [6, 14, 8], "to": [10, 16, 12], "tex": "body", "faces": {}},
        {"name": "housing", "from": [1.5, 16, 2.5], "to": [14.5, 27, 9], "tex": "body", "faces": {"north": "screen"}},
        # the sign shows rows 5-9 of its texture, where the letters are
        {"name": "sign", "from": [1, 27, 2], "to": [15, 32, 9.5], "tex": "body", "faces": {"north": "sign", "south": "sign"},
         "uv": {"north": [1, 5, 15, 10], "south": [1, 5, 15, 10]}},
    ]
    write_json({"name": "betting_terminal",
                "textures": {"body": f"{NS}:block/terminal_body", "front": f"{NS}:block/terminal_front", "screen": f"{NS}:block/terminal_screen",
                             "sign": f"{NS}:block/terminal_sign", "desk": "minecraft:block/dark_oak_planks", "paper": "minecraft:block/white_concrete"},
                "cubes": cubes,
                "export": f"sportsbook/src/main/resources/assets/{NS}/models/block/betting_terminal.json"},
               os.path.join(ROOT, "blockbench"), "betting_terminal.json")


def finish():
    """Blockbench names textures after their files; map them back to ids and add the particle texture."""
    with open(os.path.join(ROOT, "blockbench", "betting_terminal.json"), encoding="utf-8") as f:
        textures = json.load(f)["textures"]
    by_file = {v.split("/")[-1]: v for v in textures.values()}
    target = os.path.join(ASSETS, "models", "block", "betting_terminal.json")
    with open(target, encoding="utf-8") as f:
        model = json.load(f)
    fixed = {k: by_file.get(v.split("/")[-1].split(":")[-1], v) for k, v in model["textures"].items()}
    fixed["particle"] = f"{NS}:block/terminal_body"
    write_json({"parent": "minecraft:block/block", "textures": fixed, "elements": model["elements"]}, ASSETS, "models", "block",
               "betting_terminal.json")


LEAGUES = {
    "ekstraklasa": ("Ekstraklasa", "EKS", "Ekstraklasa", "EKS"),
    "premier_league": ("Premier League", "EPL", "Premier League", "EPL"),
    "la_liga": ("LaLiga", "LIGA", "LaLiga", "LIGA"),
    "bundesliga": ("Bundesliga", "BUN", "Bundesliga", "BUN"),
    "serie_a": ("Serie A", "SA", "Serie A", "SA"),
    "champions_league": ("Champions League", "UCL", "Liga Mistrzów", "LM"),
    "nba": ("NBA", "NBA", "NBA", "NBA"),
    "nhl": ("NHL", "NHL", "NHL", "NHL"),
    "atp": ("ATP Tennis", "ATP", "Tenis ATP", "ATP"),
    "wta": ("WTA Tennis", "WTA", "Tenis WTA", "WTA"),
    "ufc": ("UFC", "UFC", "UFC", "UFC"),
}

LANG = {
    "block.gamblecraft_sportsbook.betting_terminal": ("Betting Terminal", "Terminal zakładów"),
    "item.gamblecraft_sportsbook.bet_coupon": ("Bet Coupon", "Kupon zakładów"),
    "item.gamblecraft_sportsbook.bet_coupon.won": ("Winning Coupon – Pouch of Winnings", "Wygrany kupon – sakiewka z wygraną"),
    "item.gamblecraft_sportsbook.bet_coupon.lost": ("Lost Coupon", "Przegrany kupon"),
    "item.gamblecraft_sportsbook.bet_coupon.void": ("Void Coupon – Stake Back", "Kupon anulowany – zwrot stawki"),
    "item.gamblecraft_sportsbook.bet_coupon.desc": ("Bought at a betting terminal.", "Kupuje się go w terminalu zakładów."),
    "gamblecraft_sportsbook.draw": ("Draw", "Remis"),
    "gamblecraft_sportsbook.coupon.single": ("Single bet", "Zakład pojedynczy"),
    "gamblecraft_sportsbook.coupon.accumulator": ("Accumulator, %s picks", "AKO, zdarzeń: %s"),
    "gamblecraft_sportsbook.coupon.pick": ("Pick: %s @ %s", "Typ: %s @ %s"),
    "gamblecraft_sportsbook.coupon.total_odds": ("Total odds: %s", "Kurs łączny: %s"),
    "gamblecraft_sportsbook.coupon.stake": ("Stake: %s chips", "Stawka: %s żetonów"),
    "gamblecraft_sportsbook.coupon.stake_minerals": ("Stake: %s chips (paid in minerals)", "Stawka: %s żetonów (zapłacono minerałami)"),
    "gamblecraft_sportsbook.coupon.possible": ("Possible win: %s chips", "Możliwa wygrana: %s żetonów"),
    "gamblecraft_sportsbook.coupon.progress": ("%s of %s settled", "Rozstrzygnięte: %s z %s"),
    "gamblecraft_sportsbook.coupon.won": ("WON %s chips – use to collect", "WYGRANA %s żetonów – kliknij PPM, aby odebrać"),
    "gamblecraft_sportsbook.coupon.paid": ("Paid out", "Wypłacono"),
    "gamblecraft_sportsbook.coupon.lost": ("Lost", "Przegrany"),
    "gamblecraft_sportsbook.coupon.void": ("Void – use to get %s chips back", "Anulowany – kliknij PPM po zwrot %s żetonów"),
    "gamblecraft_sportsbook.bet.age_gate": ("Accept the 18+ notice first.", "Najpierw zaakceptuj ekran 18+."),
    "gamblecraft_sportsbook.bet.too_far": ("Stand at the terminal to bet.", "Podejdź do terminala, aby postawić."),
    "gamblecraft_sportsbook.bet.empty": ("The coupon is empty.", "Kupon jest pusty."),
    "gamblecraft_sportsbook.bet.too_many": ("At most %s picks on one coupon.", "Najwyżej %s zdarzeń na jednym kuponie."),
    "gamblecraft_sportsbook.bet.same_game": ("One pick per game.", "Jeden typ na mecz."),
    "gamblecraft_sportsbook.bet.stake_limits": ("The stake must be %s–%s chips.", "Stawka: od %s do %s żetonów."),
    "gamblecraft_sportsbook.bet.closed": ("Betting has closed: %s", "Zakłady zamknięte: %s"),
    "gamblecraft_sportsbook.bet.no_chips": ("You need %s chips.", "Potrzebujesz %s żetonów."),
    "gamblecraft_sportsbook.bet.no_minerals": ("Your minerals are not worth %s chips.", "Twoje minerały nie są warte %s żetonów."),
    "gamblecraft_sportsbook.bet.placed": ("Coupon bought: %s picks, odds %s, possible win %s chips.",
                                          "Kupon kupiony: zdarzeń %s, kurs %s, możliwa wygrana %s żetonów."),
    "gamblecraft_sportsbook.bet.unknown": ("This coupon is not from this world.", "Ten kupon nie pochodzi z tego świata."),
    "gamblecraft_sportsbook.bet.pending": ("Waiting for results: %s of %s settled.", "Czeka na wyniki: rozstrzygnięte %s z %s."),
    "gamblecraft_sportsbook.bet.lost": ("This coupon lost.", "Ten kupon przegrał."),
    "gamblecraft_sportsbook.bet.already_paid": ("This coupon has already been paid out.", "Ten kupon został już wypłacony."),
    "gamblecraft_sportsbook.bet.collected": ("Collected %s chips.", "Odebrano %s żetonów."),
    "gamblecraft_sportsbook.bet.news.won": ("Coupon #%s won! %s chips – open the pouch.", "Kupon #%s wygrał! %s żetonów – otwórz sakiewkę."),
    "gamblecraft_sportsbook.bet.news.lost": ("Coupon #%s lost.", "Kupon #%s przegrał."),
    "gamblecraft_sportsbook.bet.news.void": ("Coupon #%s is void: %s chips back – use it.", "Kupon #%s anulowany: zwrot %s żetonów – użyj go."),
    "gamblecraft_sportsbook.screen.title": ("Betting Terminal", "Terminal zakładów"),
    "gamblecraft_sportsbook.screen.balance": ("Chips: %s · minerals: %s", "Żetony: %s · minerały: %s"),
    "gamblecraft_sportsbook.screen.leagues": ("Leagues", "Ligi"),
    "gamblecraft_sportsbook.screen.sources": ("Data sources", "Źródła danych"),
    "gamblecraft_sportsbook.screen.stake": ("Stake", "Stawka"),
    "gamblecraft_sportsbook.screen.pay_chips": ("Pay with chips", "Płacę żetonami"),
    "gamblecraft_sportsbook.screen.pay_minerals": ("Pay with minerals", "Płacę minerałami"),
    "gamblecraft_sportsbook.screen.place": ("Buy coupon", "Kupuję kupon"),
    "gamblecraft_sportsbook.screen.slip": ("Coupon (%s)", "Kupon (%s)"),
    "gamblecraft_sportsbook.screen.slip_empty": ("Click a price to add a pick.", "Kliknij kurs, aby dodać typ."),
    "gamblecraft_sportsbook.screen.more": ("+%s more", "+%s więcej"),
    "gamblecraft_sportsbook.screen.total_odds": ("Odds: %s", "Kurs: %s"),
    "gamblecraft_sportsbook.screen.potential": ("Win: %s chips", "Wygrana: %s żetonów"),
    "gamblecraft_sportsbook.screen.limits": ("Stake %s–%s chips", "Stawka %s–%s żetonów"),
    "gamblecraft_sportsbook.screen.finished": ("FT %s", "Koniec %s"),
    "gamblecraft_sportsbook.screen.postponed": ("Postponed", "Przełożony"),
    "gamblecraft_sportsbook.screen.cancelled": ("Cancelled", "Odwołany"),
    "gamblecraft_sportsbook.screen.odds_model": ("GambleCraft's own price, from team form", "Kurs własny GambleCraft (z formy drużyn)"),
    "gamblecraft_sportsbook.screen.odds_bookmaker": ("%s price", "Kurs %s"),
    "gamblecraft_sportsbook.screen.no_leagues": ("No leagues are switched on.", "Żadna liga nie jest włączona."),
    "gamblecraft_sportsbook.screen.status.waiting": ("Waiting for the feed…", "Czekam na dane…"),
    "gamblecraft_sportsbook.screen.status.ok": ("%s · %s games", "%s · meczów: %s"),
    "gamblecraft_sportsbook.screen.status.no_source": ("No source is switched on for this league.", "Dla tej ligi nie włączono żadnego źródła."),
    "gamblecraft_sportsbook.screen.status.error": ("%s is not answering: %s", "%s nie odpowiada: %s"),
    "gamblecraft_sportsbook.sources.title": ("Data sources", "Źródła danych"),
    "gamblecraft_sportsbook.sources.feeds": ("Feeds", "Serwisy"),
    "gamblecraft_sportsbook.sources.leagues": ("Leagues", "Ligi"),
    "gamblecraft_sportsbook.sources.off": ("off", "wyłączone"),
    "gamblecraft_sportsbook.sources.loading": ("loading…", "wczytuję…"),
    "gamblecraft_sportsbook.sources.in_use": ("in use: %s", "używane: %s"),
    "gamblecraft_sportsbook.sources.failing": ("not answering", "nie odpowiada"),
    "gamblecraft_sportsbook.sources.standby": ("standby", "w rezerwie"),
    "gamblecraft_sportsbook.sources.source.espn": ("Fixtures, live scores, DraftKings prices", "Terminarz, wyniki na żywo, kursy DraftKings"),
    "gamblecraft_sportsbook.sources.source.espn.tooltip": ("ESPN's public scoreboards: football, NBA, NHL, tennis and UFC. No account needed.",
                                                           "Publiczne tabele wyników ESPN: piłka, NBA, NHL, tenis i UFC. Bez konta i klucza."),
    "gamblecraft_sportsbook.sources.source.nhl": ("The NHL's official API", "Oficjalne API NHL"),
    "gamblecraft_sportsbook.sources.source.nhl.tooltip": ("api-web.nhle.com: NHL fixtures and results, the backup for ESPN.",
                                                          "api-web.nhle.com: terminarz i wyniki NHL, zapas dla ESPN."),
    "gamblecraft_sportsbook.sources.source.openligadb": ("Bundesliga, a community database", "Bundesliga, baza społeczności"),
    "gamblecraft_sportsbook.sources.source.openligadb.tooltip": ("api.openligadb.de: Bundesliga fixtures and results, the backup for ESPN.",
                                                                 "api.openligadb.de: terminarz i wyniki Bundesligi, zapas dla ESPN."),
    "gamblecraft_sportsbook.sources.source.thesportsdb": ("Ekstraklasa; backup for the rest", "Ekstraklasa; zapas dla pozostałych"),
    "gamblecraft_sportsbook.sources.source.thesportsdb.tooltip": (
        "TheSportsDB's free tier (its public key): the only source of the Ekstraklasa, and a backup for football, NBA and NHL.",
        "Darmowy pakiet TheSportsDB (publiczny klucz): jedyne źródło Ekstraklasy i zapas dla piłki, NBA i NHL."),
    "gamblecraft_sportsbook.sources.draftkings": ("DraftKings prices", "Kursy DraftKings"),
    "gamblecraft_sportsbook.sources.draftkings.desc": ("Off: every game priced by GambleCraft", "Wyłączone: wszystkie kursy liczy GambleCraft"),
    "gamblecraft_sportsbook.sources.draftkings.tooltip": (
        "Use the bookmaker's football prices that ESPN shows. Games without them are priced from the teams' form.",
        "Bierze kursy bukmachera na piłkę, które pokazuje ESPN. Mecze bez nich wycenia GambleCraft z formy drużyn."),
    "gamblecraft_sportsbook.sources.league_ok": ("%s · %s", "%s · %s"),
    "gamblecraft_sportsbook.sources.league_error": ("%s: error", "%s: błąd"),
    "gamblecraft_sportsbook.sources.note": ("Switching a feed off does not stop coupons bought from it being settled.",
                                            "Wyłączenie źródła nie wstrzymuje rozliczania kuponów już z niego kupionych."),
    "gamblecraft_sportsbook.sources.no_permission": ("Only a server operator can change the sources.",
                                                     "Źródła może zmieniać tylko operator serwera."),
    "gamblecraft_sportsbook.terminal.header": ("BETS · LIVE", "ZAKŁADY · LIVE"),
    "gamblecraft_sportsbook.terminal.no_games": ("Waiting for games…", "Czekam na mecze…"),
    "gamblecraft_sportsbook.command.refreshing": ("Reading the feeds again…", "Odświeżam dane…"),
    "gamblecraft_sportsbook.command.unknown_switch": ("Unknown switch: %s", "Nieznany przełącznik: %s"),
}


def lang():
    en, pl = {}, {}
    for key, (e, p) in LANG.items():
        en[key] = e
        pl[key] = p
    for league, (e, es, p, ps) in LEAGUES.items():
        en[f"gamblecraft_sportsbook.league.{league}"] = e
        en[f"gamblecraft_sportsbook.league.{league}.short"] = es
        pl[f"gamblecraft_sportsbook.league.{league}"] = p
        pl[f"gamblecraft_sportsbook.league.{league}.short"] = ps
    write_json(en, ASSETS, "lang", "en_us.json")
    write_json(pl, ASSETS, "lang", "pl_pl.json")


def betting_shop(village, style):
    """7x6x7 betting shop: door to the street (west), a betting terminal facing it, benches and a lamp."""
    t = gen_structures.Template(7, 6, 7)
    for x in range(7):
        for z in range(7):
            t.set(x, 0, z, style["floor"])
            t.set(x, 4, z, style["roof"])
    for y in (1, 2, 3):
        for i in range(7):
            for x, z in ((i, 0), (i, 6), (0, i), (6, i)):
                corner = x in (0, 6) and z in (0, 6)
                t.set(x, y, z, style["post"] if corner else style["wall"], gen_structures.post_props(style["post"]) if corner else None)
    for y in (1, 2):
        t.set(0, y, 3, "minecraft:air")
    for z in (1, 2, 4, 5):
        t.set(0, 2, z, "minecraft:glass_pane", gen_structures.PANE_NS)
    for x in range(1, 6):
        for z in range(1, 6):
            t.set(x, 1, z, "minecraft:green_carpet")
    t.set(5, 1, 3, f"{NS}:betting_terminal", {"facing": "west", "half": "lower"})
    t.set(5, 2, 3, f"{NS}:betting_terminal", {"facing": "west", "half": "upper"})
    for z in (1, 5):
        t.set(2, 1, z, "minecraft:spruce_stairs", {"facing": "east", "half": "bottom", "shape": "straight", "waterlogged": "false"})
    t.set(5, 1, 1, "minecraft:barrel", {"facing": "up", "open": "false"})
    t.set(3, 3, 3, "minecraft:lantern", {"hanging": "true", "waterlogged": "false"})
    t.set(0, 3, 3, "minecraft:lime_wool")
    gen_structures.entrance(t, 0, 3, village, style)
    t.save("village", f"betting_shop_{village}.nbt")


def structures():
    gen_structures.OUT = os.path.join(DATA, "structure")
    for village, style in gen_structures.VILLAGE_STYLES.items():
        betting_shop(village, style)


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "finish":
        finish()
        print("sportsbook model finished")
    else:
        block_textures()
        item_textures()
        icon_font()
        models()
        data()
        spec()
        lang()
        structures()
        print("sportsbook assets written")

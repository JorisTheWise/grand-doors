"""The names of the doors in the languages other than English.

Each language gives a word for every material, a function that builds a door's name the way
that language orders it, the tooltip lines and the titles of the creative tabs. gen_assets.py
writes one language file for each. nl_be (Vlaams) uses the Dutch names.

A door's kind is "door", "gate" (the iron gate of bars), "glazed" or "tinted" (wood with
clear or tinted glass), or one of the house-door looks: "boarded", "windowed", "lattice".
"""

MATERIALS = ("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak",
             "bamboo", "crimson", "warped", "cobblestone", "stone_brick", "cobbled_deepslate", "deepslate_brick", "iron",
             "copper", "exposed_copper", "weathered_copper", "oxidized_copper",
             "waxed_copper", "waxed_exposed_copper", "waxed_weathered_copper", "waxed_oxidized_copper")
TABS = ("wood", "arched_wood", "stone", "iron", "copper", "glazed", "tinted", "styles")


def words(*names):
    return dict(zip(MATERIALS, names, strict=True))


def tabs(*titles):
    return dict(zip(TABS, ("Grand Doors: " + title for title in titles), strict=True))


def dutch(material, kind, split, arched):
    # "de deur" and "het hek" take different endings.
    noun = {"door": "deur", "gate": "hek", "glazed": "glasdeur", "tinted": "rookglasdeur",
            "boarded": "plankendeur", "windowed": "vensterdeur", "lattice": "roededeur"}[kind]
    double = ("dubbel " if kind == "gate" else "dubbele ") if split else ""
    return f"{material} {double}{'boog' if arched else ''}{noun}"


def german(material, kind, split, arched):
    # "die Tür", "das Tor".
    noun = {"door": "tür", "gate": "tor", "glazed": "glastür", "tinted": "rauchglastür",
            "boarded": "brettertür", "windowed": "fenstertür", "lattice": "sprossentür"}[kind]
    name = f"{material}-Bogen{noun}" if arched else f"{material}{noun}"
    return f"{('Doppeltes ' if kind == 'gate' else 'Doppelte ') if split else ''}{name}"


def french(material, kind, split, arched):
    noun = {"door": "Porte", "gate": "Grille", "glazed": "Porte vitrée", "tinted": "Porte vitrée teintée",
            "boarded": "Porte à planches", "windowed": "Porte à fenêtre", "lattice": "Porte à petits carreaux"}[kind]
    return f"{noun}{' double' if split else ''}{' cintrée' if arched else ''} {material}"


def spanish(material, kind, split, arched):
    noun = {"door": "Puerta", "gate": "Reja", "glazed": "Puerta acristalada", "tinted": "Puerta de vidrio tintado",
            "boarded": "Puerta de tablas", "windowed": "Puerta con ventana", "lattice": "Puerta de cuarterones"}[kind]
    return f"{noun}{' doble' if split else ''}{' arqueada' if arched else ''} {material}"


DUTCH = {
    "materials": words("Eiken", "Sparren", "Berken", "Oerwoudhouten", "Acacia", "Donkere eiken", "Mangrove", "Kersen",
                       "Bleke eiken", "Bamboe", "Karmozijnen", "Verwrongen", "Keistenen", "Steenblokken",
                       "Diepleisteenkeien", "Diepleisteenblokken", "IJzeren",
                       "Koperen", "Aangetaste koperen", "Verweerde koperen", "Geoxideerde koperen",
                       "Gewaxte koperen", "Gewaxte aangetaste koperen", "Gewaxte verweerde koperen",
                       "Gewaxte geoxideerde koperen"),
    "name": dutch,
    "size": "%s breed, %s hoog, %s",
    "one_leaf": "één vleugel",
    "two_leaves": "twee vleugels die in het midden opengaan",
    "sneak": "Sluip tijdens het plaatsen: draait naar je toe",
    "tabs": tabs("hout", "hout met boog", "steen", "ijzer", "koper", "met glas", "met rookglas", "huisdeuren"),
}

LANGUAGES = {
    "nl_nl": DUTCH,
    "nl_be": DUTCH,
    "de_de": {
        "materials": words("Eichenholz", "Fichtenholz", "Birkenholz", "Tropenholz", "Akazienholz", "Schwarzeichenholz",
                           "Mangrovenholz", "Kirschholz", "Blasseichenholz", "Bambus", "Karmesin", "Wirr",
                           "Bruchstein", "Steinziegel", "Bruchtiefenschiefer", "Tiefenschieferziegel", "Eisen",
                           "Kupfer", "Angelaufene Kupfer", "Verwitterte Kupfer", "Oxidierte Kupfer",
                           "Gewachste Kupfer", "Gewachste angelaufene Kupfer", "Gewachste verwitterte Kupfer",
                           "Gewachste oxidierte Kupfer"),
        "name": german,
        "size": "%s breit, %s hoch, %s",
        "one_leaf": "ein Flügel",
        "two_leaves": "zwei Flügel, die sich in der Mitte öffnen",
        "sneak": "Beim Platzieren schleichen: öffnet sich zu dir hin",
        "tabs": tabs("Holz", "Holz mit Bogen", "Stein", "Eisen", "Kupfer", "mit Glas", "mit Rauchglas", "Haustüren"),
    },
    "fr_fr": {
        "materials": words("en chêne", "en sapin", "en bouleau", "en acajou", "en acacia", "en chêne noir",
                           "en palétuvier", "en cerisier", "en chêne pâle", "en bambou", "en bois carmin",
                           "en bois biscornu", "en pierres", "en pierre taillée", "en ardoise des abîmes",
                           "en briques d'ardoise des abîmes", "en fer",
                           "en cuivre", "en cuivre exposé", "en cuivre érodé", "en cuivre oxydé",
                           "en cuivre ciré", "en cuivre exposé ciré", "en cuivre érodé ciré", "en cuivre oxydé ciré"),
        "name": french,
        "size": "%s de large, %s de haut, %s",
        "one_leaf": "un vantail",
        "two_leaves": "deux vantaux qui s'ouvrent au milieu",
        "sneak": "Accroupi en posant : s'ouvre vers vous",
        "tabs": tabs("bois", "bois cintré", "pierre", "fer", "cuivre", "vitrées", "verre teinté", "portes de maison"),
    },
    "es_es": {
        "materials": words("de roble", "de abeto", "de abedul", "de jungla", "de acacia", "de roble oscuro",
                           "de mangle", "de cerezo", "de roble pálido", "de bambú", "carmesí", "distorsionada",
                           "de adoquín", "de ladrillos de piedra", "de pizarra profunda", "de ladrillos de pizarra profunda",
                           "de hierro",
                           "de cobre", "de cobre expuesto", "de cobre desgastado", "de cobre oxidado",
                           "de cobre encerado", "de cobre expuesto encerado", "de cobre desgastado encerado",
                           "de cobre oxidado encerado"),
        "name": spanish,
        "size": "%s de ancho, %s de alto, %s",
        "one_leaf": "una hoja",
        "two_leaves": "dos hojas que se abren por el centro",
        "sneak": "Agáchate al colocarla: se abre hacia ti",
        "tabs": tabs("madera", "madera con arco", "piedra", "hierro", "cobre", "acristaladas", "vidrio tintado", "puertas de casa"),
    },
}


def language_file(mod, language, door_rows):
    """door_rows: (block name, material word key, kind, split?, named arched?, width, height)."""
    out = {
        f"tooltip.{mod}.size": language["size"],
        f"tooltip.{mod}.one_leaf": language["one_leaf"],
        f"tooltip.{mod}.two_leaves": language["two_leaves"],
        f"tooltip.{mod}.sneak": language["sneak"],
    }
    for tab, title in language["tabs"].items():
        out[f"itemGroup.{mod}.{tab}"] = title
    for name, material, kind, split, arched, width, height in door_rows:
        out[f"block.{mod}.{name}"] = f"{language['name'](language['materials'][material], kind, split, arched)} ({width}x{height})"
    return out

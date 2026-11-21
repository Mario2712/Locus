#!/usr/bin/env python3
"""
Erzeugt die Länder- und Regions-Stammdaten für Locus als Kotlin-Quelldateien.

Quellen: CLDR (via Babel) für deutsche Ländernamen, pycountry für ISO-Codes und
Subdivisions, pycountry-convert für die Kontinentzuordnung.

Aufruf:
    python3 -m venv .venv && .venv/bin/pip install babel pycountry pycountry-convert
    .venv/bin/python tools/generate_reference_data.py
"""
from pathlib import Path

import pycountry
import pycountry_convert as pcc
from babel import Locale

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "core/domain/src/main/kotlin/io/github/mario2712/locus/domain/reference"

# ISO-3166-1-Einträge, die keine UN-Mitgliedstaaten sind (abhängige Gebiete, Beobachter …).
NON_UN = {
    "AS", "AI", "AQ", "AW", "AX", "BL", "BM", "BQ", "BV", "CC", "CK", "CW", "CX", "EH",
    "FK", "FO", "GF", "GG", "GI", "GL", "GP", "GS", "GU", "HK", "HM", "IM", "IO", "JE",
    "KY", "MF", "MO", "MP", "MQ", "MS", "NC", "NF", "NU", "PF", "PM", "PN", "PR", "PS",
    "RE", "SH", "SJ", "SX", "TC", "TF", "TK", "TW", "UM", "VA", "VG", "VI", "WF", "YT",
}
# Zusätzlich zu den 193 UN-Staaten gezählt (Abstimmung v0.3).
EXTRA = {"XK": "XKX", "VA": "VAT", "TW": "TWN"}

CONTINENT_OVERRIDES = {"XK": "EU", "VA": "EU", "TL": "AS", "CY": "EU"}
CONTINENT_ENUM = {
    "EU": "EUROPE", "AS": "ASIA", "AF": "AFRICA",
    "NA": "NORTH_AMERICA", "SA": "SOUTH_AMERICA", "OC": "OCEANIA",
}

# Länder mit vorbereiteten Regionen (Bundesländer).
REGION_COUNTRIES = ["AT", "DE"]

HEADER = "// GENERIERT von tools/generate_reference_data.py – nicht manuell bearbeiten.\n"


def kstr(s: str) -> str:
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def main() -> None:
    de = Locale("de")
    rows = []
    un = [c for c in pycountry.countries if c.alpha_2 not in NON_UN]
    assert len(un) == 193, f"Erwartet 193 UN-Staaten, gefunden {len(un)}"
    codes = {c.alpha_2: c.alpha_3 for c in un} | EXTRA
    for iso2, iso3 in codes.items():
        name = de.territories[iso2]
        cont = CONTINENT_OVERRIDES.get(iso2) or pcc.country_alpha2_to_continent_code(iso2)
        rows.append((name, iso2, iso3, CONTINENT_ENUM[cont]))
    rows.sort(key=lambda r: Locale("de").territories[r[1]].lower()
              .replace("ä", "a").replace("ö", "o").replace("ü", "u"))

    OUT.mkdir(parents=True, exist_ok=True)
    lines = [HEADER, "package io.github.mario2712.locus.domain.reference\n",
             "import io.github.mario2712.locus.domain.model.Continent",
             "import io.github.mario2712.locus.domain.model.Country\n",
             f"/** {len(rows)} zählbare Länder: 193 UN-Mitgliedstaaten plus Kosovo, Vatikan und Taiwan. */",
             "internal val COUNTRY_DATA: List<Country> = listOf("]
    for name, iso2, iso3, cont in rows:
        lines.append(f"    Country({kstr(iso2)}, {kstr(iso3)}, {kstr(name)}, Continent.{cont}),")
    lines.append(")\n")
    (OUT / "CountryData.kt").write_text("\n".join(lines), encoding="utf-8")

    rlines = [HEADER, "package io.github.mario2712.locus.domain.reference\n",
              "import io.github.mario2712.locus.domain.model.Region\n",
              "internal val REGION_DATA: List<Region> = listOf("]
    for cc in REGION_COUNTRIES:
        subs = sorted(pycountry.subdivisions.get(country_code=cc), key=lambda s: s.name)
        for s in subs:
            rlines.append(f"    Region({kstr(s.code)}, {kstr(cc)}, {kstr(s.name)}),")
    rlines.append(")\n")
    (OUT / "RegionData.kt").write_text("\n".join(rlines), encoding="utf-8")
    print(f"{len(rows)} Länder, Regionen für {', '.join(REGION_COUNTRIES)} geschrieben nach {OUT}")


if __name__ == "__main__":
    main()

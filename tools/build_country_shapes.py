#!/usr/bin/env python3
"""
Erzeugt app/src/main/assets/countries.geojson aus Natural Earth (gemeinfrei).

Quelle: https://github.com/nvkelso/natural-earth-vector  (geojson/ne_50m_admin_0_countries.geojson)
Aufruf:
    .venv/bin/pip install shapely
    .venv/bin/python tools/build_country_shapes.py /pfad/zu/ne_50m_admin_0_countries.geojson

Je Land bleibt nur die Eigenschaft "iso" (ISO-3166-1 alpha-2; Kosovo = XK). Geometrien werden
vereinfacht und auf 3 Nachkommastellen gerundet, damit die Datei klein bleibt.
"""
import json
import sys
from pathlib import Path

from shapely.geometry import mapping, shape

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/countries.geojson"
TOLERANCE = 0.02  # Grad; sichtbar fein genug für die Weltkarte, deutlich kleinere Datei


def iso_of(props: dict) -> str | None:
    for key in ("ISO_A2_EH", "ISO_A2", "WB_A2"):
        value = props.get(key)
        if value and value != "-99" and len(value) == 2:
            return value.upper()
    return None


def rounded(coords):
    if isinstance(coords, (list, tuple)) and coords and isinstance(coords[0], (int, float)):
        return [round(coords[0], 3), round(coords[1], 3)]
    return [rounded(c) for c in coords]


def main(src: str) -> None:
    data = json.loads(Path(src).read_text(encoding="utf-8"))
    features = []
    skipped = []
    for feature in data["features"]:
        props = feature["properties"]
        iso = iso_of(props)
        if iso is None:
            skipped.append(props.get("NAME"))
            continue
        geom = shape(feature["geometry"]).simplify(TOLERANCE, preserve_topology=True)
        if geom.is_empty:
            continue
        g = mapping(geom)
        features.append({"type": "Feature", "properties": {"iso": iso},
                         "geometry": {"type": g["type"], "coordinates": rounded(g["coordinates"])}})
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"type": "FeatureCollection", "features": features}, separators=(",", ":")), encoding="utf-8")
    print(f"{len(features)} Länder/Gebiete → {OUT} ({OUT.stat().st_size // 1024} KB); ohne ISO übersprungen: {skipped}")


if __name__ == "__main__":
    main(sys.argv[1])

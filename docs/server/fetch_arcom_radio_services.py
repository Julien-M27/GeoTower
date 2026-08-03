#!/usr/bin/env python3
"""Fetch ARCOM Ma Radio FM/DAB+ emitter data for GeoTower radio DB enrichment.

The generated JSON is optional input for fr_radio_db_builder.py. Keeping this
as a separate cache step prevents the SQLite build from depending on network
availability and makes the final database compact.
"""

from __future__ import annotations

import argparse
import base64
import html
import json
import re
import time
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urljoin


BASE_URL = "https://www.arcom.fr"
DEFAULT_RADIOS_URL = f"{BASE_URL}/radio-et-audio-numerique/radio-fm-dab/radios"
RADIO_PATH_PREFIX = "/radio-et-audio-numerique/radio-fm-dab/radios/"
USER_AGENT = "GeoTower radio DB builder (+https://signalquest.fr)"

ROW_RE = re.compile(
    r"<tr[^>]*>\s*"
    r"<td[^>]*>(?P<mode>.*?)</td>\s*"
    r"<td[^>]*>(?P<frequency>.*?)</td>\s*"
    r"<td[^>]*>(?P<service>.*?)</td>\s*"
    r"<td[^>]*>\s*<a[^>]*data-action-url=\"(?P<action>[^\"]+)\"[^>]*>(?P<site>.*?)</a>",
    re.IGNORECASE | re.DOTALL,
)


@dataclass(frozen=True)
class RadioLink:
    href: str
    label: str


class RadioListParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.links: list[RadioLink] = []
        self._href: str | None = None
        self._text: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag != "a":
            return
        attrs_dict = {key: value for key, value in attrs}
        href = attrs_dict.get("href")
        if href and href.startswith(RADIO_PATH_PREFIX):
            self._href = href
            self._text = []

    def handle_data(self, data: str) -> None:
        if self._href:
            self._text.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag != "a" or not self._href:
            return
        label = clean(" ".join(self._text))
        if label:
            self.links.append(RadioLink(self._href, label))
        self._href = None
        self._text = []


class TextParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []
        self._skip_depth = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in {"script", "style", "svg"}:
            self._skip_depth += 1

    def handle_endtag(self, tag: str) -> None:
        if tag in {"script", "style", "svg"} and self._skip_depth:
            self._skip_depth -= 1

    def handle_data(self, data: str) -> None:
        if self._skip_depth:
            return
        text = clean(data)
        if text:
            self.parts.append(text)


def clean(value: object) -> str:
    return html.unescape(str(value)).replace("\xa0", " ").strip()


def strip_tags(value: str) -> str:
    return clean(re.sub(r"<[^>]+>", " ", value))


def fetch_url(url: str, timeout: int) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read().decode("utf-8", errors="replace")


def decode_action_url(value: str) -> str | None:
    try:
        padded = value + "=" * (-len(value) % 4)
        decoded = base64.b64decode(padded).decode("utf-8")
        return decoded if decoded.startswith("/") else f"/{decoded}"
    except Exception:
        return None


def frequency_to_khz(value: str) -> int | None:
    match = re.search(r"([0-9]+(?:[,.][0-9]+)?)\s*(GHz|MHz|kHz|Hz)?", clean(value), re.IGNORECASE)
    if not match:
        return None
    number = float(match.group(1).replace(",", "."))
    unit = (match.group(2) or "MHz").upper()
    if unit.startswith("G"):
        return int(round(number * 1_000_000))
    if unit.startswith("M"):
        return int(round(number * 1_000))
    if unit.startswith("K"):
        return int(round(number))
    return int(round(number / 1_000))


def dms_to_e6(value: str) -> int | None:
    direction_match = re.search(r"\b([NSEW])\b", value, re.IGNORECASE)
    direction = direction_match.group(1).upper() if direction_match else ""
    numbers = re.findall(r"[0-9]+(?:[,.][0-9]+)?", value)
    if not numbers:
        return None
    deg = float(numbers[0].replace(",", "."))
    minutes = float(numbers[1].replace(",", ".")) if len(numbers) > 1 else 0.0
    seconds = float(numbers[2].replace(",", ".")) if len(numbers) > 2 else 0.0
    decimal = deg + minutes / 60.0 + seconds / 3600.0
    if direction in {"S", "W"}:
        decimal = -decimal
    return int(round(decimal * 1_000_000))


def number_or_none(value: str) -> float | None:
    match = re.search(r"[0-9]+(?:[,.][0-9]+)?", clean(value))
    if not match:
        return None
    return float(match.group(0).replace(",", "."))


def extract_radio_links(page: str) -> list[RadioLink]:
    parser = RadioListParser()
    parser.feed(page)
    seen: set[str] = set()
    links: list[RadioLink] = []
    for link in parser.links:
        if link.href in seen:
            continue
        seen.add(link.href)
        links.append(link)
    return links


def extract_radio_rows(page: str) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for match in ROW_RE.finditer(page):
        action_path = decode_action_url(match.group("action"))
        if not action_path:
            continue
        rows.append(
            {
                "mode": strip_tags(match.group("mode")),
                "frequency_label": strip_tags(match.group("frequency")).replace("MHz", " MHz"),
                "service_name": re.sub(r"\s+\((?:locale|nationale)\)\s*$", "", strip_tags(match.group("service")), flags=re.IGNORECASE),
                "site_label": strip_tags(match.group("site")),
                "emitter_path": action_path,
            }
        )
    return rows


def text_lines(page: str) -> list[str]:
    parser = TextParser()
    parser.feed(page)
    return parser.parts


def value_after(lines: list[str], label: str) -> str:
    label_folded = label.casefold()
    for index, line in enumerate(lines[:-1]):
        if line.casefold() == label_folded:
            return lines[index + 1]
    return ""


def parse_emitter_detail(page: str) -> dict[str, object]:
    lines = text_lines(page)
    latitude_raw = value_after(lines, "Latitude")
    longitude_raw = value_after(lines, "Longitude")
    return {
        "emitter_name": value_after(lines, "Nom de l'émetteur"),
        "mode": value_after(lines, "Type d'émetteur"),
        "frequency_label": value_after(lines, "Fréquence"),
        "service_name": value_after(lines, "Service de radio"),
        "category": value_after(lines, "Catégorie"),
        "holder": value_after(lines, "Titulaire"),
        "committee": value_after(lines, "Comité technique"),
        "agreement_decision": value_after(lines, "Décision d'agrément du site"),
        "city": value_after(lines, "Commune d'implantation de l'émetteur"),
        "address": value_after(lines, "Adresse"),
        "department": value_after(lines, "Département"),
        "longitude_e6": dms_to_e6(longitude_raw),
        "latitude_e6": dms_to_e6(latitude_raw),
        "antenna_height_m": number_or_none(value_after(lines, "Hauteur d'antenne")),
        "antenna_top_altitude_m": number_or_none(value_after(lines, "Altitude au sommet des antennes")),
        "par_max_w": number_or_none(value_after(lines, "PAR max (Puissance apparente rayonnée)")),
    }


def build_entry(row: dict[str, str], detail: dict[str, object]) -> dict[str, object]:
    frequency_label = clean(detail.get("frequency_label") or row["frequency_label"])
    mode = clean(detail.get("mode") or row["mode"])
    service_name = clean(detail.get("service_name") or row["service_name"])
    return {
        "service_name": service_name,
        "mode": mode,
        "frequency_label": frequency_label,
        "frequency_khz": frequency_to_khz(frequency_label),
        "category": clean(detail.get("category") or ""),
        "holder": clean(detail.get("holder") or ""),
        "site_label": clean(detail.get("emitter_name") or row["site_label"]),
        "city": clean(detail.get("city") or ""),
        "address": clean(detail.get("address") or ""),
        "department": clean(detail.get("department") or ""),
        "latitude_e6": detail.get("latitude_e6"),
        "longitude_e6": detail.get("longitude_e6"),
        "antenna_height_m": detail.get("antenna_height_m"),
        "antenna_top_altitude_m": detail.get("antenna_top_altitude_m"),
        "par_max_w": detail.get("par_max_w"),
        "emitter_path": row["emitter_path"],
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--radios-url", default=DEFAULT_RADIOS_URL)
    parser.add_argument("--output", type=Path, default=Path("arcom_radio_services.json"))
    parser.add_argument("--timeout", type=int, default=60)
    parser.add_argument("--delay", type=float, default=0.05)
    parser.add_argument("--limit-radios", type=int, default=0)
    parser.add_argument("--limit-emitters", type=int, default=0)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    radios_page = fetch_url(args.radios_url, args.timeout)
    radio_links = extract_radio_links(radios_page)
    if args.limit_radios > 0:
        radio_links = radio_links[: args.limit_radios]

    entries: list[dict[str, object]] = []
    seen_emitters: set[str] = set()
    for radio_index, link in enumerate(radio_links, start=1):
        print(f"[{radio_index}/{len(radio_links)}] {link.label}", flush=True)
        radio_page = fetch_url(urljoin(BASE_URL, link.href), args.timeout)
        time.sleep(args.delay)
        for row in extract_radio_rows(radio_page):
            emitter_path = row["emitter_path"]
            if emitter_path in seen_emitters:
                continue
            if args.limit_emitters > 0 and len(entries) >= args.limit_emitters:
                break
            seen_emitters.add(emitter_path)
            detail_page = fetch_url(urljoin(BASE_URL, emitter_path), args.timeout)
            time.sleep(args.delay)
            entries.append(build_entry(row, parse_emitter_detail(detail_page)))
        if args.limit_emitters > 0 and len(entries) >= args.limit_emitters:
            break

    payload = {
        "source": "ARCOM_MA_RADIO",
        "source_url": args.radios_url,
        "fetched_at": datetime.now(timezone.utc).isoformat(),
        "entry_count": len(entries),
        "entries": entries,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {len(entries)} entries to {args.output}", flush=True)


if __name__ == "__main__":
    main()

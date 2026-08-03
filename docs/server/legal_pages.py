"""Pages legales publiques (politique de confidentialite et conditions d'utilisation).

Google Play exige une URL publiquement accessible pour la politique de confidentialite : elle est
saisie dans « Contenu de l'application » et dans la fiche du Store, et un examinateur doit pouvoir
l'ouvrir sans compte ni JavaScript. Le texte servi ici est exactement celui affiche dans
l'application (ecran « Conditions d'utilisation »), pour qu'aucune divergence ne puisse apparaitre
entre ce que lit l'utilisateur et ce que lit Google.

Source des textes : les fichiers de l'application `app/src/main/res/raw*/terms.txt`, recopies dans
`legal/terms_<lang>.txt`. A resynchroniser a chaque modification des conditions cote application.
"""

import html
import os
import re
from typing import Dict, List, Optional

from fastapi import APIRouter, Query, Request
from fastapi.responses import HTMLResponse, PlainTextResponse

router = APIRouter()

# Le repertoire est surchargeable pour le deploiement (les autres chemins de main.py sont sous
# /opt/geotower/api) ; par defaut on prend le dossier `legal/` a cote de ce module.
LEGAL_DIR = os.environ.get(
    "GEOTOWER_LEGAL_DIR",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "legal"),
)

DEFAULT_LANG = "fr"

# Libelles par langue : titre de page et intitule du lien dans le selecteur.
LANGUAGES: Dict[str, Dict[str, str]] = {
    "fr": {"label": "Français", "title": "Confidentialité et conditions d'utilisation"},
    "en": {"label": "English", "title": "Privacy and terms of use"},
    "es": {"label": "Español", "title": "Privacidad y condiciones de uso"},
    "de": {"label": "Deutsch", "title": "Datenschutz und Nutzungsbedingungen"},
    "it": {"label": "Italiano", "title": "Privacy e condizioni d'uso"},
    "pt": {"label": "Português", "title": "Privacidade e condições de utilização"},
}

BOLD_PATTERN = re.compile(r"\*\*(.+?)\*\*")
ORDERED_ITEM_PATTERN = re.compile(r"^\d+\.\s+(.*)$")


def _resolve_lang(requested: Optional[str], accept_language: Optional[str]) -> str:
    """Langue explicite, sinon negociation Accept-Language, sinon francais."""
    if requested:
        normalized = requested.strip().lower().replace("_", "-").split("-")[0]
        if normalized in LANGUAGES:
            return normalized

    for chunk in (accept_language or "").split(","):
        tag = chunk.split(";")[0].strip().lower().replace("_", "-").split("-")[0]
        if tag in LANGUAGES:
            return tag

    return DEFAULT_LANG


def _read_terms(lang: str) -> Optional[str]:
    path = os.path.join(LEGAL_DIR, f"terms_{lang}.txt")
    if not os.path.exists(path):
        return None
    with open(path, "r", encoding="utf-8") as handle:
        return handle.read()


def _inline(text: str) -> str:
    """Echappe le HTML PUIS applique le gras : l'inverse laisserait passer des balises."""
    escaped = html.escape(text)
    return BOLD_PATTERN.sub(r"<strong>\1</strong>", escaped)


def _render_body(raw: str) -> str:
    """Convertit le balisage des conditions (~, #, ##, **, -, 1., >) en HTML."""
    out: List[str] = []
    list_tag: Optional[str] = None

    def close_list() -> None:
        nonlocal list_tag
        if list_tag:
            out.append(f"</{list_tag}>")
            list_tag = None

    def open_list(tag: str) -> None:
        nonlocal list_tag
        if list_tag != tag:
            close_list()
            out.append(f"<{tag}>")
            list_tag = tag

    for line in raw.splitlines():
        stripped = line.strip()

        if not stripped:
            close_list()
            continue

        if stripped.startswith("~ "):
            close_list()
            out.append(f'<p class="updated">{_inline(stripped[2:])}</p>')
            continue

        if stripped.startswith("## "):
            close_list()
            out.append(f"<h3>{_inline(stripped[3:])}</h3>")
            continue

        if stripped.startswith("# "):
            close_list()
            out.append(f"<h2>{_inline(stripped[2:])}</h2>")
            continue

        if stripped.startswith("> "):
            close_list()
            out.append(f"<blockquote>{_inline(stripped[2:])}</blockquote>")
            continue

        if stripped.startswith("- "):
            open_list("ul")
            out.append(f"<li>{_inline(stripped[2:])}</li>")
            continue

        ordered = ORDERED_ITEM_PATTERN.match(stripped)
        if ordered:
            open_list("ol")
            out.append(f"<li>{_inline(ordered.group(1))}</li>")
            continue

        close_list()
        out.append(f"<p>{_inline(stripped)}</p>")

    close_list()
    return "\n".join(out)


def _render_page(lang: str, body: str) -> str:
    meta = LANGUAGES[lang]
    switcher = " · ".join(
        f'<a href="/confidentialite?lang={code}">{html.escape(info["label"])}</a>'
        if code != lang
        else f'<span class="current">{html.escape(info["label"])}</span>'
        for code, info in LANGUAGES.items()
    )
    title = html.escape(meta["title"])

    return f"""<!DOCTYPE html>
<html lang="{lang}">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>GeoTower — {title}</title>
    <link rel="icon" type="image/png" href="/favicon.png">
    <style>
        body {{
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background-color: #121212;
            color: #e0e0e0;
            margin: 0;
            padding: 24px 16px 64px;
            line-height: 1.65;
        }}
        .container {{
            background-color: #1e1e1e;
            max-width: 760px;
            margin: 0 auto;
            padding: 40px 32px;
            border-radius: 16px;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.8);
        }}
        h1 {{ color: #4CAF50; font-size: 2em; margin: 0 0 4px; }}
        h2 {{ color: #4CAF50; font-size: 1.3em; margin: 36px 0 8px; }}
        h3 {{ color: #8bc34a; font-size: 1.08em; margin: 24px 0 6px; }}
        p {{ margin: 10px 0; }}
        .updated {{ color: #9e9e9e; font-size: 0.92em; margin-bottom: 28px; }}
        strong {{ color: #ffffff; }}
        a {{ color: #4CAF50; }}
        ul, ol {{ margin: 10px 0; padding-left: 24px; }}
        li {{ margin: 4px 0; }}
        blockquote {{
            margin: 16px 0;
            padding: 12px 16px;
            border-left: 3px solid #4CAF50;
            background-color: #242424;
            color: #c7c7c7;
            border-radius: 0 8px 8px 0;
        }}
        .langs {{
            margin-top: 40px;
            padding-top: 20px;
            border-top: 1px solid #333;
            font-size: 0.92em;
            color: #9e9e9e;
        }}
        .langs .current {{ color: #ffffff; font-weight: bold; }}
        @media (max-width: 600px) {{
            .container {{ padding: 28px 20px; }}
            h1 {{ font-size: 1.6em; }}
        }}
    </style>
</head>
<body>
    <div class="container">
        <h1>GeoTower</h1>
        <p class="updated">{title}</p>
{body}
        <p class="langs">{switcher}</p>
    </div>
</body>
</html>
"""


@router.get("/confidentialite", response_class=HTMLResponse, include_in_schema=False)
@router.get("/privacy", response_class=HTMLResponse, include_in_schema=False)
async def privacy_policy(request: Request, lang: Optional[str] = Query(default=None)):
    resolved = _resolve_lang(lang, request.headers.get("accept-language"))
    raw = _read_terms(resolved)

    # Repli sur le francais : mieux vaut servir la politique dans une autre langue qu'un 404, qui
    # ferait echouer la verification du lien par Google Play.
    if raw is None and resolved != DEFAULT_LANG:
        resolved = DEFAULT_LANG
        raw = _read_terms(resolved)

    if raw is None:
        return PlainTextResponse(
            "Politique de confidentialite momentanement indisponible.",
            status_code=503,
        )

    return HTMLResponse(content=_render_page(resolved, _render_body(raw)))

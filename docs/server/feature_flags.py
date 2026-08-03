import copy
import json
import os
from typing import Any, Dict

from fastapi import HTTPException


FEATURE_FLAGS_PATH = os.environ.get("GEOTOWER_FEATURE_FLAGS_PATH", "/opt/geotower/data/app/features.json")
VALID_ANNOUNCEMENT_SEVERITIES = {"info", "warning", "error", "success"}

FEATURE_SIGNALQUEST_PHOTOS = "signalQuest.photos"
FEATURE_SIGNALQUEST_UPLOAD = "signalQuest.upload"
FEATURE_SIGNALQUEST_SPEEDTESTS = "signalQuest.speedtests"
FEATURE_SIGNALQUEST_COVERAGE = "signalQuest.coverage"
FEATURE_SIGNALQUEST_EXTERNAL_LINKS = "signalQuest.externalLinks"
FEATURE_CELLULARFR_PHOTOS = "cellularFr.photos"
FEATURE_CELLULARFR_EXTERNAL_LINKS = "cellularFr.externalLinks"
FEATURE_DATABASE_DOWNLOAD = "database.download"
FEATURE_DATABASE_UPDATE_CHECK = "database.updateCheck"
# Kill-switch propre a la base des identifiants eNB/gNB : la donnee vient d'un partenaire
# (eNB-Analytics), on doit pouvoir la couper sans toucher aux bases ANFR.
FEATURE_ENB_DATABASE = "enbDatabase.enabled"
FEATURE_APP_UPDATE_CHECK = "appUpdate.check"
FEATURE_LOCAL_MODE_ENABLED = "localMode.enabled"
# Kill-switch du mode simplifie (carte au lancement, tiroir lateral, fiches fusionnees).
FEATURE_SIMPLE_MODE_ENABLED = "simpleMode.enabled"
FEATURE_OUTAGES_DATA = "outages.data"
FEATURE_OFFLINE_MAPS_CATALOG = "offlineMaps.catalog"
FEATURE_OFFLINE_MAPS_DOWNLOAD = "offlineMaps.download"
FEATURE_LIVE_API_FR = "liveApi.fr"
FEATURE_LIVE_API_FR_NEARBY = "liveApi.fr.nearby"
FEATURE_LIVE_API_FR_BBOX = "liveApi.fr.bbox"
FEATURE_LIVE_API_FR_SEARCH = "liveApi.fr.search"
FEATURE_LIVE_API_FR_SITE_DETAIL = "liveApi.fr.siteDetail"
FEATURE_LIVE_API_FR_STATS = "liveApi.fr.stats"

DEFAULT_FEATURE_FLAGS: Dict[str, Any] = {
    "schemaVersion": 1,
    "cacheTtlSeconds": 3600,
    "screens": {
        "home": True,
        "nearby": True,
        "map": True,
        "compass": True,
        "stats": True,
        "settings": True,
        "help": True,
        "about": True,
        "photoUploadHistory": True,
        "supportDetail": True,
        "siteDetail": True,
        "siteSpeedtests": True,
        "elevationProfile": True,
        "throughputCalculator": True,
        "signalQuestUpload": True,
        "firstStart": True,
    },
    "menus": {
        "pagesCustomization": True,
        "communityDataSettings": True,
        "externalLinksSettings": True,
        "photoSettings": True,
        "shareSettings": True,
        "mapSettings": True,
        "siteSettings": True,
        "supportSettings": True,
        "statsSettings": True,
        "throughputSettings": True,
    },
    "features": {
        FEATURE_DATABASE_DOWNLOAD: True,
        FEATURE_DATABASE_UPDATE_CHECK: True,
        FEATURE_ENB_DATABASE: True,
        FEATURE_APP_UPDATE_CHECK: True,
        FEATURE_LOCAL_MODE_ENABLED: True,
        FEATURE_SIMPLE_MODE_ENABLED: True,
        FEATURE_OUTAGES_DATA: True,
        "outages.mapLayer": True,
        "outages.siteStatus": True,
        FEATURE_OFFLINE_MAPS_CATALOG: True,
        FEATURE_OFFLINE_MAPS_DOWNLOAD: True,
        FEATURE_LIVE_API_FR: True,
        FEATURE_LIVE_API_FR_NEARBY: True,
        FEATURE_LIVE_API_FR_BBOX: True,
        FEATURE_LIVE_API_FR_SEARCH: True,
        FEATURE_LIVE_API_FR_SITE_DETAIL: True,
        FEATURE_LIVE_API_FR_STATS: True,
        "map.search.nominatim": True,
        "map.cityBoundaries": True,
        "map.measure": True,
        "map.share": True,
        "map.azimuths": True,
        "map.location": True,
        "site.photos": True,
        "site.photoUpload": True,
        "site.photoCamera": True,
        "site.photoGallery": True,
        "site.photoExif": True,
        "site.schemes": True,
        "site.speedtests": True,
        "site.externalNavigation": True,
        "site.share": True,
        "site.frequencies": True,
        "site.spectrum": True,
        "site.elevationProfile": True,
        "site.throughputCalculator": True,
        "support.photos": True,
        "support.externalNavigation": True,
        "support.share": True,
        "stats.frequencyDetails": True,
        "stats.history": True,
        "compass.radar": True,
        "compass.reverseGeocoding": True,
        FEATURE_SIGNALQUEST_PHOTOS: True,
        FEATURE_SIGNALQUEST_UPLOAD: True,
        FEATURE_SIGNALQUEST_SPEEDTESTS: True,
        FEATURE_SIGNALQUEST_COVERAGE: True,
        FEATURE_SIGNALQUEST_EXTERNAL_LINKS: True,
        FEATURE_CELLULARFR_PHOTOS: False,
        FEATURE_CELLULARFR_EXTERNAL_LINKS: False,
        "externalLinks.cartoradio": True,
        "externalLinks.rncMobile": True,
        "externalLinks.enbAnalytics": True,
        "externalLinks.signalQuest": True,
        "externalLinks.cellularFr": False,
        "externalLinks.cellMapper": True,
        "externalLinks.anfr": True,
    },
    "actions": {
        "share.site": True,
        "share.support": True,
        "share.map": True,
        "externalNavigation.open": True,
        "externalLink.open": True,
        "databaseDownload.start": True,
        "offlineMapDownload.start": True,
        "signalQuestUpload.start": True,
    },
    "providers": {
        "map.ign": True,
        "map.osm": True,
        "map.mapLibre": True,
        "map.openTopo": True,
        "map.offline": True,
        "search.nominatim": True,
        "elevation.ign": True,
        "outages.geotower": True,
        "signalQuest": True,
        "cellularFr": False,
        "cellMapper": True,
        "cartoradio": True,
        "rncMobile": True,
        "enbAnalytics": True,
        "anfr": True,
    },
    "workers": {
        "databaseDownload": True,
        "databaseUpdateCheck": True,
        "appUpdateCheck": True,
        "offlineMapDownload": True,
        "signalQuestUpload": True,
        "widgetUpdate": True,
    },
    "platform": {
        "widgets": True,
        "androidAuto": True,
        "liveTracking": True,
        "notifications": True,
        "promotedNotifications": True,
        "backgroundLocationPrompt": True,
    },
    "secureScreens": {
        "map": False,
        "nearby": False,
        "compass": False,
        "stats": False,
        "siteDetail": False,
        "supportDetail": False,
        "siteSpeedtests": False,
        "elevationProfile": False,
        "theoreticalCoverage": False,
        "throughputCalculator": False,
    },
    "limits": {
        "nearbyMaxRadiusKm": 50,
        "photoUploadMaxCount": 20,
        "photoUploadMaxSizeMb": 10,
        "offlineMapMaxParallelDownloads": 1,
        "mapSearchMinQueryLength": 2,
        "liveApiNearbyMaxLimit": 200,
        "liveApiNearbyMaxRadiusKm": 50,
        "liveApiBboxMaxLimit": 1000,
        "liveApiBboxMaxDegrees": 3,
        "liveApiSearchMaxLimit": 100,
    },
    "homeAnnouncement": {
        "enabled": False,
        "id": "",
        "title": "",
        "message": "",
        "severity": "info",
        "actionLabel": "",
        "actionUrl": "",
        "dismissible": True,
        "minAppVersionInclusive": "",
        "maxAppVersionExclusive": "",
        "translations": {},
    },
}


def _merge_bool_map(base: Dict[str, bool], override: Any) -> Dict[str, bool]:
    merged = dict(base)
    if isinstance(override, dict):
        for key, value in override.items():
            if isinstance(key, str) and isinstance(value, bool):
                merged[key] = value
    return merged


def _merge_int_map(base: Dict[str, int], override: Any) -> Dict[str, int]:
    merged = dict(base)
    if isinstance(override, dict):
        for key, value in override.items():
            if isinstance(key, str) and isinstance(value, int):
                merged[key] = max(0, min(value, 100000))
    return merged


def _string_or_empty(data: Dict[str, Any], key: str, max_length: int) -> str:
    value = data.get(key)
    if not isinstance(value, str):
        return ""
    return value.strip()[:max_length]


def _language_key_or_empty(value: Any) -> str:
    if not isinstance(value, str):
        return ""
    normalized = value.strip().lower().replace("_", "-")[:20]
    if not normalized:
        return ""
    if not all(char.isalnum() or char == "-" for char in normalized):
        return ""
    return normalized


def _sanitize_announcement_translations(value: Any) -> Dict[str, Dict[str, str]]:
    translations: Dict[str, Dict[str, str]] = {}
    if not isinstance(value, dict):
        return translations

    for raw_language, raw_texts in value.items():
        language = _language_key_or_empty(raw_language)
        if not language or not isinstance(raw_texts, dict):
            continue

        texts = {
            "title": _string_or_empty(raw_texts, "title", 100),
            "message": _string_or_empty(raw_texts, "message", 1200),
            "actionLabel": _string_or_empty(raw_texts, "actionLabel", 48),
        }
        if texts["title"] or texts["message"] or texts["actionLabel"]:
            translations[language] = texts

    return translations


def _sanitize_home_announcement(value: Any) -> Dict[str, Any]:
    announcement = dict(DEFAULT_FEATURE_FLAGS["homeAnnouncement"])
    if not isinstance(value, dict):
        return announcement

    enabled = value.get("enabled")
    if isinstance(enabled, bool):
        announcement["enabled"] = enabled

    dismissible = value.get("dismissible")
    if isinstance(dismissible, bool):
        announcement["dismissible"] = dismissible

    announcement["id"] = _string_or_empty(value, "id", 80)
    announcement["title"] = _string_or_empty(value, "title", 100)
    announcement["message"] = _string_or_empty(value, "message", 1200)
    announcement["actionLabel"] = _string_or_empty(value, "actionLabel", 48)
    announcement["minAppVersionInclusive"] = _string_or_empty(value, "minAppVersionInclusive", 64)
    announcement["maxAppVersionExclusive"] = _string_or_empty(value, "maxAppVersionExclusive", 64)
    announcement["translations"] = _sanitize_announcement_translations(value.get("translations"))

    severity = _string_or_empty(value, "severity", 24).lower()
    if severity in VALID_ANNOUNCEMENT_SEVERITIES:
        announcement["severity"] = severity

    action_url = _string_or_empty(value, "actionUrl", 500)
    if action_url.startswith(("https://", "http://")):
        announcement["actionUrl"] = action_url

    has_translated_content = any(
        translated.get("title") or translated.get("message")
        for translated in announcement["translations"].values()
    )
    if announcement["enabled"] and not (announcement["title"] or announcement["message"] or has_translated_content):
        announcement["enabled"] = False

    return announcement


def get_feature_flags() -> Dict[str, Any]:
    flags = copy.deepcopy(DEFAULT_FEATURE_FLAGS)
    if not os.path.exists(FEATURE_FLAGS_PATH):
        return flags

    try:
        with open(FEATURE_FLAGS_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
    except Exception:
        return flags

    if not isinstance(data, dict):
        return flags

    cache_ttl = data.get("cacheTtlSeconds")
    if isinstance(cache_ttl, int) and cache_ttl > 0:
        flags["cacheTtlSeconds"] = min(cache_ttl, 24 * 60 * 60)

    flags["screens"] = _merge_bool_map(flags["screens"], data.get("screens"))
    flags["menus"] = _merge_bool_map(flags["menus"], data.get("menus"))
    flags["features"] = _merge_bool_map(flags["features"], data.get("features"))
    flags["actions"] = _merge_bool_map(flags["actions"], data.get("actions"))
    flags["providers"] = _merge_bool_map(flags["providers"], data.get("providers"))
    flags["workers"] = _merge_bool_map(flags["workers"], data.get("workers"))
    flags["platform"] = _merge_bool_map(flags["platform"], data.get("platform"))
    flags["secureScreens"] = _merge_bool_map(flags["secureScreens"], data.get("secureScreens"))
    flags["limits"] = _merge_int_map(flags["limits"], data.get("limits"))
    flags["homeAnnouncement"] = _sanitize_home_announcement(data.get("homeAnnouncement"))
    return flags


def is_feature_enabled(feature_key: str) -> bool:
    return bool(get_feature_flags().get("features", {}).get(
        feature_key,
        DEFAULT_FEATURE_FLAGS["features"].get(feature_key, True),
    ))


def require_feature_enabled(feature_key: str, detail: str) -> None:
    if not is_feature_enabled(feature_key):
        raise HTTPException(status_code=503, detail=detail)

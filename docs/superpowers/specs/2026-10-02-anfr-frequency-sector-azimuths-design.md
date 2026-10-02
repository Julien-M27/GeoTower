# ANFR frequency sector azimuths

## Goal

Carry the weekly ANFR `list_azimut` value from both database builders into the site detail UI. For each frequency, users should be able to see which antenna sectors carry it. The same sector association and frequency status color must appear in the normal frequency view, the antenna table, shared images, and PDF reports.

The example `0|120|240` means the frequency is active on sectors facing 0°, 120°, and 240°. The supplied CSV is data only; it does not define implementation instructions.

## Current flow

- The Android local builder reads the weekly Observatoire CSV and produces `geotower_fr.db`.
- The server builder at `C:\Users\Julien\Desktop\opt\geotower\api\build_fr_anfr_db.py` produces the downloadable database from the same weekly and monthly sources.
- Both builders currently store frequency details in `technique.details_frequences`. The live database copies these details and the live API returns them to the app.
- `FrequencyDetailsParser` turns those details into `FreqBand` values. `SiteFrequenciesBlock` renders the normal and grid views. The share image uses this component, while the PDF has a separate antenna table renderer.

## Data contract

Add nullable `technique.details_azimuts_frequences TEXT` to the France ANFR database. Its value is a JSON object keyed by normalized ANFR system label (trimmed and uppercase); each value is a sorted, deduplicated array of sector azimuths in degrees. Example:

```json
{"LTE 2100":[0,120,240],"5G NR 3500":[120,240]}
```

The app and server builders read `list_azimut` from each weekly CSV row. They merge repeated rows for the same station and normalized system by taking the union of valid azimuths. Pipe-separated values are parsed as degrees; 360° normalizes to 0°, and malformed or out-of-range tokens are ignored. If no valid values remain, that station/system has no mapping in the JSON object.

The database schema version advances from 7 to 8 in both builders and validators. Keep the Room identity hash, local builder DDL, generated Room schema, database validator, offline entities, and server DDL synchronized. The live database schema and API payload also gain the optional field. Existing API responses or rows without the field remain supported.

## App behavior

Extend `TechniqueEntity` and the live site DTO/model with the optional mapping. Parse it alongside `details_frequences`, and expose the relevant azimuth set on each `FreqBand` using the normalized system label. Normalize physical antenna azimuths with the same degree rules before matching.

Use the existing global frequency status-color resolver for sector markers:

- In normal view, draw one small colored marker next to each known sector for the frequency when that sector's azimuth is active.
- In grid/table view, put the same marker below the frequency label in the band cell for the corresponding antenna row.
- If a station/system has no mapping, retain the existing global indicator on its known sectors; absence of data must not mean “inactive everywhere.”
- Do not invent sectors when the app has no physical sector information.

## Share and PDF output

Shared images must retain the frequency-to-sector markers through `SiteFrequenciesBlock`. Update the independent PDF antenna table renderer to draw the same markers in the band cell below its frequency label. The same parsed `FreqBand` mapping and color resolver must drive both outputs so the screen and exports cannot disagree.

## Verification

- Add parser cases for a single azimuth, several pipe-separated azimuths, duplicate rows, 360° normalization, malformed values, and missing mappings.
- Add Android and server builder coverage proving that `list_azimut` reaches the new field for known and announced-only systems.
- Check schema version 8 and Room identity/hash validation, plus the live database/API field propagation.
- Verify the normal and grid views, shared image composition, and PDF antenna table all use the same sector mapping and color. Run the focused app and server checks relevant to these paths.

## Scope and constraints

No server deployment or publication is included. The server source directory is outside the workspace's writable roots, so implementation there will require the environment's write-access escalation. Preserve the pre-existing uncommitted `MapScreen.kt` change in the Android workspace.

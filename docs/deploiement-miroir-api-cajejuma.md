# Miroir de secours `api.cajejuma.fr`

**Il n'existe qu'un seul jeu de fichiers serveur** : `docs/server/`. `api.cajejuma.fr` n'est pas une
autre API, c'est le **même code déployé une seconde fois**. Rien à réécrire — seules l'installation
et quelques variables d'environnement changent.

L'app bascule automatiquement du principal vers le miroir quand le principal ne répond plus
(voir `ApiEndpoints`, `ServerReachability` et l'intercepteur de `RetrofitClient` côté Android), et
revient au principal dès qu'il répond de nouveau.

## Fichiers à déployer

Exactement les mêmes que sur `api.geotower.fr` :

| Fichier | Rôle |
| --- | --- |
| `main.py` | app FastAPI : bases, manifeste signé, sites HS, catalogue des cartes, version de l'app |
| `feature_flags.py` | `/api/v2/app/features` (flags distants, annonces, kill-switches) |
| `live_fr_api.py` | `/api/v2/live/...` (base live) |
| `signalquest_proxy.py` | `/api/v2/signalquest/...` (photos communautaires) |
| `legal_pages.py` + `legal/` | pages légales |
| `requirements.txt` | dépendances Python |

Les builders (`build_*.py`, `fr_*.py`, `fetch_*.py`) ne sont utiles sur le miroir que s'il génère
lui-même les bases ; sinon, il suffit d'y **recopier** les fichiers produits par le principal.

## Variables d'environnement propres au miroir

```bash
GEOTOWER_PUBLIC_API_BASE_URL=https://api.cajejuma.fr
```

C'est la seule qui **doit** changer : elle fabrique les URLs absolues du manifeste signé et du JSON
de version. Les autres (`GEOTOWER_IMPORTS_DIR`, `GEOTOWER_SITES_HS_DIR`, `GEOTOWER_FEATURE_FLAGS_PATH`,
`SIGNALQUEST_*`, `TRUSTED_PROXY_IPS`…) gardent les valeurs du principal, adaptées aux chemins de la
machine.

## État vérifié le 2026-08-03 (miroir déployé et testé)

Le miroir sert le même code que le principal. Vérifié depuis l'extérieur :
`/api/v2/{app/features, db/info, download/manifest, antennes/hs/info}` et `/confidentialite`
répondent tous 200, en moins de 0,5 s. Le manifeste est signé avec le **même `keyId`**
(`geotower-prod-2026-01`) et annonce ses propres URLs `api.cajejuma.fr`.

Sites HS : le miroir a **son propre cron autonome** (toutes les 6 h, wrapper
`/opt/geotower/scripts/update_sites_hs.sh` sous `flock`), et son `build_sites_hs.py` est à la
version du dépôt. `last_update` identique au principal.

⚠️ Le script exécuté par le cron est celui de `/opt/geotower/scripts/`, **pas** celui de
`/opt/geotower/api/` : deux copies coexistent, seule la première compte. Vérifier les deux
(`md5sum`) lors d'une mise à jour, sinon elle se perd silencieusement.

Un seul écart subsiste :

| Écart | Effet | À faire |
| --- | --- | --- |
| **Bases construites séparément** : manifeste du miroir `20260731_2215` / SHA `9259fff3…`, du principal `20260802_0010` / SHA `39de0a3a…` | Aucun : le téléchargement est épinglé au serveur qui a servi le manifeste, donc le SHA-256 correspond toujours. Le miroir sert juste une base un peu plus ancienne. | Idéalement rsync depuis le principal plutôt qu'un second build |

## Deux points à ne pas rater

1. **Même clé de signature du manifeste.** `GEOTOWER_MANIFEST_SIGNING_KEY_PEM` (ou
   `GEOTOWER_MANIFEST_SIGNING_KEY_PATH`) et `GEOTOWER_MANIFEST_KEY_ID` doivent être **identiques**
   sur les deux serveurs : l'app vérifie la signature contre les clés publiques compilées dans
   `BuildConfig.GEOTOWER_MANIFEST_PUBLIC_KEYS`. Avec une autre clé, le miroir répondrait très bien
   mais aucune base ne pourrait plus être téléchargée.
2. **Mêmes fichiers de données.** `geotower_fr.db`, `geotower_fr_radio.db`, `geotower_fr_enb.db`,
   les `version_fr*.json`, `sites_hs.geojson` et `features.json` doivent être synchronisés
   (rsync depuis le principal, ou le même cron). Le manifeste porte les SHA-256 : un fichier
   différent d'un octet est rejeté par l'app.

## Vérifier que le miroir est complet

```bash
curl -s https://api.cajejuma.fr/api/v2/app/features | head -c 200
curl -s https://api.cajejuma.fr/api/v2/db/info
curl -s https://api.cajejuma.fr/api/v2/download/manifest | head -c 400
```

Puis, côté app : **Réglages → Diagnostic → Serveur GeoTower → Choisir le serveur → « Toujours le
miroir »**. Tout doit fonctionner comme sur le principal (carte, base, sites HS, photos). Repasser
ensuite sur « Automatique ».

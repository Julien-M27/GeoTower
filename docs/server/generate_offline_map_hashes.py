import argparse
import ast
import hashlib
import json
import urllib.request
from pathlib import Path
from typing import Any, Dict, List


def load_base_catalog(main_py: Path) -> List[Dict[str, Any]]:
    module = ast.parse(main_py.read_text(encoding="utf-8"))
    for node in ast.walk(module):
        if not isinstance(node, ast.Assign):
            continue
        if not any(isinstance(target, ast.Name) and target.id == "base_catalog" for target in node.targets):
            continue
        value = ast.literal_eval(node.value)
        if isinstance(value, list):
            return value
    raise RuntimeError("base_catalog not found in main.py")


def stream_sha256(url: str) -> str:
    digest = hashlib.sha256()
    request = urllib.request.Request(url, headers={"User-Agent": "GeoTowerHashGenerator/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        while True:
            chunk = response.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate GeoTower offline map SHA-256 hashes without storing maps.")
    parser.add_argument("--main-py", default="docs/server/main.py", help="Path to docs/server/main.py")
    parser.add_argument("--out", required=True, help="Output JSON path")
    args = parser.parse_args()

    catalog = load_base_catalog(Path(args.main_py))
    hashes: Dict[str, str] = {}
    for item in catalog:
        map_id = str(item["id"])
        url = str(item["map_url"])
        print(f"Hashing {map_id}: {url}")
        hashes[map_id] = stream_sha256(url)

    output_path = Path(args.out)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(hashes, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Wrote {len(hashes)} hashes to {output_path}")


if __name__ == "__main__":
    main()

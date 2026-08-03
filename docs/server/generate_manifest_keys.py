import argparse
import base64
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate GeoTower ECDSA manifest signing keys.")
    parser.add_argument("--key-id", required=True, help="Stable key id, for example geotower-prod-2026-01")
    parser.add_argument("--private-key-out", required=True, help="Path where the private PEM key will be written")
    args = parser.parse_args()

    private_key = ec.generate_private_key(ec.SECP256R1())
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    public_der = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )

    private_path = Path(args.private_key_out)
    private_path.parent.mkdir(parents=True, exist_ok=True)
    private_path.write_bytes(private_pem)

    public_value = base64.b64encode(public_der).decode("ascii")
    print(f"Private key written to: {private_path}")
    print("Server env:")
    print(f"  GEOTOWER_MANIFEST_KEY_ID={args.key_id}")
    print(f"  GEOTOWER_MANIFEST_SIGNING_KEY_PATH={private_path}")
    print("Android Gradle property/env:")
    print(f"  GEOTOWER_MANIFEST_PUBLIC_KEYS={args.key_id}:{public_value}")


if __name__ == "__main__":
    main()

"""Decrypt an AHUTong .ahulog export using the separately held RSA private key."""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

FORMAT = "AHUTONG-LOG-1"
ALGORITHM = "RSA-OAEP-SHA256+A256GCM"


def decrypt_log(envelope_bytes: bytes, private_pem: bytes) -> bytes:
    if len(envelope_bytes) > 16 * 1024 * 1024:
        raise ValueError("Export exceeds the supported size")
    envelope = json.loads(envelope_bytes)
    if envelope.get("format") != FORMAT or envelope.get("algorithm") != ALGORITHM:
        raise ValueError("Unsupported export format or algorithm")
    private_key = serialization.load_pem_private_key(private_pem, password=None)
    if not isinstance(private_key, rsa.RSAPrivateKey) or private_key.key_size < 3072:
        raise ValueError("Expected an RSA private key with at least 3072 bits")
    public_der = private_key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    key_id = hashlib.sha256(public_der).hexdigest()[:16]
    if envelope.get("keyId") != key_id:
        raise ValueError("This private key does not match the export")
    decode = lambda name: base64.b64decode(envelope[name], validate=True)
    nonce = decode("nonce")
    if len(nonce) != 12:
        raise ValueError("Invalid AES-GCM nonce")
    aes_key = private_key.decrypt(
        decode("wrappedKey"),
        padding.OAEP(mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None),
    )
    if len(aes_key) != 32:
        raise ValueError("Invalid AES key")
    aad = f"{FORMAT}|{ALGORITHM}|{key_id}".encode()
    return AESGCM(aes_key).decrypt(nonce, decode("ciphertext"), aad)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("export", type=Path, help="Encrypted .ahulog file")
    parser.add_argument("--private-key", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path, help="New plaintext output file; existing files are refused")
    args = parser.parse_args()
    try:
        plaintext = decrypt_log(args.export.read_bytes(), args.private_key.read_bytes())
        with args.output.open("xb") as output:
            output.write(plaintext)
    except Exception as error:
        parser.exit(1, f"Unable to decrypt log: {type(error).__name__}: {error}\n")
    print(f"Decrypted log saved to {args.output}")


if __name__ == "__main__":
    main()

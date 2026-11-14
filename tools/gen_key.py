#!/usr/bin/env python3
"""Generate the demo signing identity: PKCS#8 key, X.509 cert and a PKCS#12
keystore that jarsigner can use for the v1 (JAR) signature."""
import datetime
import os

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

HERE = os.path.dirname(os.path.abspath(__file__))
KEYS = os.path.join(HERE, "..", "keys")


def main():
    os.makedirs(KEYS, exist_ok=True)
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, "VoiceMorph Demo Signer"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "VoiceMorph"),
    ])
    now = datetime.datetime.utcnow()
    cert = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(days=1))
        .not_valid_after(now + datetime.timedelta(days=365 * 30))
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .sign(key, hashes.SHA256())
    )
    with open(os.path.join(KEYS, "key.pk8"), "wb") as f:
        f.write(key.private_bytes(
            serialization.Encoding.DER,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        ))
    with open(os.path.join(KEYS, "cert.der"), "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.DER))
    p12 = pkcs12.serialize_key_and_certificates(
        b"voicemorph", key, cert, None,
        serialization.BestAvailableEncryption(b"voicemorph"),
    )
    with open(os.path.join(KEYS, "voicemorph.p12"), "wb") as f:
        f.write(p12)
    print("keys written to", os.path.abspath(KEYS))


if __name__ == "__main__":
    main()

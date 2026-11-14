#!/usr/bin/env python3
"""Add an APK Signature Scheme v2 signing block to a v1-signed, aligned APK.

Usage: sign_v2.py <in.apk> <out.apk> <key.pk8> <cert.der>

Implements RSA PKCS#1 v1.5 with SHA-256 (algorithm id 0x0103), the chunked
content digest (1 MB chunks, 0xa5 / 0x5a prefixes) and the APK Signing Block
layout as specified by Android's apksig.
"""
import struct
import sys

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

MAGIC = b"APK Sig Block 42"
ID_V2 = 0x7109871A
ALG_RSA_SHA256 = 0x0103
CHUNK = 1024 * 1024


def lp(b):
    return struct.pack("<I", len(b)) + b


def seq(*elems):
    return lp(b"".join(lp(e) for e in elems))


def chunked_digest(sections):
    # Each of the three APK sections is chunked independently into 1 MB pieces.
    digests = []
    for sec in sections:
        off = 0
        while off < len(sec):
            chunk = sec[off:off + CHUNK]
            off += len(chunk)
            digests.append(hashlib_sha256(b"\xa5" + struct.pack("<I", len(chunk)) + chunk))
    concat = b"".join(digests)
    # top-level: 0x5a || uint32 LE chunk count || concatenated chunk digests
    return hashlib_sha256(b"\x5a" + struct.pack("<I", len(digests)) + concat)


def hashlib_sha256(data):
    import hashlib
    return hashlib.sha256(data).digest()


def find_eocd(data):
    idx = data.rfind(b"PK\x05\x06")
    if idx < 0:
        raise SystemExit("no EOCD")
    return idx


def main():
    src, out, key_path, cert_path = sys.argv[1:5]
    with open(src, "rb") as f:
        data = bytearray(f.read())
    with open(key_path, "rb") as f:
        key = serialization.load_der_private_key(f.read(), password=None)
    with open(cert_path, "rb") as f:
        cert_der = f.read()
    pub_der = key.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )

    eocd_pos = find_eocd(data)
    cd_size, cd_off = struct.unpack_from("<II", data, eocd_pos + 12)

    def build_block(digest, sig):
        digests_seq = seq(struct.pack("<I", ALG_RSA_SHA256) + lp(digest))
        certs_seq = seq(cert_der)
        addl_seq = lp(b"")
        # signed-data := digests seq + certificates seq + additional attributes seq
        signed_data = digests_seq + certs_seq + addl_seq
        sigs_seq = seq(struct.pack("<I", ALG_RSA_SHA256) + lp(sig))
        signer = lp(signed_data) + sigs_seq + lp(pub_der)
        # signers := sequence of length-prefixed signer
        pair_value = seq(signer)
        pair = struct.pack("<Q", 4 + len(pair_value)) + struct.pack("<I", ID_V2) + pair_value
        block_size = len(pair) + 8 + 16
        return (struct.pack("<Q", block_size) + pair +
                struct.pack("<Q", block_size) + MAGIC)

    # pass 1: size only (signature length is fixed for RSA-2048)
    dummy_block = build_block(b"\x00" * 32, b"\x00" * (key.key_size // 8))
    block_len = len(dummy_block)

    new_cd_off = cd_off + block_len
    new_eocd = bytearray(data[eocd_pos:])
    struct.pack_into("<I", new_eocd, 16, new_cd_off)

    # The verifier digests the EOCD with its CD-offset field holding the
    # signing-block offset (i.e. the pre-insertion value), so section 3 is the
    # original EOCD bytes verbatim.
    sections = [bytes(data[:cd_off]), bytes(data[cd_off:eocd_pos]), bytes(data[eocd_pos:])]
    digest = chunked_digest(sections)

    # rebuild exactly what will be signed
    digests_seq = seq(struct.pack("<I", ALG_RSA_SHA256) + lp(digest))
    certs_seq = seq(cert_der)
    addl_seq = lp(b"")
    signed_data_for_sig = digests_seq + certs_seq + addl_seq

    sig = key.sign(signed_data_for_sig, padding.PKCS1v15(), hashes.SHA256())
    assert len(sig) == key.key_size // 8

    block = build_block(digest, sig)
    assert len(block) == block_len

    with open(out, "wb") as f:
        f.write(bytes(data[:cd_off]))
        f.write(block)
        f.write(bytes(data[cd_off:eocd_pos]))
        f.write(bytes(new_eocd))
    print("v2 signed ->", out, "block", block_len, "bytes")


if __name__ == "__main__":
    main()

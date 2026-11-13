#!/usr/bin/env python3
"""
Deterministic Android APK build script for NoFilter Ai (com.nofilter.ai).

Pipeline:
  1. aapt2 compile  -> res_flat.zip
  2. aapt2 link     -> base_resources.apk + R.java
  3. R.java -> R.kt -> kotlinc-jvm 2.4 -> classes.jar
  4. R8/D8          -> classes.dex (with kotlin-stdlib)
  5. Merge + 4-byte zipalign (uncompressed resources.arsc & binary assets)
  6. keytool + apksigner (v1 + v2 + v3 signatures) -> release/NoFilter-Ai.apk
  7. apksigner verify --verbose + aapt2 dump badging
"""
import glob
import os
import re
import shutil
import struct
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.environ.get("NF_TOOLS_DIR", "/home/user/tools")
JAVA_HOME = os.environ.get(
    "JAVA_HOME",
    "/home/user/.local/lib/python3.11/site-packages/jdk4py/java-runtime",
)
JAVA = os.path.join(JAVA_HOME, "bin", "java")
KEYTOOL = os.path.join(JAVA_HOME, "bin", "keytool")

AAPT2 = os.path.join(TOOLS, "pkgs/x/aaptjs3/package/bin/x64/linux/aapt2")
ANDROID_JAR = os.path.join(TOOLS, "android-34.jar")
KOTLIN_LIB = os.path.join(TOOLS, "pkgs/x/kotlinc/package/lib")
KOTLINC_JAR = os.path.join(KOTLIN_LIB, "kotlin-compiler.jar")
KOTLIN_STDLIB = os.path.join(KOTLIN_LIB, "kotlin-stdlib.jar")
KOTLIN_ANNOT = os.path.join(KOTLIN_LIB, "annotations-13.0.jar")
D8_JAR = os.path.join(TOOLS, "d8-jvm.jar")
APKSIGNER_JAR = os.path.join(TOOLS, "apksigner.jar")

BUILD = os.path.join(ROOT, "build")
REL_DIR = os.path.join(ROOT, "release")
OUT_APK = os.path.join(REL_DIR, "NoFilter-Ai.apk")


def run(cmd, desc):
    print(f"\n==> {desc}")
    res = subprocess.run(cmd, cwd=ROOT, text=True, capture_output=True)
    if res.stdout.strip():
        print(res.stdout.strip())
    if res.returncode != 0:
        print(res.stderr, file=sys.stderr)
        raise SystemExit(f"Build step failed ({desc}) with exit code {res.returncode}")
    return res.stdout


def r_java_to_kt(r_java_path: str, r_kt_path: str):
    """Translate aapt2-generated R.java into equivalent R.kt so kotlinc emits R$*.class."""
    src = open(r_java_path, "r", encoding="utf-8").read()
    pkg_m = re.search(r"package\s+([\w.]+)\s*;", src)
    pkg = pkg_m.group(1) if pkg_m else "com.nofilter.ai"
    lines = [f"package {pkg}", "", "object R {"]
    cur_cls = None
    for raw in src.splitlines():
        line = raw.strip()
        m_cls = re.match(r"public static final class (\w+)\s*\{", line)
        if m_cls:
            cur_cls = m_cls.group(1)
            lines.append(f"  object {cur_cls} {{")
            continue
        if cur_cls and line == "}":
            lines.append("  }")
            cur_cls = None
            continue
        if cur_cls:
            m_int = re.match(r"public static final int (\w+)=(0x[0-9a-fA-F]+|\d+);", line)
            if m_int:
                name, val = m_int.group(1), m_int.group(2)
                # Convert unsigned 32-bit hex >= 0x80000000 if needed
                iv = int(val, 16) if val.startswith("0x") else int(val)
                if iv >= 0x80000000:
                    iv -= 0x100000000
                lines.append(f"    const val {name}: Int = {iv}")
                continue
            m_arr = re.match(r"public static final int\[\] (\w+)=\{(.*?)\};", line)
            if m_arr:
                name, body = m_arr.group(1), m_arr.group(2).strip()
                vals = []
                for tok in [t.strip() for t in body.split(",") if t.strip()]:
                    iv = int(tok, 16) if tok.startswith("0x") else int(tok)
                    if iv >= 0x80000000:
                        iv -= 0x100000000
                    vals.append(str(iv))
                lines.append(f"    @JvmField val {name}: IntArray = intArrayOf({', '.join(vals)})")
    lines.append("}")
    os.makedirs(os.path.dirname(r_kt_path), exist_ok=True)
    with open(r_kt_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def merge_and_zipalign(base_apk: str, dex_files: list[str], aligned_apk: str):
    """
    Create a clean APK zip with 4-byte alignment for all uncompressed (ZIP_STORED)
    entries (including resources.arsc, PNGs, WASM, traineddata), as required by
    Android 11+ (targetSdk 34).
    """
    uncompressed_exts = (".arsc", ".png", ".webp", ".jpg", ".jpeg", ".wasm", ".traineddata")
    entries: list[tuple[str, bytes, int]] = []

    with zipfile.ZipFile(base_apk, "r") as zin:
        for info in zin.infolist():
            if info.filename.startswith("META-INF/"):
                continue
            data = zin.read(info.filename)
            is_store = (
                info.filename == "resources.arsc"
                or info.filename.lower().endswith(uncompressed_exts)
                or info.compress_type == zipfile.ZIP_STORED
            )
            entries.append((info.filename, data, zipfile.ZIP_STORED if is_store else zipfile.ZIP_DEFLATED))

    for dex in sorted(dex_files):
        entries.append((os.path.basename(dex), open(dex, "rb").read(), zipfile.ZIP_DEFLATED))

    if os.path.exists(aligned_apk):
        os.remove(aligned_apk)

    with open(aligned_apk, "wb") as raw_f:
        with zipfile.ZipFile(raw_f, "w") as zout:
            for name, data, method in entries:
                zi = zipfile.ZipInfo(filename=name, date_time=(2026, 1, 1, 0, 0, 0))
                zi.compress_type = method
                zi.create_system = 3
                zi.external_attr = 0o644 << 16
                zi.extra = b""
                if method == zipfile.ZIP_STORED:
                    # Local file header is 30 bytes + len(filename) + len(extra)
                    cur_offset = raw_f.tell()
                    name_bytes = name.encode("utf-8")
                    align = 4096 if name.endswith(".so") else 4
                    data_start = cur_offset + 30 + len(name_bytes)
                    pad = (align - (data_start % align)) % align
                    if pad > 0:
                        zi.extra = b"\x00" * pad
                zout.writestr(zi, data)


def verify_alignment(apk_path: str):
    with open(apk_path, "rb") as f:
        blob = f.read()
    with zipfile.ZipFile(apk_path, "r") as z:
        for info in z.infolist():
            if info.compress_type == zipfile.ZIP_STORED:
                off = info.header_offset
                sig, = struct.unpack_from("<I", blob, off)
                assert sig == 0x04034B50, f"Bad local header signature for {info.filename}"
                fn_len, ex_len = struct.unpack_from("<HH", blob, off + 26)
                data_off = off + 30 + fn_len + ex_len
                align = 4096 if info.filename.endswith(".so") else 4
                if data_off % align != 0:
                    raise SystemExit(f"Unaligned STORED entry {info.filename}: offset {data_off} (align {align})")
    print("==> Verified 4-byte alignment of all uncompressed entries (including resources.arsc)")


def main():
    if os.path.exists(BUILD):
        shutil.rmtree(BUILD)
    os.makedirs(BUILD, exist_ok=True)
    os.makedirs(REL_DIR, exist_ok=True)

    res_zip = os.path.join(BUILD, "res_flat.zip")
    gen_java = os.path.join(BUILD, "gen_java")
    gen_kt = os.path.join(BUILD, "gen_kt")
    base_apk = os.path.join(BUILD, "base_resources.apk")
    classes_jar = os.path.join(BUILD, "classes.jar")
    dex_dir = os.path.join(BUILD, "dex")
    aligned_apk = os.path.join(BUILD, "unsigned_aligned.apk")
    keystore = os.path.join(BUILD, "release.jks")
    os.makedirs(gen_java, exist_ok=True)
    os.makedirs(dex_dir, exist_ok=True)

    # 1. Compile resources
    run(
        [AAPT2, "compile", "--dir", "app/src/main/res", "-o", res_zip],
        "aapt2 compile resources",
    )

    # 2. Link resources + manifest + assets
    run(
        [
            AAPT2, "link",
            "-I", ANDROID_JAR,
            "--manifest", "app/src/main/AndroidManifest.xml",
            "-A", "app/src/main/assets",
            "--java", gen_java,
            "--min-sdk-version", "24",
            "--target-sdk-version", "34",
            "--version-code", "1",
            "--version-name", "1.0.0",
            "-0", "arsc",
            "-0", "wasm",
            "-0", "traineddata",
            "-o", base_apk,
            res_zip,
        ],
        "aapt2 link resources & assets",
    )

    # 3. Convert R.java -> R.kt and compile Kotlin sources
    r_java = os.path.join(gen_java, "com/nofilter/ai/R.java")
    r_kt = os.path.join(gen_kt, "com/nofilter/ai/R.kt")
    r_java_to_kt(r_java, r_kt)

    kt_sources = sorted(glob.glob(os.path.join(ROOT, "app/src/main/java/**/*.kt"), recursive=True))
    kt_sources.append(r_kt)
    print(f"Compiling {len(kt_sources)} Kotlin source files...")

    run(
        [
            JAVA,
            "--enable-native-access=ALL-UNNAMED",
            "-cp", KOTLINC_JAR,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-no-stdlib",
            "-jvm-target", "11",
            "-classpath", f"{ANDROID_JAR}:{KOTLIN_STDLIB}:{KOTLIN_ANNOT}",
            "-d", classes_jar,
            *kt_sources,
        ],
        "kotlinc compile Kotlin -> classes.jar",
    )

    # 4. Dex with D8
    run(
        [
            JAVA,
            "-cp", D8_JAR,
            "com.android.tools.r8.D8",
            "--release",
            "--min-api", "24",
            "--lib", ANDROID_JAR,
            "--output", dex_dir,
            classes_jar,
            KOTLIN_STDLIB,
            KOTLIN_ANNOT,
        ],
        "D8 dexing -> classes.dex",
    )

    dex_files = glob.glob(os.path.join(dex_dir, "classes*.dex"))
    assert dex_files, "D8 produced no classes.dex!"

    # 5. Merge + zipalign
    print("\n==> Merging DEX + resources and 4-byte aligning uncompressed entries")
    merge_and_zipalign(base_apk, dex_files, aligned_apk)
    verify_alignment(aligned_apk)

    # 6. Generate release signing key & sign APK (v1 + v2 + v3)
    run(
        [
            KEYTOOL, "-genkeypair",
            "-keystore", keystore,
            "-storepass", "nofilterai",
            "-keypass", "nofilterai",
            "-alias", "nofilter",
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "10000",
            "-dname", "CN=NoFilter Ai, OU=Mobile, O=NoFilter, L=Karachi, S=Sindh, C=PK",
        ],
        "Generate signing keystore",
    )

    run(
        [
            JAVA, "-jar", APKSIGNER_JAR, "sign",
            "--ks", keystore,
            "--ks-key-alias", "nofilter",
            "--ks-pass", "pass:nofilterai",
            "--key-pass", "pass:nofilterai",
            "--v1-signing-enabled", "true",
            "--v2-signing-enabled", "true",
            "--v3-signing-enabled", "true",
            "--out", OUT_APK,
            aligned_apk,
        ],
        "Sign APK (v1 + v2 + v3)",
    )

    # 7. Verify signature & badging
    run(
        [JAVA, "-jar", APKSIGNER_JAR, "verify", "--verbose", "--print-certs", OUT_APK],
        "apksigner verify",
    )
    badging = run([AAPT2, "dump", "badging", OUT_APK], "aapt2 dump badging")
    for line in badging.splitlines()[:12]:
        print("  ", line)

    size_mb = os.path.getsize(OUT_APK) / (1024 * 1024)
    print(f"\nSUCCESS: Built signed installable APK -> {OUT_APK} ({size_mb:.2f} MB)")


if __name__ == "__main__":
    main()

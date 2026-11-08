#!/usr/bin/env python3
"""Verify the kotlin-compiler-embeddable jar resolved from the JetBrains Space
mirror is byte-identical to the one Maven Central publishes.

The build resolves this single 60 MB artifact from the JetBrains Space dev
repository (see settings.gradle.kts and buildSrc/) because Maven Central
rate-limits GitHub Actions runners and redirects the download to the GitHub
release asset CDN, which intermittently returns 504 Gateway Time-out.

Since the mirror is a dev channel, this script guards against silent
substitution: it downloads the jar from both Maven Central and the mirror,
computes SHA-256 for each, and compares them against a pinned checksum in
gradle/kotlin-compiler-embeddable-<version>.sha256.

If any of the three hashes disagree, the script exits non-zero so CI fails
before wasting time on a Gradle build that would compile against untrusted
bytes.

Run locally:   python3 scripts/verify_kotlin_compiler.py
Run in CI:     called from .github/workflows/build.yml before the Gradle step
"""

from __future__ import annotations

import hashlib
import re
import sys
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
VERSIONS_FILE = REPO_ROOT / "gradle" / "libs.versions.toml"
CHECKSUM_DIR = REPO_ROOT / "gradle"

MAVEN_CENTRAL_URL = "https://repo.maven.apache.org/maven2"
JB_SPACE_URL = "https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev"

GROUP = "org.jetbrains.kotlin"
ARTIFACT = "kotlin-compiler-embeddable"


def read_kotlin_version() -> str:
    """Extract the Kotlin version from gradle/libs.versions.toml."""
    text = VERSIONS_FILE.read_text(encoding="utf-8")
    # Matches:  kotlin = "2.4.0"
    match = re.search(r'^\s*kotlin\s*=\s*"([^"]+)"', text, re.MULTILINE)
    if not match:
        print("ERROR: could not find 'kotlin' version in libs.versions.toml", file=sys.stderr)
        sys.exit(1)
    return match.group(1)


def download_and_hash(url: str) -> str:
    """Download a URL and return its SHA-256 hex digest."""
    print(f"  downloading from {url} ...", flush=True)
    h = hashlib.sha256()
    with urllib.request.urlopen(url, timeout=120) as resp:
        while True:
            chunk = resp.read(65536)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    version = read_kotlin_version()
    print(f"Kotlin version: {version}")

    checksum_file = CHECKSUM_DIR / f"{ARTIFACT}-{version}.sha256"
    if not checksum_file.exists():
        print(
            f"ERROR: pinned checksum file not found: {checksum_file}",
            file=sys.stderr,
        )
        print(
            "Create it by running this script locally and then storing the "
            "Maven Central hash:",
            file=sys.stderr,
        )
        print(
            f"  echo '<hash>  {ARTIFACT}-{version}.jar' > {checksum_file}",
            file=sys.stderr,
        )
        return 1

    pinned_hash = checksum_file.read_text(encoding="utf-8").strip().split()[0]
    print(f"Pinned SHA-256: {pinned_hash}")

    jar_path = f"{GROUP.replace('.', '/')}/{ARTIFACT}/{version}/{ARTIFACT}-{version}.jar"

    print("\n1. Maven Central (canonical source):")
    mc_hash = download_and_hash(f"{MAVEN_CENTRAL_URL}/{jar_path}")

    print("\n2. JetBrains Space mirror:")
    jb_hash = download_and_hash(f"{JB_SPACE_URL}/{jar_path}")

    print()
    print(f"  Maven Central:  {mc_hash}")
    print(f"  JetBrains Space: {jb_hash}")
    print(f"  Pinned:          {pinned_hash}")

    ok = True
    if mc_hash != pinned_hash:
        print(
            "FAIL: Maven Central jar does not match the pinned checksum.\n"
            "  Either the pinned checksum is stale, or Maven Central is "
            "serving unexpected bytes.",
            file=sys.stderr,
        )
        ok = False

    if jb_hash != pinned_hash:
        print(
            "FAIL: JetBrains Space mirror jar does not match the pinned "
            "checksum.\n"
            "  The dev-channel mirror is serving different bytes than Maven "
            "Central. Do NOT proceed with the build.",
            file=sys.stderr,
        )
        ok = False

    if mc_hash != jb_hash:
        print(
            "FAIL: Maven Central and the JetBrains Space mirror disagree.\n"
            "  Both should serve identical bytes for a released version.",
            file=sys.stderr,
        )
        ok = False

    if ok:
        print("\nAll three hashes match. Safe to build.")
        return 0

    print("\nIf you just bumped the Kotlin version, update the pinned "
          "checksum file:", file=sys.stderr)
    print(f"  echo '{mc_hash}  {ARTIFACT}-{version}.jar' > {checksum_file}",
          file=sys.stderr)
    print("Then verify the mirror matches before committing.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())

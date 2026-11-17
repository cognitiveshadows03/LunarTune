#!/usr/bin/env python3
"""Verify the kotlin-compiler-embeddable jar served to the build matches the
pinned SHA-256 of the one Maven Central publishes.

The build resolves this single 60 MB artifact from Google's Maven Central mirror
(see settings.gradle.kts and buildSrc/) because Maven Central rate-limits GitHub
Actions runners and redirects the download to the GitHub release asset CDN,
which intermittently returns 504 Gateway Time-out.

That mirror is a straight copy of Maven Central rather than a dev channel, but
it is still a third party, so this script downloads the jar from both the mirror
and Maven Central, hashes each one, and compares them against the pinned
checksum in gradle/kotlin-compiler-embeddable-<version>.sha256. If any hash
disagrees the script exits non-zero so CI fails before wasting time on a Gradle
build that would compile against unexpected bytes.

A download that fails outright is retried a few times with backoff, and then
reported as a hard error: if neither the mirror nor Maven Central can serve the
artifact, the Gradle build downstream cannot succeed either, so there is nothing
to gain by pretending the check passed.

Run locally:   python3 scripts/verify_kotlin_compiler.py
Run in CI:     called from .github/workflows/build.yml before the Gradle step
"""

from __future__ import annotations

import hashlib
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
VERSIONS_FILE = REPO_ROOT / "gradle" / "libs.versions.toml"
CHECKSUM_DIR = REPO_ROOT / "gradle"

# Order matters: the mirror is what Gradle actually resolves from, so it is
# checked first and its failure message names the host the build uses.
SOURCE_URLS = [
    ("Google Maven Central mirror", "https://maven-central.storage-download.googleapis.com/maven2"),
    ("Maven Central (canonical source)", "https://repo.maven.apache.org/maven2"),
]

GROUP = "org.jetbrains.kotlin"
ARTIFACT = "kotlin-compiler-embeddable"

ATTEMPTS = 3
RETRY_DELAY_SECONDS = 5
TIMEOUT_SECONDS = 120


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
    """Download a URL and return its SHA-256 hex digest, retrying on failure."""
    last_error: Exception | None = None
    for attempt in range(1, ATTEMPTS + 1):
        print(f"  downloading from {url} (attempt {attempt}/{ATTEMPTS}) ...", flush=True)
        h = hashlib.sha256()
        try:
            with urllib.request.urlopen(url, timeout=TIMEOUT_SECONDS) as resp:
                while True:
                    chunk = resp.read(65536)
                    if not chunk:
                        break
                    h.update(chunk)
            return h.hexdigest()
        except (urllib.error.URLError, OSError) as error:
            last_error = error
            print(f"  attempt {attempt} failed: {error}", flush=True)
            if attempt < ATTEMPTS:
                time.sleep(RETRY_DELAY_SECONDS * attempt)
    raise RuntimeError(f"could not download {url}: {last_error}")


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

    hashes: dict[str, str] = {}
    unreachable: list[str] = []

    for index, (label, base_url) in enumerate(SOURCE_URLS, start=1):
        print(f"\n{index}. {label}:")
        try:
            hashes[label] = download_and_hash(f"{base_url}/{jar_path}")
        except RuntimeError as error:
            print(f"  FAILED: {error}", file=sys.stderr)
            unreachable.append(label)

    if len(unreachable) == len(SOURCE_URLS):
        print(
            "\nFAIL: the jar could not be downloaded from any source.\n"
            "  A Gradle build cannot resolve it either, so this is reported as "
            "an error rather than skipped.",
            file=sys.stderr,
        )
        return 1

    print()
    for label, digest in hashes.items():
        print(f"  {label}: {digest}")
    print(f"  Pinned: {pinned_hash}")

    ok = True
    for label, digest in hashes.items():
        if digest != pinned_hash:
            print(
                f"FAIL: the jar from {label} does not match the pinned checksum.\n"
                "  Either the pinned checksum is stale, or that source is "
                "serving unexpected bytes.",
                file=sys.stderr,
            )
            ok = False

    for label in unreachable:
        print(
            f"WARNING: {label} was unreachable, so it could not be compared. "
            "The remaining source still matched the pin.",
            file=sys.stderr,
        )

    if len(hashes) > 1 and len(set(hashes.values())) > 1:
        print(
            "FAIL: the sources disagree with each other.\n"
            "  They should serve identical bytes for a released version.",
            file=sys.stderr,
        )
        ok = False

    if ok:
        print("\nEvery reachable source matches the pinned checksum. Safe to build.")
        return 0

    print("\nIf you just bumped the Kotlin version, update the pinned "
          "checksum file:", file=sys.stderr)
    canonical = hashes.get("Maven Central (canonical source)") or next(iter(hashes.values()))
    print(f"  echo '{canonical}  {ARTIFACT}-{version}.jar' > {checksum_file}",
          file=sys.stderr)
    print("Then verify every other source matches before committing.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())

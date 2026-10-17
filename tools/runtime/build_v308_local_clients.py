#!/usr/bin/env python3
"""Build deterministic LocalLab localhost/airgap variants from exact SpawnPK v308.

No client binary is committed. This patcher accepts only the exact pinned v308 JAR and
fails closed on every transformed class preimage.

localhost:
- enables the client's already-present local socket mode;
- game/AUX socket host becomes 127.0.0.1;
- expands exact-v308 Walk-here unreachable-target fallback from radius 1 to radius 2;
- other web/update endpoints remain stock.

airgap:
- includes localhost mode;
- redirects every known SpawnPK updater/web/API constant to LocalLab loopback;
- contains no SpawnPK/AWS production endpoint strings after patching.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import stat
import uuid
import struct
import tempfile
import zipfile
from pathlib import Path

INPUT_SHA = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"
LOCALHOST_SHA = "15ceb89669ddfe0a666e65b4a5291705af692a50e479bbde17e751eceb7fd23e"
AIRGAP_SHA = "024fad774453430bb964076b98d460a6dae821ee31100322d104e30d9a9c97a7"

PREIMAGE_SHA = {
    "rs/cache/b/e.class": "e21c305dd2ac52c16508e560f1770ea859f509670dced089424c274e8693638c",
    "rs/Client.class": "ba7631ed8d76917b83742508c9df0d241aa20898521b94b23fb3a0dc8f0b2d45",
    "rs/f/a$a.class": "a2b590461bcacc7bc287c9d8835a805a10e80c8626a2ac98b2bb05bd0a15e972",
    "rs/f/a.class": "7bf992941766a40fddaead4c8bfe5e94a46c19c63d06800e26f45b78d001bbc6",
    "rs/l/d/d.class": "62cc1ffba2f01eb86ed15da6db46ef74141080b66f9ce5e63167e7728a3461b3",
    "rs/l/d/e.class": "27950903da06624393549a196bd01d60e857e418c2bdc7ac5e380614351ada3d",
    "rs/gui/m.class": "b5f6cc5d6bfddd98ff5ff85b906ff7015105261312f9470113cc535c160c59b1",
    "rs/gui/n.class": "6ddebb61f0ce4f1db48ed7465c3c786e4c1e94d390744710ffd0dfe1b9141b63",
    "rs/gui/o.class": "225c0be2ba9aa36bcccf8b9566c064b9ab264d0745fc2fdcb101f523656e6a88",
    "rs/s/b/n.class": "5b59b093891fd39d1a80a04300ebb9b10fa397e877a8d5e5262eb15c133ce724",
    "rs/s/t/l.class": "e3a9d20dadb0fcb02de38a9318b5e3d75579f0fea55df6c6ec8f3ee6975ed9d0",
}

AIRGAP_POST_SHA = {
    "rs/cache/b/e.class": "b2ea9097d8d523e9603fc59e8cb0f23035f47be34e859c13c0f599208018f3fb",
    "rs/Client.class": "270ec10d18cdb8afc7720dc708014ff81370002a2ae155ad87aed2200b7ab43a",
    "rs/f/a$a.class": "23055b1d4da44692c95eb39a0205b7288c50566945d610ea0f6bcd6979807b31",
    "rs/f/a.class": "d2002314df33c59525c1702781bf0e4ba5d42c6cc30c0e6877c9dc5d65fc3e18",
    "rs/l/d/d.class": "dd9e8f9ea27663d7e640504d06f36292e2a2090766b7909a832b73e32d571566",
    "rs/l/d/e.class": "1cf6bdec92bdb4c019fc076ef7f231caba0b9b52f0ac26ff3ec4cba2c80d40cd",
    "rs/gui/m.class": "c8df0b7afc6c34ec942302d26f08ee2a5554f69f0d14e3ad7a4d05e5cca84f7f",
    "rs/gui/n.class": "9ca2f3114e1e978404d51196083d0f3f2a49021d4b48a839f8e41d0abe75e8c0",
    "rs/gui/o.class": "45433c1eae61ea0fa5418d880606cd8b9e9d827885d971e2bbd841ecbfde2399",
    "rs/s/b/n.class": "fe5e30be23d8918c9fff4e26a352d0e434d3a4b7003c47eaa29f9f659d61046e",
    "rs/s/t/l.class": "e5efbffe0abf777390ac19b110fdfe66bd166467534ad3cbaeeee1c981206c48",
}

LOCALHOST_POST_SHA = {
    "rs/Client.class": "ed6be206a8d387e6efd16bf86292497e5c98794c597b1aef963fcb642b54d560",
    "rs/f/a.class": "b86bd49f29aab65afc6f8f2306d8ef2b228b9dfe3d8c417b937c09d49aaf224a",
}

# Exact classfile instruction sequence in v308 rs/f/a.<clinit>:
# iconst_0; invokestatic Boolean.valueOf; putstatic d
LOCAL_MODE_FALSE = bytes((0x03, 0xB8, 0x01, 0x17, 0xB3, 0x00, 0xED))
LOCAL_MODE_TRUE = bytes((0x04,)) + LOCAL_MODE_FALSE[1:]

# Exact v308 rs.Client.a(IIIIIIIIIZI)Z fallback-loop instruction prefix:
# bipush 100; istore 24; iconst_1; istore 25; iload 25; iconst_2; if_icmpge
# The exact rs/Client.class preimage is independently SHA-pinned above, and this
# 11-byte sequence occurs exactly once in that class. Changing only iconst_2 to
# iconst_3 expands the already-enabled Walk-here nearest-reachable fallback from
# radius 1 to radius 2 without changing collision masks, packet framing, method
# size, branch offsets, or StackMapTable positions.
WALK_FALLBACK_RADIUS_ONE = bytes(
    (0x10, 0x64, 0x36, 0x18, 0x04, 0x36, 0x19, 0x15, 0x19, 0x05, 0xA2)
)
WALK_FALLBACK_RADIUS_TWO = bytes(
    (0x10, 0x64, 0x36, 0x18, 0x04, 0x36, 0x19, 0x15, 0x19, 0x06, 0xA2)
)

AIRGAP_REPLACEMENTS = (
    (
        b"https://d2n1q79xme98rn.cloudfront.net/\x01\x01/",
        b"http://127.0.0.1:43595/\x01\x01/",
    ),
    (
        b"https://hqxg0u8s64.execute-api.ca-central-1.amazonaws.com/Production/tradingpost?search_text=\x01&page=\x01",
        b"http://127.0.0.1:43595/Production/tradingpost?search_text=\x01&page=\x01",
    ),
    (
        b"https://spawnpk.net/forums/index.php?/forum/23-report-a-player/",
        b"http://127.0.0.1:43595/",
    ),
    (
        b"https://spawnpk.net/forums/index.php?/forum/10-updates/",
        b"http://127.0.0.1:43595/",
    ),
    (
        b"https://spawnpk.net/forums/index.php?/topic/5710-the-official-spawnpk-price-guide-for-2017/",
        b"http://127.0.0.1:43595/",
    ),
    (b"http://spawnpk.net/forums/", b"http://127.0.0.1:43595/"),
    (b"https://spawnpk.net", b"http://127.0.0.1:43595"),
    (b"http://spawnpk.net", b"http://127.0.0.1:43595"),
    (b"www.spawnpk.net/register", b"127.0.0.1"),
    (b"www.spawnpk.org", b"127.0.0.1"),
)

FORBIDDEN_AIRGAP_TOKENS = (
    b"spawnpk.net",
    b"spawnpk.org",
    b"cloudfront.net",
    b"execute-api.",
    b"amazonaws.com",
)


def sha256_bytes(blob: bytes) -> str:
    return hashlib.sha256(blob).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def lexical_absolute(path: Path) -> Path:
    return Path(
        os.path.abspath(
            os.path.normpath(
                os.fspath(path)
            )
        )
    )


def lstat_or_none(path: Path):
    try:
        return os.lstat(path)
    except FileNotFoundError:
        return None


def is_reparse_or_symlink(st) -> bool:
    file_attributes = getattr(st, "st_file_attributes", 0)
    reparse_flag = getattr(
        stat,
        "FILE_ATTRIBUTE_REPARSE_POINT",
        0x400,
    )
    return (
        stat.S_ISLNK(st.st_mode)
        or bool(file_attributes & reparse_flag)
    )


def assert_ordinary_directory(path: Path, label: str):
    st = lstat_or_none(path)
    if st is None:
        raise RuntimeError(f"{label} directory missing: {path}")
    if is_reparse_or_symlink(st):
        raise RuntimeError(f"{label} must not be a reparse/symlink: {path}")
    if not stat.S_ISDIR(st.st_mode):
        raise RuntimeError(f"{label} is not an ordinary directory: {path}")
    return st


def assert_regular_file_or_absent(path: Path, label: str):
    st = lstat_or_none(path)
    if st is None:
        return None
    if is_reparse_or_symlink(st):
        raise RuntimeError(f"{label} must not be a reparse/symlink: {path}")
    if not stat.S_ISREG(st.st_mode):
        raise RuntimeError(f"{label} is not an ordinary regular file: {path}")
    return st


def file_identity(st) -> tuple[int, int, int, int]:
    return (
        int(st.st_dev),
        int(st.st_ino),
        int(st.st_size),
        int(st.st_mtime_ns),
    )


def assert_canonical_output_path(requested: Path) -> Path:
    repo = lexical_absolute(Path(__file__).parent.parent.parent)
    expected = lexical_absolute(repo / "local-client")
    output = lexical_absolute(requested)

    if os.path.normcase(os.fspath(output)) != os.path.normcase(os.fspath(expected)):
        raise RuntimeError(
            "canonical output lexical identity drift "
            f"expected={expected} actual={output}"
        )

    assert_ordinary_directory(repo, "repository root")

    output_state = lstat_or_none(output)
    if output_state is not None:
        assert_ordinary_directory(output, "canonical local-client")

    return output


def restore_moved_entry_without_follow(
    moved: Path,
    destination: Path,
    label: str,
) -> None:
    if lstat_or_none(destination) is not None:
        raise RuntimeError(
            f"{label} cannot restore moved entry because destination exists: "
            f"{destination}"
        )
    if lstat_or_none(moved) is None:
        raise RuntimeError(f"{label} moved entry disappeared: {moved}")
    os.replace(moved, destination)


def new_same_directory_leaf_path(destination: Path, purpose: str) -> Path:
    leaf = destination.with_name(
        f".{destination.name}.spawnpk-{purpose}-{uuid.uuid4().hex}.tmp"
    )
    if os.path.lexists(leaf):
        raise RuntimeError(f"private leaf collision: {leaf}")
    return leaf


def copy_verified_same_directory_leaf(
    source: Path,
    destination: Path,
    expected_sha: str,
    purpose: str,
) -> Path:
    prefix = f".{destination.name}.spawnpk-{purpose}-"
    fd, leaf_text = tempfile.mkstemp(
        prefix=prefix,
        suffix=".tmp",
        dir=destination.parent,
    )
    leaf = Path(leaf_text)
    complete = False

    try:
        with os.fdopen(fd, "wb") as target, source.open("rb") as source_handle:
            shutil.copyfileobj(source_handle, target, length=1024 * 1024)
            target.flush()
            os.fsync(target.fileno())

        actual = sha256_file(leaf)
        if actual != expected_sha:
            raise RuntimeError(
                "private leaf hash mismatch "
                f"purpose={purpose} expected={expected_sha} actual={actual}"
            )

        complete = True
        return leaf
    finally:
        if not complete and os.path.lexists(leaf):
            leaf_state = assert_regular_file_or_absent(
                leaf,
                f"{purpose} private leaf cleanup",
            )
            if leaf_state is not None:
                leaf.unlink()


def assert_verified_leaf(path: Path, expected_sha: str, label: str) -> None:
    st = assert_regular_file_or_absent(path, label)
    if st is None:
        raise RuntimeError(f"{label} private leaf missing: {path}")

    actual = sha256_file(path)
    if actual != expected_sha:
        raise RuntimeError(
            f"{label} private leaf ownership lost "
            f"expected={expected_sha} actual={actual} path={path}"
        )


def remove_verified_leaf(path, expected_sha: str, label: str) -> None:
    if path is None or not os.path.lexists(path):
        return
    assert_verified_leaf(path, expected_sha, label)
    path.unlink()


def assert_destination_snapshot_owned(record: dict) -> None:
    destination = record["destination"]
    current_state = assert_regular_file_or_absent(
        destination,
        f"{record['name']} canonical destination",
    )

    if record["existed"]:
        if current_state is None:
            raise RuntimeError(
                f"{record['name']} destination disappeared after backup snapshot"
            )

        current_identity = file_identity(current_state)
        if current_identity != record["snapshot_identity"]:
            raise RuntimeError(
                f"{record['name']} destination identity changed after backup snapshot "
                f"snapshot={record['snapshot_identity']} current={current_identity}"
            )

        backup_sha = sha256_file(record["backup"])
        current_sha = sha256_file(destination)
        if current_sha != backup_sha:
            raise RuntimeError(
                f"{record['name']} destination changed after backup snapshot "
                f"snapshot={backup_sha} current={current_sha}"
            )
    elif current_state is not None:
        raise RuntimeError(
            f"{record['name']} destination appeared after backup snapshot"
        )


def restore_private_leaf_without_overwrite(
    source_leaf: Path,
    destination: Path,
    expected_sha: str,
    label: str,
) -> None:
    assert_verified_leaf(source_leaf, expected_sha, label)
    source_state = assert_regular_file_or_absent(source_leaf, label)
    if source_state is None:
        raise RuntimeError(f"{label} source leaf disappeared")
    source_identity = file_identity(source_state)

    try:
        os.link(source_leaf, destination)
    except FileExistsError as error:
        raise RuntimeError(
            f"{label} destination appeared before no-overwrite restore: {destination}"
        ) from error

    restored_state = assert_regular_file_or_absent(
        destination,
        f"{label} restored destination",
    )
    if (
        restored_state is None
        or file_identity(restored_state) != source_identity
    ):
        raise RuntimeError(
            f"{label} restored destination identity does not match source leaf"
        )

    restored_sha = sha256_file(destination)
    if restored_sha != expected_sha:
        raise RuntimeError(
            f"{label} restored destination hash mismatch "
            f"expected={expected_sha} actual={restored_sha}"
        )

    source_leaf.unlink()


def patch_utf8_constants(data: bytes) -> bytes:
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")

    count = struct.unpack(">H", data[8:10])[0]
    pos = 10
    index = 1
    out = bytearray(data[:10])

    while index < count:
        tag = data[pos]
        out.append(tag)
        pos += 1

        if tag == 1:
            length = struct.unpack(">H", data[pos : pos + 2])[0]
            raw = data[pos + 2 : pos + 2 + length]
            pos += 2 + length
            new = raw

            for old, replacement in AIRGAP_REPLACEMENTS:
                new = new.replace(old, replacement)

            out += struct.pack(">H", len(new))
            out += new
        elif tag in (3, 4):
            out += data[pos : pos + 4]
            pos += 4
        elif tag in (5, 6):
            out += data[pos : pos + 8]
            pos += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            out += data[pos : pos + 2]
            pos += 2
        elif tag in (9, 10, 11, 12, 17, 18):
            out += data[pos : pos + 4]
            pos += 4
        elif tag == 15:
            out += data[pos : pos + 3]
            pos += 3
        else:
            raise ValueError(f"unsupported constant-pool tag {tag} at {index}")

        index += 1

    out += data[pos:]
    return bytes(out)


def toggle_local_mode(data: bytes) -> bytes:
    count = data.count(LOCAL_MODE_FALSE)
    if count != 1:
        raise ValueError(f"local-mode preimage count expected=1 actual={count}")
    return data.replace(LOCAL_MODE_FALSE, LOCAL_MODE_TRUE, 1)


def patch_walk_here_fallback_radius(data: bytes) -> bytes:
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("Walk-here fallback target is not a class file")

    old_count = data.count(WALK_FALLBACK_RADIUS_ONE)
    preexisting_new_count = data.count(WALK_FALLBACK_RADIUS_TWO)
    if old_count != 1 or preexisting_new_count != 0:
        raise ValueError(
            "Walk-here fallback preimage drift "
            f"radius1Count={old_count} radius2Count={preexisting_new_count}"
        )

    patched = data.replace(
        WALK_FALLBACK_RADIUS_ONE,
        WALK_FALLBACK_RADIUS_TWO,
        1,
    )

    old_post_count = patched.count(WALK_FALLBACK_RADIUS_ONE)
    new_post_count = patched.count(WALK_FALLBACK_RADIUS_TWO)
    if old_post_count != 0 or new_post_count != 1:
        raise ValueError(
            "Walk-here fallback postimage drift "
            f"radius1Count={old_post_count} radius2Count={new_post_count}"
        )

    if len(patched) != len(data):
        raise ValueError("Walk-here fallback patch changed classfile length")

    return patched


def selftest_walk_here_fallback_patch() -> None:
    synthetic = (
        b"\xca\xfe\xba\xbe"
        + b"prefix"
        + WALK_FALLBACK_RADIUS_ONE
        + b"suffix"
    )
    patched = patch_walk_here_fallback_radius(synthetic)

    expected = (
        b"\xca\xfe\xba\xbe"
        + b"prefix"
        + WALK_FALLBACK_RADIUS_TWO
        + b"suffix"
    )
    if patched != expected:
        raise ValueError("Walk-here fallback selftest postimage mismatch")

    for label, invalid in (
        ("missing", b"\xca\xfe\xba\xbe" + b"no-pattern"),
        (
            "duplicate",
            b"\xca\xfe\xba\xbe"
            + WALK_FALLBACK_RADIUS_ONE
            + WALK_FALLBACK_RADIUS_ONE,
        ),
        (
            "already-patched",
            b"\xca\xfe\xba\xbe" + WALK_FALLBACK_RADIUS_TWO,
        ),
    ):
        try:
            patch_walk_here_fallback_radius(invalid)
        except ValueError:
            continue
        raise ValueError(
            f"Walk-here fallback selftest did not fail closed: {label}"
        )

    print(
        "V308_WALK_HERE_FALLBACK_PATCH_SELFTEST_PASS "
        "radiusBefore=1 radiusAfter=2 byteLengthNeutral=true failClosed=true"
    )


def deterministic_info(source: zipfile.ZipInfo) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(source.filename, (1980, 1, 1, 0, 0, 0))
    # Store entries without deflate so whole-JAR bytes do not depend on the
    # caller's Python/zlib version. Class/resource payload bytes remain exact.
    info.compress_type = zipfile.ZIP_STORED
    info.external_attr = source.external_attr
    info.create_system = source.create_system
    info.flag_bits = source.flag_bits & ~0x08
    return info


def build_variant(source: Path, output: Path, airgap: bool) -> dict:
    expected_post = AIRGAP_POST_SHA if airgap else LOCALHOST_POST_SHA
    changed = {}

    with zipfile.ZipFile(source, "r") as zin, zipfile.ZipFile(
        output,
        "w",
        compression=zipfile.ZIP_STORED,
    ) as zout:
        for source_info in zin.infolist():
            raw = zin.read(source_info.filename)
            patched = raw

            if source_info.filename in PREIMAGE_SHA:
                actual_pre = sha256_bytes(raw)
                expected_pre = PREIMAGE_SHA[source_info.filename]

                if actual_pre != expected_pre:
                    raise ValueError(
                        f"preimage drift {source_info.filename} "
                        f"expected={expected_pre} actual={actual_pre}"
                    )

            if source_info.filename.endswith(".class") and airgap:
                patched = patch_utf8_constants(patched)

            if source_info.filename == "rs/Client.class":
                patched = patch_walk_here_fallback_radius(patched)

            if source_info.filename == "rs/f/a.class":
                patched = toggle_local_mode(patched)

            if patched != raw:
                changed[source_info.filename] = {
                    "preSha256": sha256_bytes(raw),
                    "postSha256": sha256_bytes(patched),
                }

            zout.writestr(deterministic_info(source_info), patched)

    if set(changed) != set(expected_post):
        raise ValueError(
            f"changed-entry set drift expected={sorted(expected_post)} "
            f"actual={sorted(changed)}"
        )

    for name, expected in expected_post.items():
        actual = changed[name]["postSha256"]
        if actual != expected:
            raise ValueError(
                f"postimage drift {name} expected={expected} actual={actual}"
            )

    return changed


def assert_container_invariants(
    source: Path,
    output: Path,
    changed_entries: set[str],
) -> None:
    with zipfile.ZipFile(source, "r") as original, zipfile.ZipFile(
        output,
        "r",
    ) as rebuilt:
        original_names = original.namelist()
        rebuilt_names = rebuilt.namelist()

        if len(original_names) != len(set(original_names)):
            raise ValueError("exact v308 input JAR contains duplicate entry names")

        if original_names != rebuilt_names:
            raise ValueError("rebuilt JAR entry order/name inventory drift")

        signatures = [
            name
            for name in original_names
            if name.upper().startswith("META-INF/")
            and name.upper().endswith((".SF", ".RSA", ".DSA", ".EC"))
        ]
        if signatures:
            raise ValueError(
                "signed exact-client JAR is outside this patch authority: "
                + repr(signatures)
            )

        manifest_name = "META-INF/MANIFEST.MF"
        if manifest_name not in original_names:
            raise ValueError("exact v308 JAR manifest missing")

        manifest = original.read(manifest_name)
        if b"Main-Class: rs.gui.Launcher" not in manifest:
            raise ValueError("exact v308 JAR main-class authority drift")

        if rebuilt.read(manifest_name) != manifest:
            raise ValueError("rebuilt JAR manifest bytes changed")

        for name in original_names:
            if name in changed_entries:
                continue

            if rebuilt.read(name) != original.read(name):
                raise ValueError(
                    "unrelated JAR entry payload changed: " + name
                )


def assert_airgap_no_external_authority(path: Path) -> None:
    remaining = []

    with zipfile.ZipFile(path, "r") as zf:
        for name in zf.namelist():
            if not name.endswith(".class"):
                continue

            lower = zf.read(name).lower()
            hits = [
                token.decode("ascii")
                for token in FORBIDDEN_AIRGAP_TOKENS
                if token in lower
            ]

            if hits:
                remaining.append((name, hits))

    if remaining:
        raise ValueError(f"airgap external endpoint authority remains: {remaining}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--selftest-walk-here",
        action="store_true",
        help="run the source-only fail-closed Walk-here byte-patch regression",
    )
    parser.add_argument("exact_v308_jar", type=Path, nargs="?")
    parser.add_argument("output_directory", type=Path, nargs="?")
    args = parser.parse_args()

    if args.selftest_walk_here:
        if args.exact_v308_jar is not None or args.output_directory is not None:
            parser.error(
                "--selftest-walk-here does not accept JAR/output positional arguments"
            )
        selftest_walk_here_fallback_patch()
        return 0

    if args.exact_v308_jar is None or args.output_directory is None:
        parser.error(
            "exact_v308_jar and output_directory are required outside selftest mode"
        )

    source = args.exact_v308_jar.resolve()
    output = assert_canonical_output_path(args.output_directory)

    if not source.is_file():
        raise SystemExit(f"exact v308 JAR missing: {source}")

    actual_input = sha256_file(source)
    if actual_input != INPUT_SHA:
        raise SystemExit(
            f"exact v308 SHA mismatch expected={INPUT_SHA} actual={actual_input}"
        )

    with tempfile.TemporaryDirectory(
        prefix="spawnpk-v308-local-client-build-"
    ) as transaction_dir_text:
        transaction_dir = Path(transaction_dir_text)
        stage = transaction_dir / "stage"
        backup = transaction_dir / "backup"
        stage.mkdir()
        backup.mkdir()

        localhost = stage / "client-localhost.jar"
        airgap = stage / "client-airgap.jar"
        staged_manifest = stage / "v308-local-client-patch-manifest.json"

        # Generate only into transaction-owned staging. Canonical runtime paths
        # remain untouched until every existing deterministic check has passed.
        localhost_changed = build_variant(source, localhost, airgap=False)
        airgap_changed = build_variant(source, airgap, airgap=True)

        assert_container_invariants(
            source,
            localhost,
            set(localhost_changed),
        )
        assert_container_invariants(
            source,
            airgap,
            set(airgap_changed),
        )
        assert_airgap_no_external_authority(airgap)

        localhost_sha = sha256_file(localhost)
        airgap_sha = sha256_file(airgap)

        if localhost_sha != LOCALHOST_SHA:
            raise SystemExit(
                f"localhost JAR SHA mismatch expected={LOCALHOST_SHA} actual={localhost_sha}"
            )
        if airgap_sha != AIRGAP_SHA:
            raise SystemExit(
                f"airgap JAR SHA mismatch expected={AIRGAP_SHA} actual={airgap_sha}"
            )

        manifest = {
            "format": "spawnpk-v308-local-client-patch-v1",
            "inputSha256": INPUT_SHA,
            "localhostSha256": localhost_sha,
            "airgapSha256": airgap_sha,
            "localhostChangedEntries": localhost_changed,
            "airgapChangedEntries": airgap_changed,
            "airgapExternalEndpointAuthority": False,
            "gamePort": 43594,
            "auxPort": 43595,
            "loopbackHost": "127.0.0.1",
            "updaterBase": "http://127.0.0.1:43595/spk_live/",
            "jarEntryCompression": "stored",
            "wholeJarDeterminismIndependentOfZlib": True,
            "walkHereFallbackRadius": 2,
            "walkHereFallbackExactMethod": "rs.Client.a(IIIIIIIIIZI)Z",
            "walkHereFallbackByteLengthNeutral": True,
            "unchangedEntryPayloadIdentity": True,
            "entryInventoryAndOrderPreserved": True,
            "manifestPayloadPreserved": True,
            "transactionalPublication": True,
        }

        staged_manifest.write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        staged = (
            ("client-localhost.jar", localhost, LOCALHOST_SHA),
            ("client-airgap.jar", airgap, AIRGAP_SHA),
            (
                "v308-local-client-patch-manifest.json",
                staged_manifest,
                sha256_file(staged_manifest),
            ),
        )

        print(
            "V308_LOCAL_CLIENT_STAGE_VERIFY_PASS "
            f"localhost={localhost_sha} airgap={airgap_sha} count={len(staged)}"
        )

        output_existed = os.path.lexists(output)
        output_created = False
        if output_existed:
            assert_ordinary_directory(output, "canonical local-client")
        else:
            os.mkdir(output)
            output_created = True
            assert_ordinary_directory(
                output,
                "transaction-created canonical local-client",
            )

        publication = []
        for index, (name, staged_path, expected_sha) in enumerate(staged):
            assert_ordinary_directory(
                output,
                "canonical local-client before destination snapshot",
            )
            destination = output / name
            backup_path = backup / f"{index}-{name}"
            destination_state = assert_regular_file_or_absent(
                destination,
                f"{name} canonical destination snapshot",
            )
            existed = destination_state is not None
            snapshot_identity = (
                file_identity(destination_state)
                if destination_state is not None
                else None
            )

            if existed:
                shutil.copyfile(destination, backup_path)

                destination_after_backup = assert_regular_file_or_absent(
                    destination,
                    f"{name} canonical destination after backup",
                )
                if (
                    destination_after_backup is None
                    or file_identity(destination_after_backup) != snapshot_identity
                ):
                    raise SystemExit(
                        f"canonical destination identity changed during backup: {destination}"
                    )

                if sha256_file(backup_path) != sha256_file(destination):
                    raise SystemExit(
                        f"rollback backup verification failed: {destination}"
                    )

            publication.append(
                {
                    "name": name,
                    "stage": staged_path,
                    "destination": destination,
                    "expected_sha": expected_sha,
                    "backup": backup_path,
                    "existed": existed,
                    "snapshot_identity": snapshot_identity,
                    "publish_leaf": None,
                    "preimage_leaf": None,
                    "committed": False,
                    "published_sha": None,
                    "published_identity": None,
                }
            )

        print(
            "V308_LOCAL_CLIENT_BACKUP_READY "
            f"count={len(publication)}"
        )

        touched = []
        try:
            for record in publication:
                destination = record["destination"]

                record["publish_leaf"] = copy_verified_same_directory_leaf(
                    record["stage"],
                    destination,
                    record["expected_sha"],
                    "publish",
                )

                # Re-prove both sides of the canonical transition immediately
                # before acquiring mutation ownership.
                assert_destination_snapshot_owned(record)
                assert_verified_leaf(
                    record["publish_leaf"],
                    record["expected_sha"],
                    f"{record['name']} publish",
                )

                touched.append(record)

                if record["existed"]:
                    backup_sha = sha256_file(record["backup"])
                    record["preimage_leaf"] = new_same_directory_leaf_path(
                        destination,
                        "commit-preimage",
                    )

                    # Capture the exact canonical file at commit time instead
                    # of overwriting it. This transition is same-directory and
                    # atomic. The captured bytes must still equal the frozen
                    # rollback snapshot.
                    os.replace(
                        destination,
                        record["preimage_leaf"],
                    )

                    try:
                        moved_state = assert_regular_file_or_absent(
                            record["preimage_leaf"],
                            f"{record['name']} moved commit preimage",
                        )
                        if moved_state is None:
                            raise RuntimeError("moved commit preimage disappeared")

                        moved_identity = file_identity(moved_state)
                        if moved_identity != record["snapshot_identity"]:
                            raise RuntimeError(
                                "moved commit preimage identity drift "
                                f"snapshot={record['snapshot_identity']} "
                                f"moved={moved_identity}"
                            )

                        displaced_sha = sha256_file(record["preimage_leaf"])
                    except BaseException:
                        if not os.path.lexists(destination):
                            restore_moved_entry_without_follow(
                                record["preimage_leaf"],
                                destination,
                                f"{record['name']} commit preimage compensation",
                            )
                            record["preimage_leaf"] = None
                        raise

                    if displaced_sha != backup_sha:
                        restore_moved_entry_without_follow(
                            record["preimage_leaf"],
                            destination,
                            f"{record['name']} changed commit preimage",
                        )
                        record["preimage_leaf"] = None

                        raise RuntimeError(
                            f"{record['name']} destination changed during commit "
                            f"snapshot={backup_sha} displaced={displaced_sha}"
                        )

                # Hard-link creation is no-overwrite on both Windows and Unix.
                # It creates the canonical directory entry only if the name is
                # still absent; the verified private leaf remains available
                # until that discrete transition succeeds.
                publish_leaf_state = assert_regular_file_or_absent(
                    record["publish_leaf"],
                    f"{record['name']} publish leaf before link",
                )
                if publish_leaf_state is None:
                    raise RuntimeError(
                        f"{record['name']} publish leaf disappeared before link"
                    )
                publish_identity = file_identity(publish_leaf_state)

                try:
                    os.link(
                        record["publish_leaf"],
                        destination,
                    )
                except FileExistsError as error:
                    if record["preimage_leaf"] is not None:
                        preimage_sha = sha256_file(record["preimage_leaf"])
                        if not os.path.lexists(destination):
                            restore_private_leaf_without_overwrite(
                                record["preimage_leaf"],
                                destination,
                                preimage_sha,
                                f"{record['name']} failed commit preimage",
                            )
                            record["preimage_leaf"] = None

                    raise RuntimeError(
                        f"{record['name']} destination appeared before atomic publish"
                    ) from error

                published_state = assert_regular_file_or_absent(
                    destination,
                    f"{record['name']} canonical published destination",
                )
                if (
                    published_state is None
                    or file_identity(published_state) != publish_identity
                ):
                    raise RuntimeError(
                        f"{record['name']} canonical publish identity drift"
                    )

                record["committed"] = True
                record["published_sha"] = record["expected_sha"]
                record["published_identity"] = publish_identity

                # Canonical and private leaf now refer to the same exact inode.
                # Drop only the transaction-owned private name.
                remove_verified_leaf(
                    record["publish_leaf"],
                    record["expected_sha"],
                    f"{record['name']} committed publish",
                )
                record["publish_leaf"] = None

            # Re-verify the complete generated set while rollback ownership is
            # still active. No success output is emitted before this finishes.
            for record in publication:
                final_state = assert_regular_file_or_absent(
                    record["destination"],
                    f"{record['name']} final canonical destination",
                )
                if (
                    final_state is None
                    or file_identity(final_state) != record["published_identity"]
                ):
                    raise RuntimeError(
                        f"{record['name']} final canonical identity drift"
                    )

                final_sha = sha256_file(record["destination"])
                if final_sha != record["expected_sha"]:
                    raise RuntimeError(
                        "final generated artifact hash mismatch "
                        f"name={record['name']} "
                        f"expected={record['expected_sha']} "
                        f"actual={final_sha}"
                    )

            # Existing-destination preimages remain rollback authority until
            # the whole generated set is verified. Retire them only now.
            for record in publication:
                if record["preimage_leaf"] is None:
                    continue

                backup_sha = sha256_file(record["backup"])
                remove_verified_leaf(
                    record["preimage_leaf"],
                    backup_sha,
                    f"{record['name']} committed preimage",
                )
                record["preimage_leaf"] = None

            print(
                "V308_LOCAL_CLIENT_FINAL_VERIFY_PASS "
                f"count={len(publication)} rollbackOwned=true"
            )
        except BaseException as publish_error:
            rollback_errors = []

            for record in reversed(touched):
                try:
                    destination = record["destination"]

                    if not record["committed"]:
                        remove_verified_leaf(
                            record["publish_leaf"],
                            record["expected_sha"],
                            f"{record['name']} uncommitted publish",
                        )
                        record["publish_leaf"] = None

                        if record["preimage_leaf"] is not None:
                            preimage_sha = sha256_file(record["preimage_leaf"])
                            if os.path.lexists(destination):
                                raise RuntimeError(
                                    f"{record['name']} cannot restore uncommitted "
                                    "preimage because destination was recreated"
                                )

                            restore_private_leaf_without_overwrite(
                                record["preimage_leaf"],
                                destination,
                                preimage_sha,
                                f"{record['name']} uncommitted preimage",
                            )
                            record["preimage_leaf"] = None
                        continue

                    current_state = assert_regular_file_or_absent(
                        destination,
                        f"{record['name']} committed rollback destination",
                    )
                    if current_state is None:
                        raise RuntimeError(
                            f"{record['name']} committed destination disappeared"
                        )

                    current_identity = file_identity(current_state)
                    if current_identity != record["published_identity"]:
                        raise RuntimeError(
                            f"{record['name']} rollback lifetime identity lost "
                            f"published={record['published_identity']} "
                            f"current={current_identity}"
                        )

                    current_sha = sha256_file(destination)
                    if current_sha != record["published_sha"]:
                        raise RuntimeError(
                            f"{record['name']} rollback ownership lost "
                            f"published={record['published_sha']} current={current_sha}"
                        )

                    quarantine = new_same_directory_leaf_path(
                        destination,
                        "rollback-published",
                    )
                    os.replace(destination, quarantine)

                    try:
                        quarantine_state = assert_regular_file_or_absent(
                            quarantine,
                            f"{record['name']} rollback quarantine",
                        )
                        if quarantine_state is None:
                            raise RuntimeError("rollback quarantine disappeared")
                        quarantine_identity = file_identity(quarantine_state)
                        if quarantine_identity != current_identity:
                            raise RuntimeError(
                                "rollback quarantine identity drift "
                                f"before={current_identity} moved={quarantine_identity}"
                            )
                        quarantined_sha = sha256_file(quarantine)
                    except BaseException:
                        if not os.path.lexists(destination):
                            restore_moved_entry_without_follow(
                                quarantine,
                                destination,
                                f"{record['name']} rollback quarantine compensation",
                            )
                            quarantine = None
                        raise

                    if quarantined_sha != record["published_sha"]:
                        if not os.path.lexists(destination):
                            restore_moved_entry_without_follow(
                                quarantine,
                                destination,
                                f"{record['name']} changed rollback publication",
                            )
                            quarantine = None

                        raise RuntimeError(
                            f"{record['name']} rollback ownership changed during "
                            f"quarantine published={record['published_sha']} "
                            f"moved={quarantined_sha}"
                        )

                    if record["existed"]:
                        backup_sha = sha256_file(record["backup"])

                        if record["preimage_leaf"] is None:
                            record["preimage_leaf"] = (
                                copy_verified_same_directory_leaf(
                                    record["backup"],
                                    destination,
                                    backup_sha,
                                    "rollback-restore",
                                )
                            )

                        try:
                            restore_private_leaf_without_overwrite(
                                record["preimage_leaf"],
                                destination,
                                backup_sha,
                                f"{record['name']} rollback preimage",
                            )
                            record["preimage_leaf"] = None
                        except BaseException:
                            if not os.path.lexists(destination):
                                restore_private_leaf_without_overwrite(
                                    quarantine,
                                    destination,
                                    quarantined_sha,
                                    f"{record['name']} rollback compensation",
                                )
                                quarantine = None
                            raise

                        restored_sha = sha256_file(destination)
                        if restored_sha != backup_sha:
                            raise RuntimeError(
                                f"{record['name']} restored destination does not "
                                "match rollback backup"
                            )
                    elif os.path.lexists(destination):
                        raise RuntimeError(
                            f"{record['name']} absent-before destination was "
                            "recreated during rollback"
                        )

                    remove_verified_leaf(
                        quarantine,
                        record["published_sha"],
                        f"{record['name']} rollback quarantine",
                    )
                except BaseException as rollback_error:
                    rollback_errors.append(
                        f"{record['name']}: {rollback_error}"
                    )

            # Clean only exact transaction-owned private leaves that remain.
            for record in publication:
                try:
                    remove_verified_leaf(
                        record["publish_leaf"],
                        record["expected_sha"],
                        f"{record['name']} residual publish",
                    )
                    record["publish_leaf"] = None
                except BaseException as leaf_error:
                    rollback_errors.append(
                        f"{record['name']} publish-leaf: {leaf_error}"
                    )

                try:
                    if record["preimage_leaf"] is not None:
                        # If an originally existing destination cannot be
                        # restored because another process recreated the
                        # canonical name, preserve the exact pre-transaction
                        # bytes rather than deleting the only same-directory
                        # recovery copy.
                        if (
                            record["existed"]
                            and not record["committed"]
                            and os.path.lexists(record["destination"])
                        ):
                            rollback_errors.append(
                                f"{record['name']} preimage preserved after "
                                "rollback ownership loss: "
                                f"{record['preimage_leaf']}"
                            )
                            continue

                        expected_preimage_sha = (
                            sha256_file(record["backup"])
                            if record["existed"]
                            else record["expected_sha"]
                        )
                        remove_verified_leaf(
                            record["preimage_leaf"],
                            expected_preimage_sha,
                            f"{record['name']} residual preimage",
                        )
                        record["preimage_leaf"] = None
                except BaseException as leaf_error:
                    rollback_errors.append(
                        f"{record['name']} preimage-leaf: {leaf_error}"
                    )

            if output_created:
                try:
                    assert_ordinary_directory(
                        output,
                        "transaction-created canonical local-client rollback",
                    )
                    os.rmdir(output)
                except BaseException as directory_error:
                    rollback_errors.append(
                        "canonical local-client directory cleanup: "
                        f"{directory_error}"
                    )

            if rollback_errors:
                raise RuntimeError(
                    "v308 local-client publication failed and rollback was incomplete; "
                    f"publish={publish_error}; "
                    f"rollback={' | '.join(rollback_errors)}"
                ) from publish_error

            print(
                "V308_LOCAL_CLIENT_ROLLBACK_COMPLETE "
                f"restored={len(touched)}"
            )
            raise

        print(
            "V308_LOCAL_CLIENT_PATCH_PASS "
            f"input={INPUT_SHA} "
            f"localhost={localhost_sha} "
            f"airgap={airgap_sha} "
            f"localhostChanged={len(localhost_changed)} "
            f"airgapChanged={len(airgap_changed)} "
            "externalEndpointAuthority=false "
            "unchangedEntryPayloadIdentity=true "
            "transactionalPublication=true"
        )
        return 0


if __name__ == "__main__":
    raise SystemExit(main())

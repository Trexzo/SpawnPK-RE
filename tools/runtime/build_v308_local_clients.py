#!/usr/bin/env python3
"""Build deterministic LocalLab localhost/airgap variants from exact SpawnPK v308.

No client binary is committed. This patcher accepts only the exact pinned v308 JAR and
fails closed on every transformed class preimage.

localhost:
- enables the client's already-present local socket mode;
- game/AUX socket host becomes 127.0.0.1;
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
import shutil
import struct
import tempfile
import zipfile
from pathlib import Path

INPUT_SHA = "854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6"
LOCALHOST_SHA = "01c878a56ee25fb112dfe8b459dbd11ea26cfa8a92a7f287a4e5ee53f673cdbd"
AIRGAP_SHA = "83b3e27e2aae50512d044ae4c74d84afb36df8b8a8051b5eb0c9275427363c33"

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
    "rs/Client.class": "5b77aa27a32f752101af6d160f217c4d1fba6024ff5d2bd78102f4afd232b8f2",
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
    "rs/f/a.class": "b86bd49f29aab65afc6f8f2306d8ef2b228b9dfe3d8c417b937c09d49aaf224a",
}

# Exact classfile instruction sequence in v308 rs/f/a.<clinit>:
# iconst_0; invokestatic Boolean.valueOf; putstatic d
LOCAL_MODE_FALSE = bytes((0x03, 0xB8, 0x01, 0x17, 0xB3, 0x00, 0xED))
LOCAL_MODE_TRUE = bytes((0x04,)) + LOCAL_MODE_FALSE[1:]

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
    parser.add_argument("exact_v308_jar", type=Path)
    parser.add_argument("output_directory", type=Path)
    args = parser.parse_args()

    source = args.exact_v308_jar.resolve()
    output = args.output_directory.resolve()

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

        output_existed = output.exists()
        output.mkdir(parents=True, exist_ok=True)

        publication = []
        for index, (name, staged_path, expected_sha) in enumerate(staged):
            destination = output / name
            backup_path = backup / f"{index}-{name}"
            existed = destination.is_file()

            if destination.exists() and not existed:
                raise SystemExit(
                    f"canonical generated output is not a regular file: {destination}"
                )

            if existed:
                shutil.copyfile(destination, backup_path)
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
                }
            )

        print(
            "V308_LOCAL_CLIENT_BACKUP_READY "
            f"count={len(publication)}"
        )

        touched = []
        try:
            for record in publication:
                # Record rollback ownership before the first canonical write.
                touched.append(record)
                shutil.copyfile(record["stage"], record["destination"])

                published_sha = sha256_file(record["destination"])
                if published_sha != record["expected_sha"]:
                    raise RuntimeError(
                        "published artifact hash mismatch "
                        f"name={record['name']} "
                        f"expected={record['expected_sha']} "
                        f"actual={published_sha}"
                    )

            # Re-verify the complete generated set while rollback ownership is
            # still active. No success output is emitted before this finishes.
            for record in publication:
                final_sha = sha256_file(record["destination"])
                if final_sha != record["expected_sha"]:
                    raise RuntimeError(
                        "final generated artifact hash mismatch "
                        f"name={record['name']} "
                        f"expected={record['expected_sha']} "
                        f"actual={final_sha}"
                    )

            print(
                "V308_LOCAL_CLIENT_FINAL_VERIFY_PASS "
                f"count={len(publication)} rollbackOwned=true"
            )
        except BaseException as publish_error:
            rollback_errors = []

            for record in reversed(touched):
                try:
                    if record["existed"]:
                        shutil.copyfile(
                            record["backup"],
                            record["destination"],
                        )
                        restored_sha = sha256_file(record["destination"])
                        backup_sha = sha256_file(record["backup"])
                        if restored_sha != backup_sha:
                            raise RuntimeError(
                                "restored destination does not match rollback backup"
                            )
                    elif record["destination"].exists():
                        record["destination"].unlink()
                except BaseException as rollback_error:
                    rollback_errors.append(
                        f"{record['name']}: {rollback_error}"
                    )

            if not output_existed:
                try:
                    output.rmdir()
                except OSError:
                    pass

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

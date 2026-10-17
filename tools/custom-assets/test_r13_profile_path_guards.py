#!/usr/bin/env python3
"""Focused path-containment regression for the R13 isolated profile builder."""
from __future__ import annotations

import importlib.util
import sys
import tempfile
from pathlib import Path


def load_builder():
    path = Path(__file__).with_name("build_r13_isolated_profile.py")
    spec = importlib.util.spec_from_file_location(
        "r13_profile_builder_under_test",
        path,
    )
    if spec is None or spec.loader is None:
        raise AssertionError("cannot import R13 profile builder")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def require_rejected(builder, base: Path, output: Path, real_home: Path) -> None:
    try:
        builder.validate_output_home(base, output, real_home)
    except ValueError:
        return
    raise AssertionError(f"expected isolated-home rejection: {output}")


def main() -> None:
    builder = load_builder()

    with tempfile.TemporaryDirectory(prefix="r13-path-guard-") as raw:
        root = Path(raw).resolve()
        source_home = root / "source-home"
        base = source_home / ".spawnpk"
        base.mkdir(parents=True)

        real_home = root / "real-home"
        real_home.mkdir()

        separate = root / "isolated-output"

        require_rejected(builder, base, real_home, real_home)
        require_rejected(builder, base, source_home, real_home)
        require_rejected(builder, base, base, real_home)
        require_rejected(builder, base, base / "nested-home", real_home)

        builder.validate_output_home(
            base,
            separate,
            real_home,
        )

        separate.mkdir()

        require_rejected(
            builder,
            base,
            separate,
            real_home,
        )

    print(
        "R13_ISOLATED_PROFILE_PATH_GUARD_PASS "
        "realHomeRejected=true "
        "sourceHomeRejected=true "
        "cacheRootRejected=true "
        "cacheDescendantRejected=true "
        "existingOutputRejected=true "
        "separateSandboxAccepted=true"
    )


if __name__ == "__main__":
    main()

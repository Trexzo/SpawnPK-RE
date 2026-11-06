#!/usr/bin/env python3
"""R13 Windows GUI egress admission — audit only, NEVER launches game Java.

A v308 airgap JAR's static constant rewriting does not prove OS network
isolation. Before allowing concurrency with existing v312 sessions, require
an enabled Windows Firewall *ActiveStore* program-wide outbound BLOCK rule
bound to a PRIVATE executable that no existing running Java client uses.

This program NEVER creates a firewall rule, launches/stops a process, or
changes user settings. It reports an explicit BLOCKED until external OS
isolation can be verified. Passing this audit is NOT graphical acceptance.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from typing import Any


class GuardError(RuntimeError):
    pass


@dataclass(frozen=True)
class GateCheck:
    key: str
    ok: bool
    detail: str


def powershell(script: str, *, timeout: float = 12) -> tuple[bool, str]:
    try:
        result = subprocess.run(
            ["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, errors="replace", timeout=timeout, check=False,
        )
        return result.returncode == 0, result.stdout.strip()
    except (OSError, subprocess.TimeoutExpired):
        return False, "powershell unavailable"


def path_eq(left: str, right: str) -> bool:
    return os.path.normcase(os.path.normpath(left)) == os.path.normcase(os.path.normpath(right))


def basic_private_exe(exe: Path, private_root: Path) -> GateCheck:
    # Caller must own a separate JDK installation. Never apply a program-wide
    # egress rule to a JDK executable already used by live v312 clients.
    if not exe.is_absolute() or not private_root.is_absolute():
        return GateCheck("dedicated_java", False, "absolute paths required")
    try:
        actual = exe.resolve(strict=True)
        root = private_root.resolve(strict=True)
        if not actual.is_file() or not root.is_dir() or exe.is_symlink():
            return GateCheck("dedicated_java", False, "Java exe/private root must be regular")
        # Only a separately snapshotted JDK in an invocation-owned
        # temp workspace may be protected by our per-program firewall rule.
        # A shared JDK under Program Files is NOT a dedicated executable.
        temp = Path(tempfile.gettempdir()).resolve()
        if (root.name != "jdk" or
                not root.parent.name.startswith("spawnpk-r13-owned-jdk-") or
                root.parent.parent != temp or
                root.parent.is_symlink()):
            return GateCheck("dedicated_java", False, "not an R13 invocation-owned private JDK snapshot")
        if not actual.is_relative_to(root) or actual.name.lower() not in ("java.exe", "javaw.exe"):
            return GateCheck("dedicated_java", False, "Java executable must be inside owned private JDK")
    except (OSError, ValueError):
        return GateCheck("dedicated_java", False, "dedicated Java path absent or unresolved")
    return GateCheck("dedicated_java", True, f"private_executable={actual}")


def firewall_record(exe: Path, rule_name: str) -> GateCheck:
    if os.name != "nt":
        return GateCheck("active_os_egress_block", False, "requires Windows native firewall")
    if not re.fullmatch(r"[A-Za-z0-9_. -]{4,90}", rule_name):
        return GateCheck("active_os_egress_block", False, "invalid exact firewall rule label")
    # Caller controls rule_name but we only interpolate after the restrictive
    # printable allowlist. This is read-only PowerShell.
    name = rule_name.replace("'", "''")
    script = (
        "$ErrorActionPreference='Stop'; "
        f"$r=@(Get-NetFirewallRule -PolicyStore ActiveStore -DisplayName '{name}' -ErrorAction Stop); "
        "if($r.Count -ne 1){exit 12}; "
        "$q=$r[0]; "
        "$program=@($q | Get-NetFirewallApplicationFilter -ErrorAction Stop); "
        "$addr=@($q | Get-NetFirewallAddressFilter -ErrorAction Stop); "
        "$ports=@($q | Get-NetFirewallPortFilter -ErrorAction Stop); "
        "$service=(Get-Service -Name mpssvc -ErrorAction Stop).Status.ToString(); "
        "$profiles=@(Get-NetFirewallProfile -PolicyStore ActiveStore -ErrorAction Stop | "
        "Select-Object Name,Enabled); "
        "[pscustomobject]@{Name=$q.DisplayName; Enabled=$q.Enabled.ToString();"
        "Direction=$q.Direction.ToString(); Action=$q.Action.ToString();"
        "Profile=$q.Profile.ToString(); Programs=@($program|ForEach-Object{$_.Program});"
        "RemoteAddresses=@($addr|ForEach-Object{$_.RemoteAddress});"
        "Protocols=@($ports|ForEach-Object{$_.Protocol.ToString()});"
        "RemotePorts=@($ports|ForEach-Object{$_.RemotePort});"
        "FirewallService=$service; Profiles=$profiles} | ConvertTo-Json -Depth 5 -Compress"
    )
    ok, raw = powershell(script)
    if not ok:
        return GateCheck("active_os_egress_block", False, "no exact ActiveStore firewall rule or OS inspection unavailable")
    try:
        rec = json.loads(raw)
        programs = rec.get("Programs") or []
        addresses = rec.get("RemoteAddresses") or []
        protocols = rec.get("Protocols") or []
        ports = rec.get("RemotePorts") or []
        profiles = rec.get("Profiles") or []
        # Powershell JSON scalar values must be handled as arrays deliberately.
        for item in (programs, addresses, protocols, ports, profiles):
            if not isinstance(item, list):
                raise ValueError("firewall JSON filter unexpectedly scalar")
        same_exe = len(programs) == 1 and isinstance(programs[0], str) and path_eq(programs[0], str(exe.resolve()))
        all_addresses = len(addresses) == 1 and str(addresses[0]).lower() in ("any", "*")
        all_protocols = len(protocols) == 1 and str(protocols[0]).lower() in ("any", "256")
        all_ports = len(ports) == 1 and str(ports[0]).lower() in ("any", "0")
        enabled_profiles = (
            len(profiles) >= 3 and
            all(p.get("Enabled") is True for p in profiles) and
            {"domain", "private", "public"}.issubset(
                {str(p.get("Name", "")).lower() for p in profiles}
            )
        )
        valid = (
            rec.get("Enabled") == "True" and
            rec.get("Action") == "Block" and
            rec.get("Direction") == "Outbound" and
            rec.get("Profile") in ("Any", "Domain, Private, Public") and
            rec.get("FirewallService") == "Running" and
            enabled_profiles and same_exe and all_addresses and all_protocols and all_ports
        )
    except (TypeError, ValueError, KeyError, OSError):
        return GateCheck("active_os_egress_block", False, "firewall record malformed")
    return GateCheck("active_os_egress_block", valid,
                     "active_program_block_scope="+("verified" if valid else "incomplete"))


def no_existing_java_uses(exe: Path) -> GateCheck:
    if os.name != "nt":
        return GateCheck("dedicated_exe_not_in_use", False, "requires Windows CIM process inventory")
    script = (
        "$ErrorActionPreference='Stop'; "
        "Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" "
        "| Select-Object -ExpandProperty ExecutablePath | ConvertTo-Json -Compress"
    )
    ok, result = powershell(script)
    if not ok:
        return GateCheck("dedicated_exe_not_in_use", False, "cannot inspect live Java executable paths")
    try:
        inventory = json.loads(result) if result else []
        if not isinstance(inventory, list):
            inventory = [inventory]
        if any(isinstance(x, str) and path_eq(x, str(exe.resolve())) for x in inventory):
            return GateCheck("dedicated_exe_not_in_use", False, "dedicated JDK executable already in use")
    except (ValueError, TypeError, OSError):
        return GateCheck("dedicated_exe_not_in_use", False, "Java process inventory parsing failed")
    return GateCheck("dedicated_exe_not_in_use", True,
                     f"java_processes_inspected={len(inventory)}; dedicated_exe_unused=true")


def audit(exe: Path, private_root: Path, rule_name: str) -> dict[str, Any]:
    checks = [
        basic_private_exe(exe, private_root),
        no_existing_java_uses(exe),
        firewall_record(exe, rule_name),
    ]
    ready = all(x.ok for x in checks)
    return {
        "result": "R13_WINDOWS_GUI_OS_EGRESS_GATE_READY" if ready else "R13_WINDOWS_GUI_OS_EGRESS_GATE_BLOCKED",
        "mode": "READ_ONLY_NO_GUI_LAUNCH",
        "checks": [asdict(x) for x in checks],
        "game_processes_started": 0,
        "existing_client_processes_stopped": 0,
        "os_firewall_rules_changed": 0,
        "client_graphical_acceptance": False,
        "warning": "READY only proves this firewall program-block precondition; does NOT bypass R10 or certify native/JNI egress or real GUI",
    }


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--private-client-java", required=True, type=Path)
    parser.add_argument("--private-jdk-root", required=True, type=Path)
    parser.add_argument("--firewall-rule-name", required=True)
    args = parser.parse_args(argv)
    result = audit(args.private_client_java, args.private_jdk_root,
                   args.firewall_rule_name)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["result"] == "R13_WINDOWS_GUI_OS_EGRESS_GATE_READY" else 2


if __name__ == "__main__":
    raise SystemExit(main())

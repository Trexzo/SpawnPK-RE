"""R13: no foreign process termination; exact input and OS-egress admission."""
import hashlib
import json
from pathlib import Path
import socket
import tempfile
import unittest
from unittest import mock

import r13_windows_server_smoke as server
import r13_windows_gui_egress_gate as egress
import r13_prepare_private_jdk as jdk


class FakeChild:
    pid = 987654321

    def __init__(self):
        self.stopped = False
        self.waited = False

    def poll(self):
        return 0 if self.stopped else None

    def terminate(self):
        self.stopped = True

    def kill(self):
        raise AssertionError("R13 should not kill a normally terminating owned child")

    def wait(self, timeout=None):
        self.waited = True
        return 0


class R13OwnedProcessTests(unittest.TestCase):
    def test_wrong_pin_denies_before_launch(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            repo=root / "checkout"
            repo.mkdir()
            fakejar=repo / "server.jar"
            fakejar.write_bytes(b"not a signed JAR")
            java=root / "java"
            java.write_bytes(b"java")
            with mock.patch("subprocess.Popen") as launch:
                with self.assertRaises(server.ProbeBlocked):
                    server.smoke(repo, "a"*40, fakejar, "0"*64, java, root)
                launch.assert_not_called()

    def test_existing_foreign_port_never_closed(self):
        with socket.socket() as foreign:
            foreign.bind(("127.0.0.1", 0))
            foreign.listen(1)
            port=foreign.getsockname()[1]
            from r10_original_gui_preflight import loopback_ports
            self.assertFalse(loopback_ports((port,))[0].ok)
            self.assertEqual(foreign.getsockname()[1], port)

    def test_smoke_only_terminates_own_new_child(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            repo=root / "checkout"
            repo.mkdir()
            jar=repo / "server.jar"
            jar.write_bytes(b"mock-but-pinned-jar")
            sha=hashlib.sha256(jar.read_bytes()).hexdigest()
            java=root / "java.exe"
            java.write_bytes(b"fake-executable")
            child=FakeChild()
            with mock.patch.object(server, "check_inputs"), \
                 mock.patch.object(server, "child_listener_owned", return_value=True), \
                 mock.patch.object(server, "local_versions", return_value="b"*64), \
                 mock.patch.object(server, "ping_game"), \
                 mock.patch.object(server, "git_identity", return_value=[
                     server.Check("clean", True, "test")
                 ]), \
                 mock.patch.object(server.subprocess, "Popen", return_value=child) as start:
                receipt=server.smoke(repo,"a"*40,jar,sha,java,root)
            start.assert_called_once()
            self.assertEqual(receipt["result"],"R13_OWNED_LOOPBACK_SERVER_SMOKE_PASS")
            self.assertFalse(receipt["original_gui_started"])
            self.assertEqual(receipt["existing_clients_stopped"],0)
            self.assertTrue(child.stopped)
            self.assertTrue(child.waited)
            self.assertTrue((Path(receipt["owned_workspace"])/"r13-server-smoke-receipt.json").is_file())


class R13ReadOnlyEgressTests(unittest.TestCase):
    def test_missing_dedicated_executable_fails_closed(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            check=egress.basic_private_exe(root/"bin"/"java.exe",root)
            self.assertFalse(check.ok)

    def test_only_private_exe_within_private_root_is_allowed(self):
        with tempfile.TemporaryDirectory(prefix="spawnpk-r13-owned-jdk-") as folder:
            root=Path(folder)/"jdk"
            binfolder=root/"bin"
            binfolder.mkdir()
            exe=binfolder/"java.exe"
            exe.write_bytes(b"private java binary")
            self.assertTrue(egress.basic_private_exe(exe,root).ok)
            outside=root.parent/"outside-java.exe"
            self.assertFalse(egress.basic_private_exe(outside,root).ok)

    def test_egress_audit_requires_every_independent_condition(self):
        with tempfile.TemporaryDirectory(prefix="spawnpk-r13-owned-jdk-") as folder:
            root=Path(folder)/"jdk"
            java=root/"bin"/"java.exe"
            java.parent.mkdir()
            java.write_bytes(b"test")
            ready=egress.GateCheck("firewall",True,"mock")
            denied=egress.GateCheck("firewall",False,"no exact OS rule")
            with mock.patch.object(egress,"no_existing_java_uses",return_value=
                                   egress.GateCheck("unused",True,"mock")), \
                 mock.patch.object(egress,"firewall_record",return_value=ready):
                receipt=egress.audit(java,root,"SpawnPK R13 Test Rule")
            self.assertEqual(receipt["result"],"R13_WINDOWS_GUI_OS_EGRESS_GATE_READY")
            self.assertFalse(receipt["client_graphical_acceptance"])
            with mock.patch.object(egress,"no_existing_java_uses",return_value=
                                   egress.GateCheck("unused",True,"mock")), \
                 mock.patch.object(egress,"firewall_record",return_value=denied):
                blocked=egress.audit(java,root,"SpawnPK R13 Test Rule")
            self.assertEqual(blocked["result"],"R13_WINDOWS_GUI_OS_EGRESS_GATE_BLOCKED")
            self.assertEqual(blocked["existing_client_processes_stopped"],0)
            self.assertEqual(blocked["os_firewall_rules_changed"],0)

    def test_os_egress_without_supported_os_is_blocked(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/"private.exe"
            # At a minimum, wrong label fails on Windows and a non-Windows
            # machine always fails due to lack of Windows ActiveStore.
            if egress.os.name != "nt":
                self.assertFalse(egress.firewall_record(path, "Some Rule").ok)



class R13PrivateJdkSnapshotTests(unittest.TestCase):
    @staticmethod
    def fake_jdk(root):
        root.mkdir()
        for name in jdk.REQUIRED_JDK11:
            path=root.joinpath(*name.split("/"))
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(("JDK fixture "+name).encode("ascii"))
        return root

    def test_missing_java11_files_is_refused(self):
        with tempfile.TemporaryDirectory() as folder:
            fake=Path(folder)/"fake-jdk"
            fake.mkdir()
            with self.assertRaises(jdk.SnapshotBlocked):
                jdk.enumerate_jdk(fake)

    def test_private_jdk_snapshot_preserves_source_and_pins_output(self):
        with tempfile.TemporaryDirectory() as folder:
            private=Path(folder)/"private-parent"
            private.mkdir()
            src=self.fake_jdk(Path(folder)/"source-jdk")
            original={p.relative_to(src).as_posix(): p.read_bytes()
                      for p in src.rglob("*") if p.is_file()}
            with mock.patch.object(jdk,"guard_inputs",return_value=(src,private)),\
                 mock.patch.object(jdk,"java_version",return_value=11):
                proof=jdk.make_snapshot(src,private)
            self.assertEqual(proof["result"],"R13_PRIVATE_JAVA11_SNAPSHOT_PASS")
            self.assertEqual(proof["jdk_file_count"],len(jdk.REQUIRED_JDK11))
            self.assertEqual(proof["firewall_rules_changed"],0)
            self.assertFalse(proof["actual_gui_acceptance"])
            copied=Path(proof["private_jdk_root"])
            self.assertEqual(
                original,{p.relative_to(copied).as_posix():p.read_bytes()
                          for p in copied.rglob("*") if p.is_file()}
            )
            self.assertEqual(
                original,{p.relative_to(src).as_posix():p.read_bytes()
                          for p in src.rglob("*") if p.is_file()}
            )

    def test_snapshot_change_fails_and_only_own_temp_removed(self):
        with tempfile.TemporaryDirectory() as folder:
            parent=Path(folder)/"owned-parent"
            parent.mkdir()
            src=self.fake_jdk(Path(folder)/"source-jdk")
            foreign=parent/"user-file.txt"
            foreign.write_text("preserve")
            real_hash=jdk.hash_file
            def corrupted_copy(path):
                if "spawnpk-r13-owned-jdk-" in str(path):
                    return "f"*64
                return real_hash(path)
            with mock.patch.object(jdk,"guard_inputs",return_value=(src,parent)),\
                 mock.patch.object(jdk,"hash_file",side_effect=corrupted_copy),\
                 mock.patch.object(jdk,"java_version",return_value=11):
                with self.assertRaises(jdk.SnapshotBlocked):
                    jdk.make_snapshot(src,parent)
            self.assertEqual(foreign.read_text(),"preserve")
            self.assertEqual(sorted(x.name for x in parent.iterdir()),["user-file.txt"])

if __name__=="__main__":
    unittest.main()

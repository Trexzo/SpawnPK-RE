"""R10 fail-closed read-only GUI preflight tests."""
import argparse, hashlib, socket, tempfile, unittest, zipfile
from pathlib import Path
from unittest import mock
import r10_original_gui_preflight as r10

class PreflightTest(unittest.TestCase):
    def fake_jar(self,path,major=55,resource=True):
        with zipfile.ZipFile(path,"w") as z:
            z.writestr("spk/local/Main.class",b"\\xca\\xfe\\xba\\xbe\\x00\\x00"+major.to_bytes(2,"big"))
            if resource:z.writestr("spk/local/equipment_slots.tsv","slot\\tid\\n")

    def test_client_mismatch_and_missing_file(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"client.jar";p.write_bytes(b"unknown derivative")
            self.assertFalse(r10.candidate_digest(p,r10.CERTIFIED_AIRGAP_V308_SHA256,"airgap").ok)
            self.assertFalse(r10.candidate_digest(p.with_name("missing"),r10.ORIGINAL_V308_SHA256,"original").ok)
            self.assertTrue(r10.candidate_digest(p,hashlib.sha256(p.read_bytes()).hexdigest(),"synthetic").ok)

    def test_server_digest_class_and_resources(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/"server.jar"
            self.fake_jar(p)
            checks=r10.server_jar_identity(p,hashlib.sha256(p.read_bytes()).hexdigest())
            self.assertTrue(all(x.ok for x in checks),checks)
            self.assertFalse(r10.server_jar_identity(p,"f"*64)[0].ok)
            self.fake_jar(p,major=61)
            self.assertFalse(next(x for x in r10.server_jar_identity(p,None) if x.name=="java_11_server_bytecode").ok)
            self.fake_jar(p,resource=False)
            self.assertFalse(next(x for x in r10.server_jar_identity(p,None) if x.name=="server_jar_structure").ok)

    def test_live_port_is_not_closed_or_killed(self):
        with socket.socket() as s:
            s.bind(("127.0.0.1",0));s.listen()
            port=s.getsockname()[1]
            self.assertFalse(r10.loopback_ports((port,))[0].ok)
            self.assertEqual(s.getsockname()[1],port)
        self.assertTrue(r10.loopback_ports((port,))[0].ok)

    def test_isolated_profile(self):
        with tempfile.TemporaryDirectory() as d:
            real=Path(d);isolated=real/"sandbox";isolated.mkdir()
            self.assertFalse(r10.isolated_home(isolated,real).ok)
            (isolated/".spawnpk").mkdir()
            self.assertTrue(r10.isolated_home(isolated,real).ok)
            self.assertFalse(r10.isolated_home(isolated,isolated).ok)

    def test_git_wrong_head_and_dirty(self):
        def fake_run(command,timeout=8):
            s=" ".join(command)
            if "rev-parse HEAD" in s:return True,"b"*40
            if "get-url origin" in s:return True,"https://github.com/Trexzo/SpawnPK-RE.git"
            return True," M source.java"
        with mock.patch.object(r10,"run_readonly",fake_run):
            checks=r10.git_identity(Path("."),"a"*40)
            self.assertEqual([x.ok for x in checks],[False,True,False])

    def test_success_is_only_preflight_never_a_gui_pass(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);repo=root/"repo";(repo/"server"/"build").mkdir(parents=True)
            jar=repo/"server"/"build"/"SpawnPKLocalServer.jar";self.fake_jar(jar)
            orig=root/"original.jar";orig.write_bytes(b"orig")
            gap=root/"airgap.jar";gap.write_bytes(b"gap")
            home=root/"home";(home/".spawnpk").mkdir(parents=True)
            java=root/"java";java.write_text("fake")
            args=argparse.Namespace(repo=repo,source_head="a"*40,server_jar=jar,
                 server_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
                 original_jar=orig,airgap_jar=gap,java_exe=java,client_home=home)
            with mock.patch.object(r10,"git_identity",return_value=[r10.Check("git",True,"mock")]),\
                 mock.patch.object(r10,"candidate_digest",side_effect=lambda *a:r10.Check(a[2],True,"mock")),\
                 mock.patch.object(r10,"java_identity",return_value=r10.Check("java",True,"mock")),\
                 mock.patch.object(r10,"loopback_ports",return_value=[r10.Check("ports",True,"mock")]),\
                 mock.patch.object(r10,"active_spawnpk_java",return_value=r10.Check("processes",True,"mock")),\
                 mock.patch("subprocess.Popen") as launch:
                receipt=r10.preflight(args)
                self.assertEqual(receipt["result"],"R10_ORIGINAL_GUI_PREFLIGHT_READY")
                self.assertFalse(receipt["launch_performed"])
                self.assertEqual(receipt["existing_processes_stopped"],0)
                launch.assert_not_called()

if __name__=="__main__":
    unittest.main()

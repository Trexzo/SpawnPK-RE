import pathlib, tempfile, unittest, zipfile
from audit_spawnpk_donor import analyze

class DonorArchiveInspectorTests(unittest.TestCase):
    def make(self,p,items):
        with zipfile.ZipFile(p,'w') as z:
            for name,data in items:z.writestr(name,data)
    def test_inventory_is_read_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=pathlib.Path(tmp)/'fixture.zip'
            self.make(p,[('root/server/plugins/recipes/src/Recipe.kt',b'class Recipe'),('root/client/src/Client.java',b'class Client')])
            old=p.read_bytes();result=analyze(p)
            self.assertEqual(result['source_counts'],{'server':1,'client':1})
            self.assertEqual(result['plugin_source_counts']['recipes'],1)
            self.assertEqual(p.read_bytes(),old)
    def test_reject_path_traversal(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=pathlib.Path(tmp)/'bad.zip';self.make(p,[('../escape',b'bad')])
            with self.assertRaisesRegex(ValueError,'unsafe'):analyze(p)
    def test_reject_duplicate_members(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=pathlib.Path(tmp)/'bad.zip';self.make(p,[('same',b'a'),('same',b'b')])
            with self.assertRaisesRegex(ValueError,'duplicate'):analyze(p)

if __name__=='__main__':unittest.main()

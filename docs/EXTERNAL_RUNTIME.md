# External runtime

The repo intentionally does not contain original or patched SpawnPK client binaries.

Required local paths and certified hashes:

`	ext
evidence\client(6).jar
6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662

local-client\client-airgap.jar
9ff1b80fe81b1af2e174df71db33f5aeb5ee489c265ab2880bcda35f7797019b

local-client\client-localhost.jar
139cf87e18eed052707b05c9f49cac167ee9cc200600c74ed53a0035bfdac8cb
`

Use IMPORT_EXISTING_RUNTIME.ps1 when importing from an existing certified LocalLab checkout.

R8.5 modifies only local definition metadata in .spawnpk\configs\i.bin and
.spawnpk\configs\e.bin; bootstrap backs them up before patching.
# OpenAbsinthe proof recovery tools

These tools document and reproduce the final source-recovery boundary from the
historical Android engine. The engine itself is intentionally not tracked or
packaged. The accepted input image has SHA-256:

```text
d8e7fc7f7a56d93634b7fe827ae7f2a8be3a39dd29d4a200a81cb7d959c110da
```

`ExportPcode.java` exports the proof wrapper, merge helper, and proof circuit
from a rebased Ghidra project. `generate_proof_program.py` verifies the image
hash, compiles the export to a compact architecture-neutral operation stream,
and copies only the two lookup-table ranges read by successful executions.
The result is checked in as `rustpush/open-absinthe/src/proof_program.b64` and
interpreted by source-built Rust in `proof_vm.rs`.

Example export from the recovery project:

```bash
analyzeHeadless /private/tmp ob-correct \
  -process librust_lib_bluebubbles.so -noanalysis \
  -scriptPath tools/recovery \
  -postScript ExportPcode.java \
  0x1b45224:0xbc0 0x1b45de4:0xec 0x1b45ed0:0x5580 \
  --output=/private/tmp/openbubbles-proof.pcode
```

Validate the exported behavior, then regenerate the checked-in program:

```bash
python3 tools/recovery/run_pcode.py \
  --pcode /private/tmp/openbubbles-proof.pcode \
  --elf /path/to/historical/librust_lib_bluebubbles.so \
  --oracle /private/tmp/openbubbles-proof-oracle.json

python3 tools/recovery/generate_proof_program.py \
  --pcode /private/tmp/openbubbles-proof.pcode \
  --elf /path/to/historical/librust_lib_bluebubbles.so \
  --output rustpush/open-absinthe/src/proof_program.b64
```

Oracle captures stay outside the repository because they contain transient
handshake state. The Android and Apple references differ because their signing
state and white-box tables use different encodings. Independent ARM64 emulation
matched the p-code output for the captured nonce and deterministic nonce seeds
1, 2, and 3.

---
"@kilocode/cli": patch
"@kilocode/kilo-indexing": patch
---

Fix `kilo` installed via AUR (`kilo-bin`) or Homebrew crashing with a missing `tree-sitter.wasm` error when the indexing feature ran. These packages now bundle the required WASM files alongside the binary.

import { describe, expect, test } from "bun:test"
import { mkdtemp, writeFile } from "fs/promises"
import { tmpdir } from "os"
import { join } from "path"

import {
  resolveCoreRuntimeWasmPath,
  resolveFromDirectories,
  wasmDirectories,
} from "../../../src/tree-sitter/languageParser"

// RATIONALE: Regression guard for the CI-baked `require.resolve("web-tree-sitter/tree-sitter.wasm")`
// fallback that used to make the compiled CLI crash with an ENOENT pointing at the GitHub
// Actions runner's path when the sidecar `tree-sitter/` dir was missing from the install
// (AUR `kilo-bin`, Homebrew). The resolver must never inject a CI-baked path, and must name
// its search directories so the caller can emit an accurate diagnostic.

describe("resolveFromDirectories", () => {
  test("returns undefined when the file is missing from every probed directory", async () => {
    const a = await mkdtemp(join(tmpdir(), "ts-probe-a-"))
    const b = await mkdtemp(join(tmpdir(), "ts-probe-b-"))

    expect(resolveFromDirectories("tree-sitter.wasm", [a, b])).toBeUndefined()
  })

  test("returns the first matching candidate", async () => {
    const a = await mkdtemp(join(tmpdir(), "ts-probe-a-"))
    const b = await mkdtemp(join(tmpdir(), "ts-probe-b-"))
    const match = join(b, "tree-sitter.wasm")
    await writeFile(match, "fake-wasm")

    expect(resolveFromDirectories("tree-sitter.wasm", [a, b])).toBe(match)
  })
})

describe("wasmDirectories", () => {
  test("never includes the removed CI-baked node_modules path", () => {
    const probes = wasmDirectories("/nonexistent")
    for (const dir of probes) {
      expect(dir).not.toContain("/home/runner/")
      expect(dir).not.toContain("web-tree-sitter@")
    }
  })

  test("includes execDir and execDir/tree-sitter for the AUR / Homebrew install layout", () => {
    const probes = wasmDirectories("/nonexistent")
    const execDir = require("path").dirname(process.execPath)
    expect(probes).toContain(execDir)
    expect(probes).toContain(join(execDir, "tree-sitter"))
  })

  test("includes the FHS-strict <execDir>/../lib/kilo/tree-sitter fallback", () => {
    const probes = wasmDirectories("/nonexistent")
    const hasFhs = probes.some((p) => p.endsWith(join("lib", "kilo", "tree-sitter")))
    expect(hasFhs).toBe(true)
  })

  test("includes KILO_TREE_SITTER_WASM_DIR when set", () => {
    const prev = process.env.KILO_TREE_SITTER_WASM_DIR
    process.env.KILO_TREE_SITTER_WASM_DIR = "/custom/wasm/dir"
    try {
      expect(wasmDirectories("/nonexistent")).toContain("/custom/wasm/dir")
    } finally {
      if (prev === undefined) delete process.env.KILO_TREE_SITTER_WASM_DIR
      else process.env.KILO_TREE_SITTER_WASM_DIR = prev
    }
  })
})

describe("resolveCoreRuntimeWasmPath", () => {
  test("returns the wasm path when present in the sourceDirectory's tree-sitter/ sibling", async () => {
    const source = await mkdtemp(join(tmpdir(), "ts-resolver-src-"))
    const wasmDir = join(source, "tree-sitter")
    const wasmPath = join(wasmDir, "tree-sitter.wasm")
    await Bun.write(wasmPath, "fake-wasm")

    expect(resolveCoreRuntimeWasmPath(source)).toBe(wasmPath)
  })
})

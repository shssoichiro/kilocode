import { afterEach, describe, expect, spyOn, test } from "bun:test"
import { CodeIndexManager } from "@kilocode/kilo-indexing/engine"
import type { Config } from "../../src/config"
import { AppRuntime } from "../../src/effect/app-runtime"
import { KiloIndexing } from "../../src/kilocode/indexing"
import { InstanceBootstrap } from "../../src/project/bootstrap"
import { Instance } from "../../src/project/instance"
import { Server } from "../../src/server/server"
import { Log } from "../../src/util"
import { tmpdir } from "../fixture/fixture"

Log.init({ print: false })

const cfg: Partial<Config.Info> = {
  plugin: ["@kilocode/kilo-indexing"],
  experimental: {
    semantic_indexing: true,
  },
  indexing: {
    enabled: true,
    provider: "ollama",
    vectorStore: "qdrant",
    ollama: {
      baseUrl: "http://127.0.0.1:1",
    },
  },
}

const off: Partial<Config.Info> = {
  plugin: ["@kilocode/kilo-indexing"],
  experimental: {
    semantic_indexing: false,
  },
  indexing: {
    enabled: true,
    provider: "ollama",
    vectorStore: "qdrant",
    ollama: {
      baseUrl: "http://127.0.0.1:1",
    },
  },
}
const configDir = process.env["KILO_CONFIG_DIR"]

afterEach(async () => {
  if (configDir === undefined) delete process.env["KILO_CONFIG_DIR"]
  else process.env["KILO_CONFIG_DIR"] = configDir
  await Instance.disposeAll()
})

describe("indexing startup degradation", () => {
  test("keeps server routes alive when indexing initialization fails", async () => {
    await using tmp = await tmpdir({ git: true, config: cfg })
    process.env["KILO_CONFIG_DIR"] = tmp.path
    const app = Server.Default().app

    const config = await app.request("/config", {
      headers: {
        "x-kilo-directory": tmp.path,
      },
    })
    expect(config.status).toBe(200)

    const status = await app.request("/indexing/status", {
      headers: {
        "x-kilo-directory": tmp.path,
      },
    })
    expect(status.status).toBe(200)

    const body = await status.json()
    expect(body).toMatchObject({
      state: "Error",
    })
    expect(body.message).toContain("Failed to initialize:")
  })

  test("keeps degraded indexing queryable but unavailable", async () => {
    await using tmp = await tmpdir({ git: true, config: cfg })
    process.env["KILO_CONFIG_DIR"] = tmp.path

    await Instance.provide({
      directory: tmp.path,
      init: () => AppRuntime.runPromise(InstanceBootstrap),
      fn: async () => {
        const status = await KiloIndexing.current()

        expect(status.state).toBe("Error")
        expect(status.message).toContain("Failed to initialize:")
        expect(await KiloIndexing.available()).toBe(false)
        expect(KiloIndexing.ready()).toBe(false)
        expect(await KiloIndexing.search("boot failure")).toEqual([])
      },
    })
  })

  test("reports not ready while initialization is in flight", async () => {
    await using tmp = await tmpdir({ git: true, config: cfg })
    process.env["KILO_CONFIG_DIR"] = tmp.path
    const init = spyOn(CodeIndexManager.prototype, "initialize").mockImplementation(() => new Promise(() => {}))

    try {
      await Instance.provide({
        directory: tmp.path,
        init: () => AppRuntime.runPromise(InstanceBootstrap),
        fn: async () => {
          void KiloIndexing.init()
          await new Promise((resolve) => setTimeout(resolve, 0))

          expect(init).toHaveBeenCalled()
          expect(KiloIndexing.ready()).toBe(false)
        },
      })
    } finally {
      init.mockRestore()
    }
  })

  test("stays disabled when semantic indexing flag is off", async () => {
    await using tmp = await tmpdir({ git: true, config: off })
    process.env["KILO_CONFIG_DIR"] = tmp.path
    const init = spyOn(CodeIndexManager.prototype, "initialize")

    await Instance.provide({
      directory: tmp.path,
      init: () => AppRuntime.runPromise(InstanceBootstrap),
      fn: async () => {
        const status = await KiloIndexing.current()

        expect(status).toMatchObject({
          state: "Disabled",
          message: "Semantic indexing is disabled. Enable it in the Experimental settings.",
        })
        expect(await KiloIndexing.available()).toBe(false)
        expect(KiloIndexing.ready()).toBe(false)
        expect(await KiloIndexing.search("flag off")).toEqual([])
        expect(init).not.toHaveBeenCalled()
      },
    })
  })
})

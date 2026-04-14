import { describe, expect, test } from "bun:test"
import path from "path"
import { Flag } from "../../src/flag/flag" // kilocode_change
import { Global } from "../../src/global"
import { Installation } from "../../src/installation"
import { Database } from "../../src/storage/db"

describe("Database.Path", () => {
  // kilocode_change start
  // RATIONALE: preload sets KILO_DB=:memory: which takes precedence via iife evaluation
  test("returns database path for the current channel", () => {
    const expected = Flag.KILO_DB
      ? Flag.KILO_DB === ":memory:" || path.isAbsolute(Flag.KILO_DB)
        ? Flag.KILO_DB
        : path.join(Global.Path.data, Flag.KILO_DB)
      : ["latest", "beta"].includes(Installation.CHANNEL)
        ? path.join(Global.Path.data, "kilo.db")
        : path.join(Global.Path.data, `kilo-${Installation.CHANNEL.replace(/[^a-zA-Z0-9._-]/g, "-")}.db`)
    // kilocode_change end
    expect(Database.Path).toBe(expected)
  })
})

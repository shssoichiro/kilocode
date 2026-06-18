import { describe, expect, test } from "bun:test"
import { formatIndexingLabel, formatIndexingMessage } from "../../src/kilocode/indexing-label"

describe("indexing label", () => {
  test("formats in-progress status with counts", () => {
    expect(
      formatIndexingLabel({
        state: "In Progress",
        message: "",
        processedFiles: 21,
        totalFiles: 50,
        percent: 42,
      }),
    ).toBe("42% (21/50 files)")
  })

  test("formats indeterminate in-progress status without 0/0 counts", () => {
    expect(
      formatIndexingLabel({
        state: "In Progress",
        message: "",
        processedFiles: 0,
        totalFiles: 0,
        percent: 0,
      }),
    ).toBe("In progress")
  })

  test("formats progress percentage without counts", () => {
    expect(
      formatIndexingLabel({
        state: "In Progress",
        message: "",
        processedFiles: 0,
        totalFiles: 0,
        percent: 42,
      }),
    ).toBe("42%")
  })

  test("formats error status with backend message", () => {
    expect(
      formatIndexingLabel({
        state: "Error",
        message: "Indexing failed.",
        processedFiles: 0,
        totalFiles: 0,
        percent: 0,
      }),
    ).toBe("Indexing failed.")
  })

  test("formats stable states", () => {
    expect(
      formatIndexingLabel({
        state: "Complete",
        message: "",
        processedFiles: 1,
        totalFiles: 1,
        percent: 100,
      }),
    ).toBe("Complete")
    expect(
      formatIndexingLabel({
        state: "Disabled",
        message: "Indexing disabled.",
        processedFiles: 0,
        totalFiles: 0,
        percent: 0,
      }),
    ).toBe("Disabled")
    expect(
      formatIndexingLabel({
        state: "Standby",
        message: "Ready.",
        processedFiles: 0,
        totalFiles: 0,
        percent: 0,
      }),
    ).toBe("Standby")
  })

  test("hides empty and redundant indexing messages", () => {
    expect(
      formatIndexingMessage({
        state: "In Progress",
        message: "",
        processedFiles: 0,
        totalFiles: 0,
        percent: 0,
      }),
    ).toBeUndefined()

    expect(
      formatIndexingMessage({
        state: "In Progress",
        message: "Indexing is in progress.",
        processedFiles: 0,
        totalFiles: 0,
        percent: 0,
      }),
    ).toBeUndefined()

    expect(
      formatIndexingMessage({
        state: "In Progress",
        message: "42% (21/50 files)",
        processedFiles: 21,
        totalFiles: 50,
        percent: 42,
      }),
    ).toBeUndefined()
  })

  test("keeps useful indexing messages", () => {
    expect(
      formatIndexingMessage({
        state: "In Progress",
        message: "Scanning changed files.",
        processedFiles: 1,
        totalFiles: 10,
        percent: 10,
      }),
    ).toBe("Scanning changed files.")
  })
})

import { describe, it, expect } from "bun:test"
import { amLocales, appLocales, placeholders } from "./i18n-shared"

const PREFIX = "agentManager."

const locales = amLocales
const amEn = amLocales.en

describe("Agent Manager i18n split", () => {
  it("keeps agent manager keys out of general locale dictionaries", () => {
    for (const [locale, dict] of Object.entries(appLocales)) {
      const keys = Object.keys(dict)
      expect(
        keys.some((key) => key.startsWith(PREFIX)),
        `locale ${locale} contains agent manager keys`,
      ).toBeFalse()
    }
  })

  it("keeps every agent manager locale dictionary scoped to agentManager.* keys", () => {
    for (const [locale, dict] of Object.entries(locales)) {
      const keys = Object.keys(dict)
      expect(keys.length, `locale ${locale} should have agent manager keys`).toBeGreaterThan(0)
      const invalid = keys.filter((key) => !key.startsWith(PREFIX))
      expect(invalid, `locale ${locale} has non-agent-manager keys`).toEqual([])
    }
  })

  it("keeps every agent manager locale keyset aligned with english", () => {
    const baseKeys = Object.keys(amEn)

    for (const [locale, dict] of Object.entries(locales)) {
      const keySet = new Set(Object.keys(dict))
      const missing = baseKeys.filter((key) => !keySet.has(key))
      const extra = Array.from(keySet).filter((key) => !(key in amEn))

      expect(missing, `locale ${locale} is missing agent manager keys`).toEqual([])
      expect(extra, `locale ${locale} has unexpected agent manager keys`).toEqual([])
    }
  })

  it("keeps interpolation placeholders aligned with english", () => {
    for (const [locale, dict] of Object.entries(locales)) {
      if (locale === "en") continue

      for (const [key, value] of Object.entries(amEn)) {
        const localized = (dict as Record<string, string>)[key]
        expect(localized, `missing key ${key} in locale ${locale}`).toBeDefined()
        if (!localized) continue

        const baseVars = placeholders(value)
        const localeVars = placeholders(localized)
        expect(localeVars, `placeholder mismatch for ${key} in locale ${locale}`).toEqual(baseVars)
      }
    }
  })

  it("contains required core keys in every locale", () => {
    const required = [
      "agentManager.local",
      "agentManager.session.new",
      "agentManager.apply.error",
      "agentManager.import.failed",
    ]

    for (const [locale, dict] of Object.entries(locales)) {
      for (const key of required) {
        expect((dict as Record<string, string>)[key], `missing key ${key} in locale ${locale}`).toBeDefined()
      }
    }
  })
})

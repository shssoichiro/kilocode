#!/usr/bin/env bun

import { findAllMissingRows, names } from "../tests/unit/i18n-shared"

const rows = findAllMissingRows().sort((a, b) => {
  const file = a.file.localeCompare(b.file)
  if (file !== 0) return file
  return a.key.localeCompare(b.key)
})

if (rows.length === 0) {
  console.log("No missing translations found")
  process.exit(0)
}

const files = rows.reduce((map, row) => {
  const list = map.get(row.file) ?? []
  list.push(row)
  map.set(row.file, list)
  return map
}, new Map<string, typeof rows>())

const out = Array.from(files.entries())
  .map(([file, list]) => {
    const locale = list[0]?.locale ?? "en"
    const name = names[locale]
    const lines = list.map((row) => `${JSON.stringify(row.key)}: ${JSON.stringify(row.source)}`)
    return [`Missing from \`${file}\` (${name}):`, ...lines].join("\n")
  })
  .join("\n\n")

console.log(out)

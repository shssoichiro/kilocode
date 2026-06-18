import type { IndexingStatus } from "@kilocode/kilo-indexing/status"

export function formatIndexingLabel(status: IndexingStatus): string {
  if (status.state === "In Progress") {
    if (status.totalFiles <= 0) return status.percent > 0 ? `${status.percent}%` : "In progress"
    return `${status.percent}% (${status.processedFiles}/${status.totalFiles} files)`
  }

  if (status.state === "Error") {
    return status.message || "Failed"
  }

  return status.state
}

export function formatIndexingMessage(status: IndexingStatus): string | undefined {
  const label = formatIndexingLabel(status)
  const msg = status.message.trim()
  if (!msg || msg === label) return undefined

  const plain = msg
    .replace(/^codebase indexing (is )?/i, "")
    .replace(/^indexing (is )?/i, "")
    .replace(/[.!]+$/, "")
    .toLowerCase()

  if (plain === label.toLowerCase()) return undefined
  return msg
}

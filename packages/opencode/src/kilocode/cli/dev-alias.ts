import path from "path"
import { fileURLToPath } from "url"
import { cmd } from "@/cli/cmd/cmd"
import { Installation } from "@/installation"
import { UI } from "@/cli/ui"

export const DevAliasCommand = cmd({
  command: "dev-alias [shell]",
  describe: "print shell snippet that aliases kilodev to this checkout",
  builder: (y) =>
    y.positional("shell", {
      type: "string",
      describe: "shell flavor",
      choices: ["zsh", "bash", "fish", "powershell"] as const,
      default: "zsh",
    }),
  handler: async (args) => {
    if (!Installation.isLocal()) {
      UI.error("dev-alias only works when running from a source checkout (./bin/kilodev)")
      process.exitCode = 1
      return
    }
    const repo = await detectRepo()
    const sh = path.join(repo, "bin", "kilodev")
    const bat = path.join(repo, "bin", "kilodev.cmd")
    const q = (s: string) => `'${s.replaceAll("'", `'\\''`)}'`
    const line = (() => {
      if (args.shell === "fish") return `alias kilodev ${q(sh)}`
      if (args.shell === "powershell") return `function kilodev { & '${bat.replaceAll("'", "''")}' @args }`
      return `alias kilodev=${q(sh)}`
    })()
    process.stdout.write(line + "\n")
  },
})

async function detectRepo(): Promise<string> {
  const hint = process.env.KILO_DEV_REPO
  if (hint) return hint
  // Fallback: walk up from this source file to find packages/opencode
  const walk = async (dir: string): Promise<string> => {
    const candidate = path.join(dir, "packages", "opencode", "package.json")
    if (await Bun.file(candidate).exists()) return dir
    const parent = path.dirname(dir)
    if (parent === dir) throw new Error("cannot locate repo root")
    return walk(parent)
  }
  return walk(path.dirname(fileURLToPath(import.meta.url)))
}

// kilocode_change - new file
import type { TuiPlugin, TuiPluginApi, TuiPluginModule } from "@kilocode/plugin/tui"
import { createResource, Show } from "solid-js"
import { Process } from "@/util"

const id = "internal:kilo-sidebar-pr"

type Pr = { number: number; title: string }

async function lookup(cwd: string, branch: string): Promise<Pr | null> {
  // Try the tracking ref first (works when PR was checked out via `gh pr checkout`
  // or when the branch's upstream is a fork). Fall back to an explicit branch
  // lookup (works for same-repo branches pushed to origin).
  const build = (b?: string) => {
    const a = ["gh", "pr", "view"]
    if (b) a.push(b)
    a.push("--json", "number,title")
    return a
  }
  for (const cmd of [build(), build(branch)]) {
    const res = await Process.text(cmd, { cwd, nothrow: true, timeout: 15_000 })
    if (res.code !== 0) continue
    const text = res.text.trim()
    if (!text) continue
    const data = JSON.parse(text) as Partial<Pr>
    if (typeof data.number === "number" && typeof data.title === "string") {
      return { number: data.number, title: data.title }
    }
  }
  return null
}

function View(props: { api: TuiPluginApi }) {
  const theme = () => props.api.theme.current
  const branch = () => props.api.state.vcs?.branch
  const cwd = () => props.api.state.path.directory

  const source = () => {
    const b = branch()
    const d = cwd()
    if (!b || !d) return false
    return { branch: b, cwd: d }
  }

  const [pr] = createResource(source, async (s) => {
    if (!s) return null
    return lookup(s.cwd, s.branch).catch(() => null)
  })

  return (
    <Show when={pr()}>
      <text fg={theme().textMuted}>
        PR #{pr()!.number} - {pr()!.title}
      </text>
    </Show>
  )
}

const tui: TuiPlugin = async (api) => {
  api.slots.register({
    order: 50,
    slots: {
      sidebar_content(_ctx, _props) {
        return <View api={api} />
      },
    },
  })
}

const plugin: TuiPluginModule & { id: string } = { id, tui }

export default plugin

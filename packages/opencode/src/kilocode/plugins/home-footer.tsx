/**
 * Kilo-specific home footer plugin.
 *
 * Replaces the upstream `home_footer` slot (order 101 > upstream 100)
 * to inject the RemoteIndicator alongside the standard directory, MCP,
 * and version information.
 */
import type { TuiPlugin, TuiPluginApi, TuiPluginModule } from "@kilocode/plugin/tui"
import { createEffect, createMemo, createSignal, Match, onCleanup, onMount, Show, Switch } from "solid-js"
import { Global } from "@opencode-ai/core/global"
import { RemoteIndicator } from "@/kilocode/remote-tui"
import type { IndexingStatusState } from "@kilocode/kilo-indexing/status"
import * as Log from "@opencode-ai/core/util/log"
import { useSync } from "@tui/context/sync"
import { indexingEnabled } from "../indexing-feature"
import { formatIndexingLabel, formatIndexingMessage } from "../indexing-label"

const id = "internal:kilo-home-footer"
const log = Log.create({ service: "home-footer" })

function tone(state: IndexingStatusState, api: TuiPluginApi) {
  const theme = api.theme.current
  if (state === "Complete") return theme.success
  if (state === "Error") return theme.error
  if (state === "In Progress") return theme.warning
  return theme.textMuted
}

// ---------------------------------------------------------------------------
// Sub-components (mirror upstream home/footer with kilo additions)
// ---------------------------------------------------------------------------

function Directory(props: { api: TuiPluginApi }) {
  const theme = () => props.api.theme.current
  const dir = createMemo(() => {
    const d = props.api.state.path.directory || process.cwd()
    const out = d.replace(Global.Path.home, "~")
    const branch = props.api.state.vcs?.branch
    if (branch) return out + ":" + branch
    return out
  })

  return <text fg={theme().textMuted}>{dir()}</text>
}

function Mcp(props: { api: TuiPluginApi }) {
  const theme = () => props.api.theme.current
  const list = createMemo(() => props.api.state.mcp())
  const has = createMemo(() => list().length > 0)
  const err = createMemo(() => list().some((item) => item.status === "failed"))
  const count = createMemo(() => list().filter((item) => item.status === "connected").length)

  return (
    <Show when={has()}>
      <box gap={1} flexDirection="row" flexShrink={0}>
        <text fg={theme().text}>
          <Switch>
            <Match when={err()}>
              <span style={{ fg: theme().error }}>⊙ </span>
            </Match>
            <Match when={true}>
              <span style={{ fg: count() > 0 ? theme().success : theme().textMuted }}>⊙ </span>
            </Match>
          </Switch>
          {count()} MCP
        </text>
        <text fg={theme().textMuted}>/status</text>
      </box>
    </Show>
  )
}

function Version(props: { api: TuiPluginApi }) {
  const theme = () => props.api.theme.current

  return (
    <box flexShrink={0}>
      <text fg={theme().textMuted}>{props.api.app.version}</text>
    </box>
  )
}

// ---------------------------------------------------------------------------
// Main footer view
// ---------------------------------------------------------------------------

function Indexing(props: { api: TuiPluginApi }) {
  const theme = () => props.api.theme.current
  const sync = useSync()
  const enabled = createMemo(() => indexingEnabled(sync.data.config))
  const configured = createMemo(
    () => sync.data.config.indexing?.enabled === true || sync.data.globalConfig.indexing?.enabled === true,
  )
  const [status, setStatus] = createSignal(sync.data.indexing)
  const label = createMemo(() => formatIndexingLabel(status()))
  const msg = createMemo(() => formatIndexingMessage(status()))
  const terminal = createMemo(() => status().state === "Complete" || status().state === "Error")
  const refresh = () => {
    if (!enabled() || !configured()) return
    const params = props.api.state.path.directory ? { directory: props.api.state.path.directory } : undefined
    void props.api.client.indexing
      .status(params)
      .then((res) => {
        if (res.data) setStatus(res.data)
      })
      .catch((err) => log.debug("indexing status poll failed", { err }))
  }

  createEffect(() => {
    setStatus(sync.data.indexing)
  })

  createEffect(() => {
    if (!enabled() || !configured()) return
    refresh()
    if (terminal()) return
    const timer = setInterval(() => {
      if (terminal()) return
      refresh()
    }, 1000)
    onCleanup(() => clearInterval(timer))
  })

  return (
    <Show when={enabled()}>
      <box flexDirection="column" flexShrink={1} gap={0}>
        <text fg={theme().text} wrapMode="none" truncate>
          <b>Code Indexing</b>
        </text>
        <box flexDirection="row" gap={1}>
          <text flexShrink={0} style={{ fg: tone(status().state, props.api) }}>
            •
          </text>
          <text fg={theme().text} wrapMode="none" truncate>
            {label()}
          </text>
        </box>
        <Show when={msg()}>{(text) => <text fg={theme().textMuted}>{text()}</text>}</Show>
      </box>
    </Show>
  )
}

function View(props: { api: TuiPluginApi }) {
  const kilo = createMemo(() => props.api.state.provider.some((p) => p.id === "kilo"))
  const sdk = { client: props.api.client }

  return (
    <box
      width="100%"
      paddingTop={1}
      paddingBottom={1}
      paddingLeft={2}
      paddingRight={2}
      flexDirection="row"
      flexShrink={0}
      gap={2}
    >
      <Directory api={props.api} />
      <box gap={1} flexDirection="row" flexShrink={0}>
        <RemoteIndicator sdk={sdk} theme={props.api.theme.current} kilo={kilo()} event={props.api.event} />
        <Mcp api={props.api} />
      </box>
      <Indexing api={props.api} />
      <box flexGrow={1} />
      <Version api={props.api} />
    </box>
  )
}

// ---------------------------------------------------------------------------
// Plugin registration
// ---------------------------------------------------------------------------

const tui: TuiPlugin = async (api) => {
  api.slots.register({
    order: 99,
    slots: {
      home_footer() {
        return <View api={api} />
      },
    },
  })
}

const plugin: TuiPluginModule & { id: string } = {
  id,
  tui,
}

export default plugin

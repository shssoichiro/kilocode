import type { TuiPlugin, TuiPluginApi, TuiPluginModule } from "@kilocode/plugin/tui"
import { createEffect, createMemo, createSignal, onCleanup, Show } from "solid-js"
import type { IndexingStatusState } from "@kilocode/kilo-indexing/status"
import * as Log from "@opencode-ai/core/util/log"
import { useSync } from "@tui/context/sync"
import { formatIndexingLabel, formatIndexingMessage } from "../indexing-label"
import { indexingEnabled } from "../indexing-feature"

const id = "internal:kilo-sidebar-indexing"
const log = Log.create({ service: "sidebar-indexing" })

function tone(state: IndexingStatusState, api: TuiPluginApi) {
  const theme = api.theme.current
  if (state === "Complete") return theme.success
  if (state === "Error") return theme.error
  if (state === "In Progress") return theme.warning
  return theme.textMuted
}

function View(props: { api: TuiPluginApi }) {
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
      <box>
        <text fg={theme().text}>
          <b>Code Indexing</b>
        </text>
        <box flexDirection="row" gap={1}>
          <text flexShrink={0} style={{ fg: tone(status().state, props.api) }}>
            •
          </text>
          <text fg={theme().text} wrapMode="word">
            {label()}
          </text>
        </box>
        <Show when={msg()}>{(text) => <text fg={theme().textMuted}>{text()}</text>}</Show>
      </box>
    </Show>
  )
}

const tui: TuiPlugin = async (api) => {
  api.slots.register({
    order: 225,
    slots: {
      sidebar_content() {
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

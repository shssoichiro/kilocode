import { Component, createSignal } from "solid-js"
import { Icon } from "@kilocode/kilo-ui/icon"
import { Tooltip } from "@kilocode/kilo-ui/tooltip"
import { useLanguage } from "../../context/language"

export const PermissionParameters: Component<{ value: string }> = (props) => {
  const language = useLanguage()
  const [copied, setCopied] = createSignal(false)

  const copy = () => {
    navigator.clipboard.writeText(props.value)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div data-slot="permission-parameters">
      <div data-slot="permission-parameters-header">
        <span>{language.t("ui.permission.parameters")}</span>
        <Tooltip value={language.t("ui.permission.copyCommand")} placement="top">
          <button
            data-slot="permission-parameters-copy"
            data-copied={copied() ? "" : undefined}
            onClick={copy}
            aria-label={language.t("ui.permission.copyCommand")}
          >
            <Icon name={copied() ? "check-small" : "copy"} size="small" />
          </button>
        </Tooltip>
      </div>
      <pre data-slot="permission-parameters-pre">{props.value}</pre>
    </div>
  )
}

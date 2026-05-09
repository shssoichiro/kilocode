import { describe, expect, test } from "bun:test"
import type { Part } from "@kilocode/sdk/v2"
import { permissionParameters } from "../../webview-ui/src/components/chat/permission-dock-utils"
import type { PermissionRequest } from "../../webview-ui/src/types/messages"

function req(args: PermissionRequest["args"], tool = { messageID: "msg-1", callID: "call-1" }): PermissionRequest {
  return {
    id: "perm-1",
    sessionID: "ses-1",
    toolName: "todowrite",
    patterns: ["*"],
    always: ["*"],
    args,
    tool,
  }
}

describe("permissionParameters", () => {
  test("prefers matching tool part input", () => {
    const params = permissionParameters(req({}), {
      "msg-1": [
        {
          type: "tool",
          messageID: "msg-1",
          callID: "call-1",
          tool: "todowrite",
          state: { status: "pending", input: { todos: [{ content: "Write tests", status: "pending" }] } },
        } as unknown as Part,
      ],
    })

    expect(params).toBe(JSON.stringify({ todos: [{ content: "Write tests", status: "pending" }] }, null, 2))
  })

  test("falls back to metadata when tool part is unavailable", () => {
    const params = permissionParameters(req({ pattern: "**/*.ts" }), {})
    expect(params).toBe(JSON.stringify({ pattern: "**/*.ts" }, null, 2))
  })

  test("omits diff-only metadata because diff preview already renders it", () => {
    const params = permissionParameters(
      req({
        filepath: "src/app.ts",
        diff: "--- src/app.ts",
        filediff: { file: "src/app.ts", patch: "patch", additions: 1, deletions: 0 },
        files: [{ relativePath: "src/app.ts", type: "update", patch: "patch" }],
      }),
      {},
    )

    expect(params).toBe(JSON.stringify({ filepath: "src/app.ts" }, null, 2))
  })

  test("does not duplicate command-only bash metadata", () => {
    const params = permissionParameters(req({ command: "git status" }, undefined), {})
    expect(params).toBeUndefined()
  })

  test("returns undefined for empty metadata", () => {
    const params = permissionParameters(req({}, undefined), {})
    expect(params).toBeUndefined()
  })
})

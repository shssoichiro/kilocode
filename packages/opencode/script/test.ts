// kilocode_change - new file
// RATIONALE: Bun's mock.module() replaces module resolution globally for the
// entire process (oven-sh/bun#12823). Files using mock.module() must run in
// isolated processes to prevent cross-test contamination.
const polluters = [
  "test/cli/tui/thread.test.ts",
  "test/kilo-sessions/kilo-sessions-enable-remote.test.ts",
  "test/kilocode/local-model.test.ts",
  "test/kilocode/model-cache-org.test.ts",
  "test/kilocode/run-network.test.ts",
  "test/kilocode/session-import-service.test.ts",
  "test/mcp/headers.test.ts",
  "test/mcp/lifecycle.test.ts",
  "test/mcp/oauth-auto-connect.test.ts",
  "test/mcp/oauth-browser.test.ts",
  "test/server/experimental-session-list.test.ts",
  "test/server/global-session-list.test.ts",
  "test/tool/recall.test.ts",
  "src/commit-message/__tests__/generate.test.ts",
  "src/commit-message/__tests__/git-context.test.ts",
]

const timeout = "30000"

async function run(cmd: string[]) {
  const proc = Bun.spawn(cmd, {
    stdio: ["inherit", "inherit", "inherit"],
    windowsHide: true,
  })
  const code = await proc.exited
  if (code !== 0) process.exit(code)
}

await run(["bun", "test", "--timeout", timeout, ...polluters.flatMap((p) => ["--path-ignore-patterns", p])])

for (const file of polluters) {
  await run(["bun", "test", "--timeout", timeout, file])
}

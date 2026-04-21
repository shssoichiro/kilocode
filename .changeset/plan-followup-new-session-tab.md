---
"@kilocode/cli": patch
---

Fix the "Start new session" button on the plan follow-up prompt not switching the VS Code Agent Manager to the new session when handover generation is slow. The new session is now created first so the UI can switch to it immediately, and the handover message is injected as it becomes available.

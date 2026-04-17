package ai.kilocode.client.session.model

import ai.kilocode.client.session.SessionControllerEvent

class ConfigSelectionTest : SessionControllerTestBase() {

    fun `test selectModel updates ChatModel and calls updateConfig`() {
        val m = model()
        collect(m)
        flush()

        edt { m.selectModel("kilo", "gpt-5") }
        flush()

        assertEquals("kilo/gpt-5", m.model.model)
        assertEquals(1, rpc.configs.size)
        assertEquals("kilo/gpt-5", rpc.configs[0].second.model)
        assertController(
            """
            [kilo/gpt-5] [app: DISCONNECTED] [workspace: PENDING]
            """,
            m,
            show = false,
        )
    }

    fun `test selectAgent updates ChatModel and calls updateConfig`() {
        val m = model()
        collect(m)
        flush()

        edt { m.selectAgent("plan") }
        flush()

        assertEquals("plan", m.model.agent)
        assertEquals(1, rpc.configs.size)
        assertEquals("plan", rpc.configs[0].second.agent)
        assertController(
            """
            [plan] [app: DISCONNECTED] [workspace: PENDING]
            """,
            m,
            show = false,
        )
    }

    fun `test selectModel fires WorkspaceReady event`() {
        val m = model()
        val events = collect(m)
        flush()

        edt { m.selectModel("kilo", "gpt-5") }
        flush()

        assertTrue(events.any { it is SessionControllerEvent.WorkspaceReady })
    }
}

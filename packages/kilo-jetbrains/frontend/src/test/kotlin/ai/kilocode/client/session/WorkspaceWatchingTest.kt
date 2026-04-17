package ai.kilocode.client.session

class WorkspaceWatchingTest : SessionControllerTestBase() {

    fun `test workspace ready populates agents and models`() {
        val m = model()
        val events = collect(m)
        flush()

        projectRpc.state.value = workspaceReady()
        flush()

        assertEquals(1, m.model.agents.size)
        assertEquals("code", m.model.agents[0].name)
        assertEquals(1, m.model.models.size)
        assertEquals("gpt-5", m.model.models[0].id)
        assertFalse(m.model.isReady())
        assertTrue(events.any { it is SessionControllerEvent.WorkspaceReady })
        assertController(
            """
            [code] [kilo/gpt-5] [app: DISCONNECTED] [workspace: READY]
            """,
            m,
            show = false,
        )
    }

    fun `test workspace ready sets default agent and model`() {
        val m = model()
        collect(m)
        flush()

        projectRpc.state.value = workspaceReady()
        flush()

        assertEquals("code", m.model.agent)
        assertEquals("kilo/gpt-5", m.model.model)
    }
}

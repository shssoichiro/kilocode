package ai.kilocode.client.session.model

import ai.kilocode.client.session.SessionControllerEvent
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto

class AppWatchingTest : SessionControllerTestBase() {

    fun `test app state change fires AppChanged`() {
        val m = model()
        val events = collect(m)
        flush()

        appRpc.state.value = KiloAppStateDto(KiloAppStatusDto.READY)
        flush()

        assertTrue(events.any { it is SessionControllerEvent.AppChanged })
        assertEquals(KiloAppStatusDto.READY, m.model.app.status)
        assertController(
            """
            [app: READY] [workspace: PENDING]
            """,
            m,
            show = false,
        )
    }
}

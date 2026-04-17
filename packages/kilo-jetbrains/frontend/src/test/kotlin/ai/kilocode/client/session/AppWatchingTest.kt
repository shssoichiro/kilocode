package ai.kilocode.client.session

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
        assertController(
            """
            [app: READY] [workspace: PENDING]
            """,
            m,
            show = false,
        )
    }
}

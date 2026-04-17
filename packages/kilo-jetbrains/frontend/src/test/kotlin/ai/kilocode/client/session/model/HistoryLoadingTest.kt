package ai.kilocode.client.session.model

import ai.kilocode.client.session.SessionControllerEvent
import ai.kilocode.rpc.dto.MessageWithPartsDto

class HistoryLoadingTest : SessionControllerTestBase() {

    fun `test existing session loads history on init`() {
        val m = msg("msg1", "ses_test", "user")
        val part = part("prt1", "ses_test", "msg1", "text", text = "hello")
        rpc.history.add(MessageWithPartsDto(m, listOf(part)))

        val c = model("ses_test")
        val events = collectModel(c)
        flush()

        assertTrue(events.any { it is SessionModelEvent.HistoryLoaded })
        assertModel(
            """
            user#msg1
            text#prt1:
              hello
            """,
            c,
        )
    }

    fun `test non-empty history shows messages view`() {
        rpc.history.add(MessageWithPartsDto(msg("msg1", "ses_test", "user"), emptyList()))

        val c = model("ses_test")
        val events = collect(c)
        flush()

        assertTrue(events.any { it is SessionControllerEvent.ViewChanged && it.show })
        assertController(
            """
            user#msg1

            [app: DISCONNECTED] [workspace: PENDING]
            """,
            c,
        )
    }
}

package ai.kilocode.client.session.model

import ai.kilocode.rpc.dto.ChatEventDto

class StatusComputationTest : SessionControllerTestBase() {

    fun `test status shows tool-specific text`() {
        val (m, _, _) = prompted()

        emit(ChatEventDto.TurnOpen("ses_test"))
        flush()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")))
        flush()

        emit(ChatEventDto.PartUpdated("ses_test", part("prt1", "ses_test", "msg1", "tool", tool = "bash")))
        flush()

        assertController(
            """
            assistant#msg1
            tool#prt1 bash [PENDING]

            [code] [kilo/gpt-5] [busy] [running commands]
            """,
            m,
        )
    }

    fun `test PartUpdated after TurnClose does not fire StateChanged`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")))
        flush()
        emit(ChatEventDto.TurnOpen("ses_test"))
        flush()
        emit(ChatEventDto.TurnClose("ses_test", "completed"))
        flush()

        val before = model.filterIsInstance<SessionModelEvent.StateChanged>().size

        emit(ChatEventDto.PartUpdated("ses_test", part("prt1", "ses_test", "msg1", "text", text = "late")))
        flush()

        val after = model.filterIsInstance<SessionModelEvent.StateChanged>().size
        assertEquals(before, after)
    }
}

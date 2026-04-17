package ai.kilocode.client.session

import ai.kilocode.client.session.model.SessionModelEvent
import ai.kilocode.rpc.dto.ChatEventDto

class StatusComputationTest : SessionControllerTestBase() {

    fun `test status shows tool-specific text`() {
        val (m, _, _) = prompted()

        emit(ChatEventDto.TurnOpen("ses_test"), flush = true)

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")), flush = true)

        emit(ChatEventDto.PartUpdated("ses_test", part("prt1", "ses_test", "msg1", "tool", tool = "bash")), flush = true)

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

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")), flush = true)
        emit(ChatEventDto.TurnOpen("ses_test"), flush = true)
        emit(ChatEventDto.TurnClose("ses_test", "completed"), flush = true)

        val before = model.filterIsInstance<SessionModelEvent.StateChanged>().size

        emit(ChatEventDto.PartUpdated("ses_test", part("prt1", "ses_test", "msg1", "text", text = "late")), flush = true)

        val after = model.filterIsInstance<SessionModelEvent.StateChanged>().size
        assertEquals(before, after)
    }
}

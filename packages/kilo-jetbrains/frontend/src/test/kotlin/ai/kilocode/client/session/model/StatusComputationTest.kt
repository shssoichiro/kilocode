package ai.kilocode.client.session.model

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.rpc.dto.ChatEventDto

class StatusComputationTest : SessionManagerTestBase() {

    fun `test status shows tool-specific text`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.TurnOpen("ses_test"))
        flush()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")))
        flush()

        emit(ChatEventDto.PartUpdated("ses_test", part("prt1", "ses_test", "msg1", "tool", tool = "bash")))
        flush()

        val state = model.filterIsInstance<SessionModelEvent.StateChanged>()
            .mapNotNull { it.state as? SessionState.Busy }
            .lastOrNull()

        assertNotNull(state)
        val text = state!!.text
        assertEquals(KiloBundle.message("session.status.commands"), text)
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

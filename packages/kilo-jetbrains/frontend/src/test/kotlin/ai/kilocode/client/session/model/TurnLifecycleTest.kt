package ai.kilocode.client.session.model

import ai.kilocode.rpc.dto.ChatEventDto
import ai.kilocode.rpc.dto.MessageErrorDto

class TurnLifecycleTest : SessionManagerTestBase() {

    fun `test TurnOpen fires StateChanged to Busy`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.TurnOpen("ses_test"))
        flush()

        val state = model.filterIsInstance<SessionModelEvent.StateChanged>().lastOrNull()?.state
        assertTrue(state is SessionState.Busy)
    }

    fun `test TurnClose fires StateChanged to Idle`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.TurnOpen("ses_test"))
        flush()
        emit(ChatEventDto.TurnClose("ses_test", "completed"))
        flush()

        val state = model.filterIsInstance<SessionModelEvent.StateChanged>().last().state
        assertEquals(SessionState.Idle, state)
    }

    fun `test Error fires StateChanged to Error`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.Error("ses_test", MessageErrorDto(type = "APIError", message = "Bad Request")))
        flush()

        val state = model.filterIsInstance<SessionModelEvent.StateChanged>().last().state
        assertTrue(state is SessionState.Error)
        assertEquals("Bad Request", (state as SessionState.Error).message)
    }

    fun `test Error with null message falls back to type`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.Error("ses_test", MessageErrorDto(type = "timeout", message = null)))
        flush()

        val state = model.filterIsInstance<SessionModelEvent.StateChanged>().last().state as SessionState.Error
        assertEquals("timeout", state.message)
    }
}

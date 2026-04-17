package ai.kilocode.client.session.model

import ai.kilocode.rpc.dto.ChatEventDto
import ai.kilocode.rpc.dto.PermissionRequestDto
import ai.kilocode.rpc.dto.QuestionInfoDto
import ai.kilocode.rpc.dto.QuestionOptionDto
import ai.kilocode.rpc.dto.QuestionRequestDto
import ai.kilocode.rpc.dto.ToolRefDto

class PromptLifecycleTest : SessionManagerTestBase() {

    fun `test PermissionAsked moves state to AwaitingPermission`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.PermissionAsked("ses_test", permission("perm1")))
        flush()

        val state = model.filterIsInstance<SessionModelEvent.StateChanged>().last().state
        assertTrue(state is SessionState.AwaitingPermission)
        assertEquals("perm1", (state as SessionState.AwaitingPermission).permission.id)
        assertEquals("msg1", state.permission.tool!!.messageId)
    }

    fun `test PermissionReplied resumes Busy state`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.PermissionAsked("ses_test", permission("perm1")))
        flush()
        emit(ChatEventDto.PermissionReplied("ses_test", "perm1"))
        flush()

        val state = model.filterIsInstance<SessionModelEvent.StateChanged>().last().state
        assertTrue(state is SessionState.Busy)
    }

    fun `test QuestionAsked moves state to AwaitingQuestion`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.QuestionAsked("ses_test", question("q1")))
        flush()

        val state = model.filterIsInstance<SessionModelEvent.StateChanged>().last().state
        assertTrue(state is SessionState.AwaitingQuestion)
        assertEquals("q1", (state as SessionState.AwaitingQuestion).question.id)
        assertEquals("call1", state.question.tool!!.callId)
    }

    fun `test QuestionReplied resumes Busy state`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.QuestionAsked("ses_test", question("q1")))
        flush()
        emit(ChatEventDto.QuestionReplied("ses_test", "q1"))
        flush()

        val state = model.filterIsInstance<SessionModelEvent.StateChanged>().last().state
        assertTrue(state is SessionState.Busy)
    }

    fun `test QuestionRejected moves state to Idle`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.QuestionAsked("ses_test", question("q1")))
        flush()
        emit(ChatEventDto.QuestionRejected("ses_test", "q1"))
        flush()

        val state = model.filterIsInstance<SessionModelEvent.StateChanged>().last().state
        assertEquals(SessionState.Idle, state)
    }

    private fun permission(id: String) = PermissionRequestDto(
        id = id,
        sessionID = "ses_test",
        permission = "edit",
        patterns = listOf("*.kt"),
        always = emptyList(),
        metadata = mapOf("kind" to "edit"),
        tool = ToolRefDto("msg1", "call1"),
    )

    private fun question(id: String) = QuestionRequestDto(
        id = id,
        sessionID = "ses_test",
        questions = listOf(
            QuestionInfoDto(
                question = "Pick one",
                header = "Choice",
                options = listOf(QuestionOptionDto("A", "Option A")),
                multiple = false,
                custom = true,
            ),
        ),
        tool = ToolRefDto("msg1", "call1"),
    )
}

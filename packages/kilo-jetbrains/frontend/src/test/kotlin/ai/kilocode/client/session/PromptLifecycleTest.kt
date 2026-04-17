package ai.kilocode.client.session

import ai.kilocode.rpc.dto.ChatEventDto
import ai.kilocode.rpc.dto.PermissionRequestDto
import ai.kilocode.rpc.dto.QuestionInfoDto
import ai.kilocode.rpc.dto.QuestionOptionDto
import ai.kilocode.rpc.dto.QuestionRequestDto
import ai.kilocode.rpc.dto.ToolRefDto

class PromptLifecycleTest : SessionControllerTestBase() {

    fun `test PermissionAsked moves state to AwaitingPermission`() {
        val (m, _, _) = prompted()

        emit(ChatEventDto.PermissionAsked("ses_test", permission("perm1")), flush = true)

        assertController(
            """
            permission#perm1
            tool: msg1/call1
            name: edit
            patterns: *.kt
            always: <none>
            file: src/A.kt
            state: RESPONDING
            metadata: kind=edit

            [code] [kilo/gpt-5] [awaiting-permission]
            """,
            m,
        )
    }

    fun `test PermissionReplied resumes Busy state`() {
        val (m, _, _) = prompted()

        emit(ChatEventDto.PermissionAsked("ses_test", permission("perm1")), flush = true)
        emit(ChatEventDto.PermissionReplied("ses_test", "perm1"), flush = true)

        assertController(
            """
            [code] [kilo/gpt-5] [busy] [considering next steps]
            """,
            m,
        )
    }

    fun `test QuestionAsked moves state to AwaitingQuestion`() {
        val (m, _, _) = prompted()

        emit(ChatEventDto.QuestionAsked("ses_test", question("q1")), flush = true)

        assertController(
            """
            question#q1
            tool: msg1/call1
            header: Choice
            prompt: Pick one
            option: A - Option A
            multiple: false
            custom: true

            [code] [kilo/gpt-5] [awaiting-question]
            """,
            m,
        )
    }

    fun `test QuestionReplied resumes Busy state`() {
        val (m, _, _) = prompted()

        emit(ChatEventDto.QuestionAsked("ses_test", question("q1")), flush = true)
        emit(ChatEventDto.QuestionReplied("ses_test", "q1"), flush = true)

        assertController(
            """
            [code] [kilo/gpt-5] [busy] [considering next steps]
            """,
            m,
        )
    }

    fun `test QuestionRejected moves state to Idle`() {
        val (m, _, _) = prompted()

        emit(ChatEventDto.QuestionAsked("ses_test", question("q1")), flush = true)
        emit(ChatEventDto.QuestionRejected("ses_test", "q1"), flush = true)

        assertController(
            """
            [code] [kilo/gpt-5] [idle]
            """,
            m,
        )
    }

    private fun permission(id: String) = PermissionRequestDto(
        id = id,
        sessionID = "ses_test",
        permission = "edit",
        patterns = listOf("*.kt"),
        always = emptyList(),
        metadata = mapOf("kind" to "edit", "file" to "src/A.kt", "state" to "RESPONDING"),
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

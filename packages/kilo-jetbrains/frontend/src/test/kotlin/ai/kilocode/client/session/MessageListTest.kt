package ai.kilocode.client.session

import ai.kilocode.client.session.model.SessionModelEvent
import ai.kilocode.rpc.dto.ChatEventDto

class MessageListTest : SessionControllerTestBase() {

    fun `test MessageUpdated adds message to ChatModel`() {
        val (m, _, model) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")), flush = true)

        assertTrue(model.any { it is SessionModelEvent.MessageAdded })
        assertNotNull(m.model.message("msg1"))
    }

    fun `test PartUpdated text updates ChatModel`() {
        val (m, _, model) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")), flush = true)

        emit(ChatEventDto.PartUpdated("ses_test", part("prt1", "ses_test", "msg1", "text", text = "hello")), flush = true)

        assertTrue(model.any { it is SessionModelEvent.ContentAdded && it.messageId == "msg1" })
        assertModel(
            """
            assistant#msg1
            text#prt1:
              hello
            """,
            m,
        )
    }

    fun `test PartDelta appends text to ChatModel`() {
        val (m, _, _) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")), flush = true)

        emit(ChatEventDto.PartDelta("ses_test", "msg1", "prt1", "text", "hello "))
        emit(ChatEventDto.PartDelta("ses_test", "msg1", "prt1", "text", "world"), flush = true)

        assertModel(
            """
            assistant#msg1
            text#prt1:
              hello world
            """,
            m,
        )
    }

    fun `test MessageRemoved removes from ChatModel`() {
        val (m, _, _) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "user")), flush = true)
        assertNotNull(m.model.message("msg1"))

        emit(ChatEventDto.MessageRemoved("ses_test", "msg1"), flush = true)
        assertNull(m.model.message("msg1"))
    }
}

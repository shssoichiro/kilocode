package ai.kilocode.client.session.model

import ai.kilocode.rpc.dto.ChatEventDto

class MessageListTest : SessionControllerTestBase() {

    fun `test MessageUpdated adds message to ChatModel`() {
        val (m, _, model) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")))
        flush()

        assertTrue(model.any { it is SessionModelEvent.MessageAdded })
        assertNotNull(m.model.message("msg1"))
    }

    fun `test PartUpdated text updates ChatModel`() {
        val (m, _, model) = prompted()

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")))
        flush()

        emit(ChatEventDto.PartUpdated("ses_test", part("prt1", "ses_test", "msg1", "text", text = "hello")))
        flush()

        assertTrue(model.any { it is SessionModelEvent.ContentAdded && it.messageId == "msg1" })
        val p = m.model.content("msg1", "prt1")
        assertTrue(p is Text)
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

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "assistant")))
        flush()

        emit(ChatEventDto.PartDelta("ses_test", "msg1", "prt1", "text", "hello "))
        emit(ChatEventDto.PartDelta("ses_test", "msg1", "prt1", "text", "world"))
        flush()

        val p = m.model.content("msg1", "prt1")
        assertNotNull(p)
        assertTrue(p is Text)
        assertEquals("hello world", (p as Text).content.toString())
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

        emit(ChatEventDto.MessageUpdated("ses_test", msg("msg1", "ses_test", "user")))
        flush()
        assertNotNull(m.model.message("msg1"))

        emit(ChatEventDto.MessageRemoved("ses_test", "msg1"))
        flush()
        assertNull(m.model.message("msg1"))
    }
}

package ai.kilocode.client.session

import ai.kilocode.client.session.model.SessionModelEvent
import ai.kilocode.client.session.model.SessionState
import ai.kilocode.rpc.dto.ChatEventDto
import ai.kilocode.rpc.dto.SessionStatusDto
import com.intellij.openapi.util.Disposer

class ListenerLifecycleTest : SessionControllerTestBase() {

    fun `test listener removed on parent dispose`() {
        val m = model()
        val disposable = Disposer.newDisposable("listener-parent")
        Disposer.register(parent, disposable)

        val events = mutableListOf<SessionControllerEvent>()
        m.addListener(disposable) { events.add(it) }

        edt { m.prompt("before") }
        flush()
        val before = events.size

        Disposer.dispose(disposable)

        edt { m.prompt("after") }
        flush()

        assertEquals(before, events.size)
    }

    fun `test all listeners notified`() {
        val m = model()
        val events1 = mutableListOf<SessionControllerEvent>()
        val events2 = mutableListOf<SessionControllerEvent>()
        val d1 = Disposer.newDisposable("l1")
        val d2 = Disposer.newDisposable("l2")
        Disposer.register(parent, d1)
        Disposer.register(parent, d2)

        m.addListener(d1) { events1.add(it) }
        m.addListener(d2) { events2.add(it) }

        edt { m.prompt("go") }
        flush()

        assertTrue(events1.isNotEmpty())
        assertTrue(events2.isNotEmpty())
        assertEquals(events1.map { it::class }, events2.map { it::class })
    }

    fun `test session status busy fires StateChanged to Busy`() {
        val (_, _, model) = prompted()

        emit(ChatEventDto.SessionStatusChanged("ses_test", SessionStatusDto("busy", null)), flush = true)

        assertTrue(model.any { it is SessionModelEvent.StateChanged && (it.state is SessionState.Busy) })
    }
}

package ai.kilocode.client.session

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.app.KiloWorkspaceService
import ai.kilocode.client.app.Workspace
import ai.kilocode.client.session.model.Permission
import ai.kilocode.client.session.model.PermissionMeta
import ai.kilocode.client.session.model.Question
import ai.kilocode.client.session.model.QuestionItem
import ai.kilocode.client.session.model.QuestionOption
import ai.kilocode.client.session.model.SessionState
import ai.kilocode.client.session.ui.ConnectionPanel
import ai.kilocode.client.session.ui.PermissionPanel
import ai.kilocode.client.session.ui.PromptPanel
import ai.kilocode.client.session.ui.QuestionPanel
import ai.kilocode.client.testing.FakeAppRpcApi
import ai.kilocode.client.testing.FakeSessionRpcApi
import ai.kilocode.client.testing.FakeWorkspaceRpcApi
import ai.kilocode.rpc.dto.KiloAppStateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.KiloWorkspaceStateDto
import ai.kilocode.rpc.dto.KiloWorkspaceStatusDto
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities

@Suppress("UnstableApiUsage")
class SessionUiLayoutTest : BasePlatformTestCase() {

    private lateinit var scope: CoroutineScope
    private lateinit var sessions: KiloSessionService
    private lateinit var app: KiloAppService
    private lateinit var workspaces: KiloWorkspaceService
    private lateinit var workspace: Workspace
    private lateinit var ui: SessionUi

    override fun setUp() {
        super.setUp()
        scope = CoroutineScope(SupervisorJob())

        val rpc = FakeSessionRpcApi()
        val appRpc = FakeAppRpcApi().also {
            it.state.value = KiloAppStateDto(KiloAppStatusDto.READY)
        }
        val workspaceRpc = FakeWorkspaceRpcApi().also {
            it.state.value = KiloWorkspaceStateDto(status = KiloWorkspaceStatusDto.READY)
        }

        sessions = KiloSessionService(project, scope, rpc)
        app = KiloAppService(scope, appRpc)
        workspaces = KiloWorkspaceService(scope, workspaceRpc)
        workspace = workspaces.workspace("/test")

        ui = SessionUi(project, workspace, sessions, app, scope).apply {
            setSize(800, 600)
        }
        layout()
    }

    override fun tearDown() {
        try {
            scope.cancel()
        } finally {
            super.tearDown()
        }
    }

    fun `test root contains content and overlay layers`() {
        val root = find<JLayeredPane>(ui)

        assertEquals(2, root.componentCount)
        assertTrue(root.components.all { it is JPanel })
        val panels = root.components.map { it as JPanel }
        val overlay = panels.first { it.components.any { child -> child is ConnectionPanel } }
        val content = panels.first { it !== overlay }
        assertEquals(JLayeredPane.DEFAULT_LAYER, root.getLayer(content))
        assertEquals(JLayeredPane.PALETTE_LAYER, root.getLayer(overlay))
    }

    fun `test overlay panel matches prompt width and sits above prompt`() {
        val prompt = find<PromptPanel>(ui)
        val connection = find<ConnectionPanel>(ui)
        val overlay = connection.parent

        layout()

        val box = SwingUtilities.convertRectangle(prompt.parent, prompt.bounds, overlay)
        val h = connection.preferredSize.height

        assertEquals(box.x, connection.x)
        assertEquals(box.width, connection.width)
        assertEquals(maxOf(0, box.y - h), connection.y)
        assertEquals(h, connection.height)
    }

    fun `test overlay follows prompt when question panel changes visibility`() {
        val prompt = find<PromptPanel>(ui)
        val connection = find<ConnectionPanel>(ui)
        val overlay = connection.parent
        val question = find<QuestionPanel>(ui)

        layout()
        assertFalse(question.isVisible)

        controller().model.setState(questionState())
        layout()

        assertTrue(question.isVisible)
        val box = SwingUtilities.convertRectangle(prompt.parent, prompt.bounds, overlay)
        assertEquals(box.width, connection.width)
        assertEquals(maxOf(0, box.y - connection.preferredSize.height), connection.y)
        assertEquals(connection.preferredSize.height, connection.height)
    }

    fun `test overlay follows prompt when permission panel changes visibility`() {
        val prompt = find<PromptPanel>(ui)
        val connection = find<ConnectionPanel>(ui)
        val overlay = connection.parent
        val permission = find<PermissionPanel>(ui)

        layout()
        assertFalse(permission.isVisible)

        controller().model.setState(permissionState())
        layout()

        assertTrue(permission.isVisible)
        val box = SwingUtilities.convertRectangle(prompt.parent, prompt.bounds, overlay)
        assertEquals(box.width, connection.width)
        assertEquals(maxOf(0, box.y - connection.preferredSize.height), connection.y)
        assertEquals(connection.preferredSize.height, connection.height)
    }

    private fun layout() {
        ui.doLayout()
        find<JLayeredPane>(ui).doLayout()
    }

    private inline fun <reified T> find(root: java.awt.Container): T {
        return find(root, T::class.java) ?: error("missing ${T::class.java.simpleName}")
    }

    private fun <T> find(root: java.awt.Container, cls: Class<T>): T? {
        if (cls.isInstance(root)) return cls.cast(root)
        for (child in root.components) {
            if (cls.isInstance(child)) return cls.cast(child)
            if (child is java.awt.Container) {
                val item = find(child, cls)
                if (item != null) return item
            }
        }
        return null
    }

    private fun controller(): SessionController {
        val field = SessionUi::class.java.getDeclaredField("controller")
        field.isAccessible = true
        return field.get(ui) as SessionController
    }

    private fun questionState() = SessionState.AwaitingQuestion(
        Question(
            id = "q1",
            items = listOf(
                QuestionItem(
                    question = "Proceed?",
                    header = "Confirm",
                    options = listOf(QuestionOption("Yes", "Continue")),
                    multiple = false,
                    custom = true,
                )
            ),
        )
    )

    private fun permissionState() = SessionState.AwaitingPermission(
        Permission(
            id = "p1",
            sessionId = "ses",
            name = "edit",
            patterns = listOf("*.kt"),
            always = emptyList(),
            meta = PermissionMeta(raw = emptyMap()),
        )
    )
}

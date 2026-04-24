package ai.kilocode.client.session.ui

import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.SessionController
import ai.kilocode.client.session.SessionControllerEvent
import ai.kilocode.client.session.SessionControllerListener
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.KiloWorkspaceStatusDto
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JPanel

class ConnectionPanel(
    parent: Disposable,
    private val controller: SessionController,
) : JPanel(BorderLayout()), SessionControllerListener, Disposable {

    private val label = JBLabel().apply {
        border = JBUI.Borders.empty(4, 8)
        foreground = UIUtil.getContextHelpForeground()
    }

    init {
        Disposer.register(parent, this)
        isOpaque = true
        background = UIUtil.getPanelBackground()
        add(label, BorderLayout.CENTER)
        controller.addListener(this, this)
        render()
    }

    override fun onEvent(event: SessionControllerEvent) {
        when (event) {
            is SessionControllerEvent.AppChanged,
            is SessionControllerEvent.WorkspaceChanged -> render()

            else -> Unit
        }
    }

    private fun render() {
        val app = controller.model.app
        val workspace = controller.model.workspace

        if (app.status == KiloAppStatusDto.ERROR) {
            label.foreground = UIUtil.getErrorForeground()
            label.text = app.error ?: KiloBundle.message("session.connection.error.unknown")
            showPanel()
            return
        }

        if (workspace.status == KiloWorkspaceStatusDto.ERROR) {
            label.foreground = UIUtil.getErrorForeground()
            label.text = workspace.error ?: KiloBundle.message("session.connection.error.unknown")
            showPanel()
            return
        }

        if (app.status == KiloAppStatusDto.READY && workspace.status == KiloWorkspaceStatusDto.READY) {
            hidePanel()
            return
        }

        label.foreground = UIUtil.getContextHelpForeground()
        label.text = KiloBundle.message("session.connection.connecting")
        showPanel()
    }

    private fun showPanel() {
        if (!isVisible) {
            isVisible = true
            refresh()
            return
        }
        refresh()
    }

    private fun hidePanel() {
        if (isVisible) {
            isVisible = false
            refresh()
            return
        }
        refresh()
    }

    private fun refresh() {
        parent?.revalidate()
        parent?.repaint()
        revalidate()
        repaint()
    }

    override fun dispose() {
        // no-op
    }
}

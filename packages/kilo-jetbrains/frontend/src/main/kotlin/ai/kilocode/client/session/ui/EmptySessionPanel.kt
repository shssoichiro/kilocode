package ai.kilocode.client.session.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JPanel

/**
 * Centered empty-session panel.
 */
class EmptySessionPanel(
    parent: Disposable,
) : JPanel(GridBagLayout()), Disposable {

    init {
        Disposer.register(parent, this)
    }

    private val logo = JBLabel(
        IconLoader.getIcon("/icons/kilo-content.svg", EmptySessionPanel::class.java),
    ).apply {
        alignmentX = CENTER_ALIGNMENT
    }

    init {
        isOpaque = false

        add(logo, GridBagConstraints().apply {
            anchor = GridBagConstraints.CENTER
            insets = JBUI.insets(12)
        })
    }

    override fun dispose() {
        // no-op
    }
}

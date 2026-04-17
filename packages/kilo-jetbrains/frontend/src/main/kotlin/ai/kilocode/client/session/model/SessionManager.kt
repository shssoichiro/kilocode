package ai.kilocode.client.session.model

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.app.Workspace
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.content.Permission
import ai.kilocode.client.session.model.content.PermissionMeta
import ai.kilocode.client.session.model.content.Question
import ai.kilocode.client.session.model.content.QuestionItem
import ai.kilocode.client.session.model.content.QuestionOption
import ai.kilocode.client.session.model.content.ToolCallRef
import ai.kilocode.rpc.dto.ChatEventDto
import ai.kilocode.rpc.dto.ConfigUpdateDto
import ai.kilocode.rpc.dto.KiloAppStatusDto
import ai.kilocode.rpc.dto.KiloWorkspaceStatusDto
import ai.kilocode.rpc.dto.PermissionRequestDto
import ai.kilocode.rpc.dto.QuestionRequestDto
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Session lifecycle orchestrator for a single session.
 *
 * Accepts an optional [id] — if non-null, loads that session immediately.
 * If null, lazily creates a session on the first [prompt] call. This ensures
 * event subscription happens before the prompt is sent, eliminating races.
 *
 * Owns [SessionModel] — the single source of truth for chat content and
 * state. UIs observe model changes via [SessionModelEvent] on [chat].
 * Lifecycle events (app/workspace state, view switching) are published
 * via [SessionManagerEvent] to registered listeners.
 */
class SessionManager(
    parent: Disposable,
    id: String?,
    private val sessions: KiloSessionService,
    private val workspace: Workspace,
    private val app: KiloAppService,
    private val cs: CoroutineScope,
) : Disposable {

    companion object {
        private val LOG = Logger.getInstance(SessionManager::class.java)
    }

    init {
        Disposer.register(parent, this)
    }

    val chat = SessionModel()

    private val listeners = mutableListOf<SessionManagerListener>()
    private var sessionId: String? = id
    private val directory: String get() = workspace.directory

    private var partType: String? = null
    private var tool: String? = null
    private var eventJob: Job? = null

    fun addListener(parent: Disposable, listener: SessionManagerListener) {
        listeners.add(listener)
        Disposer.register(parent) { listeners.remove(listener) }
    }

    fun prompt(text: String) {
        showMessages()
        cs.launch {
            try {
                val id = sessionId ?: run {
                    val session = sessions.create(directory)
                    sessionId = session.id
                    subscribeEvents()
                    session.id
                }
                sessions.prompt(id, directory, text)
            } catch (e: Exception) {
                LOG.warn("prompt failed", e)
                edt {
                    val msg = e.message ?: KiloBundle.message("session.error.prompt")
                    chat.setState(SessionState.Error(msg))
                }
            }
        }
    }

    fun abort() {
        val id = sessionId ?: return
        cs.launch {
            try {
                sessions.abort(id, directory)
            } catch (e: Exception) {
                LOG.warn("abort failed", e)
            }
        }
    }

    fun selectAgent(name: String) {
        chat.agent = name
        cs.launch {
            try {
                sessions.updateConfig(directory, ConfigUpdateDto(agent = name))
            } catch (e: Exception) {
                LOG.warn("selectAgent failed", e)
            }
        }
        fire(SessionManagerEvent.WorkspaceReady)
    }

    fun selectModel(provider: String, id: String) {
        chat.model = "$provider/$id"
        cs.launch {
            try {
                sessions.updateConfig(directory, ConfigUpdateDto(model = "$provider/$id"))
            } catch (e: Exception) {
                LOG.warn("selectModel failed", e)
            }
        }
        fire(SessionManagerEvent.WorkspaceReady)
    }

    init {
        if (sessionId != null) {
            loadHistory()
            subscribeEvents()
        }

        app.connect()
        cs.launch {
            app.state.collect { state ->
                if (state.status == KiloAppStatusDto.READY) app.fetchVersionAsync()
                edt {
                    chat.app = state
                    chat.version = app.version
                    fire(SessionManagerEvent.AppChanged)
                }
            }
        }

        cs.launch {
            workspace.state.collect { state ->
                edt {
                    chat.workspace = state
                    fire(SessionManagerEvent.WorkspaceChanged)

                    if (state.status == KiloWorkspaceStatusDto.READY) {
                        chat.agents = state.agents?.agents?.map {
                            AgentItem(it.name, it.displayName ?: it.name)
                        } ?: emptyList()

                        chat.models = state.providers?.let { providers ->
                            providers.providers
                                .filter { it.id in providers.connected }
                                .flatMap { provider ->
                                    provider.models.map { (id, info) ->
                                        ModelItem(id, info.name, provider.id)
                                    }
                                }
                        } ?: emptyList()

                        if (chat.agent == null) chat.agent = state.agents?.default
                        if (chat.model == null) chat.model = state.providers?.defaults?.entries?.firstOrNull()?.value

                        chat.ready = true
                        fire(SessionManagerEvent.WorkspaceReady)
                    }
                }
            }
        }
    }

    private fun loadHistory() {
        val id = sessionId ?: return
        cs.launch {
            try {
                val history = sessions.messages(id, directory)
                edt {
                    chat.loadHistory(history)
                    if (!chat.isEmpty()) showMessages()
                }
            } catch (e: Exception) {
                LOG.warn("loadHistory failed", e)
            }
        }
    }

    private fun subscribeEvents() {
        val id = sessionId ?: return
        eventJob?.cancel()
        eventJob = cs.launch {
            sessions.events(id, directory).collect { event ->
                edt { handle(event) }
            }
        }
    }

    private fun handle(event: ChatEventDto) {
        when (event) {
            is ChatEventDto.MessageUpdated -> {
                chat.addMessage(event.info) ?: return
                showMessages()
            }

            is ChatEventDto.PartUpdated -> {
                partType = event.part.type
                tool = event.part.tool
                chat.updateContent(event.part.messageID, event.part)
                if (chat.state is SessionState.Busy) {
                    chat.setState(SessionState.Busy(status()))
                }
            }

            is ChatEventDto.PartDelta -> {
                if (event.field == "text") {
                    chat.appendDelta(event.messageID, event.partID, event.delta)
                }
            }

            is ChatEventDto.TurnOpen -> {
                partType = null
                tool = null
                chat.setState(SessionState.Busy(KiloBundle.message("session.status.considering")))
            }

            is ChatEventDto.TurnClose -> {
                partType = null
                tool = null
                chat.setState(SessionState.Idle)
            }

            is ChatEventDto.Error -> {
                partType = null
                tool = null
                val msg = event.error?.message ?: event.error?.type ?: KiloBundle.message("session.error.unknown")
                chat.setState(SessionState.Error(msg, event.error?.type))
            }

            is ChatEventDto.MessageRemoved -> {
                chat.removeMessage(event.messageID)
            }

            is ChatEventDto.PermissionAsked -> {
                chat.setState(SessionState.AwaitingPermission(toPermission(event.request)))
            }

            is ChatEventDto.PermissionReplied -> {
                if (chat.state is SessionState.AwaitingPermission) {
                    chat.setState(SessionState.Busy(KiloBundle.message("session.status.considering")))
                }
            }

            is ChatEventDto.QuestionAsked -> {
                chat.setState(SessionState.AwaitingQuestion(toQuestion(event.request)))
            }

            is ChatEventDto.QuestionReplied -> {
                if (chat.state is SessionState.AwaitingQuestion) {
                    chat.setState(SessionState.Busy(KiloBundle.message("session.status.considering")))
                }
            }

            is ChatEventDto.QuestionRejected -> {
                if (chat.state is SessionState.AwaitingQuestion) {
                    chat.setState(SessionState.Idle)
                }
            }

            is ChatEventDto.SessionStatusChanged -> {
                val state = when (event.status.type) {
                    "idle" -> SessionState.Idle
                    "busy" -> {
                        val current = chat.state
                        if (current is SessionState.Idle || current is SessionState.Error)
                            SessionState.Busy(KiloBundle.message("session.status.considering"))
                        else return // already in a more specific phase
                    }
                    "retry" -> SessionState.Retry(event.status.message ?: "", 0, 0)
                    "offline" -> SessionState.Offline(event.status.message ?: "", "")
                    else -> return
                }
                chat.setState(state)
            }
        }
    }

    private fun showMessages() {
        if (!chat.showMessages) {
            chat.showMessages = true
            fire(SessionManagerEvent.ViewChanged(true))
        }
    }

    private fun status(): String = when (partType) {
        "reasoning" -> KiloBundle.message("session.status.thinking")
        "text" -> KiloBundle.message("session.status.writing")
        "tool" -> when (tool) {
            "task" -> KiloBundle.message("session.status.delegating")
            "todowrite", "todoread" -> KiloBundle.message("session.status.planning")
            "read" -> KiloBundle.message("session.status.gathering")
            "glob", "grep", "list" -> KiloBundle.message("session.status.searching.codebase")
            "webfetch", "websearch", "codesearch" -> KiloBundle.message("session.status.searching.web")
            "edit", "write" -> KiloBundle.message("session.status.editing")
            "bash" -> KiloBundle.message("session.status.commands")
            else -> KiloBundle.message("session.status.considering")
        }
        else -> KiloBundle.message("session.status.considering")
    }

    private fun fire(event: SessionManagerEvent) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            for (l in listeners) l.onEvent(event)
            return
        }
        application.invokeLater { for (l in listeners) l.onEvent(event) }
    }

    private fun edt(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(block)
    }

    override fun dispose() {
        eventJob?.cancel()
        cs.cancel()
    }
}

private fun toPermission(dto: PermissionRequestDto): Permission {
    val ref = dto.tool?.let { ToolCallRef(it.messageID, it.callID) }
    return Permission(
        id = dto.id,
        sessionId = dto.sessionID,
        name = dto.permission,
        patterns = dto.patterns,
        always = dto.always,
        meta = PermissionMeta(raw = dto.metadata),
        tool = ref,
    )
}

private fun toQuestion(dto: QuestionRequestDto): Question {
    val ref = dto.tool?.let { ToolCallRef(it.messageID, it.callID) }
    val items = dto.questions.map {
        QuestionItem(
            question = it.question,
            header = it.header,
            options = it.options.map { opt -> QuestionOption(opt.label, opt.description) },
            multiple = it.multiple,
            custom = it.custom,
        )
    }
    return Question(id = dto.id, items = items, tool = ref)
}

package ai.kilocode.client.session

import ai.kilocode.client.app.KiloAppService
import ai.kilocode.client.app.KiloSessionService
import ai.kilocode.client.app.Workspace
import ai.kilocode.client.plugin.KiloBundle
import ai.kilocode.client.session.model.AgentItem
import ai.kilocode.client.session.model.ModelItem
import ai.kilocode.client.session.model.SessionModel
import ai.kilocode.client.session.model.SessionModelEvent
import ai.kilocode.client.session.model.SessionState
import ai.kilocode.client.session.model.Permission
import ai.kilocode.client.session.model.PermissionMeta
import ai.kilocode.client.session.model.PermissionRequestState
import ai.kilocode.client.session.model.Question
import ai.kilocode.client.session.model.QuestionItem
import ai.kilocode.client.session.model.QuestionOption
import ai.kilocode.client.session.model.ToolCallRef
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
 * state. UIs observe model changes via [SessionModelEvent] on [model].
 * Lifecycle events (app/workspace state, view switching) are published
 * via [SessionControllerEvent] to registered listeners.
 */
class SessionController(
    parent: Disposable,
    id: String?,
    private val sessions: KiloSessionService,
    private val workspace: Workspace,
    private val app: KiloAppService,
    private val cs: CoroutineScope,
) : Disposable {

    companion object {
        private val LOG = Logger.getInstance(SessionController::class.java)
    }

    init {
        Disposer.register(parent, this)
    }

    val model = SessionModel()

    private val listeners = mutableListOf<SessionControllerListener>()
    private var sessionId: String? = id
    private val directory: String get() = workspace.directory

    private var partType: String? = null
    private var tool: String? = null
    private var eventJob: Job? = null

    val ready: Boolean get() = model.isReady()

    fun addListener(parent: Disposable, listener: SessionControllerListener) {
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
                    model.setState(SessionState.Error(msg))
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
        model.agent = name
        cs.launch {
            try {
                sessions.updateConfig(directory, ConfigUpdateDto(agent = name))
            } catch (e: Exception) {
                LOG.warn("selectAgent failed", e)
            }
        }
        fire(SessionControllerEvent.WorkspaceReady)
    }

    fun selectModel(provider: String, id: String) {
        model.model = "$provider/$id"
        cs.launch {
            try {
                sessions.updateConfig(directory, ConfigUpdateDto(model = "$provider/$id"))
            } catch (e: Exception) {
                LOG.warn("selectModel failed", e)
            }
        }
        fire(SessionControllerEvent.WorkspaceReady)
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
                fire(SessionControllerEvent.AppChanged) {
                    model.app = state
                    model.version = app.version
                }
            }
        }

        cs.launch {
            workspace.state.collect { state ->
                fire(SessionControllerEvent.WorkspaceChanged) {
                    model.workspace = state

                    if (state.status != KiloWorkspaceStatusDto.READY) return@fire

                    model.agents = state.agents?.agents?.map {
                        AgentItem(it.name, it.displayName ?: it.name)
                    } ?: emptyList()

                    model.models = state.providers?.let { providers ->
                        providers.providers
                            .filter { it.id in providers.connected }
                            .flatMap { provider ->
                                provider.models.map { (id, info) ->
                                    ModelItem(id, info.name, provider.id)
                                }
                            }
                    } ?: emptyList()

                    if (this@SessionController.model.agent == null) this@SessionController.model.agent = state.agents?.default
                    if (this@SessionController.model.model == null) {
                        this@SessionController.model.model = state.providers?.defaults?.entries?.firstOrNull()?.let { "${it.key}/${it.value}" }
                    }
                }

                if (state.status == KiloWorkspaceStatusDto.READY) {
                    fire(SessionControllerEvent.WorkspaceReady)
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
                    this@SessionController.model.loadHistory(history)
                    if (!model.isEmpty()) showMessages()
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
                model.addMessage(event.info) ?: return
                showMessages()
            }

            is ChatEventDto.PartUpdated -> {
                partType = event.part.type
                tool = event.part.tool
                model.updateContent(event.part.messageID, event.part)
                if (model.state is SessionState.Busy) {
                    model.setState(SessionState.Busy(status()))
                }
            }

            is ChatEventDto.PartDelta -> {
                if (event.field == "text") {
                    model.appendDelta(event.messageID, event.partID, event.delta)
                }
            }

            is ChatEventDto.TurnOpen -> {
                partType = null
                tool = null
                model.setState(SessionState.Busy(KiloBundle.message("session.status.considering")))
            }

            is ChatEventDto.TurnClose -> {
                partType = null
                tool = null
                model.setState(SessionState.Idle)
            }

            is ChatEventDto.Error -> {
                partType = null
                tool = null
                val msg = event.error?.message ?: event.error?.type ?: KiloBundle.message("session.error.unknown")
                model.setState(SessionState.Error(msg, event.error?.type))
            }

            is ChatEventDto.MessageRemoved -> {
                model.removeMessage(event.messageID)
            }

            is ChatEventDto.PermissionAsked -> {
                model.setState(SessionState.AwaitingPermission(toPermission(event.request)))
            }

            is ChatEventDto.PermissionReplied -> {
                if (model.state is SessionState.AwaitingPermission) {
                    model.setState(SessionState.Busy(KiloBundle.message("session.status.considering")))
                }
            }

            is ChatEventDto.QuestionAsked -> {
                model.setState(SessionState.AwaitingQuestion(toQuestion(event.request)))
            }

            is ChatEventDto.QuestionReplied -> {
                if (model.state is SessionState.AwaitingQuestion) {
                    model.setState(SessionState.Busy(KiloBundle.message("session.status.considering")))
                }
            }

            is ChatEventDto.QuestionRejected -> {
                if (model.state is SessionState.AwaitingQuestion) {
                    model.setState(SessionState.Idle)
                }
            }

            is ChatEventDto.SessionStatusChanged -> {
                val state = when (event.status.type) {
                    "idle" -> SessionState.Idle
                    "busy" -> {
                        val current = model.state
                        if (current is SessionState.Idle || current is SessionState.Error)
                            SessionState.Busy(KiloBundle.message("session.status.considering"))
                        else return // already in a more specific phase
                    }
                    "retry" -> SessionState.Retry(event.status.message ?: "", 0, 0)
                    "offline" -> SessionState.Offline(event.status.message ?: "", "")
                    else -> return
                }
                model.setState(state)
            }
        }
    }

    private fun showMessages() {
        if (!model.showMessages) {
            model.showMessages = true
            fire(SessionControllerEvent.ViewChanged(true))
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

    private fun fire(event: SessionControllerEvent, before: (() -> Unit)? = null) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            before?.invoke()
            for (l in listeners) l.onEvent(event)
            return
        }
        application.invokeLater {
            before?.invoke()
            for (l in listeners) l.onEvent(event)
        }
    }

    private fun edt(block: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(block)
    }

    override fun dispose() {
        eventJob?.cancel()
        cs.cancel()
    }

    override fun toString(): String {
        val out = mutableListOf<String>()
        val body = model.toString().trim()
        if (body.isNotEmpty()) out.add(body)
        if (out.isNotEmpty()) out.add("")
        out.add(statusLine())
        return out.joinToString("\n")
    }

    private fun statusLine(): String {
        val out = mutableListOf<String>()
        model.agent?.takeIf { it.isNotBlank() }?.let { out.add("[$it]") }
        model.model?.takeIf { it.isNotBlank() }?.let { out.add("[$it]") }

        if (!ready) {
            out.add("[app: ${model.app.status}]")
            out.add("[workspace: ${model.workspace.status}]")
            return out.joinToString(" ")
        }

        when (val state = model.state) {
            is SessionState.Idle -> out.add("[idle]")
            is SessionState.Busy -> {
                out.add("[busy]")
                out.add("[${state.text.toDumpText()}]")
            }
            is SessionState.AwaitingQuestion -> out.add("[awaiting-question]")
            is SessionState.AwaitingPermission -> out.add("[awaiting-permission]")
            is SessionState.Retry -> {
                out.add("[retry]")
                state.message.takeIf { it.isNotBlank() }?.let { out.add("[$it]") }
            }
            is SessionState.Offline -> {
                out.add("[offline]")
                state.message.takeIf { it.isNotBlank() }?.let { out.add("[$it]") }
            }
            is SessionState.Error -> {
                out.add("[error]")
                out.add("[${state.message}]")
            }
        }

        return out.joinToString(" ")
    }
}

private fun toPermission(dto: PermissionRequestDto): Permission {
    val ref = dto.tool?.let { ToolCallRef(it.messageID, it.callID) }
    val file = dto.metadata["file"] ?: dto.metadata["path"]
    val state = dto.metadata["state"]?.let { raw ->
        PermissionRequestState.values().firstOrNull { item -> item.name.equals(raw, ignoreCase = true) }
    } ?: PermissionRequestState.PENDING
    return Permission(
        id = dto.id,
        sessionId = dto.sessionID,
        name = dto.permission,
        patterns = dto.patterns,
        always = dto.always,
        meta = PermissionMeta(filePath = file, raw = dto.metadata),
        tool = ref,
        state = state,
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

private fun String.toDumpText(): String {
    val text = lowercase()
        .replace("\u2026", "")
        .replace("…", "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
    return text
}

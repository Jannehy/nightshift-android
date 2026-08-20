package com.jannehy.nightshift.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Drives one of the server's two live logs.
 *
 * While the app owns a job id it reads the SSE stream; after a cold start – or
 * when a download begun elsewhere is still running – it falls back to polling
 * the log endpoint, which is what the web UI does to restore its view.
 */
class JobMonitor(private val session: Session, private val source: Source) {

    enum class Source { DOWNLOAD, NIGHTLY }

    sealed class State {
        object Idle : State()
        data class Queued(val position: Int) : State()
        object Running : State()
        data class Finished(val message: String) : State()
        data class Failed(val message: String) : State()

        val isBusy: Boolean get() = this is Queued || this is Running
    }

    val lines = mutableStateListOf<String>()
    var state by mutableStateOf<State>(State.Idle)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf<Float?>(null)
        private set
    var trackCounter by mutableStateOf<String?>(null)
        private set
    var queue by mutableStateOf<QueueStatus?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    private var streamJob: Job? = null
    private var pollJob: Job? = null

    private val api: Api? get() = session.api

    /** Runs the given endpoint call and attaches to the job it returns. */
    fun start(makeJob: suspend () -> JobRef) {
        if (state.isBusy) return
        errorMessage = null
        lines.clear()
        progress = null
        trackCounter = null
        statusMessage = null
        state = State.Queued(0)
        session.scope.launch {
            try {
                attach(makeJob())
            } catch (e: ApiException.Unauthorized) {
                state = State.Idle
                session.handleUnauthorized()
            } catch (e: Exception) {
                state = State.Failed(e.message ?: "")
                errorMessage = e.message
            }
            refreshQueue()
        }
    }

    private fun attach(job: JobRef) {
        state = if (job.position > 0) State.Queued(job.position) else State.Running
        listen(job.jobId)
    }

    private fun listen(jobId: String) {
        streamJob?.cancel()
        pollJob?.cancel()
        val client = api ?: return
        streamJob = session.scope.launch {
            try {
                client.events(jobId).collect { apply(it) }
                streamJob = null
                // Stream ended without a terminal event (server restart, timeout):
                // fall back to the log file so the UI does not hang on "running".
                if (state.isBusy) restore()
            } catch (e: ApiException.Unauthorized) {
                streamJob = null
                session.handleUnauthorized()
            } catch (e: Exception) {
                streamJob = null
                restore()
            }
        }
    }

    private fun apply(event: JobEvent) {
        if (state is State.Queued) state = State.Running
        event.logLine?.let { append(it) }
        when (event.type) {
            "status" -> {
                statusMessage = event.message
                event.progress?.let { progress = it / 100f }
            }
            "progress" -> {
                event.progress?.let { progress = it / 100f }
                val current = event.current
                val total = event.total
                if (current != null && total != null && total > 0) {
                    trackCounter = "$current/$total"
                }
            }
            "done" -> {
                progress = 1f
                statusMessage = event.message
                state = State.Finished(event.message ?: "")
            }
            "error" -> {
                statusMessage = event.message
                state = State.Failed(event.message ?: "")
            }
        }
    }

    private fun append(line: String) {
        lines.add(line)
        while (lines.size > MAX_LINES) lines.removeAt(0)
    }

    /** Reads the server-side log and, if work is still in flight, keeps polling. */
    suspend fun restore() {
        val client = api ?: return
        try {
            val log = fetchLog(client)
            applyLogState(log)
            if (log.running && streamJob == null) startPolling()
        } catch (e: ApiException.Unauthorized) {
            session.handleUnauthorized()
        } catch (e: Exception) {
            // A missing log is normal on a fresh server – stay quiet.
        }
        refreshQueue()
    }

    private suspend fun fetchLog(client: Api): LogState = when (source) {
        Source.DOWNLOAD -> client.downloadLog()
        Source.NIGHTLY -> client.nightlyLog()
    }

    private fun applyLogState(log: LogState) {
        // Only adopt the file while no live stream is feeding the view.
        if (streamJob != null && lines.isNotEmpty()) return
        val incoming = log.lines.filter { it.isNotEmpty() }
        lines.clear()
        lines.addAll(incoming.takeLast(MAX_LINES))
        state = when {
            log.failed -> State.Failed("")
            log.finished -> { progress = 1f; State.Finished("") }
            log.running -> {
                // No stream, so no progress events – read the counts off the log.
                log.counts?.let {
                    progress = it.fraction
                    trackCounter = "${it.done}/${it.total}"
                }
                State.Running
            }
            else -> State.Idle
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = session.scope.launch {
            while (true) {
                delay(2500)
                val client = api ?: return@launch
                val log = runCatching { fetchLog(client) }.getOrNull() ?: continue
                applyLogState(log)
                if (!log.running) return@launch
            }
        }
    }

    suspend fun refreshQueue() {
        queue = runCatching { api?.queue() }.getOrNull()
    }

    /** Detaches from the current job without cancelling server-side work – the
     *  server has no cancel endpoint, so this only clears the view. */
    fun clear() {
        streamJob?.cancel()
        pollJob?.cancel()
        streamJob = null
        pollJob = null
        lines.clear()
        state = State.Idle
        progress = null
        statusMessage = null
        trackCounter = null
        errorMessage = null
    }

    companion object {
        private const val MAX_LINES = 2000
    }
}

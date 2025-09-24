package io.getunleash.android.tasks

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.getunleash.android.data.DataStrategy
import io.getunleash.android.http.NetworkListener
import io.getunleash.android.unleashScope
import io.getunleash.android.util.UnleashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

data class DataJob(val id: String, val strategy: DataStrategy, val action: suspend () -> Unit)

class LifecycleAwareTaskManager(
    private val dataJobs: List<DataJob>,
    private var networkAvailable: Boolean = true,
    private val scope: CoroutineScope = unleashScope,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : LifecycleEventObserver, NetworkListener {
    companion object {
        private const val TAG = "TaskManager"
    }
    internal val foregroundWorkers = mutableMapOf<String, Job>()
    private var isForeground = false
    private var isDestroying = false

    internal fun startForegroundJobs() {
        if (!networkAvailable) {
            UnleashLogger.d(TAG, "Network not available, not starting foreground jobs")
            return
        }
        if (!isForeground) {
            isForeground = true

            dataJobs.forEach { dataJob ->
                if (foregroundWorkers[dataJob.id]?.isActive != true) {
                    UnleashLogger.d(TAG, "Starting foreground job: ${dataJob.id}")
                    foregroundWorkers[dataJob.id] = startWithStrategy(
                        dataJob.id,
                        dataJob.strategy,
                        dataJob.action
                    )
                }
            }
        }
    }

    private fun stopForegroundJobs() {
        if (isForeground || isDestroying || !networkAvailable) {
            isForeground = false

            dataJobs.forEach { dataJob ->
                if (dataJob.strategy.pauseOnBackground || isDestroying || !networkAvailable) {
                    UnleashLogger.d(TAG, "Pausing foreground job: ${dataJob.id}")
                    foregroundWorkers[dataJob.id]?.cancel()
                } else {
                    UnleashLogger.d(TAG, "Keeping job running: ${dataJob.id}")
                }
            }
        }
    }

    private fun startWithStrategy(
        id: String,
        strategy: DataStrategy,
        action: suspend () -> Unit
    ): Job {
        return scope.launch {
            withContext(ioContext) {
                while (!isDestroying && (isForeground || !strategy.pauseOnBackground)
                    && networkAvailable) {
                    if (strategy.delay > 0) {
                        delay(strategy.delay)
                    }
                    UnleashLogger.d(TAG, "[$id] Executing action within $ioContext")
                    action()
                    delay(timeMillis = strategy.interval)
                }
            }
        }
    }

    fun stop() {
        isDestroying = true
        stopForegroundJobs()
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        UnleashLogger.d(TAG, "Lifecycle state changed: $event")
        when (event) {
            Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> startForegroundJobs()
            Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> stopForegroundJobs()
            Lifecycle.Event.ON_DESTROY -> {
                isDestroying = true
                stopForegroundJobs()
            }
            else -> {}
        }
    }

    override fun onAvailable() {
        UnleashLogger.d(TAG, "Network available")
        networkAvailable = true
        startForegroundJobs()
    }

    override fun onLost() {
        UnleashLogger.d(TAG, "Network connection lost")
        networkAvailable = false
        stopForegroundJobs()
    }
}

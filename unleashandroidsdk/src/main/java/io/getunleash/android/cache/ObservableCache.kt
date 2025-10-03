package io.getunleash.android.cache

import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashState
import io.getunleash.android.unleashScope
import io.getunleash.android.util.UnleashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ObservableCache(private val cache: ToggleCache, private val coroutineScope: CoroutineScope = unleashScope) : ObservableToggleCache {
    companion object {
        private const val TAG = "ObservableCache"
    }

    private var newStateEventFlow = MutableSharedFlow<UnleashState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override fun read(): Map<String, Toggle> {
        return cache.read()
    }

    override fun get(key: String): Toggle? {
        return cache.get(key)
    }

    override fun write(state: UnleashState) {
        cache.write(state)
        UnleashLogger.d(TAG, "Done writing cache with ${newStateEventFlow.subscriptionCount.value} subscribers")
        coroutineScope.launch {
            UnleashLogger.d(TAG, "Emitting new state with ${state.toggles.size} toggles")
            newStateEventFlow.emit(state)
        }
    }

    override fun subscribeTo(featuresReceived: Flow<UnleashState>) {
        UnleashLogger.d(TAG, "Subscribing to observable cache")
        coroutineScope.launch {
            featuresReceived.collect { state ->
                withContext(Dispatchers.IO) {
                    UnleashLogger.d(TAG, "Storing new state with ${state.toggles.size} toggles for $state.context")
                    write(state)
                }
            }
        }
    }

    override fun getUpdatesFlow(): Flow<UnleashState> {
        return newStateEventFlow.asSharedFlow()
    }
}

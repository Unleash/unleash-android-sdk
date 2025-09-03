package io.getunleash.android.backup

import android.util.Log
import com.squareup.moshi.JsonAdapter
import io.getunleash.android.data.Parser.moshi
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.UnleashState
import io.getunleash.android.unleashScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BackupState(val contextId: String, val toggles: Map<String, Toggle>)

/**
 * Local backup of the last state of the Unleash SDK.
 *
 * Because it only keeps the last state, it also saves the context id, and uses it to verify the context
 * is the same when loading the state from disc.
 */
open class LocalBackup(
    private val localDir: File,
    private var lastContext: UnleashContext? = null
) {
    companion object {
        private const val TAG = "LocalBackup"
        internal const val STATE_BACKUP_FILE = "unleash_state.json"
    }
    private val backupAdapter: JsonAdapter<BackupState> = moshi.adapter(BackupState::class.java)

    fun subscribeTo(state: Flow<UnleashState>) {
        unleashScope.launch {
            withContext(Dispatchers.IO) {
                state.collect {
                    if (it.context != lastContext) {
                        lastContext = it.context
                        writeToDisc(it)
                    } else {
                        Log.d(TAG, "Context unchanged, not writing to disc")
                    }
                }
            }
        }
    }

    open fun writeToDisc(state: UnleashState) {
        try {
            // write only the last state
            val contextBackup = File(localDir.absolutePath, STATE_BACKUP_FILE)
            contextBackup.writeBytes(
                backupAdapter.toJson(
                    BackupState(
                        id(state.context),
                        state.toggles
                    )
                ).toByteArray(Charsets.UTF_8)
            )
            Log.d(TAG, "Written state to ${contextBackup.absolutePath}")
        } catch (e: Exception) {
            Log.i(TAG, "Error writing to disc", e)
        }
    }

    open fun loadFromDisc(context: UnleashContext): UnleashState? {
        val stateBackup = File(localDir.absolutePath, STATE_BACKUP_FILE)
        try {
            if (stateBackup.exists()) {
                val backupState =
                    backupAdapter.fromJson(stateBackup.readText(Charsets.UTF_8)) ?: return null
                if (backupState.contextId != id(context)) {
                    Log.i(TAG, "Context id mismatch, ignoring backup for context id ${backupState.contextId}")
                    return null
                }
                return UnleashState(context, backupState.toggles)
            } else {
                Log.d(TAG, "No backup found at ${stateBackup.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading from disc ${stateBackup.absolutePath}", e)
        }
        return null
    }

    private fun id(context: UnleashContext): String {
        return context.hashCode().toString()
    }
}

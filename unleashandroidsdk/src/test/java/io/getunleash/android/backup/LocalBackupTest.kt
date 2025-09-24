package io.getunleash.android.backup

import android.util.Log
import io.getunleash.android.BaseTest
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.UnleashState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.Test
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory

class LocalBackupTest : BaseTest() {
    @Test
    fun `loads a local backup when context matches`() {
        val backupFile = this::class.java.classLoader?.getResource("unleash-state.json")!!.path
        val tmpDir = createTempDirectory().toFile()
        File(backupFile).copyTo(File(tmpDir, LocalBackup.STATE_BACKUP_FILE))

        val backup = LocalBackup(tmpDir)
        assertThat(backup.loadFromDisc(UnleashContext(userId = "123"))).isNotNull()
    }

    @Test
    fun `does not load a local backup with a different context`() {
        val backupFile = this::class.java.classLoader?.getResource("unleash-state.json")!!.path
        val tmpDir = createTempDirectory().toFile()
        File(backupFile).copyTo(File(tmpDir, LocalBackup.STATE_BACKUP_FILE))

        val backup = LocalBackup(tmpDir)
        assertThat(backup.loadFromDisc(UnleashContext(userId = "456"))).isNull()
    }

    @Test
    fun `writes to disk on state change`() {
        val tmpDir = createTempDirectory().toFile()
        val context = UnleashContext(userId = "123")
        val state = UnleashState(context, emptyMap())
        val stateFlow = MutableStateFlow(state)

        val backup = LocalBackup(tmpDir)
        assertThat(backup.loadFromDisc(context)).isNull()

        // subscribing to state flow should trigger a write to disk
        backup.subscribeTo(stateFlow.asStateFlow())
        await().atMost(3, TimeUnit.SECONDS).untilAsserted {
            assertThat(backup.loadFromDisc(context)).isEqualTo(state)
        }

        val newContext = context.copy(userId = "456")
        val newState = UnleashState(newContext, emptyMap())
        stateFlow.value = newState
        await().atMost(3, TimeUnit.SECONDS).untilAsserted {
            assertThat(backup.loadFromDisc(newContext)).isEqualTo(newState)
        }

        // old context is no longer stored
        assertThat(backup.loadFromDisc(context)).isNull()
    }

    @Test
    fun `does not write to disk when state does not change`() {
        ShadowLog.setLoggable("io.getunleash", Log.DEBUG)
        val tmpDir = createTempDirectory().toFile()
        val context = UnleashContext(userId = "123")
        val state = UnleashState(context, emptyMap())
        val stateFlow = MutableStateFlow(state)
        // initialize hte backup with a known context for testing
        val backup = LocalBackup(tmpDir, context)

        assertThat(backup.loadFromDisc(context)).isNull()

        backup.subscribeTo(stateFlow.asStateFlow())

        await().atMost(2, TimeUnit.SECONDS).until {
            ShadowLog.getLogs().map { it.msg }
                .find { it.contains("Context unchanged, not writing to disc") } != null
        }
    }
}
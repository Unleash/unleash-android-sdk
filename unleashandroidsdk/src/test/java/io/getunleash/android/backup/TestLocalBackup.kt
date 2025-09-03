package io.getunleash.android.backup

import io.getunleash.android.data.UnleashContext
import io.getunleash.android.data.UnleashState
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test helper that blocks loadFromDisc until the provided latch is counted down.
 */
open class TestLocalBackup(
    localDir: File,
    private val startLatch: CountDownLatch,
    private val timeoutSeconds: Long = 5,
    var writeCalls: Int = 0
) : LocalBackup(localDir) {

    override fun loadFromDisc(context: UnleashContext): UnleashState? {
        // wait until the test releases the latch to simulate a slow disk load
        try {
            startLatch.await(timeoutSeconds, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            // ignore
        }
        return super.loadFromDisc(context)
    }

    override fun writeToDisc(state: UnleashState) {
        super.writeToDisc(state)
        writeCalls++
    }
}


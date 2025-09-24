package io.getunleash.android

import io.getunleash.android.util.LogLevel
import io.getunleash.android.util.LoggerWrapper
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.text.SimpleDateFormat
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(manifest= Config.NONE, sdk = [21])
abstract class BaseTest {

    @Before
    fun setup() {
        LoggerWrapper.logLevel = LogLevel.DEBUG
        ShadowLog.stream = System.out
        // Add a timestamp to the log output
        val df  = SimpleDateFormat("[yyyy-MM-dd'T'HH:mm:ss.SSSZ]")
        ShadowLog.setTimeSupplier {
            df.format(Date())
        }
        System.setProperty("json-unit.libraries", "moshi")
    }
}


package io.getunleash.android

import android.content.Context
import androidx.lifecycle.Lifecycle
import io.getunleash.android.backup.LocalBackup
import io.getunleash.android.backup.TestLocalBackup
import io.getunleash.android.data.ImpressionEvent
import io.getunleash.android.data.Toggle
import io.getunleash.android.data.UnleashContext
import io.getunleash.android.events.HeartbeatEvent
import io.getunleash.android.events.UnleashFetcherHeartbeatListener
import io.getunleash.android.events.UnleashImpressionEventListener
import io.getunleash.android.events.UnleashReadyListener
import io.getunleash.android.events.UnleashStateListener
import io.getunleash.android.polling.Status
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.awaitility.Awaitility.await
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito.mock
import org.robolectric.shadows.ShadowLog
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory

class DefaultUnleashTest : BaseTest() {
    private val staticToggleList = listOf(
        Toggle(name = "feature1", enabled = true),
        Toggle(name = "feature2", enabled = false),
    )

    @Test
    fun testDefaultUnleashWithStaticCache() {
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .delayedInitialization(false) // start immediately
                .build(),
            cacheImpl = InspectableCache(staticToggleList.associateBy { it.name }),
            lifecycle = mock(Lifecycle::class.java)
        )
        assertThat(unleash.isEnabled("feature1")).isTrue()
        assertThat(unleash.isEnabled("feature2")).isFalse()
        assertThat(unleash.isEnabled("nonExisting")).isFalse()
    }

    @Test
    fun testDefaultUnleashWithBootstrap() {
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            lifecycle = mock(Lifecycle::class.java),
        )
        unleash.start(bootstrap = staticToggleList)
        assertThat(unleash.isEnabled("feature1")).isTrue()
        assertThat(unleash.isEnabled("feature2")).isFalse()
        assertThat(unleash.isEnabled("nonExisting")).isFalse()
    }

    @Test
    fun `adding on ready listeners should notify all`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
        )
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .proxyUrl(server.url("").toString())
                .clientKey("key-123")
                .pollingStrategy.enabled(true)
                .metricsStrategy.enabled(true)
                .metricsStrategy.delay(10000) // delay enough so it won't trigger a new request
                .localStorageConfig.enabled(false)
                .build(),
            lifecycle = mock(Lifecycle::class.java),
        )

        var onReady1 = false
        var onReady2 = false
        var onReady3 = false

        // adding listeners before start should not be overridden
        unleash.addUnleashEventListener(object : UnleashReadyListener {
            override fun onReady() {
                onReady1 = true
            }
        })

        // listeners added at start should also be called
        unleash.start(
            listOf(object : UnleashReadyListener {
                override fun onReady() {
                    onReady2 = true
                }
            },
                object : UnleashReadyListener {
                    override fun onReady() {
                        onReady3 = true
                    }
                })
        )

        await().atMost(1, TimeUnit.SECONDS).until {
            onReady1 && onReady2 && onReady3
        }
    }

    @Test
    fun `initializing client twice should show a console warning`() {
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            cacheImpl = InspectableCache(staticToggleList.associateBy { it.name }),
            lifecycle = mock(Lifecycle::class.java),
        )

        unleash.start()
        unleash.start()

        assertThat(ShadowLog.getLogs())
            .map(ShadowLog.LogItem::msg)
            .contains(Tuple("Unleash already started, ignoring start call"))
    }

    @Test
    fun `configuring impression event to true at config level will emit an impression event on all features`() {
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .forceImpressionData(true)
                .build(),
            unleashContext = UnleashContext(userId = "123"),
            lifecycle = mock(Lifecycle::class.java)
        )

        var ready = false
        val impressionEvents = mutableListOf<ImpressionEvent>()
        unleash.start(
            eventListeners = listOf(object : UnleashReadyListener, UnleashImpressionEventListener {
                override fun onImpression(event: ImpressionEvent) {
                    println("Impression event received: ${event.featureName}")
                    impressionEvents.add(event)
                }

                override fun onReady() {
                    ready = true
                }
            }), bootstrap = listOf(
                Toggle(name = "with-impression-1", enabled = true, impressionData = true),
                Toggle(name = "with-impression-2", enabled = true, impressionData = true),
                Toggle(name = "with-impression-3", enabled = true, impressionData = true),
                Toggle(name = "without-impression", enabled = false)
            )
        )
        await().atMost(1, TimeUnit.SECONDS).until { ready }


        unleash.isEnabled("with-impression-1")
        unleash.isEnabled("with-impression-2", true)
        unleash.isEnabled("without-impression")
        unleash.isEnabled("with-impression-3", false)
        unleash.isEnabled("non-existing-toggle")

        await().atMost(1, TimeUnit.SECONDS).until { impressionEvents.size >= 5 }
        assertThat(impressionEvents).hasSize(5)
        assertThat(impressionEvents)
            .extracting("featureName")
            .containsExactlyInAnyOrder(
                "with-impression-1",
                "with-impression-2",
                "without-impression",
                "with-impression-3",
                "non-existing-toggle"
            )
    }

    @Test
    fun `feature with impression event set to true will emit an impression event`() {
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            unleashContext = UnleashContext(userId = "123"),
            lifecycle = mock(Lifecycle::class.java)
        )

        var ready = false
        val impressionEvents = mutableListOf<ImpressionEvent>()
        unleash.start(
            eventListeners = listOf(object : UnleashReadyListener, UnleashImpressionEventListener {
                override fun onImpression(event: ImpressionEvent) {
                    impressionEvents.add(event)
                }

                override fun onReady() {
                    ready = true
                }
            }), bootstrap = listOf(
                Toggle(name = "with-impression-1", enabled = true, impressionData = true),
                Toggle(name = "with-impression-2", enabled = true, impressionData = true),
                Toggle(name = "with-impression-3", enabled = true, impressionData = true),
                Toggle(name = "without-impression", enabled = false)
            )
        )
        await().atMost(1, TimeUnit.SECONDS).until { ready }


        unleash.isEnabled("with-impression-1")
        unleash.isEnabled("with-impression-2", true)
        unleash.isEnabled("without-impression")
        unleash.isEnabled("with-impression-3", false)
        unleash.isEnabled("non-existing-toggle")

        await().atMost(1, TimeUnit.SECONDS).until { impressionEvents.size >= 3 }
        assertThat(impressionEvents).hasSize(3)
        assertThat(impressionEvents)
            .extracting("featureName")
            .containsExactlyInAnyOrder("with-impression-1", "with-impression-2", "with-impression-3")
    }

    @Test
    fun `validate we can read featureEnabled from the variants`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
        )
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .proxyUrl(server.url("").toString())
                .clientKey("key-123")
                .pollingStrategy.enabled(true)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            lifecycle = mock(Lifecycle::class.java),
        )

        var ready = false
        unleash.addUnleashEventListener(object : UnleashReadyListener {
            override fun onReady() {
                ready = true
            }
        })
        unleash.start()

        await().atMost(2, TimeUnit.SECONDS).until { ready }
        val variant = unleash.getVariant("AwesomeDemo")
        assertThat(variant).isNotNull
        assertThat(variant.enabled).isTrue()
        assertThat(variant.featureEnabled).isTrue()
        assertThat(variant.name).isEqualTo("black")
    }

    @Test
    fun `can listen to heartbeat events when polling`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(304)
        )
        server.enqueue(
            MockResponse().setResponseCode(500)
        )
        server.enqueue(
            MockResponse().setResponseCode(304)
        )
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .proxyUrl(server.url("").toString())
                .clientKey("key-123")
                .pollingStrategy.enabled(true)
                .pollingStrategy.delay(20000) // delay enough so it won't trigger a new request
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            unleashContext = UnleashContext(userId = "1"),
            lifecycle = mock(Lifecycle::class.java),
        )

        var togglesUpdated = 0
        var togglesChecked = 0
        var togglesThrottled = 0
        var togglesFailed = 0

        unleash.start(eventListeners = listOf(object : UnleashFetcherHeartbeatListener {
            override fun togglesUpdated() {
                togglesUpdated++
            }

            override fun togglesChecked() {
                togglesChecked++
            }

            override fun onError(event: HeartbeatEvent) {
                if (event.status == Status.THROTTLED) togglesThrottled++ else togglesFailed++
            }
        }))

        await().atMost(5, TimeUnit.SECONDS).until {
            togglesUpdated == 1
        }
        // change context to force a refresh
        unleash.setContextAsync(UnleashContext(userId = "2"))
        await().atMost(2, TimeUnit.SECONDS).until {
            togglesChecked == 1
        }
        unleash.setContextAsync(UnleashContext(userId = "3"))
        await().atMost(2, TimeUnit.SECONDS).until {
            togglesFailed == 1
        }
        // too fast request after an error should be throttled
        unleash.setContextAsync(UnleashContext(userId = "4"))
        await().atMost(2, TimeUnit.SECONDS).until {
            togglesThrottled == 1
        }

        assertThat(togglesUpdated).isEqualTo(1)
        assertThat(togglesChecked).isEqualTo(1)
        assertThat(togglesFailed).isEqualTo(1) 
        assertThat(togglesThrottled).isEqualTo(1)
    }

    @Test
    fun `when set context call we only refresh once`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(304).setBody("")
        )
        server.enqueue(
            MockResponse().setResponseCode(304).setBody("")
        )
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .proxyUrl(server.url("").toString())
                .clientKey("key-123")
                .pollingStrategy.enabled(true)
                .pollingStrategy.delay(20000) // delay enough so it won't trigger a new request
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            unleashContext = UnleashContext(userId = "1"),
            lifecycle = mock(Lifecycle::class.java),
        )

        var togglesUpdated = 0
        var togglesChecked = 0

        unleash.start(eventListeners = listOf(object : UnleashFetcherHeartbeatListener {
            override fun togglesUpdated() {
                togglesUpdated++
            }

            override fun togglesChecked() {
                togglesChecked++
            }

            override fun onError(event: HeartbeatEvent) {
                Assert.fail("Should not have errors")
            }
        }))

        await().atMost(5, TimeUnit.SECONDS).until {
            togglesUpdated == 1
        }

        // change context to force a refresh
        unleash.setContext(UnleashContext(userId = "2"))
        await().atMost(1, TimeUnit.SECONDS).until {
            togglesChecked == 1
        }
        unleash.setContext(UnleashContext(userId = "3"))
        await().atMost(1, TimeUnit.SECONDS).until {
            togglesChecked == 2
        }
    }

    @Test
    fun `if unleash is not started, setting context does not poll, until start is called`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
        )
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .proxyUrl(server.url("").toString())
                .clientKey("key-123")
                .pollingStrategy.enabled(true)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            lifecycle = mock(Lifecycle::class.java),
        )

        unleash.setContext(UnleashContext(userId = "123"))
        Thread.sleep(100)
        assertThat(server.requestCount).isEqualTo(0)

        var ready = false
        unleash.addUnleashEventListener(object : UnleashReadyListener {
            override fun onReady() {
                ready = true
            }
        })
        unleash.start()

        await().atMost(2, TimeUnit.SECONDS).until { ready }
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().requestUrl?.queryParameter("userId")).isEqualTo("123")
    }

    @Test
    fun `can load from disk using a backup`() {
        val sampleBackupResponse = File(this::class.java.classLoader?.getResource("sample-response.json")!!.path)
        val inspectableCache = InspectableCache()
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            lifecycle = mock(Lifecycle::class.java),
            cacheImpl = inspectableCache
        )

        unleash.start(bootstrapFile = sampleBackupResponse)

        await().atMost(2, TimeUnit.SECONDS).until { inspectableCache.toggles.isNotEmpty() }
        assertThat(inspectableCache.toggles).hasSize(8)
        val aToggle = inspectableCache.toggles["AwesomeDemo"]
        assertThat(aToggle).isNotNull
        assertThat(aToggle!!.enabled).isTrue()
        assertThat(aToggle.variant).isNotNull
        assertThat(aToggle.variant.name).isEqualTo("black")
    }

    @Test
    fun `can load state from a local backup`() {
        val backupFile = this::class.java.classLoader?.getResource("unleash-state.json")!!.path
        val tmpDir = createTempDirectory().toFile()
        File(backupFile).copyTo(File(tmpDir, "${DefaultUnleash.BACKUP_DIR_NAME}/${LocalBackup.STATE_BACKUP_FILE}"))

        val inspectableCache = InspectableCache()

        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(true)
                .localStorageConfig.dir(tmpDir.path)
                .build(),
            cacheImpl = inspectableCache,
            unleashContext = UnleashContext(userId = "123"),
            lifecycle = mock(Lifecycle::class.java),
        )

        var stateSet = false
        unleash.start(eventListeners = listOf(object : UnleashStateListener {
            override fun onStateChanged() {
                stateSet = true
            }
        }))

        await().atMost(2, TimeUnit.SECONDS).until { stateSet }
        assertThat(inspectableCache.toggles).hasSize(3)
    }

    @Test
    fun `when polling is disable should still be able to poll on demand`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
        )

        val inspectableCache = InspectableCache()
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .proxyUrl(server.url("").toString())
                .clientKey("key-123")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            unleashContext = UnleashContext(userId = "123"),
            cacheImpl = inspectableCache,
            lifecycle = mock(Lifecycle::class.java),
        )

        var readyState = false
        unleash.start(bootstrap = staticToggleList, eventListeners = listOf(object : UnleashReadyListener {
            override fun onReady() {
                readyState = true
            }
        }))
        await().atMost(3, TimeUnit.SECONDS).until { readyState }
        assertThat(inspectableCache.toggles).hasSize(staticToggleList.size)

        unleash.refreshTogglesNow()
        await().atMost(2, TimeUnit.SECONDS).until { inspectableCache.toggles.size == 8 }
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `when polling is disable you should eventually get the ready event after manually polling`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
        )

        var readyState = false
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .proxyUrl(server.url("").toString())
                .clientKey("key-123")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .delayedInitialization(false)
                .build(),
            unleashContext = UnleashContext(userId = "123"),
            lifecycle = mock(Lifecycle::class.java),
            eventListeners = listOf(object : UnleashReadyListener {
                override fun onReady() {
                    readyState = true
                }
            })
        )
        unleash.refreshTogglesNowAsync()
        await().atMost(3, TimeUnit.SECONDS).until { readyState }
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `is ready even when there's no is ready listener`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
        )

        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .proxyUrl(server.url("").toString())
                .clientKey("key-123")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .delayedInitialization(false)
                .build(),
            unleashContext = UnleashContext(userId = "123"),
            lifecycle = mock(Lifecycle::class.java),
        )
        unleash.refreshTogglesNow()
        await().atMost(3, TimeUnit.SECONDS).until { unleash.isReady() }
        assertThat(server.requestCount).isEqualTo(1)

        var readyState = false
        unleash.addUnleashEventListener(object : UnleashReadyListener {
            override fun onReady() {
                readyState = true
            }
        })
        await().atMost(1, TimeUnit.SECONDS).until { readyState }
    }

    @Test
    fun `when fetch toggles happens before load from local backup, upstream data is not overridden by the backup`() {
        val backupFile = this::class.java.classLoader?.getResource("unleash-state.json")!!.path
        val tmpDir = createTempDirectory().toFile()
        File(backupFile).copyTo(File(tmpDir, LocalBackup.STATE_BACKUP_FILE))

        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
        )

        val inspectableCache = InspectableCache()
        val backupStartLatch = CountDownLatch(1)
        val writeCountingBackup = TestLocalBackup(tmpDir, backupStartLatch)
        val localFactory: (File) -> TestLocalBackup = { dir -> writeCountingBackup }

        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .proxyUrl(server.url("").toString())
                .clientKey("key-123")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(true)
                .localStorageConfig.dir(tmpDir.path)
                .build(),
            cacheImpl = inspectableCache,
            unleashContext = UnleashContext(userId = "123"),
            lifecycle = mock(Lifecycle::class.java),
            // internal constructor overload available in-module to inject test factories
            localBackupFactory = localFactory,
        )

        unleash.start()
        unleash.refreshTogglesNowAsync()

        await().atMost(5, TimeUnit.SECONDS).until { unleash.isReady() }
        assertThat(inspectableCache.toggles).hasSize(8) // from sample-response.json

        // validate the backup file was updated
        assertThat(writeCountingBackup.writeCalls).isEqualTo(1)
        // now allow the backup load to proceed; it should subscribe to cache updates and write the latest state
        backupStartLatch.countDown()

        val backupFileOnDisc = File(tmpDir, LocalBackup.STATE_BACKUP_FILE)
        // wait for the backup writer to update the file
        await().atMost(3, TimeUnit.SECONDS).untilAsserted {
            assertThat(backupFileOnDisc.exists()).isTrue()
            val backupContent = backupFileOnDisc.readText(Charsets.UTF_8)
            assertThat(backupContent).isNotEmpty()
            assertThat(backupContent).doesNotContain("ff1") // from the old backup
            assertThat(backupContent).contains("AwesomeDemo") // still contains the data from the latest fetch
        }
        // validate the backup file was not overridden by the old data
        assertThat(writeCountingBackup.writeCalls).isEqualTo(1)
        assertThat(inspectableCache.toggles).hasSize(8) // still from sample-response.json
    }

    @Test
    fun `when fetch toggles happens after load from local backup, the local backup is updated`() {
        val backupFile = this::class.java.classLoader?.getResource("unleash-state.json")!!.path
        val tmpDir = createTempDirectory().toFile()
        File(backupFile).copyTo(File(tmpDir, LocalBackup.STATE_BACKUP_FILE))

        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                this::class.java.classLoader?.getResource("sample-response.json")!!.readText()
            )
        )

        val inspectableCache = InspectableCache()
        val backupStartLatch = CountDownLatch(1)
        val writeCountingBackup = TestLocalBackup(tmpDir, backupStartLatch)
        val localFactory: (File) -> TestLocalBackup = { dir -> writeCountingBackup }

        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .proxyUrl(server.url("").toString())
                .clientKey("key-123")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(true)
                .localStorageConfig.dir(tmpDir.path)
                .build(),
            cacheImpl = inspectableCache,
            unleashContext = UnleashContext(userId = "123"),
            lifecycle = mock(Lifecycle::class.java),
            // internal constructor overload available in-module to inject test factories
            localBackupFactory = localFactory,
        )

        unleash.start()
        backupStartLatch.countDown() // allow loading from backup

        await().atMost(5, TimeUnit.SECONDS).until { unleash.isReady() }
        assertThat(inspectableCache.toggles).hasSize(3) // from the backup file

        // backup should have been loaded, but not yet updated
        assertThat(writeCountingBackup.writeCalls).isEqualTo(0)
        // now sync from server again; it should update the backup with the same data
        unleash.refreshTogglesNow()

        val backupFileOnDisc = File(tmpDir, LocalBackup.STATE_BACKUP_FILE)
        // wait for the backup writer to update the file
        await().atMost(3, TimeUnit.SECONDS).untilAsserted {
            assertThat(backupFileOnDisc.exists()).isTrue()
            val backupContent = backupFileOnDisc.readText(Charsets.UTF_8)
            assertThat(backupContent).isNotEmpty()
            assertThat(backupContent).doesNotContain("ff1") // from the old backup
            assertThat(backupContent).contains("AwesomeDemo") // still contains the data from the latest fetch
        }
        // validate the backup file was not overridden by the old data
        assertThat(writeCountingBackup.writeCalls).isEqualTo(1)
    }

	@Test
    fun `constructor listeners are registered on start when delayed initialization is enabled`() {
        val staticBootstrap = listOf(
            Toggle(name = "with-impression", enabled = true, impressionData = true),
            Toggle(name = "no-impression", enabled = true, impressionData = false)
        )

        var ready = false
        var stateChanged = false
        val impressionEvents = mutableListOf<ImpressionEvent>()

        val listener = object : UnleashReadyListener, UnleashImpressionEventListener, UnleashStateListener {
            override fun onReady() {
                ready = true
            }

            override fun onImpression(event: ImpressionEvent) {
                impressionEvents.add(event)
            }

            override fun onStateChanged() {
                stateChanged = true
            }
        }

        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                // delayedInitialization is true by default; we rely on that behaviour
                .build(),
            lifecycle = mock(Lifecycle::class.java),
            eventListeners = listOf(listener)
        )

        // Start with bootstrap toggles which should trigger a state update and then ready
        unleash.start(bootstrap = staticBootstrap)

        await().atMost(2, TimeUnit.SECONDS).until { ready && stateChanged }

        // Trigger an impression event by evaluating a toggle configured to emit impressions
        unleash.isEnabled("with-impression")

        await().atMost(1, TimeUnit.SECONDS).until { impressionEvents.isNotEmpty() }
        assertThat(impressionEvents).hasSize(1)
        assertThat(impressionEvents[0].featureName).isEqualTo("with-impression")
    }

    @Test
    fun `removing ready listener before start should not notify it`() {
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .build(),
            lifecycle = mock(Lifecycle::class.java),
        )

        var onReadyCalled = false
        val readyListener = object : UnleashReadyListener {
            override fun onReady() {
                onReadyCalled = true
            }
        }

        // add then remove before calling start
        unleash.addUnleashEventListener(readyListener)
        unleash.removeUnleashEventListener(readyListener)

        unleash.start(bootstrap = staticToggleList)

        // ensure ready would be achieved but our removed listener shouldn't be called
        await().atMost(1, TimeUnit.SECONDS).until { unleash.isReady() }
        Assert.assertFalse(onReadyCalled)
    }

    @Test
    fun `removing impression listener should stop further impressions`() {
        val unleash = DefaultUnleash(
            androidContext = mock(Context::class.java),
            unleashConfig = UnleashConfig.newBuilder("test-android-app")
                .pollingStrategy.enabled(false)
                .metricsStrategy.enabled(false)
                .localStorageConfig.enabled(false)
                .forceImpressionData(true)
                .build(),
            unleashContext = UnleashContext(userId = "123"),
            lifecycle = mock(Lifecycle::class.java)
        )

        unleash.start(
            bootstrap = listOf(
                Toggle(name = "imp-1", enabled = true, impressionData = true),
                Toggle(name = "imp-2", enabled = true, impressionData = true)
            )
        )

        await().atMost(1, TimeUnit.SECONDS).until { unleash.isReady() }

        val events = mutableListOf<ImpressionEvent>()
        val impressionListener = object : UnleashImpressionEventListener {
            override fun onImpression(event: ImpressionEvent) {
                events.add(event)
            }
        }

        unleash.addUnleashEventListener(impressionListener)

        // produce some impressions
        unleash.isEnabled("imp-1")
        unleash.isEnabled("imp-2")
        unleash.isEnabled("non-existing")

        await().atMost(1, TimeUnit.SECONDS).until { events.size >= 3 }

        // remove listener
        unleash.removeUnleashEventListener(impressionListener)

        // produce more impressions which should NOT be delivered
        unleash.isEnabled("imp-1")
        unleash.isEnabled("imp-2")

        // give a short allowance for any in-flight emissions
        Thread.sleep(200)

        // events should remain the same
        assertThat(events).hasSize(3)
        assertThat(events)
            .extracting("featureName")
            .containsExactlyInAnyOrder("imp-1", "imp-2", "non-existing")
    }
}

# Migration Guide from unleash-android-proxy-sdk

This guide provides detailed steps for migrating your project from the Unleash Android Proxy SDK version to the newer Unleash Android SDK.

We will focus on the [previous sample application](https://github.com/Unleash/unleash-android-proxy-sdk/tree/main/samples/android), specifically highlighting the changes from this pull request: https://github.com/Unleash/unleash-android-proxy-sdk/pull/83

## Benefits of Migrating

This version of the Unleash Android SDK introduces several improvements, including:
- Uses the native Android logging system instead of SLF4J.
- Respects the Android lifecycle and stops polling and sending metrics in the background.
- Respects the minimum Android API level 21, but we recommend API level 23.
- Monitors network connectivity to avoid unnecessary polling (requires API level 23 or above).

## Overview

The new SDK introduces several changes and improvements, including API modifications and a new initialization process.

## Step-by-Step Migration

### 1. Update Gradle Dependency

First, update the dependency in your `build.gradle` file:

```gradle
dependencies {
    // Remove the old SDK
    implementation 'io.getunleash:unleash-android-proxy-sdk:0.5.0'

    // Add the new SDK
    implementation 'io.getunleash:unleash-android:$version'
}
```

### 2. Update the initialization code
We won't cover all the details here as most of the configuration can be set using the builders fluent methods. However, the main difference is that the new SDK requires an `Application` context to be passed to the `Unleash` constructor. This is necessary to monitor the network connectivity and respect the Android lifecycle. If you use hilt, this can be injected with `@ApplicationContext context`.
 
#### Unleash context initialization
The main differences are:
1. The application name is no longer configurable through the context, as it is constant throughout the application's lifetime. The `appName` should be set using the `UnleashConfig` builder.
2. The instance ID is no longer configurable. The SDK will generate a unique instance ID for each instance.
3. Update the import statements to use the new SDK classes.

```kotlin
val unleashContext = UnleashContext.newBuilder()
    // .appName("unleash-android") // remove this line
    // .instanceId("main-activity-unleash-demo-${Random.nextLong()}") // remove this line
    .userId("unleash_demo_user")
    .sessionId(Random.nextLong().toString())
    .build()
```

#### Unleash configuration
The main differences are:
1. Metrics are enabled by default.
2. App name is now a mandatory parameter to the builder.
3. Instance id is no longer configurable.
4. The polling mode is now a polling strategy with a fluent API.
5. The metrics interval is now part of the metrics strategy with a fluent API.

**Old SDK**
```kotlin
UnleashConfig.newBuilder()
    .appName("unleash-android")
    .instanceId("unleash-android-${Random.nextLong()}")
    .enableMetrics()
    .clientKey("xyz")
    .proxyUrl("https://eu.app.unleash-hosted.com/demo/api/frontend")
    .pollingMode(
        PollingModes.autoPoll(
            autoPollIntervalSeconds = 15
        ) {

        }
    )
    .metricsInterval(5000)
    .build()
```

**New SDK**
```kotlin
UnleashConfig.newBuilder("unleash-android")
    .clientKey("xyz")
    .proxyUrl("https://eu.app.unleash-hosted.com/demo/api/frontend")
    .pollingStrategy.interval(15000)
    .metricsStrategy.interval(5000)
    .build()
```

#### Creating the Unleash instance
The previous SDK used a builder to construct the Unleash instance while the new SDK relies on constructor parameters. There are also other meaningful changes:

1. The new SDK does not start automatically. You need to call `unleash.start()` to start the polling and metrics collection.
2. The new SDK accepts event listeners at the constructor level or as parameters when calling `unleash.start()` (you can also edit your config object setting `delayedInitialization` to false).
3. The interface `UnleashClientSpec` is now `Unleash`.

```kotlin
UnleashClient.newBuilder()
    .unleashConfig(unleashConfig)
    .unleashContext(unleashContext)
    .build()
```

**New SDK**
_Note:_ Android context is now required to be passed to the Unleash constructor and you will usually want it to be bound to the application context.

```kotlin
val unleash = DefaultUnleash(
            androidContext = context,
            unleashConfig = unleashConfig,
            unleashContext = unleashContext
        )
unleash.start()
```

#### Updating class references
Most of the classes have been moved to `io.getunleash.android` package. Update the import statements in your classes.


### Event listeners — migrating from old PollingModes callbacks to fine-grained listeners

In the old SDK it was common to register a callback directly on the polling mode, for example:

**Old SDK**
```kotlin
val config = UnleashConfig.newBuilder()
.pollingMode(
    PollingModes.autoPoll(300) {
        // process toggles when they arrive (acts as a listener)
    }
)
```

The new SDK uses a set of small, focused listener interfaces instead of a single polling callback. Mapping guidance:

- If you used the polling-mode callback to react when toggles were updated, use UnleashFetcherHeartbeatListener.togglesUpdated() or UnleashStateListener.onStateChanged(). The heartbeat listener reports fetch lifecycle events (success / not-modified / error) and is closest to the old poll callback semantics for "toggles received".
- If you only needed to know when the SDK is ready with the initial state, use UnleashReadyListener.onReady(). This fires once when the first non-empty state has been received or loaded from bootstrap/backup.
- If you need impression events (user exposure data), implement UnleashImpressionEventListener.onImpression().

Registering listeners
- At initialization time you can pass listeners to start():

```kotlin
val unleash = DefaultUnleash(
    androidContext = context,
    unleashConfig = unleashConfig,
    unleashContext = unleashContext
)

unleash.start(
    eventListeners = listOf(object : UnleashFetcherHeartbeatListener {
        override fun togglesUpdated() { /* toggles were refreshed successfully */ }
        override fun togglesChecked() { /* 304 / not modified */ }
        override fun onError(event: HeartbeatEvent) { /* error while fetching */ }
    })
)
```

- Or register them at runtime with addUnleashEventListener / removeUnleashEventListener:

```kotlin
unleash.addUnleashEventListener(myListener)
```

Which listener to choose (short guide)
- Use `UnleashReadyListener` when you want a single notification that Unleash has an initial state (e.g., unblock UI once toggles are loaded).
- Use `UnleashFetcherHeartbeatListener.togglesUpdated` when you want to react to each successful fetch (equivalent to the old polling callback that processed toggles).
- Use `UnleashStateListener.onStateChanged` when you want a callback tied to cache updates (it fires whenever the in-memory toggle state changes).
- Use `UnleashImpressionEventListener.onImpression` to receive feature evaluation events.

Cached context and skipping fetches
- Previously some clients compared cached context properties to avoid unnecessary fetches. In the new SDK the fetcher keeps track of the context used for the last fetch and will skip a fetch if the incoming UnleashContext equals the previously fetched context (see `UnleashFetcher.startWatchingContext()`). That equality check uses the UnleashContext data class equals(), so if your new context has identical values the SDK will skip the refresh automatically.
- If you still need to perform a manual diff before calling setContext (for app-level reasons), store the previous context in your code and compare manually.

Examples
- React to updated toggles (heartbeat listener):

```kotlin
val heartbeat = object : UnleashFetcherHeartbeatListener {
    override fun togglesUpdated() { /* handle new toggles */ }
    override fun togglesChecked() { }
    override fun onError(event: HeartbeatEvent) { /* handle error */ }
}
unleash.addUnleashEventListener(heartbeat)
```

- Wait until SDK ready using UnleashReadyListener:

```kotlin
val readyListener = object : UnleashReadyListener {
    override fun onReady() { /* Unleash has initial state */ }
}
// Can be passed to start() or added later
unleash.addUnleashEventListener(readyListener)
```

### Environment handling (deprecation of .environment())

In the old SDK it was possible to set an explicit environment on the config, for example:

**Old SDK**
```kotlin
UnleashConfig.newBuilder()
    .environment("dev")
    .clientKey(env.getUnleashKey())
    // ...
    .build()
```

The new SDK no longer exposes an explicit `.environment(...)` option on UnleashConfig. The environment is derived from the API key you provide. This was intentionally removed to avoid duplication and ambiguity — the client key already encodes which environment it belongs to, so having the same value in two places caused confusion.

What this means for you:
- If you previously set `.environment(env.slug)` in addition to `.clientKey(...)`, you can remove the explicit `.environment(...)` call when migrating. The client key is sufficient.
- The SDK will behave according to the environment tied to the provided client key.


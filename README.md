# Unleash Android SDK

Unleash is a private, secure, and scalable [feature management platform](https://www.getunleash.io/) built to reduce the risk of releasing new features and accelerate software development. This Android SDK is designed to help you integrate with Unleash and use feature flags inside your application.

You can use this client with [Unleash Enterprise](https://www.getunleash.io/pricing?utm_source=readme&utm_medium=android) or [Unleash Open Source](https://github.com/Unleash/unleash).

[![Coverage Status](https://coveralls.io/repos/github/Unleash/unleash-android/badge.svg?branch=main)](https://coveralls.io/github/Unleash/unleash-android?branch=main)
[![main](https://github.com/Unleash/unleash-android/actions/workflows/build.yaml/badge.svg)](https://github.com/Unleash/unleash-android/actions/workflows/build.yaml)
[![latest](https://badgen.net/maven/v/maven-central/io.getunleash/unleash-android)](https://search.maven.org/search?q=g:io.getunleash%20AND%20a:unleash-android)
[KDoc](https://unleash.github.io/unleash-android)

## Getting started

This is the Android SDK for [Unleash Frontend API](https://docs.getunleash.io/reference/front-end-api) provided by the [Unleash server](https://github.com/Unleash/unleash) or [Unleash Edge](https://github.com/Unleash/unleash-edge). It is a lightweight SDK that allows you to connect to the Unleash Frontend API and fetch feature toggles.

This supersedes the [previous Unleash Android Proxy SDK](https://github.com/Unleash/unleash-android-proxy-sdk/) this one is a an Android library instead of a Java library. 

It's not a drop-in replacement of the previous one, so it requires code changes to use it.

**What are the benefits of migrating?**
1. Respects the Android lifecycle and stops polling and sending metrics in the background.
2. Monitors network connectivity to avoid unnecessary polling (requires API level 23 or above).
3. Uses the native Android logging system instead of SLF4J.
4. Respects the minimum Android API level 21, but we recommend API level 23.

### Step 1

You will require the SDK on your classpath, so go ahead and add it to your dependency management file

#### Gradle
```kotlin
implementation("io.getunleash:unleash-android:${unleash.sdk.version}")
```
#### Maven

```xml
<dependency>
    <groupId>io.getunleash</groupId>
    <artifactId>unleash-android</artifactId>
    <version>${unleash.sdk.version}</version>
</dependency>
```

#### Minimum Android SDK
The minimum supported SDK level is 21, keeping in tune with OkHttp's requirement, but level 23 is recommended.

#### Proguard
You shouldn't have to configure proguard for this library, but if you do, you can use the following configuration:

```proguard
-keep class io.getunleash.android.** { *; }
```

### Step 2: Enable required permissions

Your app will need internet permission in order to reach the proxy and access network state to be able to react to network changes. So in your manifest file add

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Step 3: Initialize Unleash SDK

Configure your client instance (use a single instance to avoid file contention on cache directory). 
For all configuration options check the [UnleashConfig.Builder](https://gh.getunleash.io/unleash-android/-unleash%20-android%20-s-d-k/io.getunleash.android/-unleash-config/-builder/index.html) class.
```kotlin
val unleash = DefaultUnleash(
    androidContext = applicationContext, // likely a reference to your Android application context
    unleashConfig =  UnleashConfig.newBuilder(appName = "test-android-app")
        .proxyUrl("https://eu.app.unleash-hosted.com/demo/api/frontend")
        .clientKey("<client-side SDK API key>")
        .pollingStrategy.interval(60000)
        .metricsStrategy.interval(60000)
        .build()
)

// Create an initial UnleashContext
// The context can be changed at any time but we recommend you provide sensible defaults at the start
// This will make sure that the initial state for the feature toggles is correct
val initialContext = UnleashContext.newBuilder()
    .userId("However you resolve your userid")
    .sessionId("However you resolve your session id")
    .build()
unleash.setContext(initialContext)
```

### Step 4: Start unleash
From the moment you call `unleash.start()`, it will immediately poll for the latest feature toggles and then continue polling in the background. 

Because this call is asynchronous, it will not block the main thread so you may want to check if the initial state is ready before proceeding.

You have a few options: 
1. Wait until Unleash is ready. This will make sure that your app is in sync with the feature toggles.
2. Provide an initial state. This could make sense in cases where the network is not available and you want to provide a default state.
3. None of the above. Unleash comes with a default behavior for feature toggles with unknown state.

#### Step 4, option 1: Wait until Unleash is ready

You can check if the initial state is ready by checking `unleash.isReady()`. Normally, you would add an exit condition to avoid an infinite loop.

```kotlin
// Start the Unleash instance
unleash.start()
// Wait until Unleash is ready
var maxWaits = 10 // 10 * 100ms = 1 seconds
while (maxWaits > 0 && !unleash.isReady()) {
    // Wait until Unleash is ready
    Thread.sleep(100)
    maxWaits --
}
```

Alternatively, you can add an event listener to Unleash which is a more reactive approach. This way you can be notified when Unleash is ready: 
```kotlin
// Start the Unleash instance
unleash.start(
    eventListeners = listOf(object: UnleashReadyListener {
        override fun onReady() {
            // notify your app that Unleash is ready
        }
    })
)
```

#### Step 4, option 2: Provide an initial state

If you need to have a known state for your UnleashClient, you can provide the initial state as a list of toggles. This is useful when you have a known initial state for your feature toggles. 

```kotlin
val toggles = listOf(
    Toggle(name = "flag-1", enabled = true)
)
instance.start(bootstrap = toggles)
```

Alternatively, you can perform a query against the [frontend API](https://docs.getunleash.io/reference/front-end-api) using your HTTP client of choice and save the output as a json file. Then you can tell Unleash to use this file to set up toggle states.

```kotlin
val toggles = File("/tmp/proxyresponse.json")
unleash.start(bootstrapFile = toggles)
```

#### Step 4, option 3: None of the above
You can just start Unleash and it will use the default behavior for feature toggles with unknown state until it has fetched the latest state from the proxy.

In some situations this could be a valid option. For example, if you don't want the additional complexity of the option above and you are fine with the default behavior.

```kotlin
unleash.start()
```

### Step 5: Use feature toggles
After starting the Unleash instance, you can start using the feature toggles. 

```kotlin
if (unleash.isEnabled("flag-1")) {
    println("flag-1 enabled")
} else {
    println("flag-1 disabled")
}

unleash.getVariant("flag-with-variant").let { variant ->
    if (variant.featureEnabled) {
        println("variant enabled: ${variant.name}") // you can also access variant.payload
    } else {
        println("variant disabled")
    }
}
```


### Event listeners examples

**Note:** to migrate from the old SDK, see [the migration guide](docs/MigrationGuide.md#event-listeners--migrating-from-old-pollingmodes-callbacks-to-fine-grained-listeners).

This SDK exposes small focused listener interfaces. Below are short copy-paste examples showing how to register them. 

- Register a heartbeat listener (fires on successful fetch, 304 and errors):
```kotlin
val heartbeatListener = object : UnleashFetcherHeartbeatListener {
    override fun togglesUpdated() {
        // called when new toggles were fetched successfully
    }
    override fun togglesChecked() {
        // called when server returns 304 Not Modified
    }
    override fun onError(event: HeartbeatEvent) {
        // called when an error occurred while fetching
    }
}

// add at start or at runtime
unleash.start(eventListeners = listOf(heartbeatListener))
// or
unleash.addUnleashEventListener(heartbeatListener)
```

- Register a state listener (fires when in-memory state/cache changes):
```kotlin
val stateListener = object : UnleashStateListener {
    override fun onStateChanged() {
        // called when the toggle cache was updated
    }
}
unleash.addUnleashEventListener(stateListener)
```

- Ready listener (fires once when initial state arrives):
```kotlin
val readyListener = object : UnleashReadyListener {
    override fun onReady() {
        // initial state received — safe to proceed
    }
}
unleash.addUnleashEventListener(readyListener)
```

You can add or remove listeners at runtime with `addUnleashEventListener`/`removeUnleashEventListener`.


#### Example main activity
In [the sample app](app/src/main/java/io/getunleash/unleashandroid/TestApplication.kt) we use this to display the state of a toggle on the [main activity](app/src/main/java/io/getunleash/unleashandroid/MainActivity.kt). We also configured a few event listeners to display how to use them.


## Default behavior
If you don't provide an initial state, Unleash will use the default behavior for feature toggles with unknown state. This means that if a feature toggle is not found in the list of toggles, it will be disabled by default.

## Releasing

### Create a github tag prefixed with v
- So, if you want to release 0.6.0, make a tag v0.6.0 and push it.

### Using gradle
- To release next patch version run `./gradlew release`

## Publishing to Maven central
This is automatically handled when tags with v prefix is created in the repo. See `.github/workflows/release.yml`

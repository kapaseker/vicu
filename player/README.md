# Vicu Player AAR

`player` is the reusable FFmpeg-backed Android playback engine. It includes the
JNI bridge and native runtime for `arm64-v8a`, `armeabi-v7a`, `x86`, and
`x86_64`; consumers do not compile C/C++ sources.

## Build

```powershell
.\gradlew.bat :player:assembleRelease
```

The artifact is written to `player/build/outputs/aar/player-release.aar`.

## Consume locally

Copy the AAR to the consuming application's `libs/` directory:

```kotlin
dependencies {
    implementation(files("libs/player-release.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
```

Create one repository for a playback screen, attach its `Surface`, and collect
events from the screen's lifecycle-aware coroutine scope:

```kotlin
val player: PlayerRepo = PlayerRepository(context)

player.setSurface(surface)
player.open(uri)
player.events.collect { event ->
    // Render Prepared, Position, Ended, or Failed in the host UI.
}
```

The host owns UI, navigation, dependency injection, runtime media permission,
and the lifecycle call to `player.release()`. `PlayerEffect.Crop` and
`PlayerEffect.Trim` provide the current preview effects.

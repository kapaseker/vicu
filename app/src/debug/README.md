# Direct seek device regression

`SeekProbeActivity` exists only in debug APKs and is protected by the platform
`DUMP` permission (ADB shell can launch it; ordinary apps cannot). It does nothing
unless explicitly launched. It uses the real SurfaceView/native player and calls
`PlayerRepository.seekTo` directly, bypassing the UI and ViewModel. No screenshots,
frame-by-frame logging, or production probes are needed.

Install a debug APK, grant media access through the normal app UI, then run:

```powershell
adb devices -l
adb -s ALPYUN3617H00709 shell am force-stop com.rockbyte.vicu
adb -s ALPYUN3617H00709 shell am start -W -n com.rockbyte.vicu/.SeekProbeActivity -d content://media/external/video/media/1000017474 --el gap 2000 --ei repeats 2
adb -s ALPYUN3617H00709 logcat -v threadtime -s SeekProbe:I
```

Use your device serial and an accessible media URI. The documented URI is the
original 60-second `v.f42905_1789370381609.mkv` fixture, not a portable fixture ID.
The harness never requests/grants permissions. A failed open is a failed test.

Defaults: 2-second warmup; targets `5000,10000,20000,10000,5000,20000,5000,10000,20000,5000`
milliseconds; 2-second gap; one round; 6-second recovery. Supply `--es targets`
with a comma-separated list or `--el gap 0` for back-to-back seek coalescing.
Values are bounded; target positions must be at least ten seconds before EOF.
Force-stop before each run so a fresh instance receives the new arguments.

For gaps of at least 1500ms, each seek must report a position in the first second
after its target by the next request, with no position-update age over 1500ms.
Faster bursts may supersede intermediate targets: only the final target and
recovery are required. In either mode, recovery must reach the final target and
continue advancing, not merely avoid crashing. Read `ARRIVED` latency and final
`VERDICT=PASS/FAIL`; a launch success alone is not a pass. Position events prove
renderer progress, not pixel correctness or audio waveform correctness. Timing
limits are a regression budget for the fixture/device, not a universal decoder SLA.

The activity pauses after its verdict; Back releases the session. All logging is
per request, first target arrival, recovery sample, or error. Normal playback and
release APKs incur no harness overhead.

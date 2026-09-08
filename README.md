# Vicu

Standalone Android demo for extracting the first audio stream from a video as a 192 kb/s MP3.

The application package is `com.rockbyte.vicu`. It is self-contained:

- `app/libs/ffmpeg-kit-next-api.aar` contains the FFmpegKit Kotlin/Java API and resources.
- `app/src/main/jniLibs/` contains the 44 native libraries for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

The original AAR bundled the same native libraries. They have been intentionally removed from the local API AAR so Gradle packages the checked-in `jniLibs` exactly once and does not report duplicate native libraries.

Build with:

```bash
./gradlew :app:assembleDebug
```

# Vicu

Standalone Android demo for extracting the first audio stream from a video as a 192 kb/s MP3.

The application package is `com.rockbyte.vicu`. It is self-contained:

- `app/libs/ffmpeg-kit-next-api.aar` contains the FFmpegKitNext Kotlin/Java API and resources. The `jni/` folder was stripped from the full AAR built from [ffmpeg-kit-next](https://github.com/arthenica/ffmpeg-kit-next) 9.0.0 so the API AAR stays under GitHub's 100 MB file limit.
- `app/src/main/jniLibs/` contains the 48 native libraries extracted from the same full AAR for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.

To regenerate after a new ffmpeg-kit-next build: copy the full AAR to `app/libs/ffmpeg-kit-next-api.aar`, run `zip -d app/libs/ffmpeg-kit-next-api.aar 'jni/*'`, and copy the `jni/<abi>/*.so` files into `app/src/main/jniLibs/<abi>/`.

Build with:

```bash
./gradlew :app:assembleDebug
```

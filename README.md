# FILMERA

**Your camera. Enhanced in real time.**

FILMERA is an Android camera app with live, GPU-rendered filters and face effects. You
see the effect on the live camera feed, then take a photo (tap) or record a video (hold),
and share it to WhatsApp, Instagram, Telegram or any other app.

It works on its own. It does not need WhatsApp or any other app to be useful.

---

## What works today

| Feature | Status |
|---|---|
| Live camera preview (front/back, flash, pinch zoom, double-tap to flip) | ✅ |
| 29 real-time GPU filters in 5 categories (Color, Lighting, Beauty, Face, Fun) | ✅ |
| Intensity slider per filter + Enhance panel (brightness, contrast, saturation, warmth) | ✅ |
| Face effects that follow your face: Big Eyes, Alien, Shades, Clown Nose, Blush, Hearts | ✅ on ARM phones (see limitations) |
| Photo: tap shutter. Video: hold shutter (Photo mode) or tap start/stop (Video mode) | ✅ |
| Filters baked into saved photos and videos, with sound | ✅ |
| In-app gallery, viewer, delete, save to phone gallery (`Pictures/FILMERA`, `Movies/FILMERA`) | ✅ |
| Share to installed apps (WhatsApp, Instagram, Telegram, Snapchat, TikTok) or system share sheet | ✅ |
| Quality profiles (Auto / Performance / Balanced / Ultra), auto step-down when FPS drops | ✅ |
| Device compatibility screen with honest support badges | ✅ |
| Hidden developer mode (tap Version 7×): performance overlay, diagnostics export | ✅ |
| Privacy: everything on-device, no network permission, no analytics | ✅ |

## What it does *not* do, and why

**Live filters inside WhatsApp, Instagram or Telegram video calls are not possible for a
regular Android app.**

- Android 15 added a virtual camera API, but it requires a privileged permission that
  only system apps and phone manufacturers get.
- WhatsApp and Instagram open the phone's real cameras directly and don't offer a camera
  picker.
- The only known workarounds need root or Xposed modules, which Play Store rejects and
  which put users' accounts at risk.

FILMERA shows this as **Not available** on the Device compatibility screen instead of
pretending. The supported route into those apps is **capture → share**, which works now.
The route to *live* filtered calls is our own calling feature (see Roadmap).

---

## Architecture

```
                 ┌──────────── CameraX ────────────┐
                 │                                  │
           Preview (16:9)                   ImageAnalysis (16:9, RGBA)
                 │                                  │
     SurfaceTexture / OES texture         FaceTracker (MediaPipe, on-device)
                 │                                  │ smoothed FaceData
                 ▼                                  ▼
        ┌─────────────── CameraGlPipeline (GL thread) ───────────────┐
        │   FilterRenderer: filter shader + face uniforms + adjust   │
        └──────┬──────────────────────┬──────────────────────┬───────┘
               ▼                      ▼                      ▼
        Screen (SurfaceView)   MediaRecorder surface    Photo FBO → JPEG
                               (while recording)        (on demand)
                                      │                      │
                                      └──── CaptureRepository ─── gallery export
                                                     │
                                                ShareHelper → WhatsApp / Instagram / …
```

Each camera frame is drawn once per output on a dedicated GL thread, with no CPU copies
except for photo capture. Face tracking runs on low-resolution analysis frames and only
while a face filter is selected.

### Coordinate spaces

Filters and face landmarks use one shared **image space**: upright, un-mirrored, 0..1,
top-left origin. `FrameGeometry` builds two affine maps per output:

- output → image (vertex shader): handles screen crop, video rotation hint, mirroring,
  bottom-up FBOs
- image → camera buffer (fragment shader): undoes sensor rotation, then the
  SurfaceTexture transform

This is why one shader works unchanged for the screen, the video file and the photo.
It's pure Kotlin and unit tested (`FrameGeometryTest`).

### Adding a filter

Append a `FilterDefinition` to `FilterCatalog.all`. `shader` is the body of
`vec3 effect(vec2 uv)` in GLSL ES 1.00, with helpers such as `cam(uv)`, `blur9`, `edges`,
`bulge` and `faceLocal`. The UI, categories and renderer pick it up automatically. If a
shader fails to compile on some GPU, the renderer falls back to Original and lists the
filter in the developer overlay.

### Project layout

```
app/src/main/java/com/filmera/app/
  core/render/       EglCore, FilterRenderer, CameraGlPipeline, FrameGeometry, Affine2D
  core/filter/       FilterDefinition, FilterCatalog (all filters), FilterShaders
  core/face/         FaceTracker (MediaPipe), FaceData, LandmarkSmoother
  core/camera/       CameraController (CameraX), QualityProfile
  core/media/        VideoRecorder, CaptureRepository, ShareHelper
  core/performance/  DeviceInfo (compatibility report)
  core/ui/           theme + reusable components
  data/              SettingsRepository (DataStore)
  feature/           onboarding, home, camera, gallery, settings, compatibility
  navigation/        FilmeraNavHost
```

One Gradle module, with packages kept separate so they can be split into modules later
if the app grows. Dependency injection is a small `AppContainer` rather than Hilt, to keep
builds fast at this size.

---

## Build & run

Requirements: JDK 17, Android SDK 34. On this machine both are in `~/android-toolchain`.

```bash
export JAVA_HOME=~/android-toolchain/jdk-17.0.20.1+1/Contents/Home
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk (all CPU types)
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk (ARM only, R8-shrunk)
./gradlew testDebugUnitTest    # JVM unit tests
```

Install on a phone with USB debugging enabled:

```bash
~/android-toolchain/sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
```

The release build is signed with the debug key so it installs for testing. Create a real
signing key before publishing to Play Store, and publish an App Bundle (`bundleRelease`)
so each phone downloads only its own native libraries.

### Validating shaders without a phone

`ShaderDumpTest` writes every generated shader to `app/build/shaders/`. Check them with
Khronos glslangValidator:

```bash
./gradlew testDebugUnitTest
for f in app/build/shaders/*; do glslangValidator "$f" || echo "FAILED: $f"; done
```

---

## Testing

**Automated**
- `FrameGeometryTest`: rotation, crop, mirroring, video rotation hint and FBO flip maps.
- `QualityAndCatalogTest`: profile recommendation, unique filter ids, shader sanity, face
  filters handle "no face".
- All 31 shaders pass glslangValidator (GLSL ES 1.00).

**On the Android 14 emulator (x86_64)**
- Onboarding, home, camera, filters, photo, video, gallery export.
- Saved photo and video verified: upright, filter applied, video plays in portrait with
  audio.

**Still needs a real phone (please test)**
- Face effects. MediaPipe has no x86_64 library, so on the emulator face tracking reports
  "unavailable" and the rest of the app keeps working. On ARM phones it should run.
- Front camera orientation and mirroring with a real face.
- Performance on a low-end phone (turn on the performance overlay in developer mode).
- Sharing into WhatsApp and Instagram.

Test checklist for each phone: camera start, flip, every category, face filters with your
face, photo, 10 s video, hold-to-record, background/foreground while recording, share to
WhatsApp, low light, 5 minutes continuous use (heat).

---

## Known limitations

- Live filters inside other apps' calls aren't possible (see above).
- Photos are captured from the live stream (720p, or 1080p on Ultra), not the sensor's
  full resolution.
- The app is locked to portrait.
- Background blur or replacement isn't built yet (needs a segmentation model).
- Face effects track one face at a time.
- Libraries are pinned to versions compatible with the local toolchain (compileSdk 34,
  Kotlin 1.9). Newer CameraX, MediaPipe and Compose versions exist; upgrade together with
  compileSdk 35+.

## Roadmap

1. **Real-phone test pass**: face effects, performance on low-end phones, sharing.
2. **Background effects**: MediaPipe selfie segmentation for blur and replace.
3. **FILMERA Call**: one-to-one video calls over WebRTC, with the filtered GL output
   as the video track. This is how live filtered calls become possible: both people use
   FILMERA. Needs a signaling server, TURN, and accounts or contact links.
4. Full-resolution photo capture (ImageCapture + GPU reprocessing).
5. Filter packs and a creator marketplace.
6. Play Store release: signing, App Bundle, store listing, privacy policy.

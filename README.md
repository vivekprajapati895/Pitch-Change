# Pitch Player

An Android app for transposing karaoke tracks. Load a YouTube link or a local
file, shift the pitch in semitones, and save the result as MP4 or M4A.

Built for personal and classroom use. It is not intended for the Play Store.

---

## Getting an APK without installing anything

The project ships with a GitHub Actions workflow, so GitHub can build the APK
for you.

1. Create a new repository on GitHub. Private is fine.
2. Upload the contents of this folder, keeping `.github/workflows/build.yml`
   where it is. The web uploader works, but drag the whole folder in rather
   than file by file.
3. Open the **Actions** tab. The build starts on its own after the push. If it
   does not, pick **Build APK** in the sidebar and press **Run workflow**.
4. Wait roughly five to eight minutes for the first run.
5. Open the finished run and download **PitchPlayer-debug-apk** from the
   Artifacts section at the bottom. It arrives as a zip containing the APK.

Copy the APK to your phone, tap it, and allow "install unknown apps" for
whichever app you used to open it.

Free GitHub accounts get plenty of Actions minutes for this.

---

## Building it locally

You need Android Studio (Ladybug or newer) and a JDK 17 toolchain, which Android
Studio ships with.

1. Open Android Studio, choose **Open**, and select the `PitchPlayer` folder.
2. Android Studio will notice there is no Gradle wrapper JAR and offer to
   generate one. Accept. If it does not ask, run `gradle wrapper` from a
   terminal, or use **File → Sync Project with Gradle Files**.
3. Wait for the first sync. It downloads Media3, NewPipeExtractor and Compose,
   which is a few hundred megabytes the first time.
4. Plug in your phone with USB debugging on and press **Run**.

To produce a shareable APK instead:

```
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. It is signed with the debug
key so it installs without further setup. If you plan to hand it around more
widely, generate your own keystore and point `signingConfigs` at it in
`app/build.gradle.kts`.

Sideloading requires the person installing it to allow "install unknown apps"
for whatever they use to open the file.

**Minimum Android version:** 8.0 (API 26).

---

## How the pitch shifting works

Twelve semitones make an octave, and an octave is a doubling of frequency, so
shifting by `n` semitones means multiplying frequency by `2^(n/12)`. That single
line lives in `core/Pitch.kt` and is the only place the conversion happens, so
playback and export can never drift apart.

**During playback**, the factor goes straight into ExoPlayer:

```kotlin
player.playbackParameters = PlaybackParameters(speed, Pitch.factor(semitones))
```

Media3's Sonic processor handles it in real time. There is no re-buffering and
no delay when you move the slider.

One trap worth knowing about: if ExoPlayer offloads audio decoding to the phone's
DSP, Sonic never runs and pitch changes silently do nothing. `PlayerFactory`
explicitly sets `AUDIO_OFFLOAD_MODE_DISABLED` to prevent that.

**During export**, the same `SonicAudioProcessor` runs inside Media3's
`Transformer`. What you preview is what you get.

---

## How export avoids re-encoding video

The naive approach feeds the whole video through Transformer, which decodes and
re-encodes every frame. On a mid-range phone that is minutes of work and a
visible quality loss, all to change something that only affects audio.

This app does it differently:

```
download video track  ──────────────────────────┐
                                                 ├──► MediaMuxer ──► out.mp4
download audio track ──► Transformer + Sonic ───┘      (lossless copy)
```

Only the audio is decoded and re-encoded. The video track is copied sample by
sample into the output container. A five minute 720p track exports in seconds
rather than minutes, and the picture is bit-identical to the source.

`Remuxer.kt` handles the copy, including carrying over the rotation hint so
portrait recordings do not come out sideways.

Files are published through MediaStore into `Movies/PitchPlayer` and
`Music/PitchPlayer`, so they appear in the gallery and in music players rather
than being trapped inside app storage.

---

## Project layout

```
core/       Pitch maths, formatting helpers
media/      YouTube resolution via NewPipeExtractor, stream models
player/     ExoPlayer setup and the single ViewModel that drives the UI
export/     Download, pitch render, remux, MediaStore publish, foreground service
ui/         Compose screens and components
```

The whole app is one Activity with a state enum. There is no navigation
library, no dependency injection framework, and no repository layer, because at
this size those would add indirection without buying anything.

---

## Things to know

**Tempo is playback only.** You can slow a track to 50% to learn a fast passage,
but exports always render at normal speed. Changing audio duration without
re-encoding video would desynchronise the two.

**Quality past about ±5 semitones.** Sonic works in the time domain and starts
sounding warbly on sustained notes at large shifts. The UI warns you when you
cross that line. If your singers routinely need bigger shifts, the upgrade path
is SoundTouch (LGPL) wrapped as a custom Media3 `AudioProcessor`; it is a JNI
build and roughly a day of work, and it drops into the same two call sites.

**Video export needs an MP4 rendition.** `MediaMuxer` cannot write VP9 or Opus
into an MP4 container, so `Exporter` filters to MP4-family streams. YouTube
almost always offers these. When it does not, the app says so and points you at
the audio-only download.

**YouTube extraction is the fragile part.** Google changes its internals
regularly and NewPipeExtractor chases those changes. If videos stop loading, the
first thing to try is bumping the `newpipe` version in
`gradle/libs.versions.toml` to the latest tag from
`github.com/TeamNewPipe/NewPipeExtractor/releases`. Nothing else in the app
depends on YouTube.

This is also why local file support matters. Everything except the resolver
works on a file you already have, and that path has no external dependency that
can break.

**Stream URLs expire.** If a video sits open for an hour and then you hit save,
the download may fail. Reload the video and try again.

---

## Legal note

Downloading from YouTube is against their Terms of Service. Keep this to your
own use, do not publish it to an app store, and do not distribute the material
you produce with it.

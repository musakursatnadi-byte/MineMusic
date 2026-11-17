# MineMusic

**Listen to your own music while you play Minecraft.** Press **Esc**, click **MineMusic**, paste a YouTube link or type a song name, and press **Play**. The music keeps playing in the background while you mine, build and fight.

A client-side [Fabric](https://fabricmc.net/) mod for Minecraft Java Edition 1.21.11, 26.1.x, 26.2 and 26.3.

- Download: [modrinth.com/mod/minemusic](https://modrinth.com/mod/minemusic)
- License: [MIT](LICENSE). You may use, share and change it; keep the license and the author's name.

*(Türkçe açıklama aşağıda.)*

## Features

- A **MineMusic** button in the pause menu (or press **F10**)
- Play a YouTube **link** or just type a **song name** and pick the right song from the first 5 YouTube results
- Pause / resume, stop, volume slider (Minecraft's master volume applies too), repeat (off / song / list)
- Leaving a world or a server turns the music off; **F8** in the next world goes on where it stopped
- **Playlists**: as many as you like, with names; play all, previous / next, shuffle, reorder, remove
- The **song name and time** in the top-left corner (it moves below boss bars)
- Minecraft's own music goes quiet while yours plays (can be turned off)
- Shortcut keys: **F10** open, **F9** pause / resume, **F8** music off and back on where it stopped
- **36 languages** (the menus follow your Minecraft language)
- Client-side only: works on any server, only you hear your music

## How it plays music

On first use MineMusic downloads three free programs into `%APPDATA%\MineMusic\tools` (about 145 MB): [yt-dlp](https://github.com/yt-dlp/yt-dlp) gets the audio, [deno](https://github.com/denoland/deno) lets yt-dlp read YouTube, and [ffmpeg](https://github.com/yt-dlp/FFmpeg-Builds) decodes it. The sound is played with Java's own audio system. Songs are cached in `%APPDATA%\MineMusic\cache` (kept under 500 MB). The automatic download works on Windows only; on macOS / Linux install `yt-dlp`, `deno` and `ffmpeg` yourself.

Please use it for your own listening and respect the artists' work.

## Building

You need a **JDK 25** (the 1.21.11 jar is built for Java 21 with it).

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
.\gradlew.bat collectJars
```

The jars land in `dist/`. One version: `.\gradlew.bat :mc1_21_11:build`; a dev game: `.\gradlew.bat :mc26_2:runClient`.

There is an automatic in-game test (`devtest/`, only compiled with `-Pselftest=<project>`), for example
`.\gradlew.bat :mc26_2:runClient -Pselftest=mc26_2 "--args=--quickPlaySingleplayer SelfTestWorld"`.
It needs a single-player world called `SelfTestWorld` in `versions/<version>/run/saves/` (not included). `-Pselftesthome=<folder>` makes it use a copy of the MineMusic folder instead of your own.

## Project layout

- `src/client/java`: the code shared by every Minecraft version. `audio/` never touches Minecraft (playback, playlists, settings); `screen/` the menus; `hud/` the song name on screen; `keys/` shortcuts; `mixin/` the pause-menu button, muting Minecraft's music, and the boss-bar count.
- `src/main/resources/assets/minemusic/lang`: the 36 language files (`en_us.json` is the base).
- `compat/<folder>`: small version-specific pieces (`setscreen_*`, `keys_*`, `hud_*`).
- `versions/<version>/gradle.properties`: Minecraft, Java and Fabric API versions and the compat folders of each version.
- To add a Minecraft version: add it to `settings.gradle` and create `versions/<version>/gradle.properties`.

## Credits

Made by **musakursatnadi**: the idea, the features, the testing and the hand-drawn icon. The code was written with the help of an AI assistant (Claude), and the translations are AI-made too. If you speak one of the languages and see a mistake, please open an issue!

---

## Türkçe

**Minecraft oynarken kendi müziğini dinle.** **Esc**'ye bas, **MineMusic**'e tıkla, bir YouTube linki yapıştır ya da şarkının adını yaz, **Çal**'a bas. Sen oynarken müzik arka planda çalmaya devam eder.

Minecraft Java Edition 1.21.11, 26.1.x, 26.2 ve 26.3 için, sadece istemci tarafında çalışan bir Fabric modu. İndirmek için: [modrinth.com/mod/minemusic](https://modrinth.com/mod/minemusic). Lisans: [MIT](LICENSE). Kullanabilir, paylaşabilir ve değiştirebilirsin; lisansı ve yapanın adını koruman yeter.

- Esc menüsünde **MineMusic** butonu (ya da **F10**)
- YouTube **linki** ya da **şarkı adı** (ilk 5 YouTube sonucundan doğru şarkıyı seçersin)
- Duraklat / devam et, durdur, ses ayarı, tekrar (kapalı / şarkı / liste)
- **Çalma listeleri**: istediğin kadar, adlı liste; hepsini çal, önceki / sonraki, karışık, sırala, sil
- Sol üst köşede **şarkının adı ve süresi** (boss çubuğunun altına iner)
- Senin müziğin çalarken Minecraft'ın müziği susar (kapatılabilir)
- Kısayollar: **F10** aç, **F9** duraklat / devam, **F8** müziği kapat / kaldığı yerden aç
- **36 dil**; sunucuya kurulması gerekmez, müziğini sadece sen duyarsın

İlk kullanımda yt-dlp, deno ve ffmpeg `%APPDATA%\MineMusic\tools` klasörüne iner (yaklaşık 145 MB, sadece Windows'ta otomatik). Şarkılar `%APPDATA%\MineMusic\cache` klasöründe saklanır (en fazla 500 MB).

Derlemek için JDK 25 gerekir: `.\gradlew.bat collectJars` → jar'lar `dist/` klasörüne gelir.

**musakursatnadi** yaptı: fikir, özellikler, testler ve elle çizilmiş ikon. Kod bir yapay zeka asistanının (Claude) yardımıyla yazıldı, çeviriler de yapay zekayla yapıldı.

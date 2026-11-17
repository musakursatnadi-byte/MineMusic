package dev.minemusic.audio;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;

/**
 * Finds the helper programs MineMusic needs and downloads them on first use.
 * They live in %APPDATA%/MineMusic/tools so every Minecraft profile shares one copy.
 *
 * yt-dlp fetches the audio from YouTube, deno lets yt-dlp solve YouTube's JavaScript
 * challenges (yt-dlp looks for deno.exe next to itself), and ffmpeg decodes the audio.
 */
public final class Tools {
    private static final String YTDLP_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
    private static final String DENO_URL = "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip";
    private static final String FFMPEG_URL = "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl-shared.zip";

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private Tools() {
    }

    public static Path home() {
        // Dev test runs point this at a copy (-Dminemusic.home=...), so the player's own files stay untouched.
        String override = System.getProperty("minemusic.home");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String appData = System.getenv("APPDATA");
        return appData != null ? Path.of(appData, "MineMusic") : Path.of(System.getProperty("user.home"), ".minemusic");
    }

    public static Path toolsDir() {
        return home().resolve("tools");
    }

    public static Path cacheDir() {
        return home().resolve("cache");
    }

    public static String ytDlp() {
        return WINDOWS ? toolsDir().resolve("yt-dlp.exe").toString() : "yt-dlp";
    }

    public static String deno() {
        return WINDOWS ? toolsDir().resolve("deno.exe").toString() : "deno";
    }

    public static String ffmpeg() {
        return WINDOWS ? toolsDir().resolve("ffmpeg").resolve("ffmpeg.exe").toString() : "ffmpeg";
    }

    /** True when the programs are ready (always assumed outside Windows, where they come from PATH). */
    static boolean installed() {
        return !WINDOWS || Files.isRegularFile(Path.of(ytDlp())) && Files.isRegularFile(Path.of(deno())) && Files.isRegularFile(Path.of(ffmpeg()));
    }

    /** Downloads whatever is missing. Only Windows gets automatic downloads; elsewhere the programs must be on PATH. */
    public static void ensure(Consumer<Message> status, BooleanSupplier cancelled) throws IOException, InterruptedException {
        if (!WINDOWS) {
            return;
        }
        Files.createDirectories(toolsDir());

        Path ytDlp = Path.of(ytDlp());
        if (!Files.isRegularFile(ytDlp)) {
            download(YTDLP_URL, ytDlp, "yt-dlp", status, cancelled);
        }

        Path deno = Path.of(deno());
        if (!Files.isRegularFile(deno)) {
            Path zip = toolsDir().resolve("deno.zip.tmp");
            download(DENO_URL, zip, "deno", status, cancelled);
            status.accept(new Message("minemusic.status.extracting_tool", "deno"));
            extract(zip, toolsDir(), name -> name.equalsIgnoreCase("deno.exe"));
            Files.deleteIfExists(zip);
        }

        Path ffmpeg = Path.of(ffmpeg());
        if (!Files.isRegularFile(ffmpeg)) {
            Path zip = toolsDir().resolve("ffmpeg.zip.tmp");
            download(FFMPEG_URL, zip, "ffmpeg", status, cancelled);
            status.accept(new Message("minemusic.status.extracting_tool", "ffmpeg"));
            Files.createDirectories(ffmpeg.getParent());
            // The zip keeps ffmpeg.exe and the DLLs it needs in a bin/ folder.
            extract(zip, ffmpeg.getParent(), name -> name.equalsIgnoreCase("ffmpeg.exe") || name.toLowerCase(Locale.ROOT).endsWith(".dll"));
            Files.deleteIfExists(zip);
        }

        if (!Files.isRegularFile(ytDlp) || !Files.isRegularFile(deno) || !Files.isRegularFile(ffmpeg)) {
            throw new MusicException(new Message("minemusic.error.tools_missing"));
        }
    }

    private static void download(String url, Path target, String label, Consumer<Message> status, BooleanSupplier cancelled)
            throws IOException, InterruptedException {
        status.accept(new Message("minemusic.status.downloading_tool", label));
        HttpResponse<InputStream> response;
        try {
            response = send(client(null), url);
        } catch (SSLHandshakeException e) {
            // Some antivirus programs inspect HTTPS with their own certificate, which only Windows trusts.
            response = send(client(windowsTrust()), url);
        }
        if (response.statusCode() != 200) {
            response.body().close();
            throw new MusicException(new Message("minemusic.error.download_failed", label, Integer.toString(response.statusCode())));
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int lastPercent = -1;
            int n;
            while ((n = in.read(buf)) >= 0) {
                if (cancelled.getAsBoolean()) {
                    throw new InterruptedException();
                }
                out.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    int percent = (int) (done * 100 / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        status.accept(new Message("minemusic.status.downloading_tool_progress", label, Integer.toString(percent),
                                Long.toString(done / (1024 * 1024)), Long.toString(total / (1024 * 1024))));
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private static HttpResponse<InputStream> send(HttpClient client, String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "MineMusic")
                .timeout(Duration.ofMinutes(10))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    private static HttpClient client(SSLContext ssl) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(20));
        if (ssl != null) {
            builder.sslContext(ssl);
        }
        return builder.build();
    }

    private static SSLContext windowsTrust() throws IOException {
        try {
            KeyStore store = KeyStore.getInstance("Windows-ROOT");
            store.load(null, null);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(store);
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, tmf.getTrustManagers(), null);
            return ssl;
        } catch (Exception e) {
            MusicException failure = new MusicException(new Message("minemusic.error.secure_connection"));
            failure.initCause(e);
            throw failure;
        }
    }

    /** Extracts matching files from a zip, flattening folders (only the file name is kept). */
    private static void extract(Path zip, Path targetDir, java.util.function.Predicate<String> wanted) throws IOException {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                name = name.substring(name.lastIndexOf('/') + 1);
                if (!name.isEmpty() && wanted.test(name)) {
                    Files.copy(in, targetDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}

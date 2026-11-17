package dev.minemusic.audio;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Starts the helper programs and remembers them, so Stop and closing the game can kill them all. */
final class ProcessTracker {
    private final Set<Process> running = ConcurrentHashMap.newKeySet();

    /** Starts a program whose error output is mixed into its normal output (for reading messages). */
    Process start(List<String> cmd) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(cmd).redirectErrorStream(true);
        // yt-dlp is a Python program; make it print titles with Turkish letters correctly.
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        builder.environment().put("PYTHONUTF8", "1");
        return track(builder.start());
    }

    /** Starts a program whose normal output is raw data, so its error output is thrown away. */
    Process startData(List<String> cmd) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD);
        return track(builder.start());
    }

    void finish(Process process) {
        process.destroyForcibly();
        running.remove(process);
    }

    void killAll() {
        for (Process process : running) {
            process.destroyForcibly();
        }
        running.clear();
    }

    private Process track(Process process) {
        running.add(process);
        return process;
    }
}

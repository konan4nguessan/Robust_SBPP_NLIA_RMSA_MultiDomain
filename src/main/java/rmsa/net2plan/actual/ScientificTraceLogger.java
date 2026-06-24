package rmsa.net2plan.actual;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/** File-only trace logger. It never writes to the Java console. */
public final class ScientificTraceLogger {
    private boolean enabled;
    private String level = "SUMMARY";
    private String connectionFilter = "";
    private boolean blockedOnly;
    private BufferedWriter writer;

    public synchronized void open(
            boolean enabled,
            String path,
            String level,
            String connectionFilter,
            boolean blockedOnly) {
        close();
        this.enabled = false;
        this.level = normalizeLevel(level);
        this.connectionFilter = connectionFilter == null ? "" : connectionFilter.trim();
        this.blockedOnly = blockedOnly;

        if (!enabled || path == null || path.trim().isEmpty()) return;
        try {
            File file = new File(path.trim());
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            writer = new BufferedWriter(new FileWriter(file, false));
            this.enabled = true;
            traceGlobal("TRACE-START", "level=" + this.level
                    + " connectionFilter=" + this.connectionFilter
                    + " blockedOnly=" + this.blockedOnly
                    + " path=" + file.getAbsolutePath());
        } catch (IOException e) {
            writer = null;
            this.enabled = false;
        }
    }

    public synchronized void close() {
        if (writer != null) {
            try { writer.flush(); } catch (IOException ignored) {}
            try { writer.close(); } catch (IOException ignored) {}
        }
        writer = null;
        enabled = false;
    }

    public boolean isEnabled() { return enabled && writer != null; }
    public boolean isBlockedOnly() { return blockedOnly; }
    public boolean isAdmissionLevel() { return levelRank(level) >= levelRank("ADMISSION"); }
    public boolean isFullLevel() { return levelRank(level) >= levelRank("FULL"); }

    public boolean acceptsConnection(String connectionId) {
        return connectionFilter.isEmpty()
                || "-1".equals(connectionFilter)
                || (connectionId != null && connectionFilter.equals(connectionId));
    }

    public void traceConnection(String tag, String connectionId, String message) {
        if (!isEnabled() || !acceptsConnection(connectionId)) return;
        write("[" + tag + "][connectionId=" + connectionId + "] " + safe(message));
    }

    public void traceGlobal(String tag, String message) {
        if (!isEnabled()) return;
        write("[" + tag + "] " + safe(message));
    }

    private synchronized void write(String line) {
        if (writer == null) return;
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException ignored) {}
    }

    private static String normalizeLevel(String value) {
        if (value == null) return "SUMMARY";
        String v = value.trim().toUpperCase();
        if ("FULL".equals(v) || "ADMISSION".equals(v) || "SUMMARY".equals(v)) return v;
        return "SUMMARY";
    }

    private static int levelRank(String value) {
        String v = normalizeLevel(value);
        if ("FULL".equals(v)) return 3;
        if ("ADMISSION".equals(v)) return 2;
        return 1;
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ');
    }
}
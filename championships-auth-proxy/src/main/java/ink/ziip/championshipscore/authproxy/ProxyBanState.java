package ink.ziip.championshipscore.authproxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/** Persists only the proxy's independent ban-event cursor. */
final class ProxyBanState {
    private static final String CURSOR_KEY = "ban-event-cursor";

    private final File file;
    private String cursor;

    ProxyBanState(File file) {
        this.file = file;
        load();
    }

    synchronized boolean initialized() {
        return cursor != null;
    }

    synchronized String cursor() {
        if (cursor == null) throw new IllegalStateException("Proxy ban state has not been initialized");
        return cursor;
    }

    synchronized void advance(String nextCursor) throws IOException {
        if (nextCursor == null || !nextCursor.matches("^\\d{1,19}$")) {
            throw new IllegalArgumentException("Invalid bridge cursor");
        }
        cursor = nextCursor;
        Properties properties = new Properties();
        properties.setProperty(CURSOR_KEY, cursor);
        try (FileOutputStream output = new FileOutputStream(file)) {
            properties.store(output, "ChampionshipsAuthProxy state");
        }
    }

    private void load() {
        if (!file.isFile()) return;
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
            String value = properties.getProperty(CURSOR_KEY);
            if (value != null && value.matches("^\\d{1,19}$")) cursor = value;
        } catch (IOException ignored) {
            // A fresh snapshot is safer than trusting an unreadable cursor.
        }
    }
}

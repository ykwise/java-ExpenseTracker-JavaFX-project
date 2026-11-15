package com.expensetracker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Decides where the database file lives, so the data doesn't depend on which folder the
 * app happens to be started from (BUG-03).
 *
 * <ul>
 *   <li>{@code -Dexpensetracker.db=/path/file.db} always wins (tests, portable setups).</li>
 *   <li>Otherwise the operating system's per-user application data folder:
 *       macOS {@code ~/Library/Application Support/ExpenseTracker},
 *       Windows {@code %APPDATA%\ExpenseTracker},
 *       Linux {@code $XDG_DATA_HOME/expense-tracker} or {@code ~/.local/share/expense-tracker}.</li>
 * </ul>
 *
 * Earlier versions kept {@code expenses.db} in the working directory. If that file exists and
 * there is no database in the new location yet, it is <b>copied</b> there once. Copying rather
 * than moving keeps the original as a backup if anything goes wrong, and the copy is written to
 * a temporary file first and renamed into place, so a half-finished copy is never mistaken for
 * a real database.
 *
 * Everything the decision depends on (properties, environment, home and working directory)
 * is passed in, so it is tested without touching the real home folder.
 */
public final class DataLocation {

    static final String OVERRIDE_PROPERTY = "expensetracker.db";
    static final String FILE_NAME = "expenses.db";

    /** Where the database is, and where it was copied from if this run migrated a legacy file. */
    public record Result(Path databaseFile, Optional<Path> copiedFrom) {
    }

    private DataLocation() {
    }

    /** Resolves the location for the current machine and user. */
    public static Result resolve() throws IOException {
        return resolve(System.getProperty(OVERRIDE_PROPERTY), System.getProperty("os.name", ""), System.getenv(),
                Path.of(System.getProperty("user.home")), Path.of("").toAbsolutePath());
    }

    static Result resolve(String override, String osName, Map<String, String> env, Path home, Path workingDir)
            throws IOException {
        if (override != null && !override.isBlank()) {
            return new Result(Path.of(override).toAbsolutePath(), Optional.empty());
        }

        Path target = defaultDirectory(osName, env, home).resolve(FILE_NAME);
        Files.createDirectories(target.getParent());

        Path legacy = workingDir.resolve(FILE_NAME).toAbsolutePath();
        if (Files.notExists(target) && Files.isRegularFile(legacy) && !legacy.equals(target.toAbsolutePath())) {
            Path partial = target.resolveSibling(FILE_NAME + ".copying");
            Files.copy(legacy, partial, StandardCopyOption.REPLACE_EXISTING);
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE);
            return new Result(target, Optional.of(legacy));
        }
        return new Result(target, Optional.empty());
    }

    static Path defaultDirectory(String osName, Map<String, String> env, Path home) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return home.resolve("Library").resolve("Application Support").resolve("ExpenseTracker");
        }
        if (os.contains("win")) {
            String appData = env.get("APPDATA");
            Path base = appData != null && !appData.isBlank() ? Path.of(appData) : home.resolve("AppData").resolve("Roaming");
            return base.resolve("ExpenseTracker");
        }
        String xdg = env.get("XDG_DATA_HOME");
        Path base = xdg != null && !xdg.isBlank() ? Path.of(xdg) : home.resolve(".local").resolve("share");
        return base.resolve("expense-tracker");
    }
}

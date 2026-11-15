package com.expensetracker;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataLocationTest {

    @TempDir
    Path tmp;

    private Path home() throws IOException {
        return Files.createDirectories(tmp.resolve("home"));
    }

    private Path workingDir() throws IOException {
        return Files.createDirectories(tmp.resolve("project"));
    }

    @Test
    void override_isUsedAsIs() throws IOException {
        DataLocation.Result r = DataLocation.resolve("/data/mine.db", "Mac OS X", Map.of(), home(), workingDir());
        assertEquals(Path.of("/data/mine.db"), r.databaseFile());
        assertEquals(Optional.empty(), r.copiedFrom());
    }

    @Test
    void macOs_usesApplicationSupport() throws IOException {
        Path h = home();
        DataLocation.Result r = DataLocation.resolve(null, "Mac OS X", Map.of(), h, workingDir());
        assertEquals(h.resolve("Library/Application Support/ExpenseTracker/expenses.db"), r.databaseFile());
        assertTrue(Files.isDirectory(r.databaseFile().getParent()), "folder is created");
    }

    @Test
    void windows_usesAppData() {
        Path dir = DataLocation.defaultDirectory("Windows 11", Map.of("APPDATA", "/users/k/AppData/Roaming"), Path.of("/users/k"));
        assertEquals(Path.of("/users/k/AppData/Roaming/ExpenseTracker"), dir);
    }

    @Test
    void linux_usesXdgDataHomeOrLocalShare() {
        assertEquals(Path.of("/xdg/expense-tracker"),
                DataLocation.defaultDirectory("Linux", Map.of("XDG_DATA_HOME", "/xdg"), Path.of("/home/k")));
        assertEquals(Path.of("/home/k/.local/share/expense-tracker"),
                DataLocation.defaultDirectory("Linux", Map.of(), Path.of("/home/k")));
    }

    @Test
    void legacyDatabaseInWorkingDirectory_isCopiedOnceAndKept() throws IOException {
        Path project = workingDir();
        byte[] data = {1, 2, 3, 4};
        Files.write(project.resolve("expenses.db"), data);

        DataLocation.Result r = DataLocation.resolve(null, "Linux", Map.of(), home(), project);

        assertEquals(Optional.of(project.resolve("expenses.db").toAbsolutePath()), r.copiedFrom());
        assertArrayEquals(data, Files.readAllBytes(r.databaseFile()));
        assertTrue(Files.exists(project.resolve("expenses.db")), "original kept as a backup");
        assertFalse(Files.exists(r.databaseFile().resolveSibling("expenses.db.copying")), "no partial file left");
    }

    @Test
    void existingDatabase_isNeverOverwrittenByLegacyFile() throws IOException {
        Path h = home();
        Path project = workingDir();
        Path target = DataLocation.defaultDirectory("Linux", Map.of(), h).resolve("expenses.db");
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[] {9});
        Files.write(project.resolve("expenses.db"), new byte[] {1, 2, 3});

        DataLocation.Result r = DataLocation.resolve(null, "Linux", Map.of(), h, project);

        assertEquals(Optional.empty(), r.copiedFrom());
        assertArrayEquals(new byte[] {9}, Files.readAllBytes(target));
    }

    @Test
    void noLegacyFile_startsFresh() throws IOException {
        DataLocation.Result r = DataLocation.resolve(null, "Linux", Map.of(), home(), workingDir());
        assertEquals(Optional.empty(), r.copiedFrom());
        assertFalse(Files.exists(r.databaseFile()), "SQLite creates it on first connection");
    }

    @Test
    void startedFromAnotherFolder_findsTheSameDatabase() throws IOException {
        Path h = home();
        Path first = DataLocation.resolve(null, "Mac OS X", Map.of(), h, workingDir()).databaseFile();
        Path second = DataLocation.resolve(null, "Mac OS X", Map.of(), h, Files.createDirectories(tmp.resolve("elsewhere")))
                .databaseFile();
        assertEquals(first, second);
    }
}

package com.codewithsam.courier.wallet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** KEY=VALUE lines in a gitignored file that only its owner can read. */
public class EnvFile {

    private final Path path;

    public EnvFile(Path path) {
        this.path = path;
    }

    public Optional<String> get(String key) {
        return lines().stream()
                .filter(line -> line.startsWith(key + "="))
                .map(line -> line.substring(key.length() + 1))
                .findFirst();
    }

    public void put(String key, String value) {
        List<String> lines = new ArrayList<>(lines());
        lines.removeIf(line -> line.startsWith(key + "="));
        lines.add(key + "=" + value);
        try {
            if (Files.notExists(path)) {
                Files.createFile(path, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
            }
            Files.write(path, lines);
            Files.setPosixFilePermissions(path,
                    PosixFilePermissions.fromString("rw-------"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<String> lines() {
        try {
            return Files.exists(path) ? Files.readAllLines(path) : List.of();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

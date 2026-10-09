package com.codewithsam.courier.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvFileTest {

    @TempDir
    Path dir;

    @Test
    void writesKeysOwnerReadableOnly() throws Exception {
        EnvFile env = new EnvFile(dir.resolve(".env"));

        env.put("COURIER_RECIPIENT_ADDRESS", "0xabc");

        assertThat(env.get("COURIER_RECIPIENT_ADDRESS")).contains("0xabc");
        var permissions = Files.getPosixFilePermissions(dir.resolve(".env"));
        assertThat(PosixFilePermissions.toString(permissions)).isEqualTo("rw-------");
    }

    @Test
    void replacesAnExistingKeyAndKeepsTheOthers() throws Exception {
        EnvFile env = new EnvFile(dir.resolve(".env"));
        env.put("A", "1");
        env.put("B", "2");

        env.put("A", "3");

        assertThat(env.get("A")).contains("3");
        assertThat(env.get("B")).contains("2");
        assertThat(env.get("MISSING")).isEmpty();
    }
}

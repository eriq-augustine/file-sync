package dev.eriq.filesync.util;

import dev.eriq.filesync.Version;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class VersionTest {
    @Test
    public void testBase() {
        Assertions.assertNotEquals(Version.get(), Version.UNKNOWN_VERSION);
    }
}

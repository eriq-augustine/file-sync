package dev.eriq.filesync.util;

import dev.eriq.filesync.Sync;
import dev.eriq.filesync.location.EmptyLocation;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.location.LocalLocation;
import dev.eriq.filesync.location.SSHLocation;
import dev.eriq.filesync.util.JSON;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;

public class JSONTest {
    @Test
    public void testConversionBase() {
        class TestCase {
            public JSON.MapConverter object;
            public String text;

            public TestCase(JSON.MapConverter object, String text) {
                this.object = object;
                this.text = text;
            }
        }

        List<TestCase> testCases = List.of(
            new TestCase(
                new EmptyLocation(),
                String.format("{\"__class__\": \"%s\", \"base-dir\": \"\"}", EmptyLocation.class.getName())
            ),

            new TestCase(
                new LocalLocation(Path.of("foo")),
                String.format("{\"__class__\": \"%s\", \"base-dir\": \"%s\"}", LocalLocation.class.getName(), Path.of("foo").toAbsolutePath())
            ),

            new TestCase(
                new SSHLocation("host", 1234, "user", "pass", null, null, "foo"),
                String.format(String.join("\n",
                    "{",
                        "\"__class__\": \"%s\",",
                        "\"base-dir\": \"foo\",",
                        "\"hostname\": \"host\",",
                        "\"port\": 1234,",
                        "\"username\": \"user\",",
                        "\"password\": \"pass\",",
                        "\"private-key\": null,",
                        "\"public-key\": null",
                    "}").trim(),
                    SSHLocation.class.getName())
            ),

            // Empty Enum Set
            new TestCase(
                new Sync(
                    new EmptyLocation(),
                    new LocalLocation(Path.of("foo"))
                ),
                String.format(String.join("\n",
                    "{",
                        "\"__class__\": \"%s\",",
                        "\"source\": " + String.format("{\"__class__\": \"%s\", \"base-dir\": \"\"},", EmptyLocation.class.getName()),
                        "\"dest\": " + String.format("{\"__class__\": \"%s\", \"base-dir\": \"%s\"},", LocalLocation.class.getName(), Path.of("foo").toAbsolutePath()),
                        "\"identity-checks\": [],",
                        "\"conflict-behavior\": \"SKIP\",",
                    "}").trim(),
                    Sync.class.getName())
            ),

            // Single Enum Set
            new TestCase(
                new Sync(
                    new EmptyLocation(),
                    new LocalLocation(Path.of("foo")),
                    EnumSet.of(Sync.IdentityCheck.SIZE),
                    Sync.ConflictBehavior.OVERWRITE
                ),
                String.format(String.join("\n",
                    "{",
                        "\"__class__\": \"%s\",",
                        "\"source\": " + String.format("{\"__class__\": \"%s\", \"base-dir\": \"\"},", EmptyLocation.class.getName()),
                        "\"dest\": " + String.format("{\"__class__\": \"%s\", \"base-dir\": \"%s\"},", LocalLocation.class.getName(), Path.of("foo").toAbsolutePath()),
                        "\"identity-checks\": [\"SIZE\"],",
                        "\"conflict-behavior\": \"OVERWRITE\",",
                    "}").trim(),
                    Sync.class.getName())
            ),

            // Multi-Value Enum Set
            new TestCase(
                new Sync(
                    new EmptyLocation(),
                    new LocalLocation(Path.of("foo")),
                    EnumSet.of(Sync.IdentityCheck.MODTIME, Sync.IdentityCheck.SIZE),
                    Sync.ConflictBehavior.OVERWRITE
                ),
                String.format(String.join("\n",
                    "{",
                        "\"__class__\": \"%s\",",
                        "\"source\": " + String.format("{\"__class__\": \"%s\", \"base-dir\": \"\"},", EmptyLocation.class.getName()),
                        "\"dest\": " + String.format("{\"__class__\": \"%s\", \"base-dir\": \"%s\"},", LocalLocation.class.getName(), Path.of("foo").toAbsolutePath()),
                        "\"identity-checks\": [\"MODTIME\", \"SIZE\"],",
                        "\"conflict-behavior\": \"OVERWRITE\",",
                    "}").trim(),
                    Sync.class.getName())
            )
        );

        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);

            try {
                String text = testCase.object.toJSON();
                Assertions.assertEquals(JSON.normalize(testCase.text), text);

                Object newObject = JSON.fromJSON(text);
                Assertions.assertEquals(testCase.object, newObject);
            } catch (AssertionError ex) {
                throw new AssertionError(String.format("Failed test case %d.", i), ex);
            } catch (Exception ex) {
                throw new AssertionError(String.format("Crashed test case %d.", i), ex);
            }
        }
    }
}

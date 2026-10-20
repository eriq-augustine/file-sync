package dev.eriq.filesync;

import dev.eriq.filesync.location.SSHLocation;

import com.sun.security.auth.module.UnixSystem;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SSHDockerContainer {
    public static final String CONTAINER_NAME = "filesync-ssh-test";

    // TEST - Need a way for the test to fetch this image.
    public static final String IMAGE = "lscr.io/linuxserver/openssh-server:version-10.3_p1-r1";

    public static final String MOUNT_DIR = "/work";

    public static final int NICE_WAIT_MSEC = 200;
    public static final int FORCE_WAIT_MSEC = 500;

    public static final int STARTUP_WAIT_MSEC = 750;

    private static List<SSHDockerContainer> containers = new ArrayList<SSHDockerContainer>();

    private SSHLocation location;
    private Path mountDir;

    private Process process;

    public SSHDockerContainer(SSHLocation location, Path mountDir) {
        this.location = location;
        this.mountDir = mountDir;

        process = null;

        containers.add(this);
    }

    public void stop() {
        if (this.process == null) {
            return;
        }

        System.out.println(String.format("Stopping test SSH Docker container: '%s:%d'.", location.getHostname(), location.getPort()));

        Process process = this.process;
        this.process = null;

        // Try to stop the process nicely.
        try {
            process.destroy();
            if (process.waitFor(NICE_WAIT_MSEC, TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException ex) {
            // Ignore
        }

        // Stop the process foricly.
        try {
            process.destroyForcibly();
            process.waitFor(FORCE_WAIT_MSEC, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            // Ignore
        }
    }

    public static void stopAll() {
        for (SSHDockerContainer container : containers) {
            if (container != null) {
                container.stop();
            }
        }
    }

    public void start() throws IOException {
        if (process != null) {
            throw new RuntimeException("Container already started.");
        }

        System.out.println(String.format("Starting test SSH Docker container: '%s:%d'.", location.getHostname(), location.getPort()));

        UnixSystem system = new UnixSystem();

        List<String> command = List.of(
            "docker", "run",
            "--rm",
            "--name=" + CONTAINER_NAME + "-" + location.getPort(),
            "--env", "PUID=" + system.getUid(),
            "--env", "PGID=" + system.getGid(),
            "--env", "TZ=UTC",
            "--env", "PASSWORD_ACCESS=true",
            "--env", "USER_NAME=" + location.getUsername(),
            "--env", "USER_PASSWORD=" + location.getPassword(),
            "-p", location.getPort() + ":2222",
            "-v", mountDir.toAbsolutePath().toString() + ":" + MOUNT_DIR,
            "lscr.io/linuxserver/openssh-server:version-10.3_p1-r1"
        );

        ProcessBuilder builder = new ProcessBuilder(command);

        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);

        process = builder.start();

        try {
            Thread.sleep(STARTUP_WAIT_MSEC);
        } catch (InterruptedException ex) {
            // Ignore
        }
    }
}

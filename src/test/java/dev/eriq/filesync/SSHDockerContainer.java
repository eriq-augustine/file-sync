package dev.eriq.filesync;

import dev.eriq.filesync.location.SSHLocation;

import com.sun.security.auth.module.UnixSystem;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class SSHDockerContainer {
    private static final Logger logger = Logger.getLogger(SyncTest.class.getName());

    public static final String CONTAINER_NAME = "filesync-ssh-test";

    public static final String IMAGE = "lscr.io/linuxserver/openssh-server:version-10.3_p1-r1";

    public static final String MOUNT_DIR = "/work";

    public static final long NICE_WAIT_MSEC = 200;
    public static final long FORCE_WAIT_MSEC = 500;

    public static final int STARTUP_WAIT_MSEC = 750;

    public static final int PULL_WAIT_MSEC = 60 * 1000;

    private static Boolean isAvailable = null;
    private static List<SSHDockerContainer> containers = new ArrayList<SSHDockerContainer>();

    private SSHLocation location;
    private Path mountDir;

    private Process containerProcess;

    public SSHDockerContainer(SSHLocation location, Path mountDir) {
        this.location = location;
        this.mountDir = mountDir;

        containerProcess = null;

        containers.add(this);
    }

    public void stop() {
        if (this.containerProcess == null) {
            return;
        }

        logger.info(String.format("Stopping test SSH Docker container: '%s:%d'.", location.getHostname(), location.getPort()));

        terminateProcess(containerProcess, NICE_WAIT_MSEC, FORCE_WAIT_MSEC);
        containerProcess = null;
    }

    public void start() throws IOException {
        if (containerProcess != null) {
            throw new RuntimeException("Container already started.");
        }

        logger.info(String.format("Starting test SSH Docker container: '%s:%d'.", location.getHostname(), location.getPort()));

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

        containerProcess = builder.start();

        try {
            Thread.sleep(STARTUP_WAIT_MSEC);
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

    public static int terminateProcess(Process process, long niceTimeoutMS, long forceTimeoutMS) {
        if (process == null) {
            return -1;
        }

        // Try to stop the process nicely.
        try {
            process.destroy();
            if (process.waitFor(niceTimeoutMS, TimeUnit.MILLISECONDS)) {
                return process.exitValue();
            }
        } catch (InterruptedException ex) {
            // Ignore
        }

        // Stop the process foricly.
        try {
            process.destroyForcibly();
            process.waitFor(forceTimeoutMS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            // Ignore
        }

        return process.exitValue();
    }

    public static boolean pullImage() {
        ProcessBuilder builder = new ProcessBuilder("docker", "pull", IMAGE);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process process = null;
        int exitCode = -1;
        try {
            process = builder.start();
            process.waitFor(PULL_WAIT_MSEC, TimeUnit.MILLISECONDS);
            exitCode = process.exitValue();
        } catch (IOException | IllegalThreadStateException | InterruptedException ex) {
            return false;
        } finally {
            terminateProcess(process, 10, 10);
        }

        return (exitCode == 0);
    }

    public static boolean isAvailable() {
        if (isAvailable == null) {
            isAvailable = Boolean.valueOf(checkIsAvailable() && pullImage());
        }

        return isAvailable.booleanValue();
    }

    private static boolean checkIsAvailable() {
        ProcessBuilder builder = new ProcessBuilder("docker", "ps");
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);

        Process process = null;
        int exitCode = -1;
        try {
            process = builder.start();
            process.waitFor(100, TimeUnit.MILLISECONDS);
            exitCode = process.exitValue();
        } catch (IOException | IllegalThreadStateException | InterruptedException ex) {
            return false;
        } finally {
            terminateProcess(process, 10, 10);
        }

        return (exitCode == 0);
    }
}

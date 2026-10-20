# File Sync

A simple application/library for syncing files between two locations (local or remote) using SFTP.

The primary intention for this project is to be bundled up and put into an Android app.

## File Modes

This project explicitly deals with three file modes:
 - Regular Files
 - Directories
 - Symbolic Links

All other files are ignored.

Symbolic links are handled, but never followed,
e.g., syncing a link will always create a link with the same target (even if that target does not exist in the sync destination).

## Development

We use a standard maven setup.

Build:
```
mvn compile
```

Test:
```
mvn test
```

Create Jar:
```
mvn package
```

Testing SSH functionality requires Docker.
We will run a [small SSH server](https://github.com/linuxserver/docker-openssh-server) in Docker and preload it with any files/credentials the test needs.
If docker is not found on the local machine, the test will skip.
The docker infrastructure only works on UNIX systems.

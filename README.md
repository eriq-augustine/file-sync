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

## Setting Up SSH Users

It is STRONGLY recommended to use specialized users on your remote servers that are set up to use a chroot jail.
There are many different descriptions of this procedure available, we recommend starting with the [Arch Linux Wiki article](https://wiki.archlinux.org/title/SCP_and_SFTP#Secure_file_transfer_protocol_(SFTP)_with_a_chroot_jail).

Below is some sample code that *may* work for your setup.
Note that most these commands require root and there are several assumptions about names and locations in the above commands.

One-time setup:

```sh
# Create a group for all jailed users.
groupadd sshjail

# Add an SSH config to force all members of the jail group to only be allowed sftp.
cat << EOF >> /etc/ssh/sshd_config
Match group sshjail
  ChrootDirectory %h
  X11Forwarding no
  AllowTcpForwarding no
  PasswordAuthentication yes
  ForceCommand internal-sftp
EOF

# Restart SSH to load the new settings.
systemctl restart sshd.service
```

Example of setting up a user to transfer photos called `sshjail_photos` with a jail at `/media/jails/sshjail_photos`, may look like:

```sh
# Root must own the chroot directory.
mkdir -p /media/jails/sshjail_photos
chmod -R 755 /media/jails/sshjail_photos
chown root:root /media/jails/sshjail_photos

# Create the user.
useradd --no-create-home --home-dir /media/jails/sshjail_photos --groups sshjail sshjail_photos
passwd sshjail_photos

# Create a directory for the jailed user to use inside of the root-owned jail.
mkdir -p /media/jails/sshjail_photos/transfer
chmod -R 755 /media/jails/sshjail_photos/transfer
chown sshjail_photos:sshjail_photos /media/jails/sshjail_photos/transfer
```

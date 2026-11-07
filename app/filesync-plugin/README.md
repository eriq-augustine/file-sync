# File Sync Plugin

This plugin connects the File Sync library with the Cordova application.

## Development Workflow

# TEST - Script for building.

1. Package Library: `cd ../../lib && mvn -Dmaven.test.skip clean package`
2. Copy library jar into this project: `cp ../../lib/target/file-sync-*-uber.jar src/android/vendor/filesync-uber.jar`
3. Re-add plugin to main app: `cd ../filesync && cordova plugin remove filesync-app && cordova plugin add ../filesync-plugin`

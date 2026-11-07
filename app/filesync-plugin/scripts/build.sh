#!/bin/bash

# Build this plugin.
# This is a multi-step process which includes:
#  - Packaging the library.
#  - Copying the library JAR.
#  - Removing and re-adding this plugin to the app.
#  - Building the app.

readonly THIS_DIR="$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd | xargs realpath)"
readonly ROOT_DIR="${THIS_DIR}/.."
readonly APP_DIR="${ROOT_DIR}/../filesync"
readonly LIB_DIR="${ROOT_DIR}/../../lib"
readonly JAR_PATH="${ROOT_DIR}/src/android/vendor/filesync-uber.jar"

function main() {
    trap exit SIGINT

    cd "${ROOT_DIR}"

    cd "${LIB_DIR}"
    mvn -Dmaven.test.skip package
    if [[ $? -ne 0 ]] ; then
        echo -e "\nFailed to build library."
        return 1
    fi

    cd "${ROOT_DIR}"
    cp "${LIB_DIR}/target/file-sync-1.0.0-SNAPSHOT-uber.jar" "${JAR_PATH}"
    if [[ $? -ne 0 ]] ; then
        echo -e "\nFailed to copy library jar."
        return 2
    fi

    cd "${APP_DIR}"
    cordova plugin remove filesync-app || true  # May fail if the plugin has not already been added.
    cordova plugin add "${ROOT_DIR}"
    if [[ $? -ne 0 ]] ; then
        echo -e "\nFailed to add plugin to app."
        return 3
    fi

    cd "${APP_DIR}"
    cordova build -- --gradleArg=--stacktrace
    if [[ $? -ne 0 ]] ; then
        echo -e "\nFailed to build app."
        return 4
    fi

    echo -e "\nSuccess"
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"

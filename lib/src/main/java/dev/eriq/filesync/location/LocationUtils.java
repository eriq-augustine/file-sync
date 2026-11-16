package dev.eriq.filesync.location;

import dev.eriq.filesync.errors.BadInputError;
import dev.eriq.filesync.location.EmptyLocation;
import dev.eriq.filesync.location.LocalLocation;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.location.SSHLocation;

import java.util.Map;

public class LocationUtils {
    private LocationUtils() {}

    public static Location createLocation(String className, Map<String, Object> data) {
        if (className.equals(EmptyLocation.class.getName())) {
            return (EmptyLocation)(EmptyLocation.fromMap(data));
        } else if (className.equals(LocalLocation.class.getName())) {
            return (LocalLocation)(LocalLocation.fromMap(data));
        } else if (className.equals(SSHLocation.class.getName())) {
            return (SSHLocation)(SSHLocation.fromMap(data));
        } else {
            throw new BadInputError(String.format("Unknown location class '%s'.", className));
        }
    }
}


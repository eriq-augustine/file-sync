package dev.eriq.filesync;

import dev.eriq.filesync.Logging;
import dev.eriq.filesync.Sync;
import dev.eriq.filesync.location.Location;;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public class CLI {
    public static final Sync.ConflictBehavior DEFAULT_CONFLICT_BEHAVIOR = Sync.ConflictBehavior.SKIP;

    public static void main(String[] argv) {
        Logging.init();

        List<String> args = new ArrayList<String>();
        for (String arg : argv) {
            arg = arg.trim();
            if (arg.length() > 0) {
                args.add(arg);
            }
        }

        if ((args.size() < 2) || (args.size() > 4) || args.contains("-h") || args.contains("--help")) {
            showUsage();
        }

        Location source = Location.fromString(args.get(0));
        Location dest = Location.fromString(args.get(1));

        Sync.ConflictBehavior conflictBahavior = DEFAULT_CONFLICT_BEHAVIOR;
        if (args.size() > 2) {
            conflictBahavior = Sync.ConflictBehavior.valueOf(args.get(2));
        }

        EnumSet<Sync.IdentityCheck> identityChecks = EnumSet.noneOf(Sync.IdentityCheck.class);
        if (args.size() > 3) {
            for (String check : args.get(3).split(",")) {
                identityChecks.add(Sync.IdentityCheck.valueOf(check));
            }
        }

        Sync sync = new Sync(source, dest, identityChecks, conflictBahavior);
        sync.sync();
    }

    public static void showUsage() {
        System.out.println("USAGE: <source> <dest> [conflict-behavior [identity-checks]]");
        System.out.println("");

        System.out.println("Conflict behavior is a single value that states what to do when two files are in conflict.");
        System.out.println("Valid values are: " + List.of(Sync.ConflictBehavior.values()).stream().map(value -> value.toString()).collect(Collectors.joining(", ")));
        System.out.println("Default value: " + Sync.ConflictBehavior.SKIP);
        System.out.println("");

        System.out.println("Identity checks are a comma separated list of additional identity checks that will determine if files are in conflict.");
        System.out.println("A dirent's (file's) type and name (relative path) are always used to determine conflicts.");
        System.out.println("Valid values are: " + List.of(Sync.IdentityCheck.values()).stream().map(value -> value.toString()).collect(Collectors.joining(", ")));
        System.out.println("By default, no additional identity checks are used.");

        System.exit(1);
    }
}

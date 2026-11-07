package dev.eriq.filesync.plugin;

import dev.eriq.filesync.Logging;
import dev.eriq.filesync.Version;
import dev.eriq.filesync.location.Location;
import dev.eriq.filesync.util.JSON;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FileSyncPlugin extends CordovaPlugin {
    private static final String ACTION_VERSION = "version";
    private static final String ACTION_TEST_LOCATION = "test-location";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        // TEST
        Logging.setDebugLevel();

        switch (action) {
            case ACTION_VERSION:
                callbackContext.success(Version.get());
                return true;
            case ACTION_TEST_LOCATION:
                callbackContext.success(testLocation(args.getJSONObject(0)));
                return true;
            default:
                callbackContext.error(String.format("Unknown action: '%s'.", action));
                return false;
        }
    }

    private static String testLocation(JSONObject data) {
        Location location = null;
        try {
            location = (Location)(JSON.fromJSON(data.toString()));
        } catch (Exception ex) {
            return String.format("Failed to deserialize location: '%s'.", ex);
        }

        return location.test();
    }
}

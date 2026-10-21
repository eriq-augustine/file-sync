package dev.eriq.filesync.util;

public class StringUtils {
    public static String nullEmptyString(String text) {
        if ((text == null) || (text.length() == 0)) {
            return null;
        }

        return text;
    }
}

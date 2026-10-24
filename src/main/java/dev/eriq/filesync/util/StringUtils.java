package dev.eriq.filesync.util;

public class StringUtils {
    /**
     * Return null if the string is either null or empty,
     * otherwise return the given string.
     */
    public static String nullEmptyString(String text) {
        if ((text == null) || (text.length() == 0)) {
            return null;
        }

        return text;
    }

    /**
     * Return null if the object is null,
     * otherwise return object.toString().
     */
    public static String nullOrToString(Object object) {
        if (object == null) {
            return null;
        }

        return object.toString();
    }

    /**
     * Check if two objects are both null or equals.
     */
    public static boolean nullOrEquals(Object a, Object b) {
        if ((a == null) && (b == null)) {
            return true;
        }

        if ((a == null) || (b == null)) {
            return false;
        }

        return a.equals(b);
    }
}

package dev.eriq.filesync.util;

import dev.eriq.filesync.errors.BadInputError;
import dev.eriq.filesync.errors.NotSupportedError;

import org.json.JSONObject;
import org.json.JSONException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class JSON {
    public static final int INDENT = 4;

    public static final String CLASS_NAME_KEY = "__class__";

    /**
     * A base for classes to serialize to/from JSON.
     *
     * In additon to implementing serializeToMap(), children should implement the static fromMap() method.
     */
    public abstract static class MapConverter {
        /**
         * Get a JSON-compatible representation of this object.
         */
        public final Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<String, Object>();
            data.put(CLASS_NAME_KEY, this.getClass().getName());

            serializeToMap(data);

            return data;
        }

        /**
         * Serialize this object into the given map.
         *
         * Children should generally serialize their parents first and then thempselves.
         *
         * Strings anywhere in this map must be strings
         * and values anywhere in this map must be standard JSON values
         * (see: https://stleary.github.io/JSON-java/org/json/JSONObject.html ).
         */
        protected abstract void serializeToMap(Map<String, Object> data);

        /**
         * Create an instance of this (sub)class with a map that came from toMap().
         */
        public static Object fromMap(Map<String, Object> map) {
            throw new NotSupportedError("Child class has not implemented JSON (map) deserialization.");
        }

        public String toJSON() {
            return toJSON(false, true);
        }

        public String toJSON(boolean pretty, boolean sort) {
            Map<String, Object> map = toMap();
            if (sort) {
                map = new TreeMap(map);
            }

            JSONObject json = new JSONObject(map);

            if (pretty) {
                json.toString(INDENT);
            }

            return json.toString();
        }
    }

    public static String toJSON(MapConverter object) {
        return toJSON(object, false, true);
    }

    public static String toJSON(MapConverter object, boolean pretty, boolean sort) {
        return object.toJSON(pretty, sort);
    }

    public static Object fromJSON(String text) {
        JSONObject json = null;
        try {
            json = new JSONObject(text);
        } catch (JSONException ex) {
            throw new BadInputError("Failed to parse JSON for MapConverter.", ex);
        }

        Map<String, Object> data = json.toMap();
        return fromMap(data);
    }

    public static Object fromMap(Map<String, Object> data) {
        if (!data.containsKey(CLASS_NAME_KEY)) {
            throw new BadInputError(String.format("JSON object cannot be deserialized. It does not contain a class key: '%s'.", CLASS_NAME_KEY));
        }

        String className = data.get(CLASS_NAME_KEY).toString();
        Class serialClass = null;
        try {
            serialClass = Class.forName(className);
        } catch (ClassNotFoundException ex) {
            throw new BadInputError(String.format("Could not find class for deserialization: '%s'.", className), ex);
        }

        Method constructionMethod = null;
        try {
            constructionMethod = serialClass.getDeclaredMethod("fromMap", Map.class);
        } catch (NoSuchMethodException ex) {
            throw new BadInputError(String.format("Deserialization class '%s' does not have the static method 'fromMap'.", className), ex);
        }

        Object result = null;
        try {
            result = constructionMethod.invoke(null, data);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new BadInputError(String.format("Failed to call deserialization method '%s.fromMap()'.", className), ex);
        }

        return result;
    }

    public static String normalize(String text) {
        return normalize(text, false, true);
    }

    public static String normalize(String text, boolean pretty, boolean sort) {
        JSONObject json = new JSONObject(text);

        if (sort) {
            Map<String, Object> entries = new TreeMap<String, Object>(json.toMap());
            json = new JSONObject(json.toMap());
        }

        if (pretty) {
            json.toString(INDENT);
        }

        return json.toString();
    }
}

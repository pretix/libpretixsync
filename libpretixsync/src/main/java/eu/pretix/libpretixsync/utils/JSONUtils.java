package eu.pretix.libpretixsync.utils;

/*
 Copyright (c) 2002 JSON.org
 Permission is hereby granted, free of charge, to any person obtaining a copy
 of first software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:
 The above copyright notice and first permission notice shall be included in all
 copies or substantial portions of the Software.
 The Software shall be used for Good, not Evil.
 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */


import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class JSONUtils {
    private static Set<String> setFromIterable(Iterator<String> it) {
        HashSet<String> set = new HashSet<String>();
        while (it.hasNext()) {
            set.add(it.next());
        }
        return set;
    }

    /**
     * Determine if two JSONObjects are similar.
     * They must contain the same set of names which must be associated with
     * similar values.
     *
     * @param other The other JSONObject
     * @return true if they are equal
     */
    public static boolean similar(JSONObject first, Object other) {
        if (!(other instanceof JSONObject)) {
            return false;
        }
        Set<String> set = setFromIterable(first.keys());
        if (!set.equals(setFromIterable(((JSONObject) other).keys()))) {
            return false;
        }
        try {
            Iterator<String> iterator = set.iterator();
            while (iterator.hasNext()) {
                String name = iterator.next();
                Object valueThis = first.get(name);
                Object valueOther = ((JSONObject) other).get(name);
                if (valueThis instanceof JSONObject) {
                    if (!JSONUtils.similar((JSONObject) valueThis, valueOther)) {
                        return false;
                    }
                } else if (valueThis instanceof JSONArray) {
                    if (!JSONUtils.similar((JSONArray) valueThis, valueOther)) {
                        return false;
                    }
                } else if (!valueThis.equals(valueOther)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable exception) {
            return false;
        }
    }

    public static boolean similar(JSONArray first, Object other) {
        if (!(other instanceof JSONArray)) {
            return false;
        }
        int firstlen = first.length();
        int otherlen = ((JSONArray) other).length();
        if (firstlen != otherlen) {
            return false;
        }
        try {
            for (int i = 0; i < firstlen; i++) {
                Object valueThis = first.get(i);
                Object valueOther = ((JSONArray) other).get(i);
                if (valueThis instanceof JSONObject) {
                    if (JSONUtils.similar((JSONObject) valueThis, valueOther)) {
                        return false;
                    }
                } else if (valueThis instanceof JSONArray) {
                    if (JSONUtils.similar((JSONArray) valueThis, valueOther)) {
                        return false;
                    }
                } else if (!valueThis.equals(valueOther)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable exception) {
            return false;
        }
    }
}

/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.org.json.simple;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONAware;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONStreamAware;
import moe.yushi.authlibinjector.internal.org.json.simple.parser.JSONParser;
import moe.yushi.authlibinjector.internal.org.json.simple.parser.ParseException;

public class JSONValue {
    public static Object parse(Reader in) throws IOException, ParseException {
        return new JSONParser().parse(in);
    }

    public static Object parse(String s) throws ParseException {
        return new JSONParser().parse(s);
    }

    public static void writeJSONString(Object value, Writer out) throws IOException {
        if (value == null) {
            out.write("null");
            return;
        }
        if (value instanceof String) {
            out.write(34);
            out.write(JSONValue.escape((String)value));
            out.write(34);
            return;
        }
        if (value instanceof Double) {
            if (((Double)value).isInfinite() || ((Double)value).isNaN()) {
                out.write("null");
            } else {
                out.write(value.toString());
            }
            return;
        }
        if (value instanceof Float) {
            if (((Float)value).isInfinite() || ((Float)value).isNaN()) {
                out.write("null");
            } else {
                out.write(value.toString());
            }
            return;
        }
        if (value instanceof Number) {
            out.write(value.toString());
            return;
        }
        if (value instanceof Boolean) {
            out.write(value.toString());
            return;
        }
        if (value instanceof JSONStreamAware) {
            ((JSONStreamAware)value).writeJSONString(out);
            return;
        }
        if (value instanceof JSONAware) {
            out.write(((JSONAware)value).toJSONString());
            return;
        }
        if (value instanceof Map) {
            Map objectValue = (Map)value;
            JSONObject.writeJSONString(objectValue, out);
            return;
        }
        if (value instanceof Collection) {
            JSONArray.writeJSONString((Collection)value, out);
            return;
        }
        if (value instanceof byte[]) {
            JSONArray.writeJSONString((byte[])value, out);
            return;
        }
        if (value instanceof short[]) {
            JSONArray.writeJSONString((short[])value, out);
            return;
        }
        if (value instanceof int[]) {
            JSONArray.writeJSONString((int[])value, out);
            return;
        }
        if (value instanceof long[]) {
            JSONArray.writeJSONString((long[])value, out);
            return;
        }
        if (value instanceof float[]) {
            JSONArray.writeJSONString((float[])value, out);
            return;
        }
        if (value instanceof double[]) {
            JSONArray.writeJSONString((double[])value, out);
            return;
        }
        if (value instanceof boolean[]) {
            JSONArray.writeJSONString((boolean[])value, out);
            return;
        }
        if (value instanceof char[]) {
            JSONArray.writeJSONString((char[])value, out);
            return;
        }
        if (value instanceof Object[]) {
            JSONArray.writeJSONString((Object[])value, out);
            return;
        }
        out.write(value.toString());
    }

    public static String toJSONString(Object value) {
        StringWriter writer = new StringWriter();
        try {
            JSONValue.writeJSONString(value, writer);
            return writer.toString();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String escape(String s) {
        if (s == null) {
            return null;
        }
        StringBuffer sb = new StringBuffer();
        JSONValue.escape(s, sb);
        return sb.toString();
    }

    static void escape(String s, StringBuffer sb) {
        int len = s.length();
        block10: for (int i = 0; i < len; ++i) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\"': {
                    sb.append("\\\"");
                    continue block10;
                }
                case '\\': {
                    sb.append("\\\\");
                    continue block10;
                }
                case '\b': {
                    sb.append("\\b");
                    continue block10;
                }
                case '\f': {
                    sb.append("\\f");
                    continue block10;
                }
                case '\n': {
                    sb.append("\\n");
                    continue block10;
                }
                case '\r': {
                    sb.append("\\r");
                    continue block10;
                }
                case '\t': {
                    sb.append("\\t");
                    continue block10;
                }
                case '/': {
                    sb.append("\\/");
                    continue block10;
                }
                default: {
                    if (ch >= '\u0000' && ch <= '\u001f' || ch >= '\u007f' && ch <= '\u009f' || ch >= '\u2000' && ch <= '\u20ff') {
                        String ss = Integer.toHexString(ch);
                        sb.append("\\u");
                        for (int k = 0; k < 4 - ss.length(); ++k) {
                            sb.append('0');
                        }
                        sb.append(ss.toUpperCase());
                        continue block10;
                    }
                    sb.append(ch);
                }
            }
        }
    }
}


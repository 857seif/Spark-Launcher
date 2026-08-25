/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.org.json.simple;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONAware;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONStreamAware;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONValue;

public class JSONObject
extends LinkedHashMap<String, Object>
implements JSONAware,
JSONStreamAware {
    public JSONObject() {
    }

    public JSONObject(Map<String, ?> map) {
        super(map);
    }

    public static void writeJSONString(Map<String, ?> map, Writer out) throws IOException {
        if (map == null) {
            out.write("null");
            return;
        }
        boolean first = true;
        Iterator<Map.Entry<String, ?>> iter = map.entrySet().iterator();
        out.write(123);
        while (iter.hasNext()) {
            if (first) {
                first = false;
            } else {
                out.write(44);
            }
            Map.Entry<String, ?> entry = iter.next();
            out.write(34);
            out.write(JSONValue.escape(entry.getKey()));
            out.write(34);
            out.write(58);
            JSONValue.writeJSONString(entry.getValue(), out);
        }
        out.write(125);
    }

    @Override
    public void writeJSONString(Writer out) throws IOException {
        JSONObject.writeJSONString(this, out);
    }

    public static String toJSONString(Map<String, ?> map) {
        StringWriter writer = new StringWriter();
        try {
            JSONObject.writeJSONString(map, writer);
            return writer.toString();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toJSONString() {
        return JSONObject.toJSONString(this);
    }

    @Override
    public String toString() {
        return this.toJSONString();
    }

    public static String toString(String key, Object value) {
        StringBuffer sb = new StringBuffer();
        sb.append('\"');
        if (key == null) {
            sb.append("null");
        } else {
            JSONValue.escape(key, sb);
        }
        sb.append('\"').append(':');
        sb.append(JSONValue.toJSONString(value));
        return sb.toString();
    }
}


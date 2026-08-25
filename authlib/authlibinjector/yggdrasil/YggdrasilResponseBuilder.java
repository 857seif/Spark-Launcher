/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.yggdrasil;

import java.util.Map;
import java.util.UUID;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.UUIDUtils;
import moe.yushi.authlibinjector.yggdrasil.GameProfile;

public final class YggdrasilResponseBuilder {
    private YggdrasilResponseBuilder() {
    }

    public static String queryUUIDs(Map<String, UUID> result) {
        JSONArray response = new JSONArray();
        result.forEach((name, uuid) -> {
            JSONObject entry = new JSONObject();
            entry.put("id", UUIDUtils.toUnsignedUUID(uuid));
            entry.put("name", name);
            response.add(entry);
        });
        return response.toJSONString();
    }

    public static String queryProfile(GameProfile profile, boolean withSignature) {
        JSONObject response = new JSONObject();
        response.put("id", UUIDUtils.toUnsignedUUID(profile.id));
        response.put("name", profile.name);
        JSONArray properties = new JSONArray();
        profile.properties.forEach((name, value) -> {
            JSONObject entry = new JSONObject();
            entry.put("name", name);
            entry.put("value", value.value);
            if (withSignature && value.signature != null) {
                entry.put("signature", value.signature);
            }
            properties.add(entry);
        });
        response.put("properties", properties);
        return response.toJSONString();
    }
}


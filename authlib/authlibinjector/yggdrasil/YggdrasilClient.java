/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.yggdrasil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONArray;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.IOUtils;
import moe.yushi.authlibinjector.util.JsonUtils;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.util.UUIDUtils;
import moe.yushi.authlibinjector.yggdrasil.GameProfile;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilAPIProvider;

public class YggdrasilClient {
    private YggdrasilAPIProvider apiProvider;
    private Proxy proxy;

    public YggdrasilClient(YggdrasilAPIProvider apiProvider) {
        this(apiProvider, null);
    }

    public YggdrasilClient(YggdrasilAPIProvider apiProvider, Proxy proxy) {
        this.apiProvider = apiProvider;
        this.proxy = proxy;
    }

    public Map<String, UUID> queryUUIDs(Set<String> names) throws UncheckedIOException {
        String responseText;
        try {
            responseText = IOUtils.asString(IOUtils.http("POST", this.apiProvider.queryUUIDsByNames(), JSONArray.toJSONString(names).getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8", this.proxy));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Logging.log(Logging.Level.DEBUG, "Query UUIDs of " + names + " at [" + this.apiProvider + "], response: " + responseText);
        LinkedHashMap<String, UUID> result = new LinkedHashMap<String, UUID>();
        for (Object rawProfile : JsonUtils.asJsonArray(JsonUtils.parseJson(responseText))) {
            JSONObject profile = JsonUtils.asJsonObject(rawProfile);
            result.put(JsonUtils.asJsonString(profile.get("name")), this.parseUnsignedUUID(JsonUtils.asJsonString(profile.get("id"))));
        }
        return result;
    }

    public Optional<UUID> queryUUID(String name) throws UncheckedIOException {
        return Optional.ofNullable(this.queryUUIDs(Collections.singleton(name)).get(name));
    }

    public Optional<GameProfile> queryProfile(UUID uuid, boolean withSignature) throws UncheckedIOException {
        String responseText;
        String url = this.apiProvider.queryProfile(uuid);
        if (withSignature) {
            url = url + "?unsigned=false";
        }
        try {
            responseText = IOUtils.asString(IOUtils.http("GET", url, this.proxy));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (responseText.isEmpty()) {
            Logging.log(Logging.Level.DEBUG, "Query profile of [" + uuid + "] at [" + this.apiProvider + "], not found");
            return Optional.empty();
        }
        Logging.log(Logging.Level.DEBUG, "Query profile of [" + uuid + "] at [" + this.apiProvider + "], response: " + responseText);
        return Optional.of(this.parseGameProfile(JsonUtils.asJsonObject(JsonUtils.parseJson(responseText))));
    }

    private GameProfile parseGameProfile(JSONObject json) {
        GameProfile profile = new GameProfile();
        profile.id = this.parseUnsignedUUID(JsonUtils.asJsonString(json.get("id")));
        profile.name = JsonUtils.asJsonString(json.get("name"));
        profile.properties = new LinkedHashMap<String, GameProfile.PropertyValue>();
        for (Object rawProperty : JsonUtils.asJsonArray(json.get("properties"))) {
            JSONObject property = (JSONObject)rawProperty;
            GameProfile.PropertyValue entry = new GameProfile.PropertyValue();
            entry.value = JsonUtils.asJsonString(property.get("value"));
            if (property.containsKey("signature")) {
                entry.signature = JsonUtils.asJsonString(property.get("signature"));
            }
            profile.properties.put(JsonUtils.asJsonString(property.get("name")), entry);
        }
        return profile;
    }

    private UUID parseUnsignedUUID(String uuid) throws UncheckedIOException {
        try {
            return UUIDUtils.fromUnsignedUUID(uuid);
        }
        catch (IllegalArgumentException e) {
            throw IOUtils.newUncheckedIOException(e.getMessage());
        }
    }
}


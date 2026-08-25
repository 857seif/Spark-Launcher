/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector;

import java.io.UncheckedIOException;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.JsonUtils;
import moe.yushi.authlibinjector.util.KeyUtils;

public class APIMetadata {
    private String apiRoot;
    private List<String> skinDomains;
    private Optional<PublicKey> decodedPublickey;
    private Map<String, Object> meta;

    public static APIMetadata parse(String apiRoot, String metadataResponse) throws UncheckedIOException {
        JSONObject response = JsonUtils.asJsonObject(JsonUtils.parseJson(metadataResponse));
        List skinDomains = Optional.ofNullable(response.get("skinDomains")).map(it -> JsonUtils.asJsonArray(it).stream().map(JsonUtils::asJsonString).collect(Collectors.toList())).orElse(Collections.emptyList());
        Optional<PublicKey> decodedPublickey = Optional.ofNullable(response.get("signaturePublickey")).map(JsonUtils::asJsonString).map(KeyUtils::parseSignaturePublicKey);
        Map meta = Optional.ofNullable(response.get("meta")).map(it -> new TreeMap<String, Object>(JsonUtils.asJsonObject(it))).orElse(Collections.emptyMap());
        return new APIMetadata(apiRoot, Collections.unmodifiableList(skinDomains), Collections.unmodifiableMap(meta), decodedPublickey);
    }

    public APIMetadata(String apiRoot, List<String> skinDomains, Map<String, Object> meta, Optional<PublicKey> decodedPublickey) {
        this.apiRoot = Objects.requireNonNull(apiRoot);
        this.skinDomains = Objects.requireNonNull(skinDomains);
        this.meta = Objects.requireNonNull(meta);
        this.decodedPublickey = Objects.requireNonNull(decodedPublickey);
    }

    public String getApiRoot() {
        return this.apiRoot;
    }

    public List<String> getSkinDomains() {
        return this.skinDomains;
    }

    public Map<String, Object> getMeta() {
        return this.meta;
    }

    public Optional<PublicKey> getDecodedPublickey() {
        return this.decodedPublickey;
    }

    public String toString() {
        return MessageFormat.format("APIMetadata [apiRoot={0}, skinDomains={1}, decodedPublickey={2}, meta={3}]", this.apiRoot, this.skinDomains, this.decodedPublickey, this.meta);
    }
}


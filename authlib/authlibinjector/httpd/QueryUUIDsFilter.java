
package moe.yushi.authlibinjector.httpd;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import moe.yushi.authlibinjector.httpd.URLFilter;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.util.IOUtils;
import moe.yushi.authlibinjector.util.JsonUtils;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilClient;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilResponseBuilder;

public class QueryUUIDsFilter
implements URLFilter {
    private YggdrasilClient mojangClient;
    private YggdrasilClient customClient;
    private static final int MSB_MASK = 32768;
    static final String NAME_SUFFIX = "@mojang";

    public QueryUUIDsFilter(YggdrasilClient mojangClient, YggdrasilClient customClient) {
        this.mojangClient = mojangClient;
        this.customClient = customClient;
    }

    @Override
    public boolean canHandle(String domain) {
        return domain.equals("api.mojang.com");
    }

    @Override
    public Optional<Response> handle(String domain, String path, IHTTPSession session) throws IOException {
        if (domain.equals("api.mojang.com") && path.equals("/profiles/minecraft") && session.getMethod().equals("POST")) {
            LinkedHashSet<String> request = new LinkedHashSet<String>();
            JsonUtils.asJsonArray(JsonUtils.parseJson(IOUtils.asString(IOUtils.asBytes(session.getInputStream())))).forEach(element -> request.add(JsonUtils.asJsonString(element)));
            return Optional.of(Response.newFixedLength(Status.OK, "application/json; charset=utf-8", YggdrasilResponseBuilder.queryUUIDs(this.performQuery(request))));
        }
        return Optional.empty();
    }

    private Map<String, UUID> performQuery(Set<String> names) {
        LinkedHashSet<String> customNames = new LinkedHashSet<String>();
        LinkedHashSet<String> mojangNames = new LinkedHashSet<String>();
        names.forEach(name -> {
            if (name.endsWith(NAME_SUFFIX)) {
                mojangNames.add(name.substring(0, name.length() - NAME_SUFFIX.length()));
            } else {
                customNames.add((String)name);
            }
        });
        LinkedHashMap<String, UUID> result = new LinkedHashMap<String, UUID>();
        if (!customNames.isEmpty()) {
            result.putAll(this.customClient.queryUUIDs(customNames));
        }
        if (!mojangNames.isEmpty()) {
            this.mojangClient.queryUUIDs(mojangNames).forEach((name, uuid) -> result.put(name + NAME_SUFFIX, QueryUUIDsFilter.maskUUID(uuid)));
        }
        return result;
    }

    static UUID maskUUID(UUID uuid) {
        if (QueryUUIDsFilter.isMaskedUUID(uuid)) {
            Logging.log(Logging.Level.WARNING, "UUID already masked: " + uuid);
        }
        return new UUID(uuid.getMostSignificantBits() | 0x8000L, uuid.getLeastSignificantBits());
    }

    static boolean isMaskedUUID(UUID uuid) {
        return (uuid.getMostSignificantBits() & 0x8000L) != 0L;
    }

    static UUID unmaskUUID(UUID uuid) {
        return new UUID(uuid.getMostSignificantBits() & 0xFFFFFFFFFFFF7FFFL, uuid.getLeastSignificantBits());
    }
}


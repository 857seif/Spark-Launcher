
package moe.yushi.authlibinjector.httpd;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import moe.yushi.authlibinjector.httpd.QueryUUIDsFilter;
import moe.yushi.authlibinjector.httpd.URLFilter;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.util.UUIDUtils;
import moe.yushi.authlibinjector.yggdrasil.GameProfile;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilClient;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilResponseBuilder;

public class QueryProfileFilter
implements URLFilter {
    private static final Pattern PATH_REGEX = Pattern.compile("^/session/minecraft/profile/(?<uuid>[0-9a-f]{32})$");
    private YggdrasilClient mojangClient;
    private YggdrasilClient customClient;

    public QueryProfileFilter(YggdrasilClient mojangClient, YggdrasilClient customClient) {
        this.mojangClient = mojangClient;
        this.customClient = customClient;
    }

    @Override
    public boolean canHandle(String domain) {
        return domain.equals("sessionserver.mojang.com");
    }

    @Override
    public Optional<Response> handle(String domain, String path, IHTTPSession session) throws IOException {
        Optional<GameProfile> response;
        UUID uuid;
        if (!domain.equals("sessionserver.mojang.com")) {
            return Optional.empty();
        }
        Matcher matcher = PATH_REGEX.matcher(path);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            uuid = UUIDUtils.fromUnsignedUUID(matcher.group("uuid"));
        }
        catch (IllegalArgumentException e) {
            return Optional.of(Response.newFixedLength(Status.NO_CONTENT, null, null));
        }
        boolean withSignature = false;
        List<String> unsignedValues = session.getParameters().get("unsigned");
        if (unsignedValues != null && unsignedValues.get(0).equals("false")) {
            withSignature = true;
        }
        if (QueryUUIDsFilter.isMaskedUUID(uuid)) {
            response = this.mojangClient.queryProfile(QueryUUIDsFilter.unmaskUUID(uuid), withSignature);
            response.ifPresent(profile -> {
                profile.id = uuid;
                profile.name = profile.name + "@mojang";
            });
        } else {
            response = this.customClient.queryProfile(uuid, withSignature);
        }
        if (response.isPresent()) {
            return Optional.of(Response.newFixedLength(Status.OK, null, YggdrasilResponseBuilder.queryProfile(response.get(), withSignature)));
        }
        return Optional.of(Response.newFixedLength(Status.NO_CONTENT, null, null));
    }
}


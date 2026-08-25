
package moe.yushi.authlibinjector.httpd;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import moe.yushi.authlibinjector.httpd.URLFilter;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;
import moe.yushi.authlibinjector.util.IOUtils;
import moe.yushi.authlibinjector.util.JsonUtils;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilClient;

public class LegacySkinAPIFilter
implements URLFilter {
    private static final Pattern PATH_SKINS = Pattern.compile("^/MinecraftSkins/(?<username>[^/]+)\\.png$");
    private YggdrasilClient upstream;

    public LegacySkinAPIFilter(YggdrasilClient upstream) {
        this.upstream = upstream;
    }

    @Override
    public boolean canHandle(String domain) {
        return domain.equals("skins.minecraft.net");
    }

    @Override
    public Optional<Response> handle(String domain, String path, IHTTPSession session) {
        Optional skinUrl;
        if (!domain.equals("skins.minecraft.net")) {
            return Optional.empty();
        }
        Matcher matcher = PATH_SKINS.matcher(path);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String username = matcher.group("username");
        username = LegacySkinAPIFilter.correctEncoding(username);
        try {
            skinUrl = this.upstream.queryUUID(username).flatMap(uuid -> this.upstream.queryProfile((UUID)uuid, false)).flatMap(profile -> Optional.ofNullable(profile.properties.get("textures"))).map(property -> IOUtils.asString(Base64.getDecoder().decode(property.value))).flatMap(texturesPayload -> this.obtainTextureUrl((String)texturesPayload, "SKIN"));
        }
        catch (UncheckedIOException e) {
            throw IOUtils.newUncheckedIOException("Failed to fetch skin metadata for " + username, e);
        }
        if (skinUrl.isPresent()) {
            byte[] data;
            String url = (String)skinUrl.get();
            Logging.log(Logging.Level.DEBUG, "Retrieving skin for " + username + " from " + url);
            try {
                data = IOUtils.http("GET", url);
            }
            catch (IOException e) {
                throw IOUtils.newUncheckedIOException("Failed to retrieve skin from " + url, e);
            }
            Logging.log(Logging.Level.INFO, "Retrieved skin for " + username + " from " + url + ", " + data.length + " bytes");
            return Optional.of(Response.newFixedLength(Status.OK, "image/png", new ByteArrayInputStream(data), data.length));
        }
        Logging.log(Logging.Level.INFO, "No skin is found for " + username);
        return Optional.of(Response.newFixedLength(Status.NOT_FOUND, null, null));
    }

    private Optional<String> obtainTextureUrl(String texturesPayload, String textureType) throws UncheckedIOException {
        JSONObject payload = JsonUtils.asJsonObject(JsonUtils.parseJson(texturesPayload));
        JSONObject textures = JsonUtils.asJsonObject(payload.get("textures"));
        return Optional.ofNullable(textures.get(textureType)).map(JsonUtils::asJsonObject).map(it -> Optional.ofNullable(it.get("url")).map(JsonUtils::asJsonString).orElseThrow(() -> IOUtils.newUncheckedIOException("Invalid JSON: Missing texture url")));
    }

    private static String correctEncoding(String grable) {
        return new String(grable.getBytes(StandardCharsets.ISO_8859_1));
    }
}


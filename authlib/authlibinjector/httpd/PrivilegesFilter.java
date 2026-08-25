/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.httpd;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import moe.yushi.authlibinjector.httpd.URLFilter;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.internal.org.json.simple.JSONObject;

public class PrivilegesFilter
implements URLFilter {
    private final Map<String, Boolean> privileges = new LinkedHashMap<String, Boolean>();

    public PrivilegesFilter() {
        this.privileges.put("onlineChat", true);
        this.privileges.put("multiplayerServer", true);
        this.privileges.put("multiplayerRealms", true);
        this.privileges.put("telemetry", false);
    }

    @Override
    public boolean canHandle(String domain) {
        return domain.equals("api.minecraftservices.com");
    }

    @Override
    public Optional<Response> handle(String domain, String path, IHTTPSession session) throws IOException {
        if (domain.equals("api.minecraftservices.com") && path.equals("/privileges") && session.getMethod().equals("GET")) {
            JSONObject response = new JSONObject();
            JSONObject privilegesJson = new JSONObject();
            this.privileges.forEach((name, enabled) -> {
                JSONObject privilegeJson = new JSONObject();
                privilegeJson.put("enabled", enabled);
                privilegesJson.put(name, privilegeJson);
            });
            response.put("privileges", privilegesJson);
            return Optional.of(Response.newFixedLength(Status.OK, "application/json; charset=utf-8", response.toJSONString()));
        }
        return Optional.empty();
    }
}


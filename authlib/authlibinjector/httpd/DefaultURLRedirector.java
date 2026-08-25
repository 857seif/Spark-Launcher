/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.httpd;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import moe.yushi.authlibinjector.APIMetadata;
import moe.yushi.authlibinjector.httpd.URLRedirector;

public class DefaultURLRedirector
implements URLRedirector {
    private Map<String, String> domainMapping = new HashMap<String, String>();
    private String apiRoot;

    public DefaultURLRedirector(APIMetadata config) {
        this.initDomainMapping();
        this.apiRoot = config.getApiRoot();
    }

    private void initDomainMapping() {
        this.domainMapping.put("api.mojang.com", "api");
        this.domainMapping.put("authserver.mojang.com", "authserver");
        this.domainMapping.put("sessionserver.mojang.com", "sessionserver");
        this.domainMapping.put("skins.minecraft.net", "skins");
        this.domainMapping.put("api.minecraftservices.com", "minecraftservices");
    }

    @Override
    public Optional<String> redirect(String domain, String path) {
        String subdirectory = this.domainMapping.get(domain);
        if (subdirectory == null) {
            return Optional.empty();
        }
        return Optional.of(this.apiRoot + subdirectory + path);
    }
}


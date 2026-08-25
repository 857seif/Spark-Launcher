
package moe.yushi.authlibinjector.yggdrasil;

import java.util.UUID;
import moe.yushi.authlibinjector.APIMetadata;
import moe.yushi.authlibinjector.util.UUIDUtils;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilAPIProvider;

public class CustomYggdrasilAPIProvider
implements YggdrasilAPIProvider {
    private String apiRoot;

    public CustomYggdrasilAPIProvider(APIMetadata configuration) {
        this.apiRoot = configuration.getApiRoot();
    }

    @Override
    public String queryUUIDsByNames() {
        return this.apiRoot + "api/profiles/minecraft";
    }

    @Override
    public String queryProfile(UUID uuid) {
        return this.apiRoot + "sessionserver/session/minecraft/profile/" + UUIDUtils.toUnsignedUUID(uuid);
    }

    public String toString() {
        return this.apiRoot;
    }
}


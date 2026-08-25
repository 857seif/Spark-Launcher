
package moe.yushi.authlibinjector.yggdrasil;

import java.util.UUID;
import moe.yushi.authlibinjector.util.UUIDUtils;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilAPIProvider;

public class MojangYggdrasilAPIProvider
implements YggdrasilAPIProvider {
    @Override
    public String queryUUIDsByNames() {
        return "https://api.mojang.com/profiles/minecraft";
    }

    @Override
    public String queryProfile(UUID uuid) {
        return "https://sessionserver.mojang.com/session/minecraft/profile/" + UUIDUtils.toUnsignedUUID(uuid);
    }

    public String toString() {
        return "Mojang";
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.yggdrasil;

import java.util.UUID;

public interface YggdrasilAPIProvider {
    public String queryUUIDsByNames();

    public String queryProfile(UUID var1);
}


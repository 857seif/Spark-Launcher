/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.transform;

import java.util.List;
import moe.yushi.authlibinjector.transform.TransformUnit;

public interface ClassLoadingListener {
    public void onClassLoading(ClassLoader var1, String var2, byte[] var3, List<TransformUnit> var4);
}



package moe.yushi.authlibinjector.transform;

import moe.yushi.authlibinjector.internal.org.objectweb.asm.Handle;

public interface TransformContext {
    public void markModified();

    public void requireMinimumClassVersion(int var1);

    public void upgradeClassVersion(int var1);

    public Handle acquireCallbackMetafactory();
}


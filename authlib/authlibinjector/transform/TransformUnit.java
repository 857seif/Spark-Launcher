
package moe.yushi.authlibinjector.transform;

import java.util.Optional;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.transform.TransformContext;

public interface TransformUnit {
    public Optional<ClassVisitor> transform(ClassLoader var1, String var2, ClassVisitor var3, TransformContext var4);
}



package moe.yushi.authlibinjector.transform;

import java.util.Optional;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.transform.ClassVersionException;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.util.Logging;

class ClassVersionTransformUnit
implements TransformUnit {
    private final int minVersion;
    private final int upgradedVersion;

    public ClassVersionTransformUnit(int minVersion, int upgradedVersion) {
        this.minVersion = minVersion;
        this.upgradedVersion = upgradedVersion;
    }

    @Override
    public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, final TransformContext context) {
        return Optional.of(new ClassVisitor(589824, writer){

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                int major = version & 0xFFFF;
                if (ClassVersionTransformUnit.this.minVersion != -1 && major < ClassVersionTransformUnit.this.minVersion) {
                    throw new ClassVersionException("class version (" + major + ") is lower than required(" + ClassVersionTransformUnit.this.minVersion + ")");
                }
                if (ClassVersionTransformUnit.this.upgradedVersion != -1 && major < ClassVersionTransformUnit.this.upgradedVersion) {
                    Logging.log(Logging.Level.DEBUG, "Upgrading class version from " + major + " to " + ClassVersionTransformUnit.this.upgradedVersion);
                    version = ClassVersionTransformUnit.this.upgradedVersion;
                    context.markModified();
                }
                super.visit(version, access, name, signature, superName, interfaces);
            }
        });
    }

    public String toString() {
        return "Class File Version Transformer";
    }
}


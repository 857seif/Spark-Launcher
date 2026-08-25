
package moe.yushi.authlibinjector.transform.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import moe.yushi.authlibinjector.AuthlibInjector;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.transform.support.MainArgumentsTransformer;
import moe.yushi.authlibinjector.util.Logging;

public class MC52974Workaround
implements TransformUnit {
    private static final Set<String> AFFECTED_VERSION_SERIES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("1.7.10", "1.8", "1.9", "1.10", "1.11", "1.12")));

    private MC52974Workaround() {
    }

    public static void init() {
        MainArgumentsTransformer.getVersionSeriesListeners().add(version -> {
            if (AFFECTED_VERSION_SERIES.contains(version)) {
                Logging.log(Logging.Level.INFO, "Enable MC-52974 Workaround");
                AuthlibInjector.getClassTransformer().units.add(new MC52974Workaround());
                AuthlibInjector.retransformClasses("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService");
            }
        });
    }

    @Override
    public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, final TransformContext ctx) {
        if ("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService".equals(className)) {
            return Optional.of(new ClassVisitor(589824, writer){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("fillGameProfile".equals(name) && "(Lcom/mojang/authlib/GameProfile;Z)Lcom/mojang/authlib/GameProfile;".equals(descriptor)) {
                        return new MethodVisitor(589824, super.visitMethod(access, name, descriptor, signature, exceptions)){

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                ctx.markModified();
                                super.visitLdcInsn(1);
                                super.visitVarInsn(54, 2);
                            }
                        };
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            });
        }
        return Optional.empty();
    }

    public String toString() {
        return "MC-52974 Workaround";
    }
}


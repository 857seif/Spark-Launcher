/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.transform.support;

import java.lang.invoke.MethodHandle;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.Handle;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.transform.CallbackSupport;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;

public class YggdrasilKeyTransformUnit
implements TransformUnit {
    public static final List<PublicKey> PUBLIC_KEYS = new CopyOnWriteArrayList<PublicKey>();

    @CallbackMethod
    public static boolean verifyPropertySignature(Object property, PublicKey mojangKey, MethodHandle verifyAction) throws Throwable {
        if (verifyAction.invoke(property, mojangKey)) {
            return true;
        }
        for (PublicKey customKey : PUBLIC_KEYS) {
            if (!verifyAction.invoke(property, customKey)) continue;
            return true;
        }
        return false;
    }

    @Override
    public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, final TransformContext ctx) {
        if ("com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService".equals(className)) {
            return Optional.of(new ClassVisitor(589824, writer){

                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    return new MethodVisitor(589824, super.visitMethod(access, name, desc, signature, exceptions)){

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (opcode == 182 && "com/mojang/authlib/properties/Property".equals(owner) && "isSignatureValid".equals(name) && "(Ljava/security/PublicKey;)Z".equals(descriptor)) {
                                ctx.markModified();
                                super.visitLdcInsn(new Handle(5, owner, name, descriptor, isInterface));
                                CallbackSupport.invoke(ctx, this, YggdrasilKeyTransformUnit.class, "verifyPropertySignature");
                            } else {
                                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                            }
                        }
                    };
                }
            });
        }
        return Optional.empty();
    }

    public String toString() {
        return "Yggdrasil Public Key Transformer";
    }
}


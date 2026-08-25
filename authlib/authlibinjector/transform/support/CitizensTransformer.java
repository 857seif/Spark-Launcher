/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.transform.support;

import java.util.Optional;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.Label;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;

public class CitizensTransformer
implements TransformUnit {
    @Override
    public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, final TransformContext ctx) {
        if ("net.citizensnpcs.Settings$Setting".equals(className)) {
            return Optional.of(new ClassVisitor(589824, writer){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (("loadFromKey".equals(name) || "setAtKey".equals(name)) && "(Lnet/citizensnpcs/api/util/DataKey;)V".equals(descriptor)) {
                        return new MethodVisitor(589824, super.visitMethod(access, name, descriptor, signature, exceptions)){

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitLdcInsn("general.authlib.profile-url");
                                super.visitVarInsn(25, 0);
                                super.visitFieldInsn(180, "net/citizensnpcs/Settings$Setting", "path", "Ljava/lang/String;");
                                super.visitMethodInsn(182, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false);
                                Label lbl = new Label();
                                super.visitJumpInsn(153, lbl);
                                super.visitInsn(177);
                                super.visitLabel(lbl);
                                super.visitFrame(3, 0, null, 0, null);
                                ctx.markModified();
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
        return "Citizens2 Support";
    }
}


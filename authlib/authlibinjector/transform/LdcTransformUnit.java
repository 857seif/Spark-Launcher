/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.transform;

import java.util.Optional;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.Handle;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;

public abstract class LdcTransformUnit
implements TransformUnit {
    @Override
    public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, final TransformContext ctx) {
        return Optional.of(new ClassVisitor(589824, writer){

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(589824, super.visitMethod(access, name, desc, signature, exceptions)){

                    @Override
                    public void visitLdcInsn(Object cst) {
                        if (cst instanceof String) {
                            Optional<String> transformed = LdcTransformUnit.this.transformLdc((String)cst);
                            if (transformed.isPresent() && !transformed.get().equals(cst)) {
                                ctx.markModified();
                                super.visitLdcInsn(transformed.get());
                            } else {
                                super.visitLdcInsn(cst);
                            }
                        } else {
                            super.visitLdcInsn(cst);
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object ... bootstrapMethodArguments) {
                        for (int i = 0; i < bootstrapMethodArguments.length; ++i) {
                            String constant;
                            Optional<String> transformed;
                            if (!(bootstrapMethodArguments[i] instanceof String) || !(transformed = LdcTransformUnit.this.transformLdc(constant = (String)bootstrapMethodArguments[i])).isPresent() || transformed.get().equals(constant)) continue;
                            ctx.markModified();
                            bootstrapMethodArguments[i] = transformed.get();
                        }
                        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                    }
                };
            }
        });
    }

    protected abstract Optional<String> transformLdc(String var1);
}


/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.transform.support;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.transform.CallbackSupport;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;

public class ConcatenateURLTransformUnit
implements TransformUnit {
    @CallbackMethod
    public static URL concatenateURL(URL url, String query) {
        try {
            if (url.getQuery() != null && url.getQuery().length() > 0) {
                return new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile() + "&" + query);
            }
            return new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile() + "?" + query);
        }
        catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Could not concatenate given URL with GET arguments!", ex);
        }
    }

    @Override
    public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, final TransformContext ctx) {
        if ("com.mojang.authlib.HttpAuthenticationService".equals(className)) {
            return Optional.of(new ClassVisitor(589824, writer){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("concatenateURL".equals(name) && "(Ljava/net/URL;Ljava/lang/String;)Ljava/net/URL;".equals(descriptor)) {
                        ctx.markModified();
                        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                        mv.visitCode();
                        mv.visitVarInsn(25, 0);
                        mv.visitVarInsn(25, 1);
                        CallbackSupport.invoke(ctx, mv, ConcatenateURLTransformUnit.class, "concatenateURL");
                        mv.visitInsn(176);
                        mv.visitMaxs(-1, -1);
                        mv.visitEnd();
                        return null;
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            });
        }
        return Optional.empty();
    }

    public String toString() {
        return "ConcatenateURL Workaround";
    }
}


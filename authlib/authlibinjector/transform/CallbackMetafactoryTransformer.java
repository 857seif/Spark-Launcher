
package moe.yushi.authlibinjector.transform;

import java.util.Optional;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;

class CallbackMetafactoryTransformer
implements TransformUnit {
    CallbackMetafactoryTransformer() {
    }

    @Override
    public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, final TransformContext context) {
        return Optional.of(new ClassVisitor(589824, writer){

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);
                MethodVisitor mv = super.visitMethod(4106, "__authlibinjector_metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;", null, null);
                mv.visitCode();
                mv.visitTypeInsn(187, "java/lang/invoke/ConstantCallSite");
                mv.visitInsn(89);
                mv.visitVarInsn(25, 0);
                mv.visitMethodInsn(184, "java/lang/ClassLoader", "getSystemClassLoader", "()Ljava/lang/ClassLoader;", false);
                mv.visitVarInsn(25, 3);
                mv.visitMethodInsn(182, "java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", false);
                mv.visitVarInsn(25, 1);
                mv.visitVarInsn(25, 2);
                mv.visitMethodInsn(182, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;", false);
                mv.visitMethodInsn(183, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V", false);
                mv.visitInsn(176);
                mv.visitMaxs(-1, -1);
                mv.visitEnd();
                context.markModified();
            }
        });
    }

    public String toString() {
        return "Callback Metafactory Transformer";
    }
}


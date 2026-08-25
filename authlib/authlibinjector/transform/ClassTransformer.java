/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.transform;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import moe.yushi.authlibinjector.Config;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassReader;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassWriter;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.Handle;
import moe.yushi.authlibinjector.transform.CallbackMetafactoryTransformer;
import moe.yushi.authlibinjector.transform.ClassLoadingListener;
import moe.yushi.authlibinjector.transform.ClassVersionException;
import moe.yushi.authlibinjector.transform.ClassVersionTransformUnit;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.util.Logging;

public class ClassTransformer
implements ClassFileTransformer {
    public final List<TransformUnit> units = new CopyOnWriteArrayList<TransformUnit>();
    public final List<ClassLoadingListener> listeners = new CopyOnWriteArrayList<ClassLoadingListener>();
    public final Set<String> ignores = Collections.newSetFromMap(new ConcurrentHashMap());

    @Override
    public byte[] transform(ClassLoader loader, String internalClassName, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (internalClassName != null && classfileBuffer != null) {
            try {
                String className = internalClassName.replace('/', '.');
                for (String prefix : this.ignores) {
                    if (!className.startsWith(prefix)) continue;
                    this.listeners.forEach(it -> it.onClassLoading(loader, className, classfileBuffer, Collections.emptyList()));
                    return null;
                }
                TransformHandle handle = new TransformHandle(loader, className, classfileBuffer);
                this.units.forEach(handle::accept);
                this.listeners.forEach(it -> it.onClassLoading(loader, className, handle.getFinalResult(), handle.getAppliedTransformers()));
                Optional<byte[]> transformResult = handle.finish();
                if (Config.printUntransformedClass && !transformResult.isPresent()) {
                    Logging.log(Logging.Level.DEBUG, "No transformation is applied to [" + className + "]");
                }
                return transformResult.orElse(null);
            }
            catch (Throwable e) {
                Logging.log(Logging.Level.WARNING, "Failed to transform [" + internalClassName + "]", e);
            }
        }
        return null;
    }

    private static class TransformHandle {
        private final String className;
        private final ClassLoader classLoader;
        private byte[] classBuffer;
        private List<TransformUnit> appliedTransformers;
        private int minVersion = -1;
        private int upgradedVersion = -1;
        private boolean addCallbackMetafactory = false;

        public TransformHandle(ClassLoader classLoader, String className, byte[] classBuffer) {
            this.className = className;
            this.classBuffer = classBuffer;
            this.classLoader = classLoader;
        }

        public void accept(TransformUnit unit) {
            ClassWriter writer = new ClassWriter(1);
            TransformContextImpl ctx = new TransformContextImpl(this.className);
            Optional<ClassVisitor> optionalVisitor = unit.transform(this.classLoader, this.className, writer, ctx);
            if (optionalVisitor.isPresent()) {
                ClassReader reader = new ClassReader(this.classBuffer);
                reader.accept(optionalVisitor.get(), 0);
                if (ctx.modifiedMark) {
                    Logging.log(Logging.Level.INFO, "Transformed [" + this.className + "] with [" + unit + "]");
                    if (this.appliedTransformers == null) {
                        this.appliedTransformers = new ArrayList<TransformUnit>();
                    }
                    this.appliedTransformers.add(unit);
                    this.classBuffer = writer.toByteArray();
                    if (ctx.minVersionMark > this.minVersion) {
                        this.minVersion = ctx.minVersionMark;
                    }
                    if (ctx.upgradedVersionMark > this.upgradedVersion) {
                        this.upgradedVersion = ctx.upgradedVersionMark;
                    }
                    this.addCallbackMetafactory |= ctx.callbackMetafactoryRequested;
                }
            }
        }

        public Optional<byte[]> finish() {
            if (this.appliedTransformers == null || this.appliedTransformers.isEmpty()) {
                return Optional.empty();
            }
            if (this.addCallbackMetafactory) {
                this.accept(new CallbackMetafactoryTransformer());
            }
            if (this.minVersion == -1 && this.upgradedVersion == -1) {
                return Optional.of(this.classBuffer);
            }
            try {
                this.accept(new ClassVersionTransformUnit(this.minVersion, this.upgradedVersion));
                return Optional.of(this.classBuffer);
            }
            catch (ClassVersionException e) {
                Logging.log(Logging.Level.WARNING, "Skipping [" + this.className + "], " + e.getMessage());
                return Optional.empty();
            }
        }

        public List<TransformUnit> getAppliedTransformers() {
            return this.appliedTransformers == null ? Collections.emptyList() : this.appliedTransformers;
        }

        public byte[] getFinalResult() {
            return this.classBuffer;
        }
    }

    private static class TransformContextImpl
    implements TransformContext {
        private final String className;
        public boolean modifiedMark;
        public int minVersionMark = -1;
        public int upgradedVersionMark = -1;
        public boolean callbackMetafactoryRequested = false;

        public TransformContextImpl(String className) {
            this.className = className;
        }

        @Override
        public void markModified() {
            this.modifiedMark = true;
        }

        @Override
        public void requireMinimumClassVersion(int version) {
            if (this.minVersionMark < version) {
                this.minVersionMark = version;
            }
        }

        @Override
        public void upgradeClassVersion(int version) {
            if (this.upgradedVersionMark < version) {
                this.upgradedVersionMark = version;
            }
        }

        @Override
        public Handle acquireCallbackMetafactory() {
            this.callbackMetafactoryRequested = true;
            return new Handle(6, this.className.replace('.', '/'), "__authlibinjector_metafactory", "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;)Ljava/lang/invoke/CallSite;", false);
        }
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.transform.support;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import moe.yushi.authlibinjector.AuthlibInjector;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.transform.CallbackSupport;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.transform.support.MainArgumentsTransformer;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.util.WeakIdentityHashMap;

public class MC52974_1710Workaround {
    private static final Map<Object, Optional<Object>> markedGameProfiles = new WeakIdentityHashMap<Object, Optional<Object>>();

    private MC52974_1710Workaround() {
    }

    public static void init() {
        MainArgumentsTransformer.getVersionSeriesListeners().add(version -> {
            if ("1.7.10".equals(version)) {
                Logging.log(Logging.Level.INFO, "Enable MC-52974 Workaround for 1.7.10");
                AuthlibInjector.getClassTransformer().units.add(new SessionTransformer());
                AuthlibInjector.getClassTransformer().units.add(new S0CPacketSpawnPlayerTransformer());
                AuthlibInjector.retransformClasses("bbs", "net.minecraft.util.Session", "gb", "net.minecraft.network.play.server.S0CPacketSpawnPlayer");
            }
        });
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @CallbackMethod
    public static void markGameProfile(Object gp) {
        Map<Object, Optional<Object>> map = markedGameProfiles;
        synchronized (map) {
            markedGameProfiles.putIfAbsent(gp, Optional.empty());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @CallbackMethod
    public static Object accessGameProfile(Object gp, Object minecraftServer, boolean isNotchName) {
        Map<Object, Optional<Object>> map = markedGameProfiles;
        synchronized (map) {
            Optional<Object> value = markedGameProfiles.get(gp);
            if (value != null) {
                if (value.isPresent()) {
                    return value.get();
                }
                if (minecraftServer != null) {
                    Logging.log(Logging.Level.INFO, "Filling properties for " + gp);
                    try {
                        ClassLoader cl = minecraftServer.getClass().getClassLoader();
                        Class<?> classGameProfile = cl.loadClass("com.mojang.authlib.GameProfile");
                        Object gameProfile = classGameProfile.getConstructor(UUID.class, String.class).newInstance(classGameProfile.getMethod("getId", new Class[0]).invoke(gp, new Object[0]), classGameProfile.getMethod("getName", new Class[0]).invoke(gp, new Object[0]));
                        Class<?> classMinecraftServer = cl.loadClass("net.minecraft.server.MinecraftServer");
                        Object minecraftSessionService = (isNotchName ? classMinecraftServer.getMethod("av", new Class[0]) : classMinecraftServer.getMethod("func_147130_as", new Class[0])).invoke(minecraftServer, new Object[0]);
                        Object filledGameProfile = cl.loadClass("com.mojang.authlib.minecraft.MinecraftSessionService").getMethod("fillProfileProperties", classGameProfile, Boolean.TYPE).invoke(minecraftSessionService, gameProfile, true);
                        markedGameProfiles.put(gp, Optional.of(filledGameProfile));
                        return filledGameProfile;
                    }
                    catch (ReflectiveOperationException e) {
                        Logging.log(Logging.Level.WARNING, "Failed to inject GameProfile properties", e);
                    }
                }
            }
        }
        return gp;
    }

    private static <T> Optional<T> detectNotchName(String input, String ifNotchName, String ifDeobf, Function<Boolean, T> callback) {
        if (ifNotchName.equals(input)) {
            return Optional.of(callback.apply(true));
        }
        if (ifDeobf.equals(input)) {
            return Optional.of(callback.apply(false));
        }
        return Optional.empty();
    }

    private static class S0CPacketSpawnPlayerTransformer
    implements TransformUnit {
        private S0CPacketSpawnPlayerTransformer() {
        }

        @Override
        public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext ctx) {
            return MC52974_1710Workaround.detectNotchName(className, "gb", "net.minecraft.network.play.server.S0CPacketSpawnPlayer", isNotchName -> new ClassVisitor(589824, writer, (Boolean)isNotchName, ctx){
                final /* synthetic */ Boolean val$isNotchName;
                final /* synthetic */ TransformContext val$ctx;
                {
                    this.val$isNotchName = bl;
                    this.val$ctx = transformContext;
                    super(x0, x1);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (this.val$isNotchName != false ? "b".equals(name) && "(Let;)V".equals(descriptor) : "func_148840_b".equals(name) && "(Lnet/minecraft/network/PacketBuffer;)V".equals(descriptor)) {
                        return new MethodVisitor(589824, super.visitMethod(access, name, descriptor, signature, exceptions)){

                            @Override
                            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                                super.visitFieldInsn(opcode, owner, name, descriptor);
                                if (opcode == 180 && (val$isNotchName != false ? "gb".equals(owner) && "b".equals(name) && "Lcom/mojang/authlib/GameProfile;".equals(descriptor) : "net/minecraft/network/play/server/S0CPacketSpawnPlayer".equals(owner) && "field_148955_b".equals(name) && "Lcom/mojang/authlib/GameProfile;".equals(descriptor))) {
                                    val$ctx.markModified();
                                    if (val$isNotchName.booleanValue()) {
                                        super.visitMethodInsn(184, "net/minecraft/server/MinecraftServer", "I", "()Lnet/minecraft/server/MinecraftServer;", false);
                                    } else {
                                        super.visitMethodInsn(184, "net/minecraft/server/MinecraftServer", "func_71276_C", "()Lnet/minecraft/server/MinecraftServer;", false);
                                    }
                                    super.visitLdcInsn(val$isNotchName != false ? 1 : 0);
                                    CallbackSupport.invoke(val$ctx, this.mv, MC52974_1710Workaround.class, "accessGameProfile");
                                    super.visitTypeInsn(192, "com/mojang/authlib/GameProfile");
                                }
                            }
                        };
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            });
        }

        public String toString() {
            return "1.7.10 MC-52974 Workaround (S0CPacketSpawnPlayer)";
        }
    }

    private static class SessionTransformer
    implements TransformUnit {
        private SessionTransformer() {
        }

        @Override
        public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, TransformContext ctx) {
            return MC52974_1710Workaround.detectNotchName(className, "bbs", "net.minecraft.util.Session", isNotchName -> new ClassVisitor(589824, writer, (Boolean)isNotchName, ctx){
                final /* synthetic */ Boolean val$isNotchName;
                final /* synthetic */ TransformContext val$ctx;
                {
                    this.val$isNotchName = bl;
                    this.val$ctx = transformContext;
                    super(x0, x1);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (this.val$isNotchName != false ? "e".equals(name) && "()Lcom/mojang/authlib/GameProfile;".equals(descriptor) : "func_148256_e".equals(name) && "()Lcom/mojang/authlib/GameProfile;".equals(descriptor)) {
                        return new MethodVisitor(589824, super.visitMethod(access, name, descriptor, signature, exceptions)){

                            @Override
                            public void visitInsn(int opcode) {
                                if (opcode == 176) {
                                    val$ctx.markModified();
                                    super.visitInsn(89);
                                    CallbackSupport.invoke(val$ctx, this.mv, MC52974_1710Workaround.class, "markGameProfile");
                                }
                                super.visitInsn(opcode);
                            }
                        };
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            });
        }

        public String toString() {
            return "1.7.10 MC-52974 Workaround (Session)";
        }
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.transform.support;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.transform.CallbackSupport;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;

public class SkinWhitelistTransformUnit
implements TransformUnit {
    private static final String[] DEFAULT_WHITELISTED_DOMAINS = new String[]{".minecraft.net", ".mojang.com"};
    private static final String[] DEFAULT_BLACKLISTED_DOMAINS = new String[]{"education.minecraft.net", "bugs.mojang.com"};
    private static final List<String> WHITELISTED_DOMAINS = new CopyOnWriteArrayList<String>();

    public static boolean domainMatches(String pattern, String domain) {
        if (pattern.isEmpty()) {
            return false;
        }
        if (pattern.startsWith(".")) {
            return domain.endsWith(pattern);
        }
        return domain.equals(pattern);
    }

    public static List<String> getWhitelistedDomains() {
        return WHITELISTED_DOMAINS;
    }

    @CallbackMethod
    public static boolean isWhitelistedDomain(String url) {
        String domain;
        try {
            domain = new URI(url).getHost();
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL '" + url + "'");
        }
        for (String pattern : DEFAULT_BLACKLISTED_DOMAINS) {
            if (!SkinWhitelistTransformUnit.domainMatches(pattern, domain)) continue;
            return false;
        }
        for (String pattern : DEFAULT_WHITELISTED_DOMAINS) {
            if (!SkinWhitelistTransformUnit.domainMatches(pattern, domain)) continue;
            return true;
        }
        for (String pattern : WHITELISTED_DOMAINS) {
            if (!SkinWhitelistTransformUnit.domainMatches(pattern, domain)) continue;
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
                    if (("isWhitelistedDomain".equals(name) || "isAllowedTextureDomain".equals(name)) && "(Ljava/lang/String;)Z".equals(desc)) {
                        ctx.markModified();
                        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                        mv.visitCode();
                        mv.visitVarInsn(25, 0);
                        CallbackSupport.invoke(ctx, mv, SkinWhitelistTransformUnit.class, "isWhitelistedDomain");
                        mv.visitInsn(172);
                        mv.visitMaxs(-1, -1);
                        mv.visitEnd();
                        return null;
                    }
                    return super.visitMethod(access, name, desc, signature, exceptions);
                }
            });
        }
        return Optional.empty();
    }

    public String toString() {
        return "Texture Whitelist Transformer";
    }
}


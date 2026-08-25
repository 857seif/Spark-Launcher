/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.util;

import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import moe.yushi.authlibinjector.util.IOUtils;

public final class KeyUtils {
    public static byte[] decodePEMPublicKey(String pem) throws IllegalArgumentException {
        pem = IOUtils.removeNewLines(pem);
        String header = "-----BEGIN PUBLIC KEY-----";
        String end = "-----END PUBLIC KEY-----";
        if (pem.startsWith("-----BEGIN PUBLIC KEY-----") && pem.endsWith("-----END PUBLIC KEY-----")) {
            return Base64.getDecoder().decode(pem.substring("-----BEGIN PUBLIC KEY-----".length(), pem.length() - "-----END PUBLIC KEY-----".length()));
        }
        throw new IllegalArgumentException("Bad key format");
    }

    public static PublicKey parseX509PublicKey(byte[] encodedKey) throws GeneralSecurityException {
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encodedKey));
    }

    public static PublicKey parseSignaturePublicKey(String pem) throws UncheckedIOException {
        try {
            return KeyUtils.parseX509PublicKey(KeyUtils.decodePEMPublicKey(pem));
        }
        catch (IllegalArgumentException | GeneralSecurityException e) {
            throw IOUtils.newUncheckedIOException("Bad signature public key", e);
        }
    }

    private KeyUtils() {
    }
}


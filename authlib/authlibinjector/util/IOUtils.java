/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class IOUtils {
    public static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";
    public static final String CONTENT_TYPE_TEXT = "text/plain; charset=utf-8";

    private static HttpURLConnection createConnection(String url, Proxy proxy) throws IOException {
        if (proxy == null) {
            return (HttpURLConnection)new URL(url).openConnection();
        }
        return (HttpURLConnection)new URL(url).openConnection(proxy);
    }

    public static byte[] http(String method, String url) throws IOException {
        return IOUtils.http(method, url, null);
    }

    public static byte[] http(String method, String url, Proxy proxy) throws IOException {
        HttpURLConnection conn = IOUtils.createConnection(url, proxy);
        conn.setRequestMethod(method);
        try (InputStream in = conn.getInputStream();){
            byte[] byArray = IOUtils.asBytes(in);
            return byArray;
        }
    }

    public static byte[] http(String method, String url, byte[] payload, String contentType) throws IOException {
        return IOUtils.http(method, url, payload, contentType, null);
    }

    public static byte[] http(String method, String url, byte[] payload, String contentType, Proxy proxy) throws IOException {
        HttpURLConnection conn = IOUtils.createConnection(url, proxy);
        conn.setRequestMethod(method);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", contentType);
        try (OutputStream out = conn.getOutputStream();){
            out.write(payload);
        }
        var7_7 = null;
        try (InputStream in = conn.getInputStream();){
            byte[] byArray = IOUtils.asBytes(in);
            return byArray;
        }
        catch (Throwable throwable) {
            var7_7 = throwable;
            throw throwable;
        }
    }

    public static byte[] asBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        IOUtils.transfer(in, out);
        return out.toByteArray();
    }

    public static void transfer(InputStream from, OutputStream to) throws IOException {
        int read;
        byte[] buf = new byte[8192];
        while ((read = from.read(buf)) != -1) {
            to.write(buf, 0, read);
        }
    }

    public static String asString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String removeNewLines(String input) {
        return input.replace("\n", "").replace("\r", "");
    }

    public static UncheckedIOException newUncheckedIOException(String message) throws UncheckedIOException {
        return new UncheckedIOException(new IOException(message));
    }

    public static UncheckedIOException newUncheckedIOException(String message, Throwable cause) throws UncheckedIOException {
        return new UncheckedIOException(new IOException(message, cause));
    }

    private IOUtils() {
    }
}


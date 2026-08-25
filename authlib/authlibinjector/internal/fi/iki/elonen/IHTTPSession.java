/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

public interface IHTTPSession {
    public InetSocketAddress getRemoteAddress();

    public String getMethod();

    public String getUri();

    public String getQueryParameterString();

    public Map<String, List<String>> getParameters();

    public Map<String, String> getHeaders();

    public InputStream getInputStream() throws IOException;
}


/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.httpd;

import java.io.IOException;
import java.util.Optional;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;

public interface URLFilter {
    public boolean canHandle(String var1);

    public Optional<Response> handle(String var1, String var2, IHTTPSession var3) throws IOException;
}


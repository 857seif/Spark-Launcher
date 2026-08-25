/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.httpd;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import moe.yushi.authlibinjector.httpd.URLFilter;
import moe.yushi.authlibinjector.httpd.URLRedirector;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IStatus;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.NanoHTTPD;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.util.IOUtils;
import moe.yushi.authlibinjector.util.Logging;

public class URLProcessor {
    private static final Pattern URL_REGEX = Pattern.compile("^(?<protocol>https?):\\/\\/(?<domain>[^\\/]+)(?<path>\\/?.*)$");
    private static final Pattern LOCAL_URL_REGEX = Pattern.compile("^/(?<protocol>https?)/(?<domain>[^\\/]+)(?<path>\\/.*)$");
    private List<URLFilter> filters;
    private URLRedirector redirector;
    private volatile NanoHTTPD httpd;
    private final Object httpdLock = new Object();
    private static final Set<String> ignoredHeaders = new HashSet<String>(Arrays.asList("host", "expect", "connection", "keep-alive", "transfer-encoding"));

    public URLProcessor(List<URLFilter> filters, URLRedirector redirector) {
        this.filters = filters;
        this.redirector = redirector;
    }

    public Optional<String> transformURL(String inputUrl) {
        String path;
        String domain;
        Matcher matcher = URL_REGEX.matcher(inputUrl);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String protocol = matcher.group("protocol");
        Optional<String> result = this.transform(protocol, domain = matcher.group("domain"), path = matcher.group("path"));
        if (result.isPresent()) {
            Logging.log(Logging.Level.DEBUG, "Transformed url [" + inputUrl + "] to [" + result.get() + "]");
        }
        return result;
    }

    private Optional<String> transform(String protocol, String domain, String path) {
        boolean handleLocally = false;
        for (URLFilter filter : this.filters) {
            if (!filter.canHandle(domain)) continue;
            handleLocally = true;
            break;
        }
        if (handleLocally) {
            return Optional.of("http://127.0.0.1:" + this.getLocalApiPort() + "/" + protocol + "/" + domain + path);
        }
        return this.redirector.redirect(domain, path);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private int getLocalApiPort() {
        Object object = this.httpdLock;
        synchronized (object) {
            if (this.httpd == null) {
                this.httpd = this.createHttpd();
                try {
                    this.httpd.start();
                }
                catch (IOException e) {
                    throw new IllegalStateException("Httpd failed to start");
                }
                Logging.log(Logging.Level.INFO, "Httpd is running on port " + this.httpd.getListeningPort());
            }
            return this.httpd.getListeningPort();
        }
    }

    private NanoHTTPD createHttpd() {
        return new NanoHTTPD("127.0.0.1", 0){

            @Override
            public Response serve(IHTTPSession session) {
                Matcher matcher = LOCAL_URL_REGEX.matcher(session.getUri());
                if (matcher.find()) {
                    String protocol = matcher.group("protocol");
                    String domain = matcher.group("domain");
                    String path = matcher.group("path");
                    for (URLFilter filter : URLProcessor.this.filters) {
                        Optional<Response> result;
                        if (!filter.canHandle(domain)) continue;
                        try {
                            result = filter.handle(domain, path, session);
                        }
                        catch (Throwable e) {
                            Logging.log(Logging.Level.WARNING, "An error occurred while processing request [" + session.getUri() + "]", e);
                            return Response.newFixedLength(Status.INTERNAL_ERROR, "text/plain; charset=utf-8", "Internal Server Error");
                        }
                        if (!result.isPresent()) continue;
                        Logging.log(Logging.Level.DEBUG, "Request to [" + session.getUri() + "] is handled by [" + filter + "]");
                        return result.get();
                    }
                    String target = URLProcessor.this.redirector.redirect(domain, path).orElseGet(() -> protocol + "://" + domain + path);
                    try {
                        return URLProcessor.this.reverseProxy(session, target);
                    }
                    catch (IOException e) {
                        Logging.log(Logging.Level.WARNING, "Reverse proxy error", e);
                        return Response.newFixedLength(Status.BAD_GATEWAY, "text/plain; charset=utf-8", "Bad Gateway");
                    }
                }
                Logging.log(Logging.Level.DEBUG, "No handler is found for [" + session.getUri() + "]");
                return Response.newFixedLength(Status.NOT_FOUND, "text/plain; charset=utf-8", "Not Found");
            }
        };
    }

    private Response reverseProxy(IHTTPSession session, String upstream) throws IOException {
        InputStream upstreamIn;
        String method = session.getMethod();
        String url = session.getQueryParameterString() == null ? upstream : upstream + "?" + session.getQueryParameterString();
        LinkedHashMap<String, String> requestHeaders = new LinkedHashMap<String, String>(session.getHeaders());
        ignoredHeaders.forEach(requestHeaders::remove);
        InputStream clientIn = session.getInputStream();
        Logging.log(Logging.Level.DEBUG, "Reverse proxy: > " + method + " " + url + ", headers: " + requestHeaders);
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setDoOutput(clientIn != null);
        requestHeaders.forEach(conn::setRequestProperty);
        if (clientIn != null) {
            try (OutputStream upstreamOut = conn.getOutputStream();){
                IOUtils.transfer(clientIn, upstreamOut);
            }
        }
        final int responseCode = conn.getResponseCode();
        final String reponseMessage = conn.getResponseMessage();
        LinkedHashMap<String, List> responseHeaders = new LinkedHashMap<String, List>();
        conn.getHeaderFields().forEach((name, values) -> {
            if (name != null && !ignoredHeaders.contains(name.toLowerCase())) {
                responseHeaders.put((String)name, (List)values);
            }
        });
        try {
            upstreamIn = conn.getInputStream();
        }
        catch (IOException e) {
            upstreamIn = conn.getErrorStream();
        }
        Logging.log(Logging.Level.DEBUG, "Reverse proxy: < " + responseCode + " " + reponseMessage + " , headers: " + responseHeaders);
        IStatus status = new IStatus(){

            @Override
            public int getRequestStatus() {
                return responseCode;
            }

            @Override
            public String getDescription() {
                return responseCode + " " + reponseMessage;
            }
        };
        long contentLength = -1L;
        for (Map.Entry header : responseHeaders.entrySet()) {
            if (!"content-length".equalsIgnoreCase((String)header.getKey())) continue;
            contentLength = Long.parseLong((String)((List)header.getValue()).get(0));
            break;
        }
        Response response = contentLength == -1L ? (conn.getHeaderField("transfer-encoding") == null ? Response.newFixedLength(status, null, upstreamIn, 0L) : Response.newChunked(status, null, upstreamIn)) : Response.newFixedLength(status, null, upstreamIn, contentLength);
        responseHeaders.forEach((name, values) -> values.forEach(value -> response.addHeader((String)name, (String)value)));
        return response;
    }
}


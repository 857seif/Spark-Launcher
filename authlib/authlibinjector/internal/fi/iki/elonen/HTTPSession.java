/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Function;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.ChunkedInputStream;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.FixedLengthInputStream;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.NanoHTTPD;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.ResponseException;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.util.Logging;

class HTTPSession
implements IHTTPSession {
    public static final int BUFSIZE = 8192;
    private final OutputStream outputStream;
    private final BufferedInputStream inputStream;
    private final InetSocketAddress remoteAddr;
    private String uri;
    private String method;
    private String queryParameterString;
    private Map<String, List<String>> parms;
    private Map<String, String> headers;
    private String protocolVersion;
    private InputStream parsedInputStream;
    private boolean expect100Continue;
    private boolean continueSent;
    private boolean isServing;
    private final Object servingLock = new Object();

    public HTTPSession(InputStream inputStream, OutputStream outputStream, InetSocketAddress remoteAddr) {
        this.inputStream = new BufferedInputStream(inputStream, 8192);
        this.outputStream = outputStream;
        this.remoteAddr = remoteAddr;
    }

    private ByteArrayInputStream readHeader() throws IOException {
        byte[] buf = new byte[8192];
        int splitbyte = 0;
        int rlen = 0;
        int read = -1;
        this.inputStream.mark(8192);
        try {
            read = this.inputStream.read(buf, 0, 8192);
        }
        catch (IOException e) {
            NanoHTTPD.safeClose(this.inputStream);
            NanoHTTPD.safeClose(this.outputStream);
            throw new ConnectionCloseException();
        }
        if (read == -1) {
            NanoHTTPD.safeClose(this.inputStream);
            NanoHTTPD.safeClose(this.outputStream);
            throw new ConnectionCloseException();
        }
        while (read > 0 && (splitbyte = HTTPSession.findHeaderEnd(buf, rlen += read)) <= 0) {
            read = this.inputStream.read(buf, rlen, 8192 - rlen);
        }
        if (splitbyte < rlen) {
            this.inputStream.reset();
            this.inputStream.skip(splitbyte);
        }
        return new ByteArrayInputStream(buf, 0, rlen);
    }

    private void parseHeader(BufferedReader in) throws ResponseException {
        try {
            String requestLine = in.readLine();
            if (requestLine == null) {
                throw new ResponseException(Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
            }
            StringTokenizer st = new StringTokenizer(requestLine);
            if (!st.hasMoreTokens()) {
                throw new ResponseException(Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
            }
            this.method = st.nextToken();
            if (!st.hasMoreTokens()) {
                throw new ResponseException(Status.BAD_REQUEST, "BAD REQUEST: Missing URI.");
            }
            String rawUri = st.nextToken();
            int qmi = rawUri.indexOf(63);
            if (qmi >= 0) {
                this.queryParameterString = rawUri.substring(qmi + 1);
                this.parms = Collections.unmodifiableMap(HTTPSession.decodeParms(this.queryParameterString));
                this.uri = HTTPSession.decodePercent(rawUri.substring(0, qmi));
            } else {
                this.queryParameterString = null;
                this.parms = Collections.emptyMap();
                this.uri = HTTPSession.decodePercent(rawUri);
            }
            if (st.hasMoreTokens()) {
                this.protocolVersion = st.nextToken();
            } else {
                this.protocolVersion = "HTTP/1.1";
                Logging.log(Logging.Level.DEBUG, "no protocol version specified, strange. Assuming HTTP/1.1.");
            }
            LinkedHashMap<String, String> headers = new LinkedHashMap<String, String>();
            String line = in.readLine();
            while (line != null && !line.trim().isEmpty()) {
                int p = line.indexOf(58);
                if (p >= 0) {
                    headers.put(line.substring(0, p).trim().toLowerCase(Locale.ROOT), line.substring(p + 1).trim());
                }
                line = in.readLine();
            }
            this.headers = Collections.unmodifiableMap(headers);
        }
        catch (IOException ioe) {
            throw new ResponseException(Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage(), ioe);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Loose catch block
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public void execute(Function<IHTTPSession, Response> handler) throws IOException {
        Response r = null;
        try {
            boolean keepAlive;
            this.parseHeader(new BufferedReader(new InputStreamReader((InputStream)this.readHeader(), StandardCharsets.ISO_8859_1)));
            String transferEncoding = this.headers.get("transfer-encoding");
            String contentLengthStr = this.headers.get("content-length");
            if (transferEncoding != null && contentLengthStr == null) {
                if (!"chunked".equals(transferEncoding)) throw new ResponseException(Status.NOT_IMPLEMENTED, "Unsupported Transfer-Encoding");
                this.parsedInputStream = new ChunkedInputStream(this.inputStream);
            } else if (transferEncoding == null && contentLengthStr != null) {
                int contentLength = -1;
                try {
                    contentLength = Integer.parseInt(contentLengthStr);
                }
                catch (NumberFormatException numberFormatException) {
                    // empty catch block
                }
                if (contentLength < 0) {
                    throw new ResponseException(Status.BAD_REQUEST, "The request has an invalid Content-Length header.");
                }
                this.parsedInputStream = new FixedLengthInputStream(this.inputStream, contentLength);
            } else {
                if (transferEncoding != null && contentLengthStr != null) {
                    throw new ResponseException(Status.BAD_REQUEST, "Content-Length and Transfer-Encoding cannot exist at the same time.");
                }
                this.parsedInputStream = null;
            }
            this.expect100Continue = "HTTP/1.1".equals(this.protocolVersion) && "100-continue".equals(this.headers.get("expect")) && this.parsedInputStream != null;
            this.continueSent = false;
            this.isServing = true;
            try {
                r = handler.apply(this);
            }
            finally {
                Object contentLength = this.servingLock;
                synchronized (contentLength) {
                    this.isServing = false;
                }
            }
            if (this.parsedInputStream != null && (!this.expect100Continue || this.continueSent)) {
                while (this.parsedInputStream.read() != -1) {
                }
            }
            boolean bl = keepAlive = "HTTP/1.1".equals(this.protocolVersion) && !"close".equals(this.headers.get("connection"));
            if (r == null) {
                throw new ResponseException(Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: Serve() returned a null response.");
            }
            r.setRequestMethod(this.method);
            r.setKeepAlive(keepAlive);
            r.send(this.outputStream);
            if (!keepAlive) throw new ConnectionCloseException();
            if ("close".equals(r.getHeader("connection"))) {
                throw new ConnectionCloseException();
            }
            NanoHTTPD.safeClose(r);
            return;
        }
        catch (SocketException e) {
            throw e;
            catch (SocketTimeoutException ste) {
                throw ste;
            }
            catch (IOException ioe) {
                Response resp = Response.newFixedLength(Status.INTERNAL_ERROR, "text/plain; charset=utf-8", "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
                resp.send(this.outputStream);
                NanoHTTPD.safeClose(this.outputStream);
                return;
            }
            catch (ResponseException re) {
                Response resp = Response.newFixedLength(re.getStatus(), "text/plain; charset=utf-8", re.getMessage());
                resp.send(this.outputStream);
                NanoHTTPD.safeClose(this.outputStream);
                return;
            }
        }
        finally {
            NanoHTTPD.safeClose(r);
        }
    }

    @Override
    public final Map<String, String> getHeaders() {
        return this.headers;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public final InputStream getInputStream() throws IOException {
        Object object = this.servingLock;
        synchronized (object) {
            if (!this.isServing) {
                throw new IllegalStateException();
            }
            if (this.expect100Continue && !this.continueSent) {
                this.continueSent = true;
                this.outputStream.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
            }
        }
        return this.parsedInputStream;
    }

    @Override
    public final String getMethod() {
        return this.method;
    }

    @Override
    public final Map<String, List<String>> getParameters() {
        return this.parms;
    }

    @Override
    public String getQueryParameterString() {
        return this.queryParameterString;
    }

    @Override
    public final String getUri() {
        return this.uri;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return this.remoteAddr;
    }

    private static int findHeaderEnd(byte[] buf, int rlen) {
        int splitbyte = 0;
        while (splitbyte + 1 < rlen) {
            if (buf[splitbyte] == 13 && buf[splitbyte + 1] == 10 && splitbyte + 3 < rlen && buf[splitbyte + 2] == 13 && buf[splitbyte + 3] == 10) {
                return splitbyte + 4;
            }
            if (buf[splitbyte] == 10 && buf[splitbyte + 1] == 10) {
                return splitbyte + 2;
            }
            ++splitbyte;
        }
        return 0;
    }

    private static String decodePercent(String str) {
        try {
            return URLDecoder.decode(str, "UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<String, List<String>> decodeParms(String parms) {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        StringTokenizer st = new StringTokenizer(parms, "&");
        while (st.hasMoreTokens()) {
            String e = st.nextToken();
            int sep = e.indexOf(61);
            String key = null;
            String value = null;
            if (sep >= 0) {
                key = HTTPSession.decodePercent(e.substring(0, sep)).trim();
                value = HTTPSession.decodePercent(e.substring(sep + 1));
            } else {
                key = HTTPSession.decodePercent(e).trim();
                value = "";
            }
            ArrayList<String> values = (ArrayList<String>)result.get(key);
            if (values == null) {
                values = new ArrayList<String>();
                result.put(key, values);
            }
            values.add(value);
        }
        return result;
    }

    public static class ConnectionCloseException
    extends SocketException {
    }
}


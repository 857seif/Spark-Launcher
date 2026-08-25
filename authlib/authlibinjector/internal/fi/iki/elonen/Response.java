/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.ChunkedOutputStream;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.ContentType;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IStatus;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.NanoHTTPD;
import moe.yushi.authlibinjector.util.Logging;

public class Response
implements Closeable {
    private IStatus status;
    private String mimeType;
    private InputStream data;
    private long contentLength;
    private final Map<String, String> headers = new LinkedHashMap<String, String>();
    private String requestMethod;
    private boolean chunkedTransfer;
    private boolean keepAlive;

    protected Response(IStatus status, String mimeType, InputStream data, long totalBytes) {
        this.status = status;
        this.mimeType = mimeType;
        if (data == null) {
            this.data = new ByteArrayInputStream(new byte[0]);
            this.contentLength = 0L;
        } else {
            this.data = data;
            this.contentLength = totalBytes;
        }
        this.chunkedTransfer = this.contentLength < 0L;
        this.keepAlive = true;
    }

    @Override
    public void close() throws IOException {
        if (this.data != null) {
            this.data.close();
        }
    }

    public void addHeader(String name, String value) {
        this.headers.put(name.toLowerCase(Locale.ROOT), Objects.requireNonNull(value));
    }

    public String getHeader(String name) {
        return this.headers.get(name.toLowerCase(Locale.ROOT));
    }

    public InputStream getData() {
        return this.data;
    }

    public String getMimeType() {
        return this.mimeType;
    }

    public String getRequestMethod() {
        return this.requestMethod;
    }

    public IStatus getStatus() {
        return this.status;
    }

    public void setKeepAlive(boolean useKeepAlive) {
        this.keepAlive = useKeepAlive;
    }

    protected void send(OutputStream outputStream) {
        SimpleDateFormat gmtFrmt = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            long pending;
            if (this.status == null) {
                throw new Error("sendResponse(): Status can't be null.");
            }
            PrintWriter pw = new PrintWriter((Writer)new BufferedWriter(new OutputStreamWriter(outputStream, new ContentType(this.mimeType).getEncoding())), false);
            pw.append("HTTP/1.1 ").append(this.status.getDescription()).append(" \r\n");
            if (this.mimeType != null) {
                this.printHeader(pw, "Content-Type", this.mimeType);
            }
            if (this.getHeader("date") == null) {
                this.printHeader(pw, "Date", gmtFrmt.format(new Date()));
            }
            this.headers.forEach((name, value) -> this.printHeader(pw, (String)name, (String)value));
            if (this.getHeader("connection") == null) {
                this.printHeader(pw, "Connection", this.keepAlive ? "keep-alive" : "close");
            }
            long l = pending = this.data != null ? this.contentLength : 0L;
            if (!"HEAD".equals(this.requestMethod) && this.chunkedTransfer) {
                this.printHeader(pw, "Transfer-Encoding", "chunked");
            } else {
                pending = this.sendContentLengthHeaderIfNotAlreadyPresent(pw, pending);
            }
            pw.append("\r\n");
            pw.flush();
            this.sendBodyWithCorrectTransferAndEncoding(outputStream, pending);
            outputStream.flush();
            NanoHTTPD.safeClose(this.data);
        }
        catch (IOException ioe) {
            Logging.log(Logging.Level.ERROR, "Could not send response to the client", ioe);
        }
    }

    protected void printHeader(PrintWriter pw, String key, String value) {
        pw.append(key).append(": ").append(value).append("\r\n");
    }

    protected long sendContentLengthHeaderIfNotAlreadyPresent(PrintWriter pw, long defaultSize) {
        String contentLengthString = this.getHeader("content-length");
        if (contentLengthString == null) {
            pw.print("Content-Length: " + defaultSize + "\r\n");
            return defaultSize;
        }
        long size = defaultSize;
        try {
            size = Long.parseLong(contentLengthString);
        }
        catch (NumberFormatException ex) {
            Logging.log(Logging.Level.ERROR, "content-length was not number " + contentLengthString);
        }
        return size;
    }

    private void sendBodyWithCorrectTransferAndEncoding(OutputStream outputStream, long pending) throws IOException {
        if (!"HEAD".equals(this.requestMethod) && this.chunkedTransfer) {
            ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(outputStream);
            this.sendBody(chunkedOutputStream, -1L);
            chunkedOutputStream.finish();
        } else {
            this.sendBody(outputStream, pending);
        }
    }

    private void sendBody(OutputStream outputStream, long pending) throws IOException {
        long bytesToRead;
        int read;
        boolean sendEverything;
        long BUFFER_SIZE = 16384L;
        byte[] buff = new byte[(int)BUFFER_SIZE];
        boolean bl = sendEverything = pending == -1L;
        while ((pending > 0L || sendEverything) && (read = this.data.read(buff, 0, (int)(bytesToRead = sendEverything ? BUFFER_SIZE : Math.min(pending, BUFFER_SIZE)))) > 0) {
            outputStream.write(buff, 0, read);
            if (sendEverything) continue;
            pending -= (long)read;
        }
    }

    public void setChunkedTransfer(boolean chunkedTransfer) {
        this.chunkedTransfer = chunkedTransfer;
    }

    public void setData(InputStream data) {
        this.data = data;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public void setStatus(IStatus status) {
        this.status = status;
    }

    public static Response newFixedLength(IStatus status, String mimeType, String txt) {
        byte[] bytes;
        ContentType contentType = new ContentType(mimeType);
        if (txt == null) {
            return Response.newFixedLength(status, mimeType, new ByteArrayInputStream(new byte[0]), 0L);
        }
        try {
            CharsetEncoder newEncoder = Charset.forName(contentType.getEncoding()).newEncoder();
            if (!newEncoder.canEncode(txt)) {
                contentType = contentType.tryUTF8();
            }
            bytes = txt.getBytes(contentType.getEncoding());
        }
        catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return Response.newFixedLength(status, contentType.getContentTypeHeader(), new ByteArrayInputStream(bytes), bytes.length);
    }

    public static Response newFixedLength(IStatus status, String mimeType, InputStream data, long totalBytes) {
        return new Response(status, mimeType, data, totalBytes);
    }

    public static Response newChunked(IStatus status, String mimeType, InputStream data) {
        return new Response(status, mimeType, data, -1L);
    }
}


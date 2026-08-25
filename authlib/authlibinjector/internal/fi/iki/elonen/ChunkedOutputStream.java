/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

class ChunkedOutputStream
extends FilterOutputStream {
    public ChunkedOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(int b) throws IOException {
        byte[] data = new byte[]{(byte)b};
        this.write(data, 0, 1);
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return;
        }
        this.out.write(String.format("%x\r\n", len).getBytes(StandardCharsets.US_ASCII));
        this.out.write(b, off, len);
        this.out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
    }

    public void finish() throws IOException {
        this.out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

class FixedLengthInputStream
extends InputStream {
    private final InputStream in;
    private long remaining = 0L;

    public FixedLengthInputStream(InputStream in, long length) {
        this.remaining = length;
        this.in = in;
    }

    @Override
    public synchronized int read() throws IOException {
        if (this.remaining > 0L) {
            int result = this.in.read();
            if (result == -1) {
                throw new EOFException();
            }
            --this.remaining;
            return result;
        }
        return -1;
    }

    @Override
    public synchronized int available() throws IOException {
        return Math.min(this.in.available(), (int)this.remaining);
    }
}


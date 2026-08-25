/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

class ChunkedInputStream
extends InputStream {
    private final InputStream in;
    private int currentRemaining = -1;

    public ChunkedInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public synchronized int read() throws IOException {
        if (this.currentRemaining == -2) {
            return -1;
        }
        if (this.currentRemaining == 0) {
            this.readCRLF();
            this.currentRemaining = -1;
        }
        if (this.currentRemaining == -1) {
            this.currentRemaining = this.readChunkLength();
            if (this.currentRemaining == 0) {
                this.readCRLF();
                this.currentRemaining = -2;
                return -1;
            }
        }
        int result = this.in.read();
        --this.currentRemaining;
        if (result == -1) {
            throw new EOFException();
        }
        return result;
    }

    private int readChunkLength() throws IOException {
        int length = 0;
        while (true) {
            int b;
            if ((b = this.in.read()) == -1) {
                throw new EOFException();
            }
            if (b == 13) {
                b = this.in.read();
                if (b == -1) {
                    throw new EOFException();
                }
                if (b == 10) {
                    return length;
                }
                throw new IOException("LF is expected, read: " + b);
            }
            int digit = ChunkedInputStream.hexDigit(b);
            if (digit == -1) {
                throw new IOException("Hex digit is expected, read: " + b);
            }
            if ((length & 0xF8000000) != 0) {
                throw new IOException("Chunk is too long");
            }
            length <<= 4;
            length += digit;
        }
    }

    private void readCRLF() throws IOException {
        int b1 = this.in.read();
        int b2 = this.in.read();
        if (b1 == 13 && b2 == 10) {
            return;
        }
        if (b1 == -1 || b2 == -1) {
            throw new EOFException();
        }
        throw new IOException("CRLF is expected, read: " + b1 + " " + b2);
    }

    private static int hexDigit(int ch) {
        if (ch >= 48 && ch <= 57) {
            return ch - 48;
        }
        if (ch >= 97 && ch <= 102) {
            return ch - 97 + 10;
        }
        if (ch >= 65 && ch <= 70) {
            return ch - 65 + 10;
        }
        return -1;
    }

    @Override
    public synchronized int available() throws IOException {
        if (this.currentRemaining > 0) {
            return Math.min(this.currentRemaining, this.in.available());
        }
        return 0;
    }
}


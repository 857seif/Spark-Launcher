/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.internal.fi.iki.elonen;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.HTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.IHTTPSession;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Response;
import moe.yushi.authlibinjector.internal.fi.iki.elonen.Status;
import moe.yushi.authlibinjector.util.Logging;

public abstract class NanoHTTPD {
    private final String hostname;
    private final int port;
    private volatile ServerSocket serverSocket;
    private Thread listenerThread;
    private final AsyncRunner asyncRunner = new AsyncRunner();

    static final void safeClose(Object closeable) {
        block5: {
            try {
                if (closeable == null) break block5;
                if (closeable instanceof Closeable) {
                    ((Closeable)closeable).close();
                    break block5;
                }
                if (closeable instanceof Socket) {
                    ((Socket)closeable).close();
                    break block5;
                }
                if (closeable instanceof ServerSocket) {
                    ((ServerSocket)closeable).close();
                    break block5;
                }
                throw new IllegalArgumentException("Unknown object to close");
            }
            catch (IOException e) {
                Logging.log(Logging.Level.ERROR, "Could not close", e);
            }
        }
    }

    public NanoHTTPD(int port) {
        this(null, port);
    }

    public NanoHTTPD(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    public final int getListeningPort() {
        return this.serverSocket == null ? -1 : this.serverSocket.getLocalPort();
    }

    public final boolean isAlive() {
        return this.wasStarted() && !this.serverSocket.isClosed() && this.listenerThread.isAlive();
    }

    public String getHostname() {
        return this.hostname;
    }

    public Response serve(IHTTPSession session) {
        return Response.newFixedLength(Status.NOT_FOUND, "text/plain; charset=utf-8", "Not Found");
    }

    public void start() throws IOException {
        this.start(true);
    }

    public void start(boolean daemon) throws IOException {
        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        ServerRunnable serverRunnable = new ServerRunnable();
        this.listenerThread = new Thread(serverRunnable);
        this.listenerThread.setDaemon(daemon);
        this.listenerThread.setName("NanoHttpd Main Listener");
        this.listenerThread.start();
        while (!serverRunnable.hasBinded && serverRunnable.bindException == null) {
            try {
                Thread.sleep(10L);
            }
            catch (Throwable throwable) {}
        }
        if (serverRunnable.bindException != null) {
            throw serverRunnable.bindException;
        }
    }

    public void stop() {
        try {
            NanoHTTPD.safeClose(this.serverSocket);
            this.asyncRunner.closeAll();
            if (this.listenerThread != null) {
                this.listenerThread.join();
            }
        }
        catch (Exception e) {
            Logging.log(Logging.Level.ERROR, "Could not stop all connections", e);
        }
    }

    public final boolean wasStarted() {
        return this.serverSocket != null && this.listenerThread != null;
    }

    private class ServerRunnable
    implements Runnable {
        private static final int SOCKET_READ_TIMEOUT = 5000;
        private IOException bindException;
        private boolean hasBinded = false;

        private ServerRunnable() {
        }

        @Override
        public void run() {
            try {
                NanoHTTPD.this.serverSocket.bind(NanoHTTPD.this.hostname != null ? new InetSocketAddress(NanoHTTPD.this.hostname, NanoHTTPD.this.port) : new InetSocketAddress(NanoHTTPD.this.port));
                this.hasBinded = true;
            }
            catch (IOException e) {
                this.bindException = e;
                return;
            }
            do {
                try {
                    Socket finalAccept = NanoHTTPD.this.serverSocket.accept();
                    finalAccept.setSoTimeout(5000);
                    InputStream inputStream = finalAccept.getInputStream();
                    NanoHTTPD.this.asyncRunner.exec(new ClientHandler(inputStream, finalAccept));
                }
                catch (IOException e) {
                    Logging.log(Logging.Level.DEBUG, "Communication with the client broken", e);
                }
            } while (!NanoHTTPD.this.serverSocket.isClosed());
        }
    }

    private static class AsyncRunner {
        private final AtomicLong requestCount = new AtomicLong();
        private final List<ClientHandler> running = new CopyOnWriteArrayList<ClientHandler>();

        private AsyncRunner() {
        }

        public void closeAll() {
            for (ClientHandler clientHandler : this.running) {
                clientHandler.close();
            }
        }

        public void closed(ClientHandler clientHandler) {
            this.running.remove(clientHandler);
        }

        public void exec(ClientHandler clientHandler) {
            Thread t = new Thread(clientHandler);
            t.setDaemon(true);
            t.setName("NanoHttpd Request Processor (#" + this.requestCount.incrementAndGet() + ")");
            this.running.add(clientHandler);
            t.start();
        }
    }

    private class ClientHandler
    implements Runnable {
        private final InputStream inputStream;
        private final Socket acceptSocket;

        public ClientHandler(InputStream inputStream, Socket acceptSocket) {
            this.inputStream = inputStream;
            this.acceptSocket = acceptSocket;
        }

        public void close() {
            NanoHTTPD.safeClose(this.inputStream);
            NanoHTTPD.safeClose(this.acceptSocket);
        }

        @Override
        public void run() {
            OutputStream outputStream = null;
            try {
                outputStream = this.acceptSocket.getOutputStream();
                HTTPSession session = new HTTPSession(this.inputStream, outputStream, (InetSocketAddress)this.acceptSocket.getRemoteSocketAddress());
                while (!this.acceptSocket.isClosed()) {
                    session.execute(NanoHTTPD.this::serve);
                }
            }
            catch (HTTPSession.ConnectionCloseException session) {
            }
            catch (Exception e) {
                Logging.log(Logging.Level.ERROR, "Communication with the client broken, or an bug in the handler code", e);
            }
            finally {
                NanoHTTPD.safeClose(outputStream);
                NanoHTTPD.safeClose(this.inputStream);
                NanoHTTPD.safeClose(this.acceptSocket);
                NanoHTTPD.this.asyncRunner.closed(this);
            }
        }
    }
}


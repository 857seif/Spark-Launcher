/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.util;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import moe.yushi.authlibinjector.Config;

public final class Logging {
    private static final PrintStream out = System.err;
    private static final FileChannel logfile = Logging.openLogFile();

    private Logging() {
    }

    private static FileChannel openLogFile() {
        if (System.getProperty("authlibinjector.noLogFile") != null) {
            Logging.log(Level.INFO, "Logging to file is disabled");
            return null;
        }
        Path logfilePath = Paths.get("authlib-injector.log", new String[0]).toAbsolutePath();
        try {
            FileChannel channel = FileChannel.open(logfilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            if (channel.tryLock() == null) {
                Logging.log(Level.WARNING, "Couldn't lock log file [" + logfilePath + "]");
                return null;
            }
            channel.truncate(0L);
            String logHeader = "Logging started at " + Instant.now() + System.lineSeparator();
            channel.write(Charset.defaultCharset().encode(logHeader));
            Logging.log(Level.INFO, "Logging file: " + logfilePath);
            return channel;
        }
        catch (IOException e) {
            Logging.log(Level.WARNING, "Couldn't open log file [" + logfilePath + "]");
            return null;
        }
    }

    public static void log(Level level, String message) {
        Logging.log(level, message, null);
    }

    public static void log(Level level, String message, Throwable e) {
        if (level == Level.DEBUG && !Config.verboseLogging) {
            return;
        }
        String log = "[authlib-injector] [" + (Object)((Object)level) + "] " + message;
        if (e != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println();
            e.printStackTrace(pw);
            pw.close();
            log = log + sw.toString();
        }
        log = log.replaceAll("[\\p{Cc}&&[^\r\n\t]]", "");
        out.println(log);
        if (logfile != null) {
            try {
                logfile.write(Charset.defaultCharset().encode(log + System.lineSeparator()));
                logfile.force(true);
            }
            catch (IOException ex) {
                out.println("[authlib-injector] [ERROR] Error writing to log file: " + ex);
            }
        }
    }

    public static enum Level {
        DEBUG,
        INFO,
        WARNING,
        ERROR;

    }
}


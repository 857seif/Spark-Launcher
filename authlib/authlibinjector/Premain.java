/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector;

import java.lang.instrument.Instrumentation;
import moe.yushi.authlibinjector.AuthlibInjector;
import moe.yushi.authlibinjector.InitializationException;
import moe.yushi.authlibinjector.util.Logging;

public final class Premain {
    private Premain() {
    }

    public static void premain(String arg, Instrumentation instrumentation) {
        try {
            Premain.initInjector(arg, instrumentation, false);
        }
        catch (InitializationException e) {
            Logging.log(Logging.Level.DEBUG, "A known exception has occurred", e);
            System.exit(1);
        }
        catch (Throwable e) {
            Logging.log(Logging.Level.ERROR, "An exception has occurred, exiting", e);
            System.exit(1);
        }
    }

    public static void agentmain(String arg, Instrumentation instrumentation) {
        try {
            Logging.log(Logging.Level.INFO, "Launched from agentmain");
            Premain.initInjector(arg, instrumentation, true);
        }
        catch (InitializationException e) {
            Logging.log(Logging.Level.DEBUG, "A known exception has occurred", e);
        }
        catch (Throwable e) {
            Logging.log(Logging.Level.ERROR, "An exception has occurred", e);
        }
    }

    private static void initInjector(String arg, Instrumentation instrumentation, boolean retransform) {
        AuthlibInjector.bootstrap(instrumentation, arg);
        if (retransform) {
            AuthlibInjector.retransformAllClasses();
        }
    }
}


/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector.transform.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import moe.yushi.authlibinjector.transform.support.MainArgumentsTransformer;
import moe.yushi.authlibinjector.util.Logging;

public final class ProxyParameterWorkaround {
    private static final Set<String> PROXY_PARAMETERS = new HashSet<String>(Arrays.asList("--proxyHost", "--proxyPort", "--proxyUser", "--proxyPass"));

    private ProxyParameterWorkaround() {
    }

    public static void init() {
        MainArgumentsTransformer.getArgumentsListeners().add(args -> {
            boolean proxyDetected = false;
            ArrayList<String> filtered = new ArrayList<String>();
            for (int i = 0; i < ((String[])args).length; ++i) {
                if (i + 1 < ((String[])args).length && PROXY_PARAMETERS.contains(args[i])) {
                    proxyDetected = true;
                    Logging.log(Logging.Level.WARNING, "Dropping main argument " + args[i] + " " + args[i + 1]);
                    ++i;
                    continue;
                }
                filtered.add(args[i]);
            }
            if (proxyDetected) {
                Logging.log(Logging.Level.WARNING, "--proxyHost parameter conflicts with authlib-injector, therefore proxy is disabled.");
            }
            return filtered.toArray(new String[filtered.size()]);
        });
    }
}


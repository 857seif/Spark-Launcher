
package moe.yushi.authlibinjector.transform.support;

import moe.yushi.authlibinjector.APIMetadata;
import moe.yushi.authlibinjector.transform.support.MainArgumentsTransformer;
import moe.yushi.authlibinjector.util.Logging;

public final class AuthServerNameInjector {
    private AuthServerNameInjector() {
    }

    private static String getServerName(APIMetadata meta) {
        Object serverName = meta.getMeta().get("serverName");
        if (serverName instanceof String) {
            return (String)serverName;
        }
        return meta.getApiRoot();
    }

    public static void init(APIMetadata meta) {
        MainArgumentsTransformer.getArgumentsListeners().add(args -> {
            for (int i = 0; i < ((String[])args).length - 1; ++i) {
                if (!"--versionType".equals(args[i])) continue;
                String serverName = AuthServerNameInjector.getServerName(meta);
                Logging.log(Logging.Level.DEBUG, "Setting versionType to server name: " + serverName);
                args[i + 1] = serverName;
                break;
            }
            return args;
        });
    }
}


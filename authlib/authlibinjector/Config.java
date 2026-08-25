
package moe.yushi.authlibinjector;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import moe.yushi.authlibinjector.InitializationException;
import moe.yushi.authlibinjector.util.Logging;

public final class Config {
    public static boolean verboseLogging;
    public static boolean authlibLogging;
    public static boolean printUntransformedClass;
    public static boolean dumpClass;
    public static boolean httpdDisabled;
    public static Proxy mojangProxy;
    public static Set<String> ignoredPackages;
    public static FeatureOption mojangNamespace;
    public static FeatureOption legacySkinPolyfill;
    public static boolean noShowServerName;
    private static final String[] DEFAULT_IGNORED_PACKAGES;

    private Config() {
    }

    private static void initDebugOptions() {
        String prop = System.getProperty("authlibinjector.debug");
        if ("all".equals(prop)) {
            prop = "";
            Logging.log(Logging.Level.WARNING, "'-Dauthlibinjector.debug=all' is deprecated, use '-Dauthlibinjector.debug' instead");
        }
        if (prop != null) {
            if (prop.isEmpty()) {
                verboseLogging = true;
                authlibLogging = true;
            } else {
                String[] stringArray = prop.split(",");
                int n = stringArray.length;
                block12: for (int i = 0; i < n; ++i) {
                    String option;
                    switch (option = stringArray[i]) {
                        case "verbose": {
                            verboseLogging = true;
                            continue block12;
                        }
                        case "authlib": {
                            authlibLogging = true;
                            continue block12;
                        }
                        case "printUntransformed": {
                            printUntransformedClass = true;
                            verboseLogging = true;
                            continue block12;
                        }
                        case "dumpClass": {
                            dumpClass = true;
                            continue block12;
                        }
                        default: {
                            Logging.log(Logging.Level.ERROR, "Unrecognized debug option: " + option);
                            throw new InitializationException();
                        }
                    }
                }
            }
        }
    }

    private static void initIgnoredPackages() {
        HashSet<String> pkgs = new HashSet<String>();
        for (String pkg : DEFAULT_IGNORED_PACKAGES) {
            pkgs.add(pkg);
        }
        String propIgnoredPkgs = System.getProperty("authlibinjector.ignoredPackages");
        if (propIgnoredPkgs != null) {
            for (String pkg : propIgnoredPkgs.split(",")) {
                if ((pkg = pkg.trim()).isEmpty()) continue;
                pkgs.add(pkg);
            }
        }
        ignoredPackages = Collections.unmodifiableSet(pkgs);
    }

    private static void initMojangProxy() {
        Matcher matcher;
        String prop = System.getProperty("authlibinjector.mojangProxy");
        if (prop == null) {
            prop = System.getProperty("authlibinjector.mojang.proxy");
            if (prop == null) {
                return;
            }
            Logging.log(Logging.Level.WARNING, "'-Dauthlibinjector.mojang.proxy=' is deprecated, use '-Dauthlibinjector.mojangProxy=' instead");
        }
        if (!(matcher = Pattern.compile("^(?<protocol>[^:]+)://(?<host>[^/]+)+:(?<port>\\d+)$").matcher(prop)).find()) {
            Logging.log(Logging.Level.ERROR, "Unrecognized proxy URL: " + prop);
            throw new InitializationException();
        }
        String protocol = matcher.group("protocol");
        String host = matcher.group("host");
        int port = Integer.parseInt(matcher.group("port"));
        switch (protocol) {
            case "socks": {
                mojangProxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
                break;
            }
            default: {
                Logging.log(Logging.Level.ERROR, "Unsupported proxy protocol: " + protocol);
                throw new InitializationException();
            }
        }
        Logging.log(Logging.Level.INFO, "Mojang proxy: " + mojangProxy);
    }

    private static FeatureOption parseFeatureOption(String property) {
        String prop = System.getProperty(property);
        if (prop == null) {
            return FeatureOption.DEFAULT;
        }
        try {
            return FeatureOption.valueOf(prop.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            Logging.log(Logging.Level.ERROR, "Invalid option: " + prop);
            throw new InitializationException(e);
        }
    }

    static void init() {
        Config.initDebugOptions();
        Config.initIgnoredPackages();
        Config.initMojangProxy();
        mojangNamespace = Config.parseFeatureOption("authlibinjector.mojangNamespace");
        legacySkinPolyfill = Config.parseFeatureOption("authlibinjector.legacySkinPolyfill");
        httpdDisabled = System.getProperty("authlibinjector.disableHttpd") != null;
        noShowServerName = System.getProperty("authlibinjector.noShowServerName") != null;
    }

    static {
        DEFAULT_IGNORED_PACKAGES = new String[]{"moe.yushi.authlibinjector.", "java.", "javax.", "jdk.", "com.sun.", "sun.", "net.java.", "com.google.", "com.ibm.", "com.jcraft.jogg.", "com.jcraft.jorbis.", "com.oracle.", "com.paulscode.", "org.GNOME.", "org.apache.", "org.graalvm.", "org.jcp.", "org.json.", "org.lwjgl.", "moe.yushi.authlibinjector.internal.org.objectweb.asm.", "org.w3c.", "org.xml.", "org.yaml.snakeyaml.", "gnu.trove.", "io.netty.", "it.unimi.dsi.fastutil.", "javassist.", "jline.", "joptsimple.", "oracle.", "oshi.", "paulscode."};
    }

    public static enum FeatureOption {
        DEFAULT,
        ENABLED,
        DISABLED;


        public boolean isEnabled(boolean defaultValue) {
            return this == DEFAULT ? defaultValue : this == ENABLED;
        }
    }
}


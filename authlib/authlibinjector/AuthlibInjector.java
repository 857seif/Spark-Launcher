/*
 * Decompiled with CFR 0.152.
 */
package moe.yushi.authlibinjector;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import moe.yushi.authlibinjector.APIMetadata;
import moe.yushi.authlibinjector.Config;
import moe.yushi.authlibinjector.InitializationException;
import moe.yushi.authlibinjector.httpd.DefaultURLRedirector;
import moe.yushi.authlibinjector.httpd.LegacySkinAPIFilter;
import moe.yushi.authlibinjector.httpd.PrivilegesFilter;
import moe.yushi.authlibinjector.httpd.QueryProfileFilter;
import moe.yushi.authlibinjector.httpd.QueryUUIDsFilter;
import moe.yushi.authlibinjector.httpd.URLFilter;
import moe.yushi.authlibinjector.httpd.URLProcessor;
import moe.yushi.authlibinjector.transform.ClassTransformer;
import moe.yushi.authlibinjector.transform.DumpClassListener;
import moe.yushi.authlibinjector.transform.support.AuthServerNameInjector;
import moe.yushi.authlibinjector.transform.support.AuthlibLogInterceptor;
import moe.yushi.authlibinjector.transform.support.CitizensTransformer;
import moe.yushi.authlibinjector.transform.support.ConcatenateURLTransformUnit;
import moe.yushi.authlibinjector.transform.support.ConstantURLTransformUnit;
import moe.yushi.authlibinjector.transform.support.MC52974Workaround;
import moe.yushi.authlibinjector.transform.support.MC52974_1710Workaround;
import moe.yushi.authlibinjector.transform.support.MainArgumentsTransformer;
import moe.yushi.authlibinjector.transform.support.ProxyParameterWorkaround;
import moe.yushi.authlibinjector.transform.support.SkinWhitelistTransformUnit;
import moe.yushi.authlibinjector.transform.support.YggdrasilKeyTransformUnit;
import moe.yushi.authlibinjector.util.IOUtils;
import moe.yushi.authlibinjector.util.Logging;
import moe.yushi.authlibinjector.yggdrasil.CustomYggdrasilAPIProvider;
import moe.yushi.authlibinjector.yggdrasil.MojangYggdrasilAPIProvider;
import moe.yushi.authlibinjector.yggdrasil.YggdrasilClient;

public final class AuthlibInjector {
    private static boolean booted = false;
    private static Instrumentation instrumentation;
    private static boolean retransformSupported;
    private static ClassTransformer classTransformer;

    private AuthlibInjector() {
    }

    public static synchronized void bootstrap(Instrumentation instrumentation, String apiUrl) throws InitializationException {
        if (booted) {
            Logging.log(Logging.Level.INFO, "Already started, skipping");
            return;
        }
        booted = true;
        AuthlibInjector.instrumentation = Objects.requireNonNull(instrumentation);
        Config.init();
        retransformSupported = instrumentation.isRetransformClassesSupported();
        if (!retransformSupported) {
            Logging.log(Logging.Level.WARNING, "Retransform is not supported");
        }
        Logging.log(Logging.Level.INFO, "Version: " + AuthlibInjector.class.getPackage().getImplementationVersion());
        APIMetadata apiMetadata = AuthlibInjector.fetchAPIMetadata(apiUrl);
        classTransformer = AuthlibInjector.createTransformer(apiMetadata);
        instrumentation.addTransformer(classTransformer, retransformSupported);
        ProxyParameterWorkaround.init();
        MC52974Workaround.init();
        MC52974_1710Workaround.init();
        if (!Config.noShowServerName) {
            AuthServerNameInjector.init(apiMetadata);
        }
    }

    private static Optional<String> getPrefetchedResponse() {
        String prefetched = System.getProperty("authlibinjector.yggdrasil.prefetched");
        if (prefetched == null && (prefetched = System.getProperty("org.to2mbn.authlibinjector.config.prefetched")) != null) {
            Logging.log(Logging.Level.WARNING, "'-Dorg.to2mbn.authlibinjector.config.prefetched=' is deprecated, use '-Dauthlibinjector.yggdrasil.prefetched=' instead");
        }
        return Optional.ofNullable(prefetched);
    }

    private static APIMetadata fetchAPIMetadata(String apiUrl) {
        APIMetadata metadata;
        String metadataResponse;
        if (apiUrl == null || apiUrl.isEmpty()) {
            Logging.log(Logging.Level.ERROR, "No authentication server specified");
            throw new InitializationException();
        }
        apiUrl = AuthlibInjector.addHttpsIfMissing(apiUrl);
        Logging.log(Logging.Level.INFO, "Authentication server: " + apiUrl);
        AuthlibInjector.warnIfHttp(apiUrl);
        Optional<String> prefetched = AuthlibInjector.getPrefetchedResponse();
        if (prefetched.isPresent()) {
            Logging.log(Logging.Level.DEBUG, "Prefetched metadata detected");
            try {
                metadataResponse = new String(Base64.getDecoder().decode(IOUtils.removeNewLines(prefetched.get())), StandardCharsets.UTF_8);
            }
            catch (IllegalArgumentException e) {
                Logging.log(Logging.Level.ERROR, "Unable to decode metadata: " + e + "\nEncoded metadata:\n" + prefetched.get());
                throw new InitializationException(e);
            }
        }
        try {
            URL absoluteAli;
            HttpURLConnection connection = (HttpURLConnection)new URL(apiUrl).openConnection();
            String ali = connection.getHeaderField("x-authlib-injector-api-location");
            if (ali != null && !AuthlibInjector.urlEqualsIgnoreSlash(apiUrl, (absoluteAli = new URL(connection.getURL(), ali)).toString())) {
                try (InputStream in = connection.getInputStream();){
                    while (in.read() != -1) {
                    }
                }
                catch (IOException iOException) {
                    // empty catch block
                }
                Logging.log(Logging.Level.INFO, "Redirect to: " + absoluteAli);
                apiUrl = absoluteAli.toString();
                AuthlibInjector.warnIfHttp(apiUrl);
                connection = (HttpURLConnection)absoluteAli.openConnection();
            }
            try (InputStream in = connection.getInputStream();){
                metadataResponse = IOUtils.asString(IOUtils.asBytes(in));
            }
        }
        catch (IOException e) {
            Logging.log(Logging.Level.ERROR, "Failed to fetch metadata: " + e);
            throw new InitializationException(e);
        }
        Logging.log(Logging.Level.DEBUG, "Metadata: " + metadataResponse);
        if (!apiUrl.endsWith("/")) {
            apiUrl = apiUrl + "/";
        }
        try {
            metadata = APIMetadata.parse(apiUrl, metadataResponse);
        }
        catch (UncheckedIOException e) {
            Logging.log(Logging.Level.ERROR, "Unable to parse metadata: " + e.getCause() + "\nRaw metadata:\n" + metadataResponse);
            throw new InitializationException(e);
        }
        Logging.log(Logging.Level.DEBUG, "Parsed metadata: " + metadata);
        return metadata;
    }

    private static void warnIfHttp(String url) {
        if (url.toLowerCase().startsWith("http://")) {
            Logging.log(Logging.Level.WARNING, "You are using HTTP protocol, which is INSECURE! Please switch to HTTPS if possible.");
        }
    }

    private static String addHttpsIfMissing(String url) {
        String lowercased = url.toLowerCase();
        if (!lowercased.startsWith("http://") && !lowercased.startsWith("https://")) {
            url = "https://" + url;
        }
        return url;
    }

    private static boolean urlEqualsIgnoreSlash(String a, String b) {
        if (!a.endsWith("/")) {
            a = a + "/";
        }
        if (!b.endsWith("/")) {
            b = b + "/";
        }
        return a.equals(b);
    }

    private static List<URLFilter> createFilters(APIMetadata config) {
        boolean polyfillPrivilegesApi;
        boolean mojangNamespaceDefault;
        boolean legacySkinPolyfillDefault;
        if (Config.httpdDisabled) {
            Logging.log(Logging.Level.INFO, "Disabled local HTTP server");
            return Collections.emptyList();
        }
        ArrayList<URLFilter> filters = new ArrayList<URLFilter>();
        YggdrasilClient customClient = new YggdrasilClient(new CustomYggdrasilAPIProvider(config));
        YggdrasilClient mojangClient = new YggdrasilClient(new MojangYggdrasilAPIProvider(), Config.mojangProxy);
        boolean bl = legacySkinPolyfillDefault = !Boolean.TRUE.equals(config.getMeta().get("feature.legacy_skin_api"));
        if (Config.legacySkinPolyfill.isEnabled(legacySkinPolyfillDefault)) {
            filters.add(new LegacySkinAPIFilter(customClient));
        } else {
            Logging.log(Logging.Level.INFO, "Disabled legacy skin API polyfill");
        }
        boolean bl2 = mojangNamespaceDefault = !Boolean.TRUE.equals(config.getMeta().get("feature.no_mojang_namespace"));
        if (Config.mojangNamespace.isEnabled(mojangNamespaceDefault)) {
            filters.add(new QueryUUIDsFilter(mojangClient, customClient));
            filters.add(new QueryProfileFilter(mojangClient, customClient));
        } else {
            Logging.log(Logging.Level.INFO, "Disabled Mojang namespace");
        }
        boolean bl3 = polyfillPrivilegesApi = !Boolean.TRUE.equals(config.getMeta().get("feature.privileges_api"));
        if (polyfillPrivilegesApi) {
            Logging.log(Logging.Level.INFO, "Polyfill privileges API");
            filters.add(new PrivilegesFilter());
        }
        return filters;
    }

    private static ClassTransformer createTransformer(APIMetadata config) {
        URLProcessor urlProcessor = new URLProcessor(AuthlibInjector.createFilters(config), new DefaultURLRedirector(config));
        ClassTransformer transformer = new ClassTransformer();
        transformer.ignores.addAll(Config.ignoredPackages);
        if (Config.dumpClass) {
            transformer.listeners.add(new DumpClassListener(Paths.get("", new String[0]).toAbsolutePath()));
        }
        if (Config.authlibLogging) {
            transformer.units.add(new AuthlibLogInterceptor());
        }
        transformer.units.add(new MainArgumentsTransformer());
        transformer.units.add(new ConstantURLTransformUnit(urlProcessor));
        transformer.units.add(new CitizensTransformer());
        transformer.units.add(new ConcatenateURLTransformUnit());
        transformer.units.add(new SkinWhitelistTransformUnit());
        SkinWhitelistTransformUnit.getWhitelistedDomains().addAll(config.getSkinDomains());
        transformer.units.add(new YggdrasilKeyTransformUnit());
        config.getDecodedPublickey().ifPresent(YggdrasilKeyTransformUnit.PUBLIC_KEYS::add);
        return transformer;
    }

    public static void retransformClasses(String ... classNames) {
        if (!retransformSupported) {
            return;
        }
        HashSet<String> classNamesSet = new HashSet<String>(Arrays.asList(classNames));
        Object[] classes = (Class[])Stream.of(instrumentation.getAllLoadedClasses()).filter(clazz -> classNamesSet.contains(clazz.getName())).filter(AuthlibInjector::canRetransformClass).toArray(Class[]::new);
        if (classes.length > 0) {
            Logging.log(Logging.Level.INFO, "Attempt to retransform classes: " + Arrays.toString(classes));
            try {
                instrumentation.retransformClasses((Class<?>[])classes);
            }
            catch (Throwable e) {
                Logging.log(Logging.Level.WARNING, "Failed to retransform", e);
            }
        }
    }

    public static void retransformAllClasses() {
        if (!retransformSupported) {
            return;
        }
        Logging.log(Logging.Level.INFO, "Attempt to retransform all classes");
        long t0 = System.currentTimeMillis();
        Class[] classes = (Class[])Stream.of(instrumentation.getAllLoadedClasses()).filter(AuthlibInjector::canRetransformClass).toArray(Class[]::new);
        if (classes.length > 0) {
            try {
                instrumentation.retransformClasses(classes);
            }
            catch (Throwable e) {
                Logging.log(Logging.Level.WARNING, "Failed to retransform", e);
                return;
            }
        }
        long t1 = System.currentTimeMillis();
        Logging.log(Logging.Level.INFO, "Retransformed " + classes.length + " classes in " + (t1 - t0) + "ms");
    }

    private static boolean canRetransformClass(Class<?> clazz) {
        if (!instrumentation.isModifiableClass(clazz)) {
            return false;
        }
        String name = clazz.getName();
        for (String prefix : Config.ignoredPackages) {
            if (!name.startsWith(prefix)) continue;
            return false;
        }
        return true;
    }

    public static ClassTransformer getClassTransformer() {
        return classTransformer;
    }
}


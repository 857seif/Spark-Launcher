
package moe.yushi.authlibinjector.transform.support;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Stream;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.Type;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.transform.CallbackSupport;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.util.Logging;

public class AuthlibLogInterceptor
implements TransformUnit {
    private static Set<ClassLoader> interceptedClassloaders = Collections.newSetFromMap(new WeakHashMap());

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @CallbackMethod
    public static void onClassLoading(ClassLoader classLoader) {
        Class<?> classLogManager;
        try {
            classLogManager = classLoader.loadClass("org.apache.logging.log4j.LogManager");
        }
        catch (ClassNotFoundException e) {
            return;
        }
        ClassLoader clLog4j = classLogManager.getClassLoader();
        Set<ClassLoader> set = interceptedClassloaders;
        synchronized (set) {
            if (!interceptedClassloaders.add(clLog4j)) {
                return;
            }
        }
        try {
            AuthlibLogInterceptor.registerLogHandle(clLog4j);
            Logging.log(Logging.Level.INFO, "Registered log handler on " + clLog4j);
        }
        catch (Throwable e) {
            Logging.log(Logging.Level.WARNING, "Failed to register log handler on " + clLog4j, e);
        }
    }

    private static void registerLogHandle(ClassLoader cl) throws ReflectiveOperationException {
        HashMap<String, Object> values;
        Object appender;
        Object patternLayout;
        String appenderName = "AUTHLIB_INJECTOR_CONSOLE_APPENDER";
        String loggerName = "AUTHLIB_INJECTOR_AUTHLIB_LOGGER";
        String authlibPackageName = "com.mojang.authlib";
        Class<?> classLayout = cl.loadClass("org.apache.logging.log4j.core.Layout");
        Class<?> classAppender = cl.loadClass("org.apache.logging.log4j.core.Appender");
        Class<?> classAppenderRef = cl.loadClass("org.apache.logging.log4j.core.config.AppenderRef");
        Class<?> classLevel = cl.loadClass("org.apache.logging.log4j.Level");
        Class<?> classFilter = cl.loadClass("org.apache.logging.log4j.core.Filter");
        Class<?> classLoggerConfig = cl.loadClass("org.apache.logging.log4j.core.config.LoggerConfig");
        Class<?> classConfiguration = cl.loadClass("org.apache.logging.log4j.core.config.Configuration");
        Class<?> classPatternLayout = cl.loadClass("org.apache.logging.log4j.core.layout.PatternLayout");
        Class<?> classConsoleAppender = cl.loadClass("org.apache.logging.log4j.core.appender.ConsoleAppender");
        Object loggerContext = cl.loadClass("org.apache.logging.log4j.LogManager").getDeclaredMethod("getContext", Boolean.TYPE).invoke(null, false);
        Object configuration = cl.loadClass("org.apache.logging.log4j.core.LoggerContext").getMethod("getConfiguration", new Class[0]).invoke(loggerContext, new Object[0]);
        try {
            Object builder = classPatternLayout.getDeclaredMethod("newBuilder", new Class[0]).invoke(null, new Object[0]);
            Class<?> classBuilder = cl.loadClass("org.apache.logging.log4j.core.layout.PatternLayout$Builder");
            patternLayout = classBuilder.getMethod("build", new Class[0]).invoke(builder, new Object[0]);
        }
        catch (NoSuchMethodException ex) {
            HashMap<String, Object> values2 = new HashMap<String, Object>();
            values2.put("alwaysWriteExceptions", true);
            values2.put("noConsoleNoAnsi", true);
            patternLayout = AuthlibLogInterceptor.invokeCreateMethod(classPatternLayout, "createLayout", configuration, values2);
        }
        try {
            Object buider = classConsoleAppender.getDeclaredMethod("newBuilder", new Class[0]).invoke(null, new Object[0]);
            Class<?> classBuilder = cl.loadClass("org.apache.logging.log4j.core.appender.ConsoleAppender$Builder");
            classBuilder.getMethod("withLayout", classLayout).invoke(buider, patternLayout);
            classBuilder.getMethod("withName", String.class).invoke(buider, "AUTHLIB_INJECTOR_CONSOLE_APPENDER");
            appender = classBuilder.getMethod("build", new Class[0]).invoke(buider, new Object[0]);
        }
        catch (NoSuchMethodException ex) {
            values = new HashMap<String, Object>();
            values.put("Layout", patternLayout);
            values.put("name", "AUTHLIB_INJECTOR_CONSOLE_APPENDER");
            values.put("follow", false);
            values.put("direct", false);
            values.put("ignoreExceptions", true);
            appender = AuthlibLogInterceptor.invokeCreateMethod(classConsoleAppender, "createAppender", configuration, values);
        }
        classAppender.getMethod("start", new Class[0]).invoke(appender, new Object[0]);
        values = new HashMap();
        values.put("ref", "AUTHLIB_INJECTOR_CONSOLE_APPENDER");
        Object appenderRef = AuthlibLogInterceptor.invokeCreateMethod(classAppenderRef, "createAppenderRef", configuration, values);
        Object appenderRefs = Array.newInstance(classAppenderRef, 1);
        Array.set(appenderRefs, 0, appenderRef);
        HashMap<String, Object> values3 = new HashMap<String, Object>();
        values3.put("additivity", false);
        values3.put("level", classLevel.getDeclaredField("ALL").get(null));
        values3.put("name", "AUTHLIB_INJECTOR_AUTHLIB_LOGGER");
        values3.put("includeLocation", "com.mojang.authlib");
        values3.put("AppenderRef", appenderRefs);
        Object loggerConfig = AuthlibLogInterceptor.invokeCreateMethod(classLoggerConfig, "createLogger", configuration, values3);
        classLoggerConfig.getMethod("addAppender", classAppender, classLevel, classFilter).invoke(loggerConfig, appender, null, null);
        try {
            classConfiguration.getMethod("addAppender", classAppender).invoke(configuration, appender);
        }
        catch (NoSuchMethodException e) {
            ((Map)classConfiguration.getMethod("getAppenders", new Class[0]).invoke(configuration, new Object[0])).put("AUTHLIB_INJECTOR_CONSOLE_APPENDER", appender);
        }
        try {
            classConfiguration.getMethod("addLogger", String.class, classLoggerConfig).invoke(configuration, "com.mojang.authlib", loggerConfig);
        }
        catch (NoSuchMethodException e) {
            Class<?> classBaseConfiguration = cl.loadClass("org.apache.logging.log4j.core.config.BaseConfiguration");
            Field fieldLoggers = classBaseConfiguration.getDeclaredField("loggers");
            fieldLoggers.setAccessible(true);
            ((Map)fieldLoggers.get(configuration)).put("com.mojang.authlib", loggerConfig);
            Method methodSetParents = classBaseConfiguration.getDeclaredMethod("setParents", new Class[0]);
            methodSetParents.setAccessible(true);
            methodSetParents.invoke(configuration, new Object[0]);
        }
        cl.loadClass("org.apache.logging.log4j.core.LoggerContext").getMethod("updateLoggers", classConfiguration).invoke(loggerContext, configuration);
    }

    private static Object invokeCreateMethod(Class<?> owner, String methodName, Object config, Map<String, Object> values) throws ReflectiveOperationException {
        ClassLoader cl = owner.getClassLoader();
        Class<?> classConfiguration = cl.loadClass("org.apache.logging.log4j.core.config.Configuration");
        Class<?> classPluginAttribute = cl.loadClass("org.apache.logging.log4j.core.config.plugins.PluginAttribute");
        Method methodPluginAttributeValue = classPluginAttribute.getMethod("value", new Class[0]);
        Class<?> classPluginElement = cl.loadClass("org.apache.logging.log4j.core.config.plugins.PluginElement");
        Method methodPluginElementValue = classPluginElement.getMethod("value", new Class[0]);
        Class<?> classPluginFactory = cl.loadClass("org.apache.logging.log4j.core.config.plugins.PluginFactory");
        Method method = Stream.of(owner.getDeclaredMethods()).filter(it -> it.getName().equals(methodName)).filter(it -> it.getDeclaredAnnotation(classPluginFactory) != null).findFirst().orElseThrow(NoSuchMethodException::new);
        Object[] input = new Object[method.getParameterCount()];
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; ++i) {
            Class<?> type = parameters[i].getType();
            String key = null;
            Object annotation = parameters[i].getDeclaredAnnotation(classPluginAttribute);
            if (annotation != null) {
                key = (String)methodPluginAttributeValue.invoke(annotation, new Object[0]);
            } else {
                annotation = parameters[i].getDeclaredAnnotation(classPluginElement);
                if (annotation != null) {
                    key = (String)methodPluginElementValue.invoke(annotation, new Object[0]);
                }
            }
            if (key == null) {
                if (!classConfiguration.isAssignableFrom(type)) continue;
                input[i] = config;
                continue;
            }
            Object value = values.get(key);
            if (value == null) continue;
            if (type.isPrimitive() || type.isInstance(value)) {
                input[i] = value;
                continue;
            }
            if (type != String.class) continue;
            input[i] = value.toString();
        }
        return method.invoke(null, input);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public Optional<ClassVisitor> transform(ClassLoader classLoader, final String className, ClassVisitor writer, final TransformContext ctx) {
        if (className.startsWith("com.mojang.authlib.")) {
            Set<ClassLoader> set = interceptedClassloaders;
            synchronized (set) {
                if (interceptedClassloaders.contains(classLoader)) {
                    return Optional.empty();
                }
            }
            return Optional.of(new ClassVisitor(589824, writer){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("<clinit>".equals(name)) {
                        return new MethodVisitor(589824, super.visitMethod(access, name, descriptor, signature, exceptions)){

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                super.visitLdcInsn(Type.getType("L" + className.replace('.', '/') + ";"));
                                super.visitMethodInsn(182, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader;", false);
                                CallbackSupport.invoke(ctx, this.mv, AuthlibLogInterceptor.class, "onClassLoading");
                                ctx.markModified();
                            }
                        };
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            });
        }
        return Optional.empty();
    }

    public String toString() {
        return "Authlib Log Interceptor";
    }
}


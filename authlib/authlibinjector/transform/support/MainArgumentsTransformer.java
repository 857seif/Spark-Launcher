
package moe.yushi.authlibinjector.transform.support;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.ClassVisitor;
import moe.yushi.authlibinjector.internal.org.objectweb.asm.MethodVisitor;
import moe.yushi.authlibinjector.transform.CallbackMethod;
import moe.yushi.authlibinjector.transform.CallbackSupport;
import moe.yushi.authlibinjector.transform.TransformContext;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.util.Logging;

public class MainArgumentsTransformer
implements TransformUnit {
    private static final List<Function<String[], String[]>> ARGUMENTS_LISTENERS = new CopyOnWriteArrayList<Function<String[], String[]>>();
    private static final List<Consumer<String>> VERSION_SERIES_LISTENERS = new CopyOnWriteArrayList<Consumer<String>>();

    @Override
    public Optional<ClassVisitor> transform(ClassLoader classLoader, String className, ClassVisitor writer, final TransformContext ctx) {
        if ("net.minecraft.client.main.Main".equals(className)) {
            return Optional.of(new ClassVisitor(589824, writer){

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ("main".equals(name) && "([Ljava/lang/String;)V".equals(descriptor)) {
                        return new MethodVisitor(589824, super.visitMethod(access, name, descriptor, signature, exceptions)){

                            @Override
                            public void visitCode() {
                                super.visitCode();
                                ctx.markModified();
                                super.visitVarInsn(25, 0);
                                CallbackSupport.invoke(ctx, this.mv, MainArgumentsTransformer.class, "processMainArguments");
                                super.visitVarInsn(58, 0);
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
        return "Main Arguments Transformer";
    }

    @CallbackMethod
    public static String[] processMainArguments(String[] args) {
        Logging.log(Logging.Level.DEBUG, "Original main arguments: " + Stream.of(args).collect(Collectors.joining(" ")));
        String[] result = args;
        for (Function<String[], String[]> listener : ARGUMENTS_LISTENERS) {
            result = listener.apply(result);
        }
        Logging.log(Logging.Level.DEBUG, "Transformed main arguments: " + Stream.of(result).collect(Collectors.joining(" ")));
        return result;
    }

    public static List<Function<String[], String[]>> getArgumentsListeners() {
        return ARGUMENTS_LISTENERS;
    }

    public static Optional<String> inferVersionSeries(String[] args) {
        boolean hit = false;
        for (String arg : args) {
            if (hit) {
                if (arg.startsWith("--")) {
                    hit = false;
                } else {
                    return Optional.of(arg);
                }
            }
            if (!"--assetIndex".equals(arg)) continue;
            hit = true;
        }
        return Optional.empty();
    }

    public static List<Consumer<String>> getVersionSeriesListeners() {
        return VERSION_SERIES_LISTENERS;
    }

    static {
        MainArgumentsTransformer.getArgumentsListeners().add(args -> {
            MainArgumentsTransformer.inferVersionSeries(args).ifPresent(versionSeries -> {
                Logging.log(Logging.Level.DEBUG, "Version series detected: " + versionSeries);
                VERSION_SERIES_LISTENERS.forEach(listener -> listener.accept(versionSeries));
            });
            return args;
        });
    }
}


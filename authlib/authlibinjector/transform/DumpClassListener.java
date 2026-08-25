
package moe.yushi.authlibinjector.transform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import moe.yushi.authlibinjector.transform.ClassLoadingListener;
import moe.yushi.authlibinjector.transform.TransformUnit;
import moe.yushi.authlibinjector.util.Logging;

public class DumpClassListener
implements ClassLoadingListener {
    private Path outputPath;

    public DumpClassListener(Path outputPath) {
        this.outputPath = outputPath;
    }

    @Override
    public void onClassLoading(ClassLoader classLoader, String className, byte[] bytecode, List<TransformUnit> appliedTransformers) {
        if (!appliedTransformers.isEmpty()) {
            Path dumpFile = this.outputPath.resolve(className + "_dump.class");
            try {
                Files.write(dumpFile, bytecode, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                Logging.log(Logging.Level.INFO, "Transformed class is dumped to [" + dumpFile + "]");
            }
            catch (IOException e) {
                Logging.log(Logging.Level.WARNING, "Failed to dump class [" + className + "]", e);
            }
        }
    }
}


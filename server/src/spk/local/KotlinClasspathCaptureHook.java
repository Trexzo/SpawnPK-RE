package spk.local;

import java.nio.file.Path;

/** Deterministic package-local seam for Kotlin classpath capture regressions. */
@FunctionalInterface
interface KotlinClasspathCaptureHook {
    void beforeCopy(
        Path root,
        Path target,
        int index
    ) throws Exception;
}

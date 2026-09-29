package spk.local

import java.io.File
import java.io.InputStream
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.jar.Attributes
import java.util.jar.JarFile
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies
import kotlin.script.experimental.jvm.updateClasspath
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.templates.standard.SimpleScriptTemplate
import spk.plugin.api.Plugin
import spk.plugin.api.PluginContext
import spk.plugin.api.PluginManifest

/**
 * Kotlin .kts source loader.
 *
 * Scripts compile against an explicit allowlist classpath supplied by the
 * server. They do not inherit the server implementation classpath.
 */
internal class KotlinPluginLoader @JvmOverloads constructor(
    apiJar: Path,
    compileClasspath: List<Path>,
    private val captureHook: KotlinClasspathCaptureHook? = null
) : PluginLoader {
    private val apiJar: Path =
        apiJar.toAbsolutePath().normalize()
    private val compileClasspath: List<Path>
    private val host = BasicJvmScriptingHost()

    init {
        require(Files.isRegularFile(this.apiJar)) {
            "plugin API JAR missing: ${this.apiJar}"
        }

        validateApiJar(this.apiJar)

        val normalized = LinkedHashSet<Path>()
        normalized.add(this.apiJar)

        for (entry in compileClasspath) {
            val path = entry.toAbsolutePath().normalize()
            require(Files.isRegularFile(path)) {
                "Kotlin script compile dependency missing: $path"
            }

            val name =
                path.fileName?.toString()
                    ?.lowercase(Locale.ROOT)
                    ?: ""

            require(name != "spawnpklocalserver.jar") {
                "server implementation JAR is forbidden on Kotlin script compile classpath: $path"
            }

            validateDependencyJar(path)

            normalized.add(path)
        }

        this.compileClasspath =
            normalized.toList()
    }

    override fun supports(source: PluginSource?): Boolean {
        if (source == null || source.hasEntrypoint()) {
            return false
        }

        val name =
            source.path().fileName
                ?.toString()
                ?.lowercase(Locale.ROOT)
                ?: return false

        return name.endsWith(".kts")
    }

    override fun load(source: PluginSource): PluginRuntime {
        require(supports(source)) {
            "unsupported Kotlin script source: ${source.path()}"
        }

        val script = source.path()
        val scriptText = source.requireScriptText()
        val snapshot =
            ClasspathSnapshot.capture(
                compileClasspath,
                captureHook
            )
        var dependencyLoader: URLClassLoader? = null

        try {
            val files = snapshot.files

            validateApiJar(
                files.first().toPath()
            )

            for (file in files.drop(1)) {
                validateDependencyJar(
                    file.toPath()
                )
            }

            val compilation =
                createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
                    updateClasspath(files)
                    compilerOptions(
                        "-jvm-target",
                        "11",
                        "-Xjdk-release=11"
                    )
                }

            val apiParent =
                ScriptApiClassLoader(
                    Plugin::class.java.classLoader
                )

            val ownedDependencyLoader =
                URLClassLoader(
                    files.map {
                        it.toURI().toURL()
                    }.toTypedArray(),
                    apiParent
                )
            dependencyLoader =
                ownedDependencyLoader

            val evaluation =
                ScriptEvaluationConfiguration {
                    jvm {
                        baseClassLoader(
                            ownedDependencyLoader
                        )
                        loadDependencies(false)
                    }
                }

            val evaluated =
                host.eval(
                    scriptText.toScriptSource(
                        script.toString()
                    ),
                    compilation,
                    evaluation
                )

            if (evaluated is ResultWithDiagnostics.Failure) {
                throw IllegalArgumentException(
                    diagnosticMessage(
                        script,
                        evaluated.reports
                    )
                )
            }

            val result =
                evaluated.valueOrNull()
                    ?: throw IllegalArgumentException(
                        "Kotlin script produced no evaluation result: $script"
                    )

            val value =
                when (val returned = result.returnValue) {
                    is ResultValue.Value ->
                        returned.value
                    is ResultValue.Error ->
                        throw IllegalArgumentException(
                            "Kotlin plugin script evaluation failed: $script errorClass=" +
                                returned.error.javaClass.name,
                            returned.error
                        )
                    else ->
                        throw IllegalArgumentException(
                            "Kotlin plugin script must end with a Plugin expression: $script result=" +
                                returned::class.java.name
                        )
                }

            val plugin =
                value as? Plugin
                    ?: throw IllegalArgumentException(
                        "Kotlin plugin script result does not implement Plugin: $script resultClass=" +
                            (value?.javaClass?.name ?: "<null>")
                    )

            return LoadedKotlinScript(
                plugin,
                script,
                ownedDependencyLoader,
                snapshot
            )
        } catch (failure: Throwable) {
            try {
                dependencyLoader?.close()
            } catch (cleanup: Throwable) {
                suppressIfDistinct(
                    failure,
                    cleanup
                )
            }

            snapshot.retire(
                failure
            )

            throw failure
        }
    }

    private fun validateApiJar(path: Path) {
        JarFile(path.toFile(), false).use { jar ->
            validateManifestClasspath(
                path,
                jar
            )

            val required =
                PluginApiExportContract
                    .resources()
            val seen =
                LinkedHashSet<String>()
            val entries =
                jar.entries()

            while (entries.hasMoreElements()) {
                val entry =
                    entries.nextElement()

                if (entry.isDirectory) {
                    continue
                }

                val name =
                    entry.name
                        .replace('\\', '/')
                validateJarIndexEntry(
                    path,
                    name
                )

                val effectiveName =
                    effectiveVersionedResource(
                        name
                    ) ?: name

                if (!effectiveName.endsWith(".class")) {
                    require(
                        name ==
                            "META-INF/MANIFEST.MF"
                    ) {
                        "plugin API JAR contains unsupported non-class resource: " +
                            effectiveName +
                            " archiveEntry=" + name
                    }
                    continue
                }

                if (effectiveName.startsWith("spk/")) {
                    require(
                        effectiveName.startsWith("spk/plugin/api/") ||
                            effectiveName.startsWith("spk/content/api/") ||
                            exportedEventResource(
                                effectiveName
                            )
                    ) {
                        "plugin API JAR exposes non-public SpawnPK namespace: " +
                            effectiveName +
                            " archiveEntry=" + name
                    }
                }

                require(
                    PluginApiExportContract
                        .contains(
                            effectiveName
                        )
                ) {
                    "plugin API JAR class is outside the official exported API set: " +
                        effectiveName +
                        " archiveEntry=" + name
                }

                require(
                    seen.add(
                        effectiveName
                    )
                ) {
                    "plugin API JAR contains duplicate effective API class authority: " +
                        effectiveName +
                        " archiveEntry=" + name
                }

                val expected =
                    Plugin::class.java.classLoader
                        .getResourceAsStream(
                            effectiveName
                        )
                        ?: throw IllegalArgumentException(
                            "Kotlin API server resource missing: " +
                                effectiveName +
                                " archiveEntry=" + name
                        )

                expected.use { trusted ->
                    jar.getInputStream(entry)
                        .use { candidate ->
                            require(
                                streamsEqual(
                                    trusted,
                                    candidate
                                )
                            ) {
                                "Kotlin API class does not match server API: " +
                                    path +
                                    " entry=" + name +
                                    " effective=" + effectiveName
                            }
                        }
                }
            }

            val missing =
                LinkedHashSet<String>(
                    required
                )
            missing.removeAll(
                seen
            )

            require(
                missing.isEmpty()
            ) {
                "plugin API JAR is missing required exported API classes: " +
                    missing.joinToString(
                        ","
                    )
            }
        }
    }

    private fun validateDependencyJar(path: Path) {
        JarFile(path.toFile(), false).use { jar ->
            validateManifestClasspath(
                path,
                jar
            )
            val entries = jar.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()

                if (!entry.isDirectory) {
                    val name =
                        entry.name
                            .replace('\\', '/')
                    validateJarIndexEntry(
                        path,
                        name
                    )
                    val effectiveName =
                        effectiveVersionedResource(
                            name
                        ) ?: name

                    if (effectiveName.startsWith("spk/")) {
                        require(
                            exportedDslResource(
                                effectiveName
                            )
                        ) {
                            "Kotlin script dependency contains SpawnPK classes outside the DSL allowlist: " +
                                path + " entry=" + entry.name +
                                " effective=" + effectiveName
                        }

                        val expected =
                            Plugin::class.java.classLoader
                                .getResourceAsStream(
                                    effectiveName
                                )
                                ?: throw IllegalArgumentException(
                                    "Kotlin DSL server resource missing: $effectiveName"
                                )

                        expected.use { trusted ->
                            jar.getInputStream(entry)
                                .use { candidate ->
                                    require(
                                        streamsEqual(
                                            trusted,
                                            candidate
                                        )
                                    ) {
                                        "Kotlin DSL dependency class does not match server SDK: " +
                                            path + " entry=" + entry.name +
                                            " effective=" + effectiveName
                                    }
                                }
                        }
                    }
                }
            }
        }
    }

    private fun validateJarIndexEntry(
        path: Path,
        name: String
    ) {
        require(
            name != "META-INF/INDEX.LIST"
        ) {
            "Kotlin classpath archive JAR Index is forbidden: " +
                path + " entry=" + name
        }
    }

    private fun effectiveVersionedResource(
        name: String
    ): String? {
        val prefix = "META-INF/versions/"

        if (!name.startsWith(prefix)) {
            return null
        }

        val versionEnd =
            name.indexOf(
                '/',
                prefix.length
            )

        if (versionEnd <= prefix.length ||
            versionEnd + 1 >= name.length) {
            return null
        }

        val version =
            name.substring(
                prefix.length,
                versionEnd
            )

        if (version.any { !it.isDigit() }) {
            return null
        }

        return name.substring(
            versionEnd + 1
        )
    }

    private fun validateManifestClasspath(
        path: Path,
        jar: JarFile
    ) {
        val classPath =
            BoundedManifestMain
                .readMainAttributes(
                    jar,
                    path
                )
                ?.getValue(
                    Attributes.Name.CLASS_PATH
                )
                ?.trim()

        require(
            classPath.isNullOrEmpty()
        ) {
            "Kotlin classpath archive manifest Class-Path is forbidden: " +
                path + " value=" + classPath
        }
    }

    private fun streamsEqual(
        trusted: InputStream,
        candidate: InputStream
    ): Boolean {
        val trustedBuffer = ByteArray(8192)
        val candidateBuffer = ByteArray(8192)

        while (true) {
            val trustedRead =
                trusted.readNBytes(
                    trustedBuffer,
                    0,
                    trustedBuffer.size
                )
            val candidateRead =
                candidate.readNBytes(
                    candidateBuffer,
                    0,
                    candidateBuffer.size
                )

            if (trustedRead != candidateRead) {
                return false
            }

            if (trustedRead == 0) {
                return true
            }

            for (index in 0 until trustedRead) {
                if (trustedBuffer[index] !=
                    candidateBuffer[index]) {
                    return false
                }
            }
        }
    }

    private fun exportedEventResource(name: String): Boolean =
        name == "spk/event/DomainEventBus.class" ||
            name == "spk/event/DomainEventBus\$Event.class" ||
            name == "spk/event/DomainEventBus\$Cancellable.class" ||
            name == "spk/event/DomainEventBus\$Priority.class" ||
            name == "spk/event/DomainEventBus\$Listener.class" ||
            name == "spk/event/DomainEventBus\$Subscription.class"

    private fun exportedDslResource(
        name: String
    ): Boolean =
        name ==
            "spk/plugin/kotlin/KotlinPluginDslKt.class" ||
            name.startsWith(
                "spk/plugin/kotlin/KotlinPluginDslKt\$"
            )


    private fun diagnosticMessage(
        script: Path,
        reports: List<ScriptDiagnostic>
    ): String {
        val detail =
            reports.asSequence()
                .filter {
                    it.severity >=
                        ScriptDiagnostic.Severity.WARNING
                }
                .take(8)
                .joinToString(" | ") {
                    it.message
                        .replace('\n', ' ')
                        .replace('\r', ' ')
                        .take(320)
                }

        return "Kotlin plugin script compilation failed: $script" +
            if (detail.isEmpty()) "" else " diagnostics=$detail"
    }

    private class LoadedKotlinScript(
        delegate: Plugin,
        private val source: Path,
        dependencyLoader: URLClassLoader,
        classpathSnapshot: ClasspathSnapshot
    ) : PluginRuntime {
        @Volatile
        private var delegate: Plugin? = delegate

        @Volatile
        private var callbackLoader: ClassLoader? =
            delegate.javaClass.classLoader

        @Volatile
        private var baseLoader: ClassLoader? =
            dependencyLoader

        @Volatile
        private var dependencyLoader: URLClassLoader? =
            dependencyLoader

        @Volatile
        private var classpathSnapshot: ClasspathSnapshot? =
            classpathSnapshot

        @Volatile
        private var closed = false

        override fun manifest(): PluginManifest =
            requireDelegate().manifest()

        override fun enable(context: PluginContext) {
            requireDelegate().enable(context)
        }

        override fun disable() {
            requireDelegate().disable()
        }

        override fun callbackClassLoader(): ClassLoader =
            callbackLoader
                ?: throw IllegalStateException(
                    "Kotlin plugin runtime closed: $source"
                )

        @Synchronized
        override fun close() {
            if (closed) {
                return
            }

            closed = true

            val ownedDependencyLoader =
                dependencyLoader
            val ownedSnapshot =
                classpathSnapshot
            var failure: Throwable? = null

            delegate = null
            callbackLoader = null
            baseLoader = null
            dependencyLoader = null
            classpathSnapshot = null

            try {
                ownedDependencyLoader?.close()
            } catch (cleanup: Throwable) {
                failure = cleanup
            }

            if (ownedSnapshot != null) {
                failure =
                    ownedSnapshot.retire(
                        failure
                    )
            }

            if (failure != null) {
                throw failure
            }
        }

        private fun requireDelegate(): Plugin =
            delegate
                ?: throw IllegalStateException(
                    "Kotlin plugin runtime closed: $source"
                )
    }

    private class ClasspathSnapshot private constructor(
        val root: Path,
        val files: List<File>
    ) {
        private var retired = false

        @Synchronized
        fun retire(
            primary: Throwable?
        ): Throwable? {
            if (retired) {
                return primary
            }

            retired = true

            val paths =
                files.map(File::toPath)
            val cleanup =
                KotlinClasspathCleanupDebt
                    .retire(
                        root,
                        paths
                    )

            if (cleanup == null) {
                return primary
            }

            KotlinClasspathCleanupDebt
                .register(
                    root,
                    paths
                )

            if (primary == null) {
                return cleanup
            }

            suppressIfDistinct(
                primary,
                cleanup
            )
            return primary
        }

        companion object {
            fun capture(
                originals: List<Path>,
                hook: KotlinClasspathCaptureHook?
            ): ClasspathSnapshot {
                val root =
                    Files.createTempDirectory(
                        "spawnpk-kotlin-classpath-"
                    )
                val files =
                    ArrayList<File>()

                try {
                    originals.forEachIndexed {
                        index,
                        original ->

                        val role =
                            if (index == 0) {
                                "api"
                            } else {
                                "dependency"
                            }
                        val target =
                            root.resolve(
                                index.toString()
                                    .padStart(
                                        3,
                                        '0'
                                    ) +
                                    "-" +
                                    role +
                                    ".jar"
                            )

                        files.add(
                            target.toFile()
                        )

                        hook?.beforeCopy(
                            root,
                            target,
                            index
                        )

                        Files.newInputStream(
                            original
                        ).use { input ->
                            Files.newOutputStream(
                                target,
                                StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE
                            ).use { output ->
                                val buffer =
                                    ByteArray(8192)

                                while (true) {
                                    val read =
                                        input.read(
                                            buffer
                                        )

                                    if (read < 0) {
                                        break
                                    }

                                    output.write(
                                        buffer,
                                        0,
                                        read
                                    )
                                }
                            }
                        }
                    }

                    return ClasspathSnapshot(
                        root,
                        files.toList()
                    )
                } catch (failure: Throwable) {
                    val paths =
                        files.map(File::toPath)
                    val cleanup =
                        KotlinClasspathCleanupDebt
                            .retire(
                                root,
                                paths
                            )

                    if (cleanup != null) {
                        KotlinClasspathCleanupDebt
                            .register(
                                root,
                                paths
                            )
                        suppressIfDistinct(
                            failure,
                            cleanup
                        )
                    }

                    throw failure
                }
            }
        }
    }

    private companion object {
        fun suppressIfDistinct(
            primary: Throwable,
            cleanup: Throwable
        ) {
            if (primary !== cleanup) {
                primary.addSuppressed(
                    cleanup
                )
            }
        }
    }

    /**
     * Runtime parent that exposes platform + Kotlin runtime + stable plugin API
     * identity while denying all other SpawnPK implementation classes.
     */
    private class ScriptApiClassLoader(
        private val delegate: ClassLoader
    ) : ClassLoader(null) {
        override fun loadClass(
            name: String,
            resolve: Boolean
        ): Class<*> {
            if (!allowed(name)) {
                throw ClassNotFoundException(
                    "server namespace is not exported to Kotlin scripts: $name"
                )
            }

            return delegate.loadClass(name)
        }

        override fun getResource(name: String): java.net.URL? {
            if (name.startsWith("spk/") &&
                !exportedServerResource(name)) {
                return null
            }

            return delegate.getResource(name)
        }

        override fun getResources(
            name: String
        ): java.util.Enumeration<java.net.URL> {
            if (name.startsWith("spk/") &&
                !exportedServerResource(name)) {
                return java.util.Collections
                    .emptyEnumeration()
            }

            return delegate.getResources(name)
        }

        private fun allowed(name: String): Boolean =
            name.startsWith("java.") ||
                name.startsWith("javax.") ||
                name.startsWith("jdk.") ||
                name.startsWith("sun.") ||
                name.startsWith("com.sun.") ||
                name.startsWith("org.w3c.") ||
                name.startsWith("org.xml.") ||
                name.startsWith("org.ietf.jgss.") ||
                name.startsWith("kotlin.") ||
                name.startsWith("org.jetbrains.annotations.") ||
                name.startsWith("spk.plugin.api.") ||
                name.startsWith("spk.content.api.") ||
                exportedDslClass(name) ||
                exportedEventClass(name)

        private fun exportedEventClass(name: String): Boolean =
            name == "spk.event.DomainEventBus" ||
                name == "spk.event.DomainEventBus\$Event" ||
                name == "spk.event.DomainEventBus\$Cancellable" ||
                name == "spk.event.DomainEventBus\$Priority" ||
                name == "spk.event.DomainEventBus\$Listener" ||
                name == "spk.event.DomainEventBus\$Subscription"

        private fun exportedDslClass(name: String): Boolean =
            name ==
                "spk.plugin.kotlin.KotlinPluginDslKt" ||
                name.startsWith(
                    "spk.plugin.kotlin.KotlinPluginDslKt\$"
                )


        private fun exportedServerResource(name: String): Boolean =
            name.startsWith("spk/plugin/api/") ||
                name.startsWith("spk/content/api/") ||
                name == "spk/plugin/kotlin/KotlinPluginDslKt.class" ||
                name.startsWith(
                    "spk/plugin/kotlin/KotlinPluginDslKt\$"
                ) ||
                name == "spk/event/DomainEventBus.class" ||
                name == "spk/event/DomainEventBus\$Event.class" ||
                name == "spk/event/DomainEventBus\$Cancellable.class" ||
                name == "spk/event/DomainEventBus\$Priority.class" ||
                name == "spk/event/DomainEventBus\$Listener.class" ||
                name == "spk/event/DomainEventBus\$Subscription.class"
    }
}

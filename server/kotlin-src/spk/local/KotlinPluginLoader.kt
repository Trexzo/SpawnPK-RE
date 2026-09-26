package spk.local

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
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
class KotlinPluginLoader(
    apiJar: Path,
    compileClasspath: List<Path>
) : PluginLoader {
    private val apiJar: Path =
        apiJar.toAbsolutePath().normalize()
    private val compileClasspath: List<File>
    private val host = BasicJvmScriptingHost()

    init {
        require(Files.isRegularFile(this.apiJar)) {
            "plugin API JAR missing: ${this.apiJar}"
        }

        validateApiJar(this.apiJar)

        val normalized = LinkedHashSet<Path>()
        normalized += this.apiJar

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

            normalized += path
        }

        this.compileClasspath =
            normalized.map(Path::toFile)
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

        require(Files.isRegularFile(script)) {
            "Kotlin plugin script missing: $script"
        }

        val compilation =
            createJvmCompilationConfigurationFromTemplate<SimpleScriptTemplate> {
                updateClasspath(compileClasspath)
                compilerOptions(
                    "-jvm-target",
                    "11",
                    "-Xjdk-release=11"
                )
            }

        val parent =
            ScriptApiClassLoader(
                Plugin::class.java.classLoader
            )

        val evaluation =
            ScriptEvaluationConfiguration {
                jvm {
                    baseClassLoader(parent)
                }
            }

        val evaluated =
            host.eval(
                script.toFile().toScriptSource(),
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
            parent
        )
    }

    private fun validateApiJar(path: Path) {
        JarFile(path.toFile()).use { jar ->
            val entries = jar.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()

                if (entry.isDirectory) {
                    continue
                }

                val name = entry.name.replace('\\', '/')

                if (!name.startsWith("spk/")) {
                    continue
                }

                require(
                    name.startsWith("spk/plugin/api/") ||
                        name.startsWith("spk/content/api/") ||
                        exportedEventResource(name)
                ) {
                    "plugin API JAR exposes non-public SpawnPK namespace: $name"
                }
            }
        }
    }

    private fun validateDependencyJar(path: Path) {
        if (!path.fileName.toString()
                .lowercase(Locale.ROOT)
                .endsWith(".jar")) {
            return
        }

        JarFile(path.toFile()).use { jar ->
            val entries = jar.entries()

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()

                if (!entry.isDirectory) {
                    val name =
                        entry.name
                            .replace('\\', '/')

                    if (name.startsWith("spk/")) {
                        require(
                            exportedDslResource(
                                name
                            )
                        ) {
                            "Kotlin script dependency contains SpawnPK classes outside the DSL allowlist: " +
                                path + " entry=" + entry.name
                        }

                        val expected =
                            Plugin::class.java.classLoader
                                .getResourceAsStream(name)
                                ?.use { it.readBytes() }
                                ?: throw IllegalArgumentException(
                                    "Kotlin DSL server resource missing: $name"
                                )

                        val actual =
                            jar.getInputStream(entry)
                                .use { it.readBytes() }

                        require(
                            actual.contentEquals(
                                expected
                            )
                        ) {
                            "Kotlin DSL dependency class does not match server SDK: " +
                                path + " entry=" + entry.name
                        }
                    }
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
        parent: ClassLoader
    ) : PluginRuntime {
        @Volatile
        private var delegate: Plugin? = delegate

        @Volatile
        private var callbackLoader: ClassLoader? =
            delegate.javaClass.classLoader

        @Volatile
        private var baseLoader: ClassLoader? = parent

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

        override fun close() {
            delegate = null
            callbackLoader = null
            baseLoader = null
        }

        private fun requireDelegate(): Plugin =
            delegate
                ?: throw IllegalStateException(
                    "Kotlin plugin runtime closed: $source"
                )
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

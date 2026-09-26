import spk.content.api.ContentResult
import spk.event.DomainEventBus
import spk.plugin.api.Plugin
import spk.plugin.api.PluginApiVersion
import spk.plugin.api.PluginContext
import spk.plugin.api.PluginManifest

object : Plugin {
    private var events = 0

    override fun manifest(): PluginManifest =
        PluginManifest(
            "fixture.kotlin.script",
            "1.0",
            PluginApiVersion.CURRENT,
            emptyList<String>()
        )

    override fun enable(context: PluginContext) {
        context.events().subscribe(
            DomainEventBus.Event::class.java,
            DomainEventBus.Priority.NORMAL
        ) {
            events++
        }

        context.content().command(
            "kscript",
            100
        ) {
            ContentResult.handled(
                "KOTLIN_SCRIPT_EVENTS=$events",
                null
            )
        }
    }
}

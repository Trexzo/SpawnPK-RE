import spk.content.api.ContentActionResult
import spk.content.api.ContentInteractionResult
import spk.content.api.ContentNpcOptionResult
import spk.content.api.ContentResult
import spk.event.DomainEventBus
import spk.plugin.api.Plugin
import spk.plugin.api.PluginApiVersion
import spk.plugin.api.PluginContext
import spk.plugin.api.PluginManifest
import spk.plugin.api.onAction
import spk.plugin.api.onCommand
import spk.plugin.api.onEvent
import spk.plugin.api.onItemOption
import spk.plugin.api.onNpcClick

object : Plugin {
    private var events = 0

    override fun manifest(): PluginManifest =
        PluginManifest(
            "fixture.kotlin.dsl",
            "1.0",
            PluginApiVersion.CURRENT,
            emptyList<String>()
        )

    override fun enable(context: PluginContext) {
        context.onEvent(DomainEventBus.Event::class.java) {
            events++
        }

        context.onCommand("kdsl") { _, args ->
            ContentResult.handled(
                "DSL_COMMAND=" +
                    args.joinToString(",") +
                    ";events=" + events +
                    ";tccl=" +
                    (Thread.currentThread().contextClassLoader ===
                        this.javaClass.classLoader),
                null
            )
        }

        context.onNpcClick(npcId = 12345, option = 2) {
            ContentNpcOptionResult.action("dsl:npc")
        }

        context.onItemOption(itemId = 4151, option = 1) {
            ContentInteractionResult.handled("dsl:item")
        }

        context.onAction(actionKey = "dsl:action") {
            ContentActionResult.deny("dsl:blocked")
        }
    }
}

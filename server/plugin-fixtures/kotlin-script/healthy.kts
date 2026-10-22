import spk.content.api.ContentActionResult
import spk.content.api.ContentInteractionResult
import spk.content.api.ContentNpcOptionResult
import spk.content.api.ContentResult
import spk.event.DomainEventBus
import spk.plugin.api.Plugin
import spk.plugin.api.PluginApiVersion
import spk.plugin.api.PluginContext
import spk.plugin.api.PluginManifest
import spk.plugin.kotlin.onButtonClick
import spk.plugin.kotlin.onCommand
import spk.plugin.kotlin.onEvent
import spk.plugin.kotlin.onItemOption
import spk.plugin.kotlin.onNpcClick

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
        context.onEvent<DomainEventBus.Cancellable>(
            receiveCancelled = true
        ) {
            events++
        }

        context.onCommand(
            "kscript"
        ) { _, args ->
            ContentResult.handled(
                "KOTLIN_SCRIPT_EVENTS=$events;args=" +
                    args.joinToString(",") +
                    ";tccl=" +
                    (Thread.currentThread().contextClassLoader ===
                        this.javaClass.classLoader),
                null
            )
        }

        context.onNpcClick(
            npcId = 301,
            option = 1
        ) {
            ContentNpcOptionResult.action(
                "fixture.kotlin.npc"
            )
        }

        context.onItemOption(
            itemId = 201,
            option = 1
        ) {
            ContentInteractionResult.handled(
                "FIXTURE_KOTLIN_ITEM_OK"
            )
        }

        context.onButtonClick(
            actionKey = "fixture.kotlin.button"
        ) { _ ->
            ContentActionResult.allow()
        }
    }
}

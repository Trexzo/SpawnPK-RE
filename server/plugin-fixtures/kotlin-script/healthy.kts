import spk.content.api.ContentActionResult
import spk.content.api.ContentInteractionResult
import spk.content.api.ContentNpcOptionResult
import spk.content.api.ContentResult
import spk.event.DomainEventBus
import spk.plugin.api.Plugin
import spk.plugin.api.PluginApiVersion
import spk.plugin.api.PluginContext
import spk.plugin.api.PluginManifest
import spk.plugin.kotlin.onButton
import spk.plugin.kotlin.onCommand
import spk.plugin.kotlin.onEvent
import spk.plugin.kotlin.onItem
import spk.plugin.kotlin.onNpc

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
        context.onEvent<DomainEventBus.Event> {
            events++
        }

        context.onCommand(
            "kscript"
        ) {
            ContentResult.handled(
                "KOTLIN_SCRIPT_EVENTS=$events",
                null
            )
        }

        context.onNpc(
            npcDefinitionId = 301,
            option = 1
        ) {
            ContentNpcOptionResult.action(
                "fixture.kotlin.npc"
            )
        }

        context.onItem(
            itemId = 201,
            option = 1
        ) {
            ContentInteractionResult.handled(
                "FIXTURE_KOTLIN_ITEM_OK"
            )
        }

        context.onButton(
            "fixture.kotlin.button"
        ) {
            ContentActionResult.allow()
        }
    }
}

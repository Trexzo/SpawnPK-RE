import spk.content.api.ContentActionResult
import spk.content.api.ContentInteractionResult
import spk.content.api.ContentNpcOptionResult
import spk.content.api.ContentResult
import spk.event.DomainEventBus
import spk.plugin.api.Plugin
import spk.plugin.api.PluginApiVersion
import spk.plugin.api.PluginContext
import spk.plugin.api.PluginManifest
import spk.plugin.kotlin.onAction
import spk.plugin.kotlin.onCommand
import spk.plugin.kotlin.onEvent
import spk.plugin.kotlin.onItemOption
import spk.plugin.kotlin.onNpcOption

object : Plugin {
    private var events = 0

    override fun manifest(): PluginManifest =
        PluginManifest(
            "fixture.kotlin.dsl",
            "1.0",
            PluginApiVersion.CURRENT,
            emptyList<String>()
        )

    private fun requireCallbackLoader() {
        check(
            Thread.currentThread().contextClassLoader ===
                this::class.java.classLoader
        ) {
            "dsl callback TCCL mismatch"
        }
    }

    override fun enable(context: PluginContext) {
        val content = context.content()

        content.onCommand(
            name = "dslcommand"
        ) { command ->
            requireCallbackLoader()
            ContentResult.handled(
                "DSL_COMMAND events=$events args=" +
                    command.arguments().size,
                null
            )
        }

        content.onNpcOption(
            npcDefinitionId = 301,
            option = 2
        ) { npc ->
            requireCallbackLoader()
            check(npc.npcDefinitionId() == 301)
            check(npc.option() == 2)
            ContentNpcOptionResult.action(
                "dsl.npc"
            )
        }

        content.onItemOption(
            itemId = 201
        ) { item ->
            requireCallbackLoader()
            check(item.itemId() == 201)
            check(item.option() == 1)
            ContentInteractionResult.handled(
                "DSL_ITEM"
            )
        }

        content.onAction(
            actionKey = "dsl.semantic",
            priority = 140
        ) { action ->
            requireCallbackLoader()
            check(
                action.actionKey() ==
                    "dsl.semantic"
            )
            ContentActionResult.allow()
        }

        context.events().onEvent<
            DomainEventBus.Cancellable
        >(
            priority =
                DomainEventBus.Priority.HIGH,
            receiveCancelled = true
        ) {
            requireCallbackLoader()
            events++
        }
    }
}

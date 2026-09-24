package spk.plugin.fixture

import spk.content.api.ContentInteractionResult
import spk.content.api.ContentNpcOptionResult
import spk.content.api.ContentResult
import spk.event.DomainEventBus
import spk.plugin.api.Plugin
import spk.plugin.api.PluginApiVersion
import spk.plugin.api.PluginContext
import spk.plugin.api.PluginManifest

class KotlinPluginApiFixture : Plugin {
    private class FixtureEvent : DomainEventBus.Event

    override fun manifest(): PluginManifest =
        PluginManifest(
            "fixture.kotlin",
            "1.0",
            PluginApiVersion.CURRENT,
            emptyList<String>()
        )

    override fun enable(context: PluginContext) {
        context.content().command(
            "fixturekotlin",
            100
        ) {
            ContentResult.handled(
                "FIXTURE_KOTLIN_OK",
                null
            )
        }

        context.content().objectOption(
            101,
            1,
            100
        ) {
            ContentInteractionResult.handled(
                "FIXTURE_KOTLIN_OBJECT_OK"
            )
        }

        context.content().itemOption(
            201,
            1,
            100
        ) {
            ContentInteractionResult.handled(
                "FIXTURE_KOTLIN_ITEM_OK"
            )
        }

        context.content().npcOption(
            301,
            1,
            100
        ) {
            ContentNpcOptionResult.action(
                "fixture.kotlin.npc"
            )
        }

        context.events().subscribe(
            FixtureEvent::class.java,
            DomainEventBus.Priority.NORMAL
        ) {
        }

        context.scheduler().schedule(
            1L
        ) {
        }
    }
}

package spk.plugin.api

import spk.content.api.ContentActionContext
import spk.content.api.ContentActionResult
import spk.content.api.ContentCommandContext
import spk.content.api.ContentInteractionResult
import spk.content.api.ContentItemOptionContext
import spk.content.api.ContentNpcOptionContext
import spk.content.api.ContentNpcOptionResult
import spk.content.api.ContentPlayer
import spk.content.api.ContentRegistration
import spk.content.api.ContentResult
import spk.event.DomainEventBus

fun PluginContext.onCommand(
    name: String,
    priority: Int = 100,
    handler: (ContentPlayer, List<String>) -> ContentResult
): ContentRegistration =
    content().command(name, priority) { context: ContentCommandContext ->
        handler(context.player(), context.arguments())
    }

/** Semantic NPC-option helper; no packet, scene-index, or widget identity leaks. */
fun PluginContext.onNpcClick(
    npcId: Int,
    option: Int = 1,
    priority: Int = 100,
    handler: (ContentNpcOptionContext) -> ContentNpcOptionResult
): ContentRegistration =
    content().npcOption(npcId, option, priority) { context ->
        handler(context)
    }

fun PluginContext.onItemOption(
    itemId: Int,
    option: Int = 1,
    priority: Int = 100,
    handler: (ContentItemOptionContext) -> ContentInteractionResult
): ContentRegistration =
    content().itemOption(itemId, option, priority) { context ->
        handler(context)
    }

/**
 * Semantic replacement for a raw button-id helper.
 * C2S185/widget ids intentionally remain outside the public plugin API.
 */
fun PluginContext.onAction(
    actionKey: String,
    priority: Int = 100,
    handler: (ContentActionContext) -> ContentActionResult
): ContentRegistration =
    content().action(actionKey, priority) { context ->
        handler(context)
    }

fun <E : DomainEventBus.Event> PluginContext.onEvent(
    type: Class<E>,
    priority: DomainEventBus.Priority = DomainEventBus.Priority.NORMAL,
    receiveCancelled: Boolean = false,
    handler: (E) -> Unit
): DomainEventBus.Subscription =
    events().subscribe(type, priority, receiveCancelled) { event ->
        handler(event)
    }

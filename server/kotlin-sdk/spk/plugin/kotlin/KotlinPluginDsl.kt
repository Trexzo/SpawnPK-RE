package spk.plugin.kotlin

import spk.content.api.ContentActionContext
import spk.content.api.ContentActionHandler
import spk.content.api.ContentActionResult
import spk.content.api.ContentCommandContext
import spk.content.api.ContentCommandHandler
import spk.content.api.ContentInteractionResult
import spk.content.api.ContentItemOptionContext
import spk.content.api.ContentItemOptionHandler
import spk.content.api.ContentNpcOptionContext
import spk.content.api.ContentNpcOptionHandler
import spk.content.api.ContentNpcOptionResult
import spk.content.api.ContentRegistration
import spk.event.DomainEventBus
import spk.plugin.api.PluginContext

fun PluginContext.onCommand(
    name: String,
    priority: Int = 100,
    handler: (ContentCommandContext) -> spk.content.api.ContentResult
): ContentRegistration =
    content().command(
        name,
        priority,
        ContentCommandHandler { context ->
            handler(context)
        }
    )

fun PluginContext.onNpc(
    npcDefinitionId: Int,
    option: Int = 1,
    priority: Int = 100,
    handler: (ContentNpcOptionContext) -> ContentNpcOptionResult
): ContentRegistration =
    content().npcOption(
        npcDefinitionId,
        option,
        priority,
        ContentNpcOptionHandler { context ->
            handler(context)
        }
    )

fun PluginContext.onItem(
    itemId: Int,
    option: Int = 1,
    priority: Int = 100,
    handler: (ContentItemOptionContext) -> ContentInteractionResult
): ContentRegistration =
    content().itemOption(
        itemId,
        option,
        priority,
        ContentItemOptionHandler { context ->
            handler(context)
        }
    )

/**
 * Semantic button/action helper.
 *
 * The public plugin boundary intentionally does not expose raw widget IDs.
 * Callers register protocol-independent action keys owned by core routing.
 */
fun PluginContext.onButton(
    actionKey: String,
    priority: Int = 100,
    handler: (ContentActionContext) -> ContentActionResult
): ContentRegistration =
    content().action(
        actionKey,
        priority,
        ContentActionHandler { context ->
            handler(context)
        }
    )

inline fun <reified E : DomainEventBus.Event>
    PluginContext.onEvent(
        priority: DomainEventBus.Priority =
            DomainEventBus.Priority.NORMAL,
        receiveCancelled: Boolean = false,
        crossinline listener: (E) -> Unit
    ): DomainEventBus.Subscription =
        events().subscribe(
            E::class.java,
            priority,
            receiveCancelled,
            DomainEventBus.Listener<E> { event ->
                listener(event)
            }
        )

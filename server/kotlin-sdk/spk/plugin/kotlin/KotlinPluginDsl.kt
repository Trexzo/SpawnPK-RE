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
import spk.content.api.ContentPlayer
import spk.content.api.ContentResult
import spk.content.api.ContentRegistration
import spk.event.DomainEventBus
import spk.plugin.api.PluginContext

fun PluginContext.onCommand(
    name: String,
    priority: Int = 100,
    handler: (ContentCommandContext) -> ContentResult
): ContentRegistration =
    content().command(
        name,
        priority,
        ContentCommandHandler { context ->
            handler(context)
        }
    )

fun PluginContext.onCommand(
    name: String,
    priority: Int = 100,
    handler: (ContentPlayer, List<String>) -> ContentResult
): ContentRegistration =
    content().command(
        name,
        priority,
        ContentCommandHandler { context ->
            handler(
                context.player(),
                context.arguments()
            )
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

fun PluginContext.onNpcClick(
    npcId: Int,
    option: Int = 1,
    priority: Int = 100,
    handler: (ContentNpcOptionContext) -> ContentNpcOptionResult
): ContentRegistration =
    onNpc(
        npcDefinitionId = npcId,
        option = option,
        priority = priority,
        handler = handler
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

fun PluginContext.onItemOption(
    itemId: Int,
    option: Int = 1,
    priority: Int = 100,
    handler: (ContentItemOptionContext) -> ContentInteractionResult
): ContentRegistration =
    onItem(
        itemId = itemId,
        option = option,
        priority = priority,
        handler = handler
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

/**
 * Issue #5 naming alias for semantic button/action registration.
 *
 * The argument remains a core-owned semantic action key, never a raw widget ID.
 */
fun PluginContext.onButtonClick(
    actionKey: String,
    priority: Int = 100,
    handler: (ContentPlayer) -> ContentActionResult
): ContentRegistration =
    onButton(
        actionKey = actionKey,
        priority = priority
    ) { context ->
        handler(
            context.player()
        )
    }

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

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
import spk.content.api.ContentRegistrar
import spk.content.api.ContentRegistration
import spk.content.api.ContentResult
import spk.event.DomainEventBus
import spk.plugin.api.PluginEvents

/**
 * Thin Kotlin conveniences over the stable public plugin/content API.
 *
 * These helpers create no independent registry or lifecycle state. Every
 * returned handle is the underlying Java API handle owned by the plugin
 * manager and receives the same CUSTOM_LOCALLAB provenance assigned by core.
 *
 * Raw widget/button ids are intentionally not exposed here. Kotlin plugins use
 * [onAction] with semantic action keys unless a future public contract
 * deliberately introduces another transport-independent capability.
 */
fun ContentRegistrar.onCommand(
    name: String,
    priority: Int = 100,
    handler: (ContentCommandContext) -> ContentResult
): ContentRegistration =
    command(
        name,
        priority,
        ContentCommandHandler { context ->
            handler(context)
        }
    )

fun ContentRegistrar.onNpcOption(
    npcDefinitionId: Int,
    option: Int = 1,
    priority: Int = 100,
    handler: (ContentNpcOptionContext) -> ContentNpcOptionResult
): ContentRegistration =
    npcOption(
        npcDefinitionId,
        option,
        priority,
        ContentNpcOptionHandler { context ->
            handler(context)
        }
    )

fun ContentRegistrar.onItemOption(
    itemId: Int,
    option: Int = 1,
    priority: Int = 100,
    handler: (ContentItemOptionContext) -> ContentInteractionResult
): ContentRegistration =
    itemOption(
        itemId,
        option,
        priority,
        ContentItemOptionHandler { context ->
            handler(context)
        }
    )

fun ContentRegistrar.onAction(
    actionKey: String,
    priority: Int = 100,
    handler: (ContentActionContext) -> ContentActionResult
): ContentRegistration =
    action(
        actionKey,
        priority,
        ContentActionHandler { context ->
            handler(context)
        }
    )

inline fun <reified E : DomainEventBus.Event>
    PluginEvents.onEvent(
        priority: DomainEventBus.Priority =
            DomainEventBus.Priority.NORMAL,
        receiveCancelled: Boolean = false,
        noinline handler: (E) -> Unit
    ): DomainEventBus.Subscription =
        subscribe(
            E::class.java,
            priority,
            receiveCancelled,
            DomainEventBus.Listener<E> { event ->
                handler(event)
            }
        )

package spk.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Runtime mirror of server/build.gradle pluginApiClassPatterns.
 *
 * The permanent Kotlin loader regression compares this exact set against the
 * actual Gradle-built SpawnPKPluginApi.jar so build/runtime drift fails closed.
 */
final class PluginApiExportContract {
    private static final Set<String> RESOURCES=
        Collections.unmodifiableSet(
            new LinkedHashSet<>(
                Arrays.asList(
                    "spk/content/api/ContentActionContext.class",
                    "spk/content/api/ContentActionHandler.class",
                    "spk/content/api/ContentActionResult.class",
                    "spk/content/api/ContentCommandContext.class",
                    "spk/content/api/ContentCommandHandler.class",
                    "spk/content/api/ContentDialogueContext.class",
                    "spk/content/api/ContentDialogueDefinition.class",
                    "spk/content/api/ContentDialogueHandler.class",
                    "spk/content/api/ContentDialogueNode.class",
                    "spk/content/api/ContentDialogueIntent.class",
                    "spk/content/api/ContentDialoguePresentation.class",
                    "spk/content/api/ContentDialogueTransition.class",
                    "spk/content/api/ContentInteractionResult.class",
                    "spk/content/api/ContentItemOnGroundItemContext.class",
                    "spk/content/api/ContentItemOnGroundItemHandler.class",
                    "spk/content/api/ContentItemOnItemContext.class",
                    "spk/content/api/ContentItemOnItemHandler.class",
                    "spk/content/api/ContentItemOnNpcContext.class",
                    "spk/content/api/ContentItemOnNpcHandler.class",
                    "spk/content/api/ContentItemOnObjectContext.class",
                    "spk/content/api/ContentItemOnObjectHandler.class",
                    "spk/content/api/ContentItemOnPlayerContext.class",
                    "spk/content/api/ContentItemOnPlayerHandler.class",
                    "spk/content/api/ContentItemOptionContext.class",
                    "spk/content/api/ContentItemOptionHandler.class",
                    "spk/content/api/ContentModule.class",
                    "spk/content/api/ContentNpcOptionContext.class",
                    "spk/content/api/ContentNpcOptionHandler.class",
                    "spk/content/api/ContentNpcOptionResult.class",
                    "spk/content/api/ContentNpcService.class",
                    "spk/content/api/ContentObjectOptionContext.class",
                    "spk/content/api/ContentObjectOptionHandler.class",
                    "spk/content/api/ContentPlayer.class",
                    "spk/content/api/ContentPresentation.class",
                    "spk/content/api/ContentPresentationException.class",
                    "spk/content/api/ContentPrayerBook.class",
                    "spk/content/api/ContentProvenance.class",
                    "spk/content/api/ContentRegistrar.class",
                    "spk/content/api/ContentRegistration.class",
                    "spk/content/api/ContentResult.class",
                    "spk/content/api/ContentSkill.class",
                    "spk/content/api/ContentSpellBook.class",
                    "spk/plugin/api/Plugin.class",
                    "spk/plugin/api/PluginApiVersion.class",
                    "spk/plugin/api/PluginContext.class",
                    "spk/plugin/api/PluginEvents.class",
                    "spk/plugin/api/PluginHandle.class",
                    "spk/plugin/api/PluginManager.class",
                    "spk/plugin/api/PluginManifest.class",
                    "spk/plugin/api/PluginScheduler.class",
                    "spk/plugin/api/PluginTask.class",
                    "spk/content/api/ContentActionResult$Decision.class",
                    "spk/content/api/ContentDialogueNode$InputMode.class",
                    "spk/content/api/ContentDialogueIntent$Kind.class",
                    "spk/content/api/ContentDialogueTransition$Kind.class",
                    "spk/event/DomainEventBus.class",
                    "spk/event/DomainEventBus$Event.class",
                    "spk/event/DomainEventBus$Cancellable.class",
                    "spk/event/DomainEventBus$Priority.class",
                    "spk/event/DomainEventBus$Listener.class",
                    "spk/event/DomainEventBus$Subscription.class",
                    "spk/event/PlayerEvent.class",
                    "spk/event/PlayerTickEvent.class"
                )
            )
        );

    static Set<String> resources(){
        return RESOURCES;
    }

    static boolean contains(
        String resource
    ){
        return RESOURCES.contains(
            resource
        );
    }

    private PluginApiExportContract(){}
}

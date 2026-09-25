package example.spawnpk.fixture;

import java.util.Collections;
import spk.content.api.ContentInteractionResult;
import spk.content.api.ContentNpcOptionResult;
import spk.content.api.ContentRegistration;
import spk.content.api.ContentResult;
import spk.event.DomainEventBus;
import spk.plugin.api.Plugin;
import spk.plugin.api.PluginApiVersion;
import spk.plugin.api.PluginContext;
import spk.plugin.api.PluginManifest;

public final class JavaPluginApiFixture
    implements Plugin {

    private static final class FixtureEvent
        implements DomainEventBus.Event {}

    private ContentRegistration commandRegistration;
    private ContentRegistration objectRegistration;

    @Override
    public PluginManifest manifest(){
        return new PluginManifest(
            "fixture.java",
            "1.0",
            PluginApiVersion.CURRENT,
            Collections.<String>emptyList()
        );
    }

    @Override
    public void enable(
        PluginContext context
    ){
        commandRegistration=
            context.content().command(
            "fixturejava",
            100,
            command->
                ContentResult.handled(
                    "FIXTURE_JAVA_OK",
                    null
                )
        );

        objectRegistration=
            context.content().objectOption(
            100,
            1,
            100,
            object->
                ContentInteractionResult.handled(
                    "FIXTURE_OBJECT_OK"
                )
        );

        context.content().itemOption(
            200,
            1,
            100,
            item->
                ContentInteractionResult.handled(
                    "FIXTURE_ITEM_OK"
                )
        );

        context.content().npcOption(
            300,
            1,
            100,
            npc->
                ContentNpcOptionResult.action(
                    "fixture.npc"
                )
        );

        context.events().subscribe(
            FixtureEvent.class,
            DomainEventBus.Priority.NORMAL,
            event->{}
        );

        context.scheduler().schedule(
            1L,
            ()->{}
        );
    }

    @Override
    public void disable(){
        ContentRegistration command=
            commandRegistration;
        ContentRegistration object=
            objectRegistration;

        commandRegistration=null;
        objectRegistration=null;

        if(command!=null)
            command.unregister();

        if(object!=null)
            object.close();
    }
}

package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import spk.content.api.*;

public final class ContentRegistrationLifecycleTest {
    private static final int OBJECT_ID=900001;
    private static final int NPC_ID=900002;
    private static final String ACTION_KEY=
        "lifecycle:action";

    public static void main(String[] args)throws Exception{
        World world=World.isolatedForTest(20L);
        WorldPlayer player=new WorldPlayer();

        try{
            ContentRegistry registry=world.content();

            Handles low=new Handles();
            Handles high=new Handles();

            registry.installCustom(
                module(
                    "lifecycle-low",
                    registrar->{
                        low.command=registrar.command(
                            "lifecyclecmd",
                            10,
                            context->
                                ContentResult.handled(
                                    "CMD_LOW",
                                    null
                                )
                        );

                        low.object=registrar.objectOption(
                            OBJECT_ID,
                            2,
                            10,
                            context->
                                ContentInteractionResult.handled(
                                    "OBJECT_LOW"
                                )
                        );

                        low.npc=registrar.npcOption(
                            NPC_ID,
                            3,
                            10,
                            context->
                                ContentNpcOptionResult.handled(
                                    ContentNpcService.TALK
                                )
                        );

                        low.action=registrar.action(
                            ACTION_KEY,
                            10,
                            context->
                                ContentActionResult.allow()
                        );

                        assertPending(low.command);
                        assertPending(low.object);
                        assertPending(low.npc);
                        assertPending(low.action);
                    }
                )
            );

            registry.installCustom(
                module(
                    "lifecycle-high",
                    registrar->{
                        high.command=registrar.command(
                            "lifecyclecmd",
                            20,
                            context->
                                ContentResult.handled(
                                    "CMD_HIGH",
                                    null
                                )
                        );

                        high.object=registrar.objectOption(
                            OBJECT_ID,
                            2,
                            20,
                            context->
                                ContentInteractionResult.handled(
                                    "OBJECT_HIGH"
                                )
                        );

                        high.npc=registrar.npcOption(
                            NPC_ID,
                            3,
                            20,
                            context->
                                ContentNpcOptionResult.handled(
                                    ContentNpcService.TRADE
                                )
                        );

                        high.action=registrar.action(
                            ACTION_KEY,
                            20,
                            context->
                                ContentActionResult.deny(
                                    "lifecycle:high"
                                )
                        );
                    }
                )
            );

            assertActive(low);
            assertActive(high);
            assertWinner(
                registry.commandBinding(
                    "lifecyclecmd"
                ),
                "lifecycle-high",
                20
            );
            assertWinner(
                registry.objectOptionBinding(
                    OBJECT_ID,
                    2
                ),
                "lifecycle-high",
                20
            );
            assertWinner(
                registry.npcOptionBinding(
                    NPC_ID,
                    3
                ),
                "lifecycle-high",
                20
            );

            assertWinner(
                registry.actionBinding(
                    ACTION_KEY
                ),
                "lifecycle-high",
                20
            );

            world.registerPlayer(
                player,
                "registration-lifecycle"
            );
            world.start();

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    wire,
                    new IsaacCipher(
                        new int[]{1,2,3,4}
                    )
                );

            Dispatch initial=
                dispatch(
                    world,
                    player,
                    registry,
                    writer
                );

            if(!"CMD_HIGH".equals(
                    initial.command.logText())||
               !"OBJECT_HIGH".equals(
                    initial.object.outcome())||
               initial.npc.service()!=
                    ContentNpcService.TRADE||
               initial.action.allowed()||
               !"lifecycle:high".equals(
                    initial.action.reasonKey()))
                throw new AssertionError(
                    "initial priority dispatch "+
                    initial
                );

            if(!high.command.unregister()||
               high.command.active()||
               high.command.unregister())
                throw new AssertionError(
                    "command handle idempotence"
                );

            if(!high.action.unregister()||
               high.action.active())
                throw new AssertionError(
                    "action winner removal"
                );

            assertWinner(
                registry.actionBinding(
                    ACTION_KEY
                ),
                "lifecycle-low",
                10
            );

            assertWinner(
                registry.commandBinding(
                    "lifecyclecmd"
                ),
                "lifecycle-low",
                10
            );

            Dispatch commandFallback=
                dispatch(
                    world,
                    player,
                    registry,
                    writer
                );

            if(!"CMD_LOW".equals(
                    commandFallback.command
                        .logText()))
                throw new AssertionError(
                    "command fallback not restored"
                );

            if(!commandFallback.action.allowed())
                throw new AssertionError(
                    "action fallback not restored"
                );

            if(!low.object.unregister()||
               low.object.active())
                throw new AssertionError(
                    "hidden object registration removal"
                );

            assertWinner(
                registry.objectOptionBinding(
                    OBJECT_ID,
                    2
                ),
                "lifecycle-high",
                20
            );

            Dispatch objectWinner=
                dispatch(
                    world,
                    player,
                    registry,
                    writer
                );

            if(!"OBJECT_HIGH".equals(
                    objectWinner.object
                        .outcome()))
                throw new AssertionError(
                    "hidden object removal disturbed winner"
                );

            if(!high.object.unregister()||
               high.object.unregister()||
               registry.objectOptionBinding(
                   OBJECT_ID,
                   2
               )!=null)
                throw new AssertionError(
                    "object registration removal"
                );

            if(!high.npc.unregister()||
               high.npc.active())
                throw new AssertionError(
                    "npc winner removal"
                );

            assertWinner(
                registry.npcOptionBinding(
                    NPC_ID,
                    3
                ),
                "lifecycle-low",
                10
            );

            Dispatch npcFallback=
                dispatch(
                    world,
                    player,
                    registry,
                    writer
                );

            if(npcFallback.npc.service()!=
                    ContentNpcService.TALK)
                throw new AssertionError(
                    "npc fallback not restored"
                );

            AtomicReference<ContentRegistration>
                failedHandle=
                    new AtomicReference<>();

            boolean conflict=false;
            try{
                registry.installCustom(
                    module(
                        "lifecycle-conflict",
                        registrar->
                            failedHandle.set(
                                registrar.command(
                                    "lifecyclecmd",
                                    10,
                                    context->
                                        ContentResult.handled(
                                            "CONFLICT",
                                            null
                                        )
                                )
                            )
                    )
                );
            }catch(IllegalStateException expected){
                conflict=expected.getMessage()
                    .contains(
                        "content binding conflict"
                    );
            }

            if(!conflict||
               failedHandle.get()==null||
               failedHandle.get().active()||
               failedHandle.get().unregister())
                throw new AssertionError(
                    "failed install leaked active handle"
                );

            assertWinner(
                registry.commandBinding(
                    "lifecyclecmd"
                ),
                "lifecycle-low",
                10
            );

            AtomicReference<ContentRegistration>
                cancelled=
                    new AtomicReference<>();

            registry.installCustom(
                module(
                    "lifecycle-cancel-pending",
                    registrar->{
                        ContentRegistration handle=
                            registrar.command(
                                "cancelledpending",
                                1,
                                context->
                                    ContentResult.handled(
                                        "SHOULD_NOT_RUN",
                                        null
                                    )
                            );
                        cancelled.set(handle);
                        if(!handle.unregister())
                            throw new AssertionError(
                                "pending unregister rejected"
                            );
                    }
                )
            );

            if(cancelled.get()==null||
               cancelled.get().active()||
               cancelled.get().unregister()||
               registry.commandBinding(
                   "cancelledpending"
               )!=null)
                throw new AssertionError(
                    "pending cancellation committed binding"
                );

            low.npc.close();

            if(low.npc.active()||
               registry.npcOptionBinding(
                   NPC_ID,
                   3
               )!=null)
                throw new AssertionError(
                    "close did not unregister NPC binding"
                );

            System.out.println(
                "CONTENT_REGISTRATION_LIFECYCLE_PASS "+
                "commandFallback=true "+
                "hiddenRemovalStable=true "+
                "npcFallback=true "+
                "actionFallback=true "+
                "idempotent=true "+
                "failedInstallLeak=false "+
                "pendingCancellation=true"
            );
        }finally{
            if(player.registered())
                world.unregisterPlayer(player);
            world.close();
        }
    }

    private static Dispatch dispatch(
        World world,
        WorldPlayer player,
        ContentRegistry registry,
        ServerPacketWriter writer
    )throws Exception{
        AtomicReference<ContentResult> command=
            new AtomicReference<>();
        AtomicReference<ContentInteractionResult> object=
            new AtomicReference<>();
        AtomicReference<ContentNpcOptionResult> npc=
            new AtomicReference<>();
        AtomicReference<ContentActionResult> action=
            new AtomicReference<>();

        world.submitAndWait(
            player,
            ()->{
                command.set(
                    registry.dispatchCommand(
                        player,
                        "::lifecyclecmd",
                        writer
                    )
                );
                object.set(
                    registry.dispatchObjectOption(
                        OBJECT_ID,
                        2,
                        3200,
                        3201
                    )
                );
                npc.set(
                    registry.dispatchNpcOption(
                        NPC_ID,
                        3,
                        3202,
                        3203
                    )
                );
                action.set(
                    registry.dispatchAction(
                        player,
                        ACTION_KEY
                    )
                );
            },
            5_000L
        );

        return new Dispatch(
            command.get(),
            object.get(),
            npc.get(),
            action.get()
        );
    }

    private static void assertPending(
        ContentRegistration registration
    ){
        if(registration==null||
           registration.active())
            throw new AssertionError(
                "registration active before module commit"
            );
    }

    private static void assertActive(
        Handles handles
    ){
        if(handles.command==null||
           handles.object==null||
           handles.npc==null||
           handles.action==null||
           !handles.command.active()||
           !handles.object.active()||
           !handles.npc.active()||
           !handles.action.active())
            throw new AssertionError(
                "committed handles not active"
            );
    }

    private static void assertWinner(
        ContentRegistry.BindingInfo info,
        String moduleId,
        int priority
    ){
        if(info==null||
           !moduleId.equals(info.moduleId)||
           info.priority!=priority)
            throw new AssertionError(
                "unexpected binding winner "+
                info+
                " expectedModule="+
                moduleId+
                " expectedPriority="+
                priority
            );
    }

    private interface ModuleBody{
        void register(
            ContentRegistrar registrar
        );
    }

    private static ContentModule module(
        String id,
        ModuleBody body
    ){
        return new ContentModule(){
            @Override public String id(){
                return id;
            }

            @Override public void register(
                ContentRegistrar registrar
            ){
                body.register(registrar);
            }
        };
    }

    private static final class Handles{
        ContentRegistration command;
        ContentRegistration object;
        ContentRegistration npc;
        ContentRegistration action;
    }

    private static final class Dispatch{
        final ContentResult command;
        final ContentInteractionResult object;
        final ContentNpcOptionResult npc;
        final ContentActionResult action;

        Dispatch(
            ContentResult command,
            ContentInteractionResult object,
            ContentNpcOptionResult npc,
            ContentActionResult action
        ){
            this.command=command;
            this.object=object;
            this.npc=npc;
            this.action=action;
        }

        @Override public String toString(){
            return "Dispatch{command="+
                command+
                ",object="+object+
                ",npc="+npc+
                ",action="+action+
                "}";
        }
    }

    private ContentRegistrationLifecycleTest(){}
}

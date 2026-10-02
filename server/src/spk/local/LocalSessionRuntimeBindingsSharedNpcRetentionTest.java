package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;

public final class LocalSessionRuntimeBindingsSharedNpcRetentionTest {
    private static final int QUEUE_CAPACITY=1024;

    private static final class Bridge
        implements LocalSessionRuntimeBindings.SessionBridge {
        @Override public void saveAccount(
            String tag,
            String reason
        ){}
    }

    public static void main(String[] args)throws Exception{
        assertRetryableSharedNpcContextSurvivesBindingRollback();

        System.out.println(
            "LOCAL_SESSION_RUNTIME_BINDINGS_SHARED_NPC_RETENTION_PASS "+
            "retryableOldContextPreserved=true "+
            "retryableOldPlayer81Preserved=true "+
            "retryableBindingRetrySucceeds=true"
        );
    }

    private static void assertRetryableSharedNpcContextSurvivesBindingRollback()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                700L
            );
        WorldPlayer source=
            new WorldPlayer();
        WorldPlayer viewer=
            new WorldPlayer();

        long sourceGeneration=
            world.registerPlayer(
                source,
                "binding-retain-source"
            );
        long viewerGeneration=
            world.registerPlayer(
                viewer,
                "binding-retain-viewer"
            );

        DevAuthorityWorkbench viewerDev=
            new DevAuthorityWorkbench();
        NpcRegistry viewerNpcs=
            new NpcRegistry(
                viewerDev
            );
        NpcRegistry sourceNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        OutboundPacketQueue viewerQueue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        OutboundPacketQueue sourceQueue=
            new OutboundPacketQueue();

        ServerPacketWriter viewerWriter=
            new ServerPacketWriter(
                viewerQueue,
                new IsaacCipher(
                    new int[]{1101,1102,1103,1104}
                )
            );
        ServerPacketWriter sourceWriter=
            new ServerPacketWriter(
                sourceQueue,
                new IsaacCipher(
                    new int[]{1105,1106,1107,1108}
                )
            );

        Player81WorldSync.register(
            sourceWriter,
            world,
            source,
            new DevAuthorityWorkbench()
        );
        Player81WorldSync.Context oldViewerSync=
            Player81WorldSync.register(
                viewerWriter,
                world,
                viewer,
                viewerDev
            );

        /*
         * #1621 prepublishes S2C104 before SharedNpc admission. Seed the
         * already-authoritative old Player81 contexts while transport is
         * healthy so the injected failure reaches SharedNpc rebind cleanup.
         */
        Player81WorldSync.sendPlayerOptionsIfMultiplayer(
            world
        );

        SharedNpcWorldRelay.register(
            sourceWriter,
            world,
            source,
            sourceNpcs,
            source.movement()
        );
        SharedNpcWorldRelay.register(
            viewerWriter,
            world,
            viewer,
            viewerNpcs,
            viewer.movement()
        );

        LocalSessionRuntimeBindings bindings=
            new LocalSessionRuntimeBindings(
                world,
                viewer,
                viewerDev,
                viewerNpcs,
                viewer.movement(),
                viewer.bank(),
                new Bridge()
            );

        OutboundPacketQueue.BatchReservation pressure=
            null;

        try{
            PetDefinitionRepository.Def pet=
                PetDefinitionRepository.get(
                    24019
                );

            if(pet==null)
                throw new AssertionError(
                    "missing pet 24019"
                );

            sourceNpcs.spawnPet(
                pet,
                source.movement(),
                sourceWriter
            );

            Player81WorldSync.transformForTest(
                oldViewerSync,
                BootstrapPackets.player81Idle()
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "retryable wrapper fixture mirror count="+
                    viewerNpcs.snapshot().size()
                );

            drain(viewerQueue);

            Object oldRelayContext=
                relayContextFor(
                    viewerWriter
                );

            pressure=
                OutboundPacketQueue.reserveBatch(
                    viewerQueue,
                    QUEUE_CAPACITY
                );

            boolean rejected=false;

            try{
                bindings.register(
                    viewerWriter,
                    "[binding-sharednpc-retain] "
                );
            }catch(SharedNpcWorldRelay.RetryableRegistrationException expected){
                rejected=true;
            }

            if(!rejected)
                throw new AssertionError(
                    "runtime binding did not surface retryable SharedNpc rejection"
                );

            if(bindings.context()!=null)
                throw new AssertionError(
                    "retryable binding failure retained local Player81 context"
                );

            if(relayContextFor(
                    viewerWriter
                )!=oldRelayContext)
                throw new AssertionError(
                    "binding rollback unregistered retained retryable SharedNpc Context"
                );

            if(player81ContextFor(
                    viewerWriter
                )!=oldViewerSync)
                throw new AssertionError(
                    "retryable SharedNpc rejection did not preserve exact old Player81 Context"
                );

            if(viewerNpcs.snapshot().size()!=1||
               viewerQueue.queuedBytes()!=0)
                throw new AssertionError(
                    "retryable binding rollback changed mirror/transport authority"
                );

            pressure.release();
            pressure=null;

            bindings.register(
                viewerWriter,
                "[binding-sharednpc-retain] "
            );

            if(bindings.context()==null)
                throw new AssertionError(
                    "binding retry did not install Player81 context"
                );

            Object newRelayContext=
                relayContextFor(
                    viewerWriter
                );

            if(newRelayContext==null||
               newRelayContext==oldRelayContext)
                throw new AssertionError(
                    "binding retry did not install fresh SharedNpc Context"
                );

            if(!viewerNpcs.snapshot().isEmpty())
                throw new AssertionError(
                    "binding retry retained stale old mirror"
                );

            drain(viewerQueue);

            Player81WorldSync.transformForTest(
                bindings.context(),
                BootstrapPackets.player81Idle()
            );

            SharedNpcWorldRelay.syncRemotePets(
                viewerWriter
            );

            if(viewerNpcs.snapshot().size()!=1)
                throw new AssertionError(
                    "successful binding retry did not recreate exactly one mirror"
                );

            bindings.unregister();
        }finally{
            if(pressure!=null)
                pressure.release();

            bindings.unregister();

            SharedNpcWorldRelay.unregister(
                sourceWriter
            );
            SharedNpcWorldRelay.unregister(
                viewerWriter
            );

            Player81WorldSync.unregister(
                sourceWriter
            );
            Player81WorldSync.unregister(
                viewerWriter
            );

            if(source.registered())
                world.unregisterPlayer(
                    source,
                    sourceGeneration
                );
            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewerGeneration
                );

            world.close();
        }
    }

    private static Object relayContextFor(
        ServerPacketWriter writer
    )throws Exception{
        Field field=
            SharedNpcWorldRelay.class
                .getDeclaredField(
                    "BY_WRITER"
                );
        field.setAccessible(true);

        synchronized(SharedNpcWorldRelay.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<ServerPacketWriter,Object> contexts=
                (IdentityHashMap<ServerPacketWriter,Object>)
                field.get(null);

            return contexts.get(
                writer
            );
        }
    }

    private static Object player81ContextFor(
        ServerPacketWriter writer
    )throws Exception{
        Field field=
            Player81WorldSync.class
                .getDeclaredField(
                    "BY_WRITER"
                );
        field.setAccessible(true);

        synchronized(Player81WorldSync.class){
            @SuppressWarnings("unchecked")
            IdentityHashMap<ServerPacketWriter,Object> contexts=
                (IdentityHashMap<ServerPacketWriter,Object>)
                field.get(null);

            return contexts.get(
                writer
            );
        }
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream sink=
            new ByteArrayOutputStream();

        while(queue.queuedBytes()>0)
            queue.drainTo(
                sink,
                Integer.MAX_VALUE
            );
    }

    private LocalSessionRuntimeBindingsSharedNpcRetentionTest(){}
}

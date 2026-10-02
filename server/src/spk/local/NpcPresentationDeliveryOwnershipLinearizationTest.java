package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NpcPresentationDeliveryOwnershipLinearizationTest {
    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(600L);

        proveOwnershipAdmissionBlocksUnregister(
            world
        );
        proveRecoverableEventMaskRetry();
        proveDirectEventMaskFailureConsumesRetryDebt();

        WorldPlayer viewer=
            new WorldPlayer();

        long generationA=
            world.registerPlayer(
                viewer,
                "npc-delivery-owner"
            );

        ByteArrayOutputStream outA=
            new ByteArrayOutputStream();

        ServerPacketWriter writerA=
            writer(outA,1);

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        String spawned=
            npcs.devSpawnNpc(
                1488,
                0,
                0,
                viewer.movement(),
                writerA
            );

        if(!spawned.startsWith(
                "DEV_NPC_SPAWN_OK"))
            throw new AssertionError(
                "viewer target setup failed: "+
                spawned
            );

        NpcEntity target=
            onlyNpc(npcs.snapshot());

        SharedNpcWorldRelay.register(
            writerA,
            world,
            viewer,
            npcs,
            viewer.movement()
        );

        ServerPacketWriter writerB=null;

        try{
            long now=
                System.currentTimeMillis();

            LinkedHashMap<EntityId,Long>
                recipientsA=
                    recipients(
                        viewer.id(),
                        generationA
                    );

            world.npcPresentationEvents()
                .enqueueOwned(
                    now,
                    EntityId.next(),
                    WorldNpcPresentationEvents
                        .Target.scene(
                            target.sceneIndex,
                            target.definitionId
                        ),
                    NpcSyncEncoder.Mask
                        .forceText(
                            "settlement-a"
                        ),
                    0L,
                    recipientsA
                );

            List<WorldNpcPresentationEvents.Event>
                pendingA=
                    world.npcPresentationEvents()
                        .pendingFor(
                            viewer.id(),
                            generationA,
                            now
                        );

            if(pendingA.size()!=1)
                throw new AssertionError(
                    "generation A settlement fixture missing"
                );

            long sequence=
                pendingA.get(0).sequence;

            world.npcPresentationEvents()
                .markDelivered(
                    sequence,
                    viewer.id(),
                    generationA+1L,
                    now+1L
                );

            if(world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generationA,
                        now+1L
                    ).size()!=1)
                throw new AssertionError(
                    "wrong generation settled generation A event"
                );

            world.npcPresentationEvents()
                .markDelivered(
                    sequence,
                    viewer.id(),
                    generationA,
                    now+2L
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generationA,
                        now+2L
                    ).isEmpty())
                throw new AssertionError(
                    "matching generation did not settle event"
                );

            long currentNow=now+200L;

            world.npcPresentationEvents()
                .enqueueOwned(
                    currentNow,
                    EntityId.next(),
                    WorldNpcPresentationEvents
                        .Target.scene(
                            target.sceneIndex,
                            target.definitionId
                        ),
                    NpcSyncEncoder.Mask
                        .forceText(
                            "current-a"
                        ),
                    0L,
                    recipientsA
                );

            int currentBytesBefore=
                outA.size();

            SharedNpcWorldRelay
                .flushAfterPlayer81(
                    writerA
                );

            if(outA.size()<=
                    currentBytesBefore)
                throw new AssertionError(
                    "current generation flush emitted no packet65"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generationA,
                        currentNow+1L
                    ).isEmpty())
                throw new AssertionError(
                    "current generation flush did not settle"
                );

            long staleNow=now+400L;

            world.npcPresentationEvents()
                .enqueueOwned(
                    staleNow,
                    EntityId.next(),
                    WorldNpcPresentationEvents
                        .Target.scene(
                            target.sceneIndex,
                            target.definitionId
                        ),
                    NpcSyncEncoder.Mask
                        .forceText(
                            "stale-a"
                        ),
                    0L,
                    recipientsA
                );

            if(!world.unregisterPlayer(
                    viewer,
                    generationA
                ))
                throw new AssertionError(
                    "generation A unregister failed"
                );

            long generationB=
                world.registerPlayer(
                    viewer,
                    "npc-delivery-owner"
                );

            if(generationB==generationA)
                throw new AssertionError(
                    "viewer generation did not advance"
                );

            int staleBytesBefore=
                outA.size();

            SharedNpcWorldRelay
                .flushAfterPlayer81(
                    writerA
                );

            if(outA.size()!=
                    staleBytesBefore)
                throw new AssertionError(
                    "stale generation flush emitted packet65"
                );

            if(world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        staleNow+1L
                    ).size()!=1)
                throw new AssertionError(
                    "stale event was settled by stale flush"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generationB,
                        staleNow+1L
                    ).isEmpty())
                throw new AssertionError(
                    "replacement generation inherited stale event"
                );

            SharedNpcWorldRelay.unregister(
                writerA
            );

            ByteArrayOutputStream outB=
                new ByteArrayOutputStream();

            writerB=
                writer(outB,5);

            SharedNpcWorldRelay.register(
                writerB,
                world,
                viewer,
                npcs,
                viewer.movement()
            );

            long freshNow=
                now+600L;

            LinkedHashMap<EntityId,Long>
                recipientsB=
                    recipients(
                        viewer.id(),
                        generationB
                    );

            world.npcPresentationEvents()
                .enqueueOwned(
                    freshNow,
                    EntityId.next(),
                    WorldNpcPresentationEvents
                        .Target.scene(
                            target.sceneIndex,
                            target.definitionId
                        ),
                    NpcSyncEncoder.Mask
                        .forceText(
                            "fresh-b"
                        ),
                    0L,
                    recipientsB
                );

            int freshBytesBefore=
                outB.size();

            SharedNpcWorldRelay
                .flushAfterPlayer81(
                    writerB
                );

            if(outB.size()<=
                    freshBytesBefore)
                throw new AssertionError(
                    "replacement generation flush emitted no packet65"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generationB,
                        freshNow+1L
                    ).isEmpty())
                throw new AssertionError(
                    "replacement generation event did not settle"
                );

            System.out.println(
                "NPC_PRESENTATION_DELIVERY_OWNERSHIP_LINEARIZATION_PASS "+
                "ownershipAdmissionBlocksUnregister=true "+
                "wrongGenerationSettlementRejected=true "+
                "matchingSettlementAccepted=true "+
                "currentFlushDelivered=true "+
                "staleFlushRejected=true "+
                "replacementFlushDelivered=true "+
                "eventMaskRetryCipherRewound=true "+
                "eventMaskQueueFailurePending=true "+
                "eventMaskDirectFailureDebtConsumed=true"
            );

            world.unregisterPlayer(
                viewer,
                generationB
            );
        }finally{
            SharedNpcWorldRelay.unregister(
                writerA
            );

            if(writerB!=null)
                SharedNpcWorldRelay.unregister(
                    writerB
                );

            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    viewer.generation()
                );

            world.close();
        }
    }

    private static void proveRecoverableEventMaskRetry()
        throws Exception
    {
        World retryWorld=
            World.isolatedForTest(601L);
        World cleanWorld=
            World.isolatedForTest(602L);
        WorldPlayer retryViewer=
            new WorldPlayer();
        WorldPlayer cleanViewer=
            new WorldPlayer();

        long retryGeneration=
            retryWorld.registerPlayer(
                retryViewer,
                "event-mask-retry"
            );
        long cleanGeneration=
            cleanWorld.registerPlayer(
                cleanViewer,
                "event-mask-clean"
            );

        OutboundPacketQueue retryQueue=
            new OutboundPacketQueue(1024);
        OutboundPacketQueue cleanQueue=
            new OutboundPacketQueue(1024);
        int[] seed=
            new int[]{31,32,33,34};

        ServerPacketWriter retryWriter=
            new ServerPacketWriter(
                retryQueue,
                new IsaacCipher(seed.clone())
            );
        ServerPacketWriter cleanWriter=
            new ServerPacketWriter(
                cleanQueue,
                new IsaacCipher(seed.clone())
            );

        NpcRegistry retryNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );
        NpcRegistry cleanNpcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        try{
            String retrySpawn=
                retryNpcs.devSpawnNpc(
                    1488,
                    0,
                    0,
                    retryViewer.movement(),
                    retryWriter
                );
            String cleanSpawn=
                cleanNpcs.devSpawnNpc(
                    1488,
                    0,
                    0,
                    cleanViewer.movement(),
                    cleanWriter
                );

            if(!retrySpawn.startsWith(
                    "DEV_NPC_SPAWN_OK")||
               !cleanSpawn.startsWith(
                    "DEV_NPC_SPAWN_OK"))
                throw new AssertionError(
                    "event mask retry fixture spawn failed"
                );

            ByteArrayOutputStream retryInitial=
                new ByteArrayOutputStream();
            ByteArrayOutputStream cleanInitial=
                new ByteArrayOutputStream();

            retryQueue.drainTo(
                retryInitial,
                Integer.MAX_VALUE
            );
            cleanQueue.drainTo(
                cleanInitial,
                Integer.MAX_VALUE
            );

            if(!Arrays.equals(
                    retryInitial.toByteArray(),
                    cleanInitial.toByteArray()))
                throw new AssertionError(
                    "event mask fixture initial bytes diverged"
                );

            NpcEntity retryTarget=
                onlyNpc(retryNpcs.snapshot());
            NpcEntity cleanTarget=
                onlyNpc(cleanNpcs.snapshot());

            SharedNpcWorldRelay.register(
                retryWriter,
                retryWorld,
                retryViewer,
                retryNpcs,
                retryViewer.movement()
            );
            SharedNpcWorldRelay.register(
                cleanWriter,
                cleanWorld,
                cleanViewer,
                cleanNpcs,
                cleanViewer.movement()
            );

            long retryNow=
                System.currentTimeMillis();
            long cleanNow=
                retryNow;

            retryWorld.npcPresentationEvents()
                .enqueueOwned(
                    retryNow,
                    EntityId.next(),
                    WorldNpcPresentationEvents.Target.scene(
                        retryTarget.sceneIndex,
                        retryTarget.definitionId
                    ),
                    NpcSyncEncoder.Mask.forceText(
                        "retractable-event"
                    ),
                    0L,
                    recipients(
                        retryViewer.id(),
                        retryGeneration
                    )
                );

            cleanWorld.npcPresentationEvents()
                .enqueueOwned(
                    cleanNow,
                    EntityId.next(),
                    WorldNpcPresentationEvents.Target.scene(
                        cleanTarget.sceneIndex,
                        cleanTarget.definitionId
                    ),
                    NpcSyncEncoder.Mask.forceText(
                        "retractable-event"
                    ),
                    0L,
                    recipients(
                        cleanViewer.id(),
                        cleanGeneration
                    )
                );

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    retryQueue,
                    1024
                );

            SharedNpcWorldRelay.flushAfterPlayer81(
                retryWriter
            );

            if(retryQueue.queuedBytes()!=0||
               retryWorld.npcPresentationEvents()
                    .pendingFor(
                        retryViewer.id(),
                        retryGeneration,
                        retryNow+1L
                    ).size()!=1)
                throw new AssertionError(
                    "recoverable event mask rejection committed bytes/delivery"
                );

            pressure.release();

            SharedNpcWorldRelay.flushAfterPlayer81(
                retryWriter
            );
            SharedNpcWorldRelay.flushAfterPlayer81(
                cleanWriter
            );

            ByteArrayOutputStream retryBytes=
                new ByteArrayOutputStream();
            ByteArrayOutputStream cleanBytes=
                new ByteArrayOutputStream();

            retryQueue.drainTo(
                retryBytes,
                Integer.MAX_VALUE
            );
            cleanQueue.drainTo(
                cleanBytes,
                Integer.MAX_VALUE
            );

            if(!Arrays.equals(
                    retryBytes.toByteArray(),
                    cleanBytes.toByteArray()))
                throw new AssertionError(
                    "recoverable event mask retry bytes diverged from clean same-seed send"
                );

            if(!retryWorld.npcPresentationEvents()
                    .pendingFor(
                        retryViewer.id(),
                        retryGeneration,
                        retryNow+2L
                    ).isEmpty()||
               !cleanWorld.npcPresentationEvents()
                    .pendingFor(
                        cleanViewer.id(),
                        cleanGeneration,
                        cleanNow+2L
                    ).isEmpty())
                throw new AssertionError(
                    "successful event mask publication did not settle exactly once"
                );
        }finally{
            SharedNpcWorldRelay.unregister(
                retryWriter
            );
            SharedNpcWorldRelay.unregister(
                cleanWriter
            );

            if(retryViewer.registered())
                retryWorld.unregisterPlayer(
                    retryViewer,
                    retryGeneration
                );
            if(cleanViewer.registered())
                cleanWorld.unregisterPlayer(
                    cleanViewer,
                    cleanGeneration
                );

            retryWorld.close();
            cleanWorld.close();
        }
    }

    private static void proveDirectEventMaskFailureConsumesRetryDebt()
        throws Exception
    {
        World world=
            World.isolatedForTest(603L);
        WorldPlayer viewer=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                viewer,
                "event-mask-direct"
            );

        PartialFailOutputStream out=
            new PartialFailOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                out,
                new IsaacCipher(
                    new int[]{35,36,37,38}
                )
            );

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        try{
            String spawned=
                npcs.devSpawnNpc(
                    1488,
                    0,
                    0,
                    viewer.movement(),
                    writer
                );

            if(!spawned.startsWith(
                    "DEV_NPC_SPAWN_OK"))
                throw new AssertionError(
                    "direct event mask fixture spawn failed"
                );

            NpcEntity target=
                onlyNpc(npcs.snapshot());

            SharedNpcWorldRelay.register(
                writer,
                world,
                viewer,
                npcs,
                viewer.movement()
            );

            long now=
                System.currentTimeMillis();

            world.npcPresentationEvents()
                .enqueueOwned(
                    now,
                    EntityId.next(),
                    WorldNpcPresentationEvents.Target.scene(
                        target.sceneIndex,
                        target.definitionId
                    ),
                    NpcSyncEncoder.Mask.forceText(
                        "direct-terminal"
                    ),
                    0L,
                    recipients(
                        viewer.id(),
                        generation
                    )
                );

            out.fail=true;

            boolean terminal=false;
            try{
                SharedNpcWorldRelay.flushAfterPlayer81(
                    writer
                );
            }catch(ServerPacketWriter.NonRetractablePublicationException expected){
                terminal=true;
            }

            if(!terminal)
                throw new AssertionError(
                    "direct event mask failure was not classified non-retractable"
                );

            if(!world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now+1L
                    ).isEmpty())
                throw new AssertionError(
                    "non-retractable event mask failure left semantic retry debt"
                );
        }finally{
            SharedNpcWorldRelay.unregister(
                writer
            );

            if(viewer.registered())
                world.unregisterPlayer(
                    viewer,
                    generation
                );

            world.close();
        }
    }

    private static final class PartialFailOutputStream
        extends java.io.OutputStream
    {
        final ByteArrayOutputStream bytes=
            new ByteArrayOutputStream();
        boolean fail;

        @Override public void write(int value)
            throws java.io.IOException
        {
            if(fail){
                bytes.write(value);
                throw new java.io.IOException(
                    "PARTIAL_DIRECT_FAIL"
                );
            }

            bytes.write(value);
        }

        @Override public void write(
            byte[] data,
            int offset,
            int length
        )throws java.io.IOException{
            if(fail){
                int partial=
                    Math.min(
                        1,
                        length
                    );

                if(partial>0)
                    bytes.write(
                        data,
                        offset,
                        partial
                    );

                throw new java.io.IOException(
                    "PARTIAL_DIRECT_FAIL"
                );
            }

            bytes.write(
                data,
                offset,
                length
            );
        }

        @Override public void flush()
            throws java.io.IOException
        {
            if(fail)
                throw new java.io.IOException(
                    "PARTIAL_DIRECT_FAIL"
                );
        }
    }

    private static void proveOwnershipAdmissionBlocksUnregister(
        World world
    )throws Exception{
        WorldPlayer probe=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                probe,
                "npc-delivery-lock-probe"
            );

        CountDownLatch entered=
            new CountDownLatch(1);
        CountDownLatch release=
            new CountDownLatch(1);
        CountDownLatch unregisterDone=
            new CountDownLatch(1);

        AtomicBoolean admitted=
            new AtomicBoolean();
        AtomicBoolean unregistered=
            new AtomicBoolean();

        Thread admission=
            new Thread(
                ()->{
                    try{
                        boolean accepted=
                            world.withOpenPlayerOwnershipIfCurrent(
                                probe,
                                generation,
                                ()->{
                                    entered.countDown();

                                    boolean interrupted=false;
                                    while(release.getCount()>0L){
                                        try{
                                            release.await(
                                                10L,
                                                TimeUnit.MILLISECONDS
                                            );
                                        }catch(InterruptedException ignored){
                                            interrupted=true;
                                        }
                                    }

                                    if(interrupted)
                                        Thread.currentThread()
                                            .interrupt();
                                }
                            );
                        admitted.set(accepted);
                    }catch(Exception error){
                        throw new RuntimeException(
                            error
                        );
                    }
                },
                "npc-delivery-admission-probe"
            );

        Thread unregister=
            new Thread(
                ()->{
                    try{
                        unregistered.set(
                            world.unregisterPlayer(
                                probe,
                                generation
                            )
                        );
                    }finally{
                        unregisterDone.countDown();
                    }
                },
                "npc-delivery-unregister-probe"
            );

        admission.start();

        if(!entered.await(
                2L,
                TimeUnit.SECONDS))
            throw new AssertionError(
                "ownership admission action did not start"
            );

        unregister.start();

        if(unregisterDone.await(
                100L,
                TimeUnit.MILLISECONDS))
            throw new AssertionError(
                "unregister crossed active World ownership admission"
            );

        release.countDown();

        admission.join(2_000L);
        unregister.join(2_000L);

        if(admission.isAlive()||
           unregister.isAlive())
            throw new AssertionError(
                "ownership admission concurrency probe did not terminate"
            );

        if(!admitted.get())
            throw new AssertionError(
                "current ownership admission was rejected"
            );

        if(!unregistered.get())
            throw new AssertionError(
                "unregister did not complete after admission released"
            );
    }

    private static LinkedHashMap<EntityId,Long>
        recipients(
            EntityId id,
            long generation
        ){
        LinkedHashMap<EntityId,Long> out=
            new LinkedHashMap<>();
        out.put(id,generation);
        return out;
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream out,
        int seed
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    private static NpcEntity onlyNpc(
        List<NpcEntity> npcs
    ){
        if(npcs.size()!=1)
            throw new AssertionError(
                "expected one local NPC, got "+
                npcs.size()
            );
        return npcs.get(0);
    }

    private NpcPresentationDeliveryOwnershipLinearizationTest(){}
}

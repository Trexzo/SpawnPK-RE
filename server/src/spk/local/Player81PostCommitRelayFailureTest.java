package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;

public final class Player81PostCommitRelayFailureTest {
    public static void main(String[] args)throws Exception{
        assertUnbatchedRelayFailureIsPostCommit();

        System.out.println(
            "PLAYER81_POSTCOMMIT_RELAY_FAILURE_PASS "+
            "packet81CommitReportedSuccess=true "+
            "semanticCommitExactlyOnce=true "+
            "relayFailurePostCommitOnly=true "+
            "relayEventRemainsPending=true"
        );
    }

    private static void assertUnbatchedRelayFailureIsPostCommit()
        throws Exception
    {
        World world=
            World.isolatedForTest(
                600L
            );
        WorldPlayer viewer=
            new WorldPlayer();

        long generation=
            world.registerPlayer(
                viewer,
                "packet81-postcommit-relay"
            );

        OutboundPacketQueue queue=
            new OutboundPacketQueue(
                1024
            );
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{401,402,403,404}
                )
            );

        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        Player81WorldSync.register(
            writer,
            world,
            viewer,
            new DevAuthorityWorkbench()
        );

        SharedNpcWorldRelay.register(
            writer,
            world,
            viewer,
            npcs,
            viewer.movement()
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

            if(spawned==null||
               !spawned.startsWith(
                    "DEV_NPC_SPAWN_OK"
               ))
                throw new AssertionError(
                    "relay target setup failed: "+
                    spawned
                );

            NpcEntity target=
                npcs.snapshot().get(0);

            drain(queue);

            byte[] local=
                CombatSync.player81AnimationOnly(
                    827
                );

            Player81WorldSync.PreparedBatchStart calibration=
                Player81WorldSync.beginPreparedBatchStatus(
                    writer
                );

            if(calibration.status!=
                    Player81WorldSync
                        .PreparedBatchStartStatus
                        .PREPARED||
               calibration.prepared==null)
                throw new AssertionError(
                    "packet81 calibration did not prepare"
                );

            byte[] transformed=
                Player81WorldSync.transformPrepared(
                    calibration.prepared,
                    local
                );

            Player81WorldSync.abortPreparedBatch(
                calibration.prepared
            );

            int packet81Bytes=
                transformed.length+3;
            int filler=
                1024-packet81Bytes;

            if(filler<=0)
                throw new AssertionError(
                    "unexpected packet81 calibration size="+
                    packet81Bytes
                );

            LinkedHashMap<EntityId,Long>
                recipients=
                    new LinkedHashMap<>();
            recipients.put(
                viewer.id(),
                generation
            );

            long now=
                System.currentTimeMillis();

            boolean queued=
                world.npcPresentationEvents()
                    .enqueueOwned(
                        now,
                        viewer.id(),
                        generation,
                        WorldNpcPresentationEvents
                            .Target.scene(
                                target.sceneIndex,
                                target.definitionId
                            ),
                        NpcSyncEncoder.Mask
                            .forceText(
                                "postcommit-relay-failure"
                            ),
                        0L,
                        recipients
                    );

            if(!queued)
                throw new AssertionError(
                    "relay event setup failed"
                );

            queue.offer(
                new byte[filler]
            );

            long sequenceBefore=
                Player81WorldSync
                    .latestPublishedEventSequence(
                        writer
                    );

            /*
             * The packet81 reservation consumes the exact remaining queue
             * capacity. The subsequent relay S2C65 therefore fails after the
             * packet81 transport + semantic commit point.
             *
             * varShort(81) must nevertheless return successfully.
             */
            writer.varShort(
                81,
                local
            );

            long sequenceAfter=
                Player81WorldSync
                    .latestPublishedEventSequence(
                        writer
                    );

            if(sequenceAfter<=sequenceBefore)
                throw new AssertionError(
                    "Player81 semantic sequence did not commit exactly once before relay failure before="+
                    sequenceBefore+
                    " after="+
                    sequenceAfter
                );

            if(queue.queuedBytes()!=1024)
                throw new AssertionError(
                    "packet81 did not consume calibrated queue capacity bytes="+
                    queue.queuedBytes()
                );

            if(world.npcPresentationEvents()
                    .pendingFor(
                        viewer.id(),
                        generation,
                        now+1L
                    ).size()!=1)
                throw new AssertionError(
                    "failed post-commit relay event was incorrectly marked delivered"
                );

            if(!queue.overflowed())
                throw new AssertionError(
                    "relay failure fixture did not reach outbound overflow"
                );
        }finally{
            SharedNpcWorldRelay.unregister(
                writer
            );
            Player81WorldSync.unregister(
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

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream sink=
            new ByteArrayOutputStream();

        while(queue.queuedBytes()>0)
            queue.drainTo(
                sink,
                1<<20
            );
    }

    private Player81PostCommitRelayFailureTest(){}
}

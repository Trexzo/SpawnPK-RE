package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HomeNpcBatchCommitFenceTest {
    private static final int QUEUE_CAPACITY=4096;

    public static void main(String[] args)throws Exception{
        World world=
            World.isolatedForTest(
                60_000L
            );

        try{
            MovementState movement=
                new MovementState();
            PetState petState=
                new PetState();
            HomeWorldRuntimePlan home=
                new HomeWorldRuntimePlan(
                    world.homeNpcs()
                );
            NpcRegistry npcs=
                new NpcRegistry();

            OutboundPacketQueue queue=
                new OutboundPacketQueue(
                    QUEUE_CAPACITY
                );
            IsaacCipher cipher=
                new IsaacCipher(
                    new int[]{901,902,903,904}
                );
            ServerPacketWriter writer=
                new ServerPacketWriter(
                    queue,
                    cipher
                );

            npcs.bootstrapHome(
                writer,
                movement,
                petState,
                home
            );
            drain(
                queue
            );

            Map<Integer,String> viewerBefore=
                homeProjection(
                    npcs.snapshot()
                );
            Set<Integer> visibleBefore=
                new LinkedHashSet<>(
                    home.snapshotViewerPresentation()
                        .clientVisibleWorldSceneIndexes
                );

            HomeNpcSpawnRepository.Spawn wanderer=
                chooseVisibleWanderer(
                    viewerBefore.keySet()
                );
            int scene=
                HomeNpcRuntimePlan
                    .sceneIndexForOrdinal(
                        wanderer.ordinal
                    );
            int tick=
                HomeNpcWanderRepository
                    .cadenceTicks(
                        wanderer.ordinal
                    );

            if(tick<=0||
               tick==Integer.MAX_VALUE)
                throw new AssertionError(
                    "HOME batch fixture cadence invalid "+
                    tick
                );

            WorldNpc canonical=
                world.homeNpcs()
                    .canonicalForOrdinal(
                        wanderer.ordinal
                    );

            if(canonical==null)
                throw new AssertionError(
                    "HOME batch canonical fixture missing"
                );

            Tile canonicalBefore=
                canonical.tile();
            IsaacCipher.Snapshot cipherBefore=
                cipher.snapshot();

            OutboundPacketQueue.BatchReservation pressure=
                OutboundPacketQueue.reserveBatch(
                    queue,
                    QUEUE_CAPACITY
                );

            boolean admissionFailed=false;
            String staged;

            writer.beginBatch();
            npcs.beginHomePresentationBatch(
                home
            );

            try{
                staged=
                    npcs.tickHome(
                        movement,
                        writer,
                        home,
                        tick
                    );

                if(staged==null)
                    throw new AssertionError(
                        "HOME batch fixture produced no staged pulse"
                    );

                if(homeProjection(
                        npcs.snapshot()
                    ).equals(
                        viewerBefore))
                    throw new AssertionError(
                        "HOME viewer projection did not advance prospectively"
                    );

                LocalSession.endWorldTickBatch(
                    writer
                );
            }catch(IOException expected){
                admissionFailed=true;
            }finally{
                pressure.release();
            }

            if(!admissionFailed)
                throw new AssertionError(
                    "HOME outer batch admission failure did not escape"
                );

            if(!npcs.abortHomePresentationBatch(
                    home))
                throw new AssertionError(
                    "HOME staged viewer projection was not aborted"
                );

            if(!viewerBefore.equals(
                    homeProjection(
                        npcs.snapshot()
                    )))
                throw new AssertionError(
                    "HOME viewer projection did not restore exactly after abort"
                );

            Set<Integer> visibleAfterAbort=
                new LinkedHashSet<>(
                    home.snapshotViewerPresentation()
                        .clientVisibleWorldSceneIndexes
                );

            if(!visibleBefore.equals(
                    visibleAfterAbort))
                throw new AssertionError(
                    "HOME client-visible scene set did not restore after abort"
                );

            if(!npcs.homePresentationResyncRequired())
                throw new AssertionError(
                    "HOME abort did not arm authoritative viewer resync"
                );

            Tile canonicalAfterAbort=
                canonical.tile();

            if(canonicalAfterAbort.x==
                    canonicalBefore.x&&
               canonicalAfterAbort.y==
                    canonicalBefore.y)
                throw new AssertionError(
                    "HOME canonical fixture did not advance on failed viewer tick"
                );

            assertCipherEquals(
                cipherBefore,
                cipher.snapshot()
            );

            if(queue.queuedBytes()!=0)
                throw new AssertionError(
                    "HOME failed outer batch leaked bytes="+
                    queue.queuedBytes()
                );

            /*
             * Production's next World tick cannot replay the missed historical
             * walk delta. The armed viewer fence must instead rebuild HOME from
             * current canonical authority in two staged packet-65 updates.
             */
            writer.beginBatch();
            npcs.beginHomePresentationBatch(
                home
            );

            String retried=
                npcs.tickHome(
                    movement,
                    writer,
                    home,
                    tick+1L
                );

            if(retried==null||
               !retried.contains(
                   "resync=AUTHORITATIVE_CANONICAL"))
                throw new AssertionError(
                    "HOME retry did not use authoritative resync result="+
                    retried
                );

            LocalSession.endWorldTickBatch(
                writer
            );

            if(!npcs.commitHomePresentationBatch())
                throw new AssertionError(
                    "HOME successful retry did not commit viewer projection"
                );

            drain(
                queue
            );

            Map<Integer,String> authoritative=
                homeProjection(
                    home.currentProjection(
                        movement.x(),
                        movement.y()
                    )
                );
            Map<Integer,String> viewerAfterRetry=
                homeProjection(
                    npcs.snapshot()
                );

            if(!authoritative.equals(
                    viewerAfterRetry))
                throw new AssertionError(
                    "HOME retry viewer projection differs from canonical authority"
                );

            Set<Integer> committedVisible=
                new LinkedHashSet<>(
                    home.snapshotViewerPresentation()
                        .clientVisibleWorldSceneIndexes
                );

            if(!committedVisible.equals(
                    authoritative.keySet()))
                throw new AssertionError(
                    "HOME committed client-visible set differs from authoritative projection"
                );

            if(npcs.homePresentationResyncRequired())
                throw new AssertionError(
                    "HOME successful retry left resync armed"
                );

            System.out.println(
                "HOME_NPC_BATCH_COMMIT_FENCE_PASS "+
                "abortRestoresViewerProjection=true "+
                "canonicalWorldNotRolledBack=true "+
                "retryResyncsAuthoritative=true "+
                "commitAdvancesViewerProjection=true"
            );
        }finally{
            world.close();
        }
    }

    private static HomeNpcSpawnRepository.Spawn
        chooseVisibleWanderer(
            Set<Integer> visibleScenes
        )
    {
        for(HomeNpcSpawnRepository.Spawn spawn:
                HomeNpcRuntimePlan
                    .observedWanderers()){
            int scene=
                HomeNpcRuntimePlan
                    .sceneIndexForOrdinal(
                        spawn.ordinal
                    );

            if(!visibleScenes.contains(
                    scene))
                continue;

            int cadence=
                HomeNpcWanderRepository
                    .cadenceTicks(
                        spawn.ordinal
                    );

            if(cadence<=0||
               cadence==Integer.MAX_VALUE)
                continue;

            if(!HomeNpcWanderRepository
                    .outgoing(
                        spawn.ordinal,
                        spawn.anchorX,
                        spawn.anchorY
                    ).isEmpty())
                return spawn;
        }

        throw new AssertionError(
            "no initially visible HOME wanderer fixture"
        );
    }

    private static Map<Integer,String> homeProjection(
        List<NpcEntity> entities
    ){
        LinkedHashMap<Integer,String> out=
            new LinkedHashMap<>();

        for(NpcEntity npc:entities)
            if(HomeWorldRuntimePlan
                    .isHomeWorldSceneIndex(
                        npc.sceneIndex
                    ))
                out.put(
                    npc.sceneIndex,
                    npc.definitionId+":"+
                    npc.x+":"+
                    npc.y
                );

        return out;
    }

    private static void assertCipherEquals(
        IsaacCipher.Snapshot expected,
        IsaacCipher.Snapshot actual
    ){
        if(expected.count!=actual.count||
           expected.accumulator!=actual.accumulator||
           expected.lastResult!=actual.lastResult||
           expected.counter!=actual.counter||
           !Arrays.equals(
               expected.results,
               actual.results
           )||
           !Arrays.equals(
               expected.memory,
               actual.memory
           ))
            throw new AssertionError(
                "HOME outer batch did not restore exact ISAAC snapshot"
            );
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        queue.drainTo(
            out,
            Integer.MAX_VALUE
        );
    }

    private HomeNpcBatchCommitFenceTest(){}
}

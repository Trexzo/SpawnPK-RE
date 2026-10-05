package spk.local;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.Arrays;

public final class MiniPetBatchFailureAtomicityTest {
    private static final int QUEUE_CAPACITY=1024;
    private static final int[] SEED=
        new int[]{741,742,743,744};

    public static void main(String[] args)throws Exception{
        WorldPlayer player=
            new WorldPlayer();
        MovementState movement=
            player.movement();
        PetState petState=
            player.petState();
        MiniPetService miniPets=
            player.miniPets();
        NpcRegistry npcs=
            new NpcRegistry(
                new DevAuthorityWorkbench()
            );

        ServerPacketWriter setupWriter=
            new ServerPacketWriter(
                new ByteArrayOutputStream(),
                new IsaacCipher(
                    new int[]{731,732,733,734}
                )
            );

        PetDefinitionRepository.Def main=
            PetDefinitionRepository.get(
                24019
            );

        if(main==null)
            throw new AssertionError(
                "mini atomicity main-pet fixture missing"
            );

        String mainSpawn=
            npcs.spawnPet(
                main,
                movement,
                setupWriter
            );

        if(mainSpawn==null||
           !mainSpawn.startsWith(
               "PET_SPAWN_OK"))
            throw new AssertionError(
                "mini atomicity main-pet spawn failed: "+
                mainSpawn
            );

        petState.activate(
            main
        );

        MiniPetDefinitionRepository.Def first=
            MiniPetDefinitionRepository.get(
                22088
            );

        if(first==null)
            throw new AssertionError(
                "mini atomicity configured fixture 22088 missing"
            );

        MiniPetDefinitionRepository.Def second=null;

        for(MiniPetDefinitionRepository.Def candidate:
                MiniPetDefinitionRepository.all())
            if(candidate.itemId!=first.itemId){
                second=candidate;
                break;
            }

        if(second==null)
            throw new AssertionError(
                "mini atomicity second mini definition missing"
            );

        String firstConfigured=
            miniPets.configure(
                first.itemId,
                petState,
                npcs,
                movement,
                setupWriter
            );

        if(firstConfigured==null||
           !firstConfigured.contains(
               "MINIPET_CONFIGURED")||
           !petState.miniConfigured()||
           petState.miniItemId()!=first.itemId||
           npcs.miniPet()==null)
            throw new AssertionError(
                "mini atomicity initial configure failed result="+
                firstConfigured
            );

        NpcEntity firstActor=
            npcs.miniPet();

        OutboundPacketQueue queue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        IsaacCipher writerCipher=
            new IsaacCipher(
                SEED.clone()
            );
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                writerCipher
            );

        IsaacCipher.Snapshot configureCipherBefore=
            writerCipher.snapshot();

        OutboundPacketQueue.BatchReservation
            configurePressure=
                OutboundPacketQueue.reserveBatch(
                    queue,
                    QUEUE_CAPACITY
                );

        boolean configureFailed=false;

        try{
            miniPets.configure(
                second.itemId,
                petState,
                npcs,
                movement,
                writer
            );
        }catch(java.io.IOException expected){
            configureFailed=true;
        }finally{
            configurePressure.release();
        }

        if(!configureFailed)
            throw new AssertionError(
                "mini configure queue pressure did not fail"
            );

        assertWriterRewound(
            writer,
            queue,
            writerCipher,
            configureCipherBefore,
            "configure"
        );

        if(!petState.miniConfigured()||
           petState.miniItemId()!=first.itemId||
           npcs.miniPet()!=firstActor)
            throw new AssertionError(
                "mini configure failure changed semantic preimage"
            );

        String configureRetry=
            miniPets.configure(
                second.itemId,
                petState,
                npcs,
                movement,
                writer
            );

        if(configureRetry==null||
           !configureRetry.contains(
               "MINIPET_CONFIGURED")||
           !petState.miniConfigured()||
           petState.miniItemId()!=second.itemId||
           npcs.miniPet()==null||
           npcs.miniPet()==firstActor)
            throw new AssertionError(
                "same-writer mini configure retry did not commit result="+
                configureRetry
            );

        drain(
            queue
        );

        NpcEntity configuredActor=
            npcs.miniPet();
        IsaacCipher.Snapshot offCipherBefore=
            writerCipher.snapshot();

        OutboundPacketQueue.BatchReservation
            offPressure=
                OutboundPacketQueue.reserveBatch(
                    queue,
                    QUEUE_CAPACITY
                );

        boolean offFailed=false;

        try{
            miniPets.off(
                petState,
                npcs,
                writer
            );
        }catch(java.io.IOException expected){
            offFailed=true;
        }finally{
            offPressure.release();
        }

        if(!offFailed)
            throw new AssertionError(
                "mini off queue pressure did not fail"
            );

        assertWriterRewound(
            writer,
            queue,
            writerCipher,
            offCipherBefore,
            "off"
        );

        if(!petState.miniConfigured()||
           petState.miniItemId()!=second.itemId||
           npcs.miniPet()!=configuredActor)
            throw new AssertionError(
                "mini off failure changed semantic preimage"
            );

        String offRetry=
            miniPets.off(
                petState,
                npcs,
                writer
            );

        if(offRetry==null||
           !offRetry.contains(
               "MINIPET_DISABLED")||
           petState.miniConfigured()||
           npcs.miniPet()!=null)
            throw new AssertionError(
                "same-writer mini off retry did not commit result="+
                offRetry
            );

        System.out.println(
            "MINIPET_BATCH_FAILURE_ATOMICITY_PASS "+
            "configureAbortRestoresWriter=true "+
            "configureStateUnchanged=true "+
            "offAbortRestoresWriter=true "+
            "offStateUnchanged=true "+
            "retryWorks=true"
        );
    }

    private static void assertWriterRewound(
        ServerPacketWriter writer,
        OutboundPacketQueue queue,
        IsaacCipher cipher,
        IsaacCipher.Snapshot expectedCipher,
        String stage
    )throws Exception{
        if(writer.terminal())
            throw new AssertionError(
                "mini "+stage+
                " failure terminal-latched retractable writer"
            );

        if(queue.queuedBytes()!=0)
            throw new AssertionError(
                "mini "+stage+
                " failure leaked packet bytes="+
                queue.queuedBytes()
            );

        Field depthField=
            ServerPacketWriter.class
                .getDeclaredField(
                    "batchDepth"
                );
        depthField.setAccessible(true);

        if(depthField.getInt(writer)!=0)
            throw new AssertionError(
                "mini "+stage+
                " failure left batch depth="+
                depthField.getInt(writer)
            );

        Field pendingField=
            ServerPacketWriter.class
                .getDeclaredField(
                    "pending"
                );
        pendingField.setAccessible(true);
        ByteArrayOutputStream pending=
            (ByteArrayOutputStream)
                pendingField.get(
                    writer
                );

        if(pending.size()!=0)
            throw new AssertionError(
                "mini "+stage+
                " failure left pending bytes="+
                pending.size()
            );

        Field checkpointField=
            ServerPacketWriter.class
                .getDeclaredField(
                    "batchCipherCheckpoint"
                );
        checkpointField.setAccessible(true);

        if(checkpointField.get(writer)!=null)
            throw new AssertionError(
                "mini "+stage+
                " failure left cipher checkpoint active"
            );

        assertCipherEquals(
            expectedCipher,
            cipher.snapshot(),
            stage
        );
    }

    private static void assertCipherEquals(
        IsaacCipher.Snapshot expected,
        IsaacCipher.Snapshot actual,
        String stage
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
                "mini "+stage+
                " failure did not restore exact ISAAC snapshot"
            );
    }

    private static void drain(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        int drained=
            queue.drainTo(
                out,
                1<<20
            );

        if(drained<=0)
            throw new AssertionError(
                "expected committed mini transaction bytes before drain"
            );
    }

    private MiniPetBatchFailureAtomicityTest(){}
}

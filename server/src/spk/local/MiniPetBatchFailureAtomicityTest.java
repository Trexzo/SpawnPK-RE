package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

public final class MiniPetBatchFailureAtomicityTest {
    private static final int QUEUE_CAPACITY=1024;
    private static final int MINI_ITEM=23629;

    public static void main(String[] args)throws Exception{
        assertConfigureAbortRestoresWriterAndState();
        assertOffAbortRestoresWriterAndState();

        System.out.println(
            "MINIPET_BATCH_FAILURE_ATOMICITY_PASS "+
            "configureAbortRestoresWriter=true "+
            "configureStateUnchanged=true "+
            "offAbortRestoresWriter=true "+
            "offStateUnchanged=true "+
            "retryWorks=true"
        );
    }

    private static void assertConfigureAbortRestoresWriterAndState()
        throws Exception
    {
        Fixture failed=
            Fixture.withMainPet();
        OutboundPacketQueue queue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        int[] seed=
            new int[]{101,102,103,104};
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    seed.clone()
                )
            );

        queue.offer(
            new byte[QUEUE_CAPACITY]
        );

        NpcEntity mainBefore=
            failed.npcs.pet();

        boolean escaped=false;
        try{
            failed.service.configure(
                MINI_ITEM,
                failed.state,
                failed.npcs,
                failed.movement,
                writer
            );
        }catch(IOException expected){
            escaped=true;
        }

        if(!escaped)
            throw new AssertionError(
                "configure queue admission failure did not escape"
            );

        assertWriterRewound(
            writer,
            queue,
            "configure"
        );

        if(failed.state.miniConfigured()||
           failed.npcs.miniPet()!=null||
           failed.npcs.pet()!=mainBefore)
            throw new AssertionError(
                "failed configure changed semantic preimage"
            );

        drainDiscard(
            queue
        );

        String retry=
            failed.service.configure(
                MINI_ITEM,
                failed.state,
                failed.npcs,
                failed.movement,
                writer
            );

        if(retry==null||
           !retry.startsWith(
               "MINIPET_CONFIGURED")||
           !failed.state.miniConfigured()||
           failed.state.miniItemId()!=MINI_ITEM||
           failed.npcs.miniPet()==null)
            throw new AssertionError(
                "configure same-writer retry failed result="+
                retry
            );

        byte[] retryBytes=
            drainBytes(
                queue
            );

        Fixture control=
            Fixture.withMainPet();
        OutboundPacketQueue controlQueue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        ServerPacketWriter controlWriter=
            new ServerPacketWriter(
                controlQueue,
                new IsaacCipher(
                    seed.clone()
                )
            );

        String clean=
            control.service.configure(
                MINI_ITEM,
                control.state,
                control.npcs,
                control.movement,
                controlWriter
            );

        if(clean==null||
           !clean.startsWith(
               "MINIPET_CONFIGURED"))
            throw new AssertionError(
                "configure control failed result="+
                clean
            );

        byte[] cleanBytes=
            drainBytes(
                controlQueue
            );

        if(!Arrays.equals(
                retryBytes,
                cleanBytes))
            throw new AssertionError(
                "configure retry ciphertext/pending state differs from clean control retry="+
                retryBytes.length+
                " clean="+
                cleanBytes.length
            );
    }

    private static void assertOffAbortRestoresWriterAndState()
        throws Exception
    {
        Fixture failed=
            Fixture.withConfiguredMini();
        OutboundPacketQueue queue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        int[] seed=
            new int[]{111,112,113,114};
        ServerPacketWriter writer=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    seed.clone()
                )
            );

        queue.offer(
            new byte[QUEUE_CAPACITY]
        );

        NpcEntity miniBefore=
            failed.npcs.miniPet();
        int itemBefore=
            failed.state.miniItemId();

        boolean escaped=false;
        try{
            failed.service.off(
                failed.state,
                failed.npcs,
                writer
            );
        }catch(IOException expected){
            escaped=true;
        }

        if(!escaped)
            throw new AssertionError(
                "off queue admission failure did not escape"
            );

        assertWriterRewound(
            writer,
            queue,
            "off"
        );

        if(!failed.state.miniConfigured()||
           failed.state.miniItemId()!=itemBefore||
           failed.npcs.miniPet()!=miniBefore)
            throw new AssertionError(
                "failed off changed configured mini preimage"
            );

        drainDiscard(
            queue
        );

        String retry=
            failed.service.off(
                failed.state,
                failed.npcs,
                writer
            );

        if(retry==null||
           !retry.startsWith(
               "MINIPET_DISABLED")||
           failed.state.miniConfigured()||
           failed.npcs.miniPet()!=null)
            throw new AssertionError(
                "off same-writer retry failed result="+
                retry
            );

        byte[] retryBytes=
            drainBytes(
                queue
            );

        Fixture control=
            Fixture.withConfiguredMini();
        OutboundPacketQueue controlQueue=
            new OutboundPacketQueue(
                QUEUE_CAPACITY
            );
        ServerPacketWriter controlWriter=
            new ServerPacketWriter(
                controlQueue,
                new IsaacCipher(
                    seed.clone()
                )
            );

        String clean=
            control.service.off(
                control.state,
                control.npcs,
                controlWriter
            );

        if(clean==null||
           !clean.startsWith(
               "MINIPET_DISABLED"))
            throw new AssertionError(
                "off control failed result="+
                clean
            );

        byte[] cleanBytes=
            drainBytes(
                controlQueue
            );

        if(!Arrays.equals(
                retryBytes,
                cleanBytes))
            throw new AssertionError(
                "off retry ciphertext/pending state differs from clean control retry="+
                retryBytes.length+
                " clean="+
                cleanBytes.length
            );
    }

    private static void assertWriterRewound(
        ServerPacketWriter writer,
        OutboundPacketQueue queue,
        String stage
    )throws Exception{
        if(writer.terminal())
            throw new AssertionError(
                stage+
                " admission failure terminal-latched writer"
            );

        if(intField(
                writer,
                "batchDepth")!=0)
            throw new AssertionError(
                stage+
                " left packet batch active"
            );

        ByteArrayOutputStream pending=
            (ByteArrayOutputStream)
                objectField(
                    writer,
                    "pending"
                );

        if(pending.size()!=0)
            throw new AssertionError(
                stage+
                " left pending packet bytes="+
                pending.size()
            );

        if(objectField(
                writer,
                "batchCipherCheckpoint")!=null)
            throw new AssertionError(
                stage+
                " left cipher checkpoint active"
            );

        if(queue.queuedBytes()!=
                QUEUE_CAPACITY)
            throw new AssertionError(
                stage+
                " changed pressure queue bytes="+
                queue.queuedBytes()
            );
    }

    private static int intField(
        Object target,
        String name
    )throws Exception{
        Field field=
            target.getClass()
                .getDeclaredField(
                    name
                );
        field.setAccessible(true);
        return field.getInt(
            target
        );
    }

    private static Object objectField(
        Object target,
        String name
    )throws Exception{
        Field field=
            target.getClass()
                .getDeclaredField(
                    name
                );
        field.setAccessible(true);
        return field.get(
            target
        );
    }

    private static byte[] drainBytes(
        OutboundPacketQueue queue
    )throws Exception{
        ByteArrayOutputStream out=
            new ByteArrayOutputStream();

        queue.drainTo(
            out,
            Integer.MAX_VALUE
        );

        return out.toByteArray();
    }

    private static void drainDiscard(
        OutboundPacketQueue queue
    )throws Exception{
        drainBytes(
            queue
        );
    }

    private static final class Fixture {
        final MovementState movement=
            new MovementState();
        final PetState state=
            new PetState();
        final NpcRegistry npcs=
            new NpcRegistry();
        final MiniPetService service=
            new MiniPetService();

        static Fixture withMainPet()
            throws Exception
        {
            Fixture fixture=
                new Fixture();

            PetDefinitionRepository.Def main=
                PetDefinitionRepository.get(
                    22519
                );

            if(main==null)
                throw new AssertionError(
                    "main pet fixture missing"
                );

            ServerPacketWriter setup=
                new ServerPacketWriter(
                    new ByteArrayOutputStream(),
                    new IsaacCipher(
                        new int[]{121,122,123,124}
                    )
                );

            String spawned=
                fixture.npcs.spawnPet(
                    main,
                    fixture.movement,
                    setup
                );

            if(spawned==null||
               !spawned.startsWith(
                   "PET_SPAWN_OK"))
                throw new AssertionError(
                    "main pet fixture failed result="+
                    spawned
                );

            fixture.state.activate(
                main
            );

            return fixture;
        }

        static Fixture withConfiguredMini()
            throws Exception
        {
            Fixture fixture=
                withMainPet();

            ServerPacketWriter setup=
                new ServerPacketWriter(
                    new ByteArrayOutputStream(),
                    new IsaacCipher(
                        new int[]{131,132,133,134}
                    )
                );

            String configured=
                fixture.service.configure(
                    MINI_ITEM,
                    fixture.state,
                    fixture.npcs,
                    fixture.movement,
                    setup
                );

            if(configured==null||
               !configured.startsWith(
                   "MINIPET_CONFIGURED")||
               fixture.npcs.miniPet()==null)
                throw new AssertionError(
                    "configured mini fixture failed result="+
                    configured
                );

            return fixture;
        }
    }

    private MiniPetBatchFailureAtomicityTest(){}
}

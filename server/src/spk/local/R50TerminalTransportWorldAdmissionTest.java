package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * R50 deterministic fail-closed command admission after R49 outbound errors.
 * All simulated actions are local counters, not real account mutations.
 */
public final class R50TerminalTransportWorldAdmissionTest {
    private static final int[] SEED={9,7,5,3};

    private static final class PrefixThenThrow extends OutputStream {
        int bytes;
        int writes;
        @Override public void write(int b)throws IOException{
            writes++;
            if(bytes>=2)throw new IOException("R50 injected partial socket write");
            bytes++;
        }
    }

    private static void require(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }

    public static void main(String[] args)throws Exception{
        directInMemoryWriterUnchanged();
        queuedHealthyWorldActionAllowed();
        terminalTransportPreventsQueuedWorldMutation();
        secondTypedRequestRejectedAfterFirstCommitted();
        preexistingQueueOverflowFencesWorld();
        System.out.println(
            "R50_TERMINAL_WORLD_ADMISSION_PASS "+
            "normalAllowed=true directTestWriterUnchanged=true "+
            "queuedWorldCallbackRefusedAfterSocketFailure=true "+
            "secondRequestRefused=true overflowRefused=true "+
            "previousAcceptedMutationNotRolledBack=true"
        );
    }

    private static void directInMemoryWriterUnchanged()throws IOException{
        ServerPacketWriter direct=new ServerPacketWriter(
            new ByteArrayOutputStream(),new IsaacCipher(SEED.clone()));
        direct.requireGameActionAdmission();
        direct.fixed(104,new byte[]{1});
        direct.requireGameActionAdmission();
    }

    private static void queuedHealthyWorldActionAllowed()throws IOException{
        OutboundPacketQueue queue=new OutboundPacketQueue();
        ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(SEED.clone()));
        int[] actions={0};
        writer.requireGameActionAdmission();
        actions[0]++;
        writer.fixed(53,new byte[]{1,2,3});
        writer.requireGameActionAdmission();
        actions[0]++;
        require(actions[0]==2&&!queue.transportFailed()&&!queue.overflowed(),
                "healthy queued typed requests blocked");
    }

    private static void terminalTransportPreventsQueuedWorldMutation()
        throws IOException{
        OutboundPacketQueue queue=new OutboundPacketQueue();
        ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(SEED.clone()));
        writer.fixed(53,new byte[]{1,2,3,4});

        // The socket-thread preflight occurs before World queue insertion.
        writer.requireGameActionAdmission();
        PrefixThenThrow socket=new PrefixThenThrow();
        try{
            queue.drainTo(socket,8192);
            throw new AssertionError("R49 partial socket unexpectedly succeeded");
        }catch(IOException expected){
            require(queue.transportFailed(),"R49 terminal marker missing");
        }

        // World callback receives the command later, after the socket died.
        int[] inventoryMutations={0};
        try{
            writer.requireGameActionAdmission();
            inventoryMutations[0]++;
            throw new AssertionError("World command ran after socket failure");
        }catch(IOException expected){
            require(inventoryMutations[0]==0,
                    "failed socket permitted queued World mutation");
        }

        int attempts=socket.writes;
        try{
            queue.drainTo(socket,8192);
            throw new AssertionError("poisoned stream retried");
        }catch(IOException expected){
            require(socket.writes==attempts,
                    "poisoned stream repeated socket write");
        }
    }

    private static void secondTypedRequestRejectedAfterFirstCommitted()
        throws IOException{
        OutboundPacketQueue queue=new OutboundPacketQueue();
        ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(SEED.clone()));
        int[] commits={0};
        writer.requireGameActionAdmission();
        commits[0]++; // First request was genuinely admitted.
        writer.fixed(53,new byte[]{1,2,3,4});
        try{
            queue.drainTo(new PrefixThenThrow(),4096);
            throw new AssertionError("injected write unexpectedly succeeded");
        }catch(IOException expected){
            require(queue.transportFailed(),"first request terminal state missing");
        }

        // This mirrors the R50 guard at the beginning of each typed request.
        try{
            writer.requireGameActionAdmission();
            commits[0]++;
            throw new AssertionError("second request ran after stream failed");
        }catch(IOException expected){
            require(commits[0]==1,
                    "second command mutated state or old committed command rolled back");
        }
    }

    private static void preexistingQueueOverflowFencesWorld()
        throws IOException{
        OutboundPacketQueue queue=new OutboundPacketQueue(1024);
        ServerPacketWriter writer=new ServerPacketWriter(
            queue,new IsaacCipher(SEED.clone()));
        queue.offer(new byte[1024]);
        try{
            queue.offer(new byte[]{42});
            throw new AssertionError("bounded queue should overflow");
        }catch(IOException expected){
            require(queue.overflowed(),"capacity overflow state missing");
            require(!queue.transportFailed(),
                    "capacity overflow confused with partial socket transport");
        }

        int[] mutations={0};
        try{
            writer.requireGameActionAdmission();
            mutations[0]++;
            throw new AssertionError("overflowed transport admitted World mutation");
        }catch(IOException expected){
            require(mutations[0]==0,
                    "queue overflow allowed new World mutation");
            require(expected.getMessage().contains("overflow"),
                    "no explicit overflow denial reason");
        }
    }
}

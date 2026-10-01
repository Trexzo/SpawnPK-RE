package spk.local;

import java.io.*;

/**
 * Exact R2 writer with one R3 hook: packet 81 bodies are offered to the
 * per-session multiplayer synchronizer before framing.  With no registered
 * synchronizer the byte stream is identical to R2.14.
 */
final class ServerPacketWriter {
    private static final Object ATOMIC_PAIR_LOCK=
        new Object();

    static final class AtomicPairBatch {
        private final ServerPacketWriter first;
        private final ServerPacketWriter second;
        private final int firstBytes;
        private final int secondBytes;
        private final OutboundPacketQueue.PairReservation reservation;
        private boolean completed;

        private AtomicPairBatch(
            ServerPacketWriter first,
            int firstBytes,
            ServerPacketWriter second,
            int secondBytes,
            OutboundPacketQueue.PairReservation reservation
        ){
            this.first=first;
            this.firstBytes=firstBytes;
            this.second=second;
            this.secondBytes=secondBytes;
            this.reservation=reservation;
        }

        void commit()throws IOException{
            synchronized(ATOMIC_PAIR_LOCK){
                lockWriters(
                    first,
                    second,
                    ()->{
                        if(completed)
                            throw new IllegalStateException(
                                "atomic pair batch already completed"
                            );

                        if(first.batchDepth!=1||
                           second.batchDepth!=1)
                            throw new IllegalStateException(
                                "atomic pair batch depth changed"
                            );

                        byte[] firstData=
                            first.pending.toByteArray();
                        byte[] secondData=
                            second.pending.toByteArray();

                        if(first.batchPlayer81!=null||
                           second.batchPlayer81!=null){
                            reservation.release();
                            throw new IllegalStateException(
                                "atomic pair batch cannot contain staged packet-81 presentation"
                            );
                        }

                        if(firstData.length!=
                                firstBytes||
                           secondData.length!=
                                secondBytes){
                            reservation.release();
                            throw new IllegalStateException(
                                "atomic pair batch size mismatch expected="+
                                firstBytes+"/"+secondBytes+
                                " actual="+
                                firstData.length+"/"+secondData.length
                            );
                        }

                        reservation.commit(
                            firstData,
                            secondData
                        );

                        first.pending.reset();
                        second.pending.reset();
                        first.completeBatchLocked();
                        second.completeBatchLocked();
                        completed=true;
                    }
                );
            }
        }

        void abort(){
            synchronized(ATOMIC_PAIR_LOCK){
                try{
                    lockWriters(
                        first,
                        second,
                        ()->{
                            if(completed)
                                return;

                            reservation.release();
                            first.abortBatchLocked();
                            second.abortBatchLocked();
                            completed=true;
                        }
                    );
                }catch(IOException impossible){
                    throw new AssertionError(
                        impossible
                    );
                }
            }
        }
    }

    @FunctionalInterface
    private interface PairWriterAction {
        void run() throws IOException;
    }

    private final OutputStream out;
    private final OutboundPacketQueue queue;
    private final IsaacCipher cipher;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream(4096);
    private int batchDepth;
    private IsaacCipher.Snapshot batchCipherCheckpoint;
    private Player81WorldSync.BatchTransaction batchPlayer81;

    ServerPacketWriter(OutputStream out, IsaacCipher cipher) {
        this.out=out;
        this.queue=null;
        this.cipher=cipher;
    }

    ServerPacketWriter(OutboundPacketQueue queue, IsaacCipher cipher) {
        this.out=null;
        this.queue=queue;
        this.cipher=cipher;
        if(queue==null)throw new NullPointerException("queue");
    }

    synchronized void fixed(int opcode, byte[] body) throws IOException {
        if(body==null)body=new byte[0];
        writeOpcode(opcode);
        pending.write(body);
        autoFlush();
    }

    void varShort(
        int opcode,
        byte[] body
    )throws IOException{
        byte[] checkedBody=
            body==null
                ?new byte[0]
                :body;

        if(opcode==81){
            final boolean batched;
            final Player81WorldSync.BatchTransaction prior;

            synchronized(this){
                batched=batchDepth>0;
                prior=batchPlayer81;
            }

            final byte[] transformed;
            final Player81WorldSync.BatchTransaction staged;

            if(batched){
                Player81WorldSync.BatchTransform result=
                    Player81WorldSync.transformBatched(
                        this,
                        checkedBody,
                        prior
                    );
                transformed=result.body;
                staged=result.transaction;
            }else{
                transformed=
                    Player81WorldSync.transform(
                        this,
                        checkedBody
                    );
                staged=null;
            }

            if(transformed.length>65535)
                throw new IllegalArgumentException(
                    "varShort payload too large: "+
                    transformed.length
                );

            synchronized(this){
                if(batched){
                    if(batchDepth<=0||
                       batchPlayer81!=prior)
                        throw new IllegalStateException(
                            "packet-81 batch ownership changed during transform"
                        );
                    batchPlayer81=staged;
                }

                writeOpcode(opcode);
                pending.write(
                    (transformed.length>>>8)&255
                );
                pending.write(
                    transformed.length&255
                );
                pending.write(transformed);
                autoFlush();
            }

            if(!batched){
                // Never call back into World/Player81 ownership while
                // holding the writer monitor.  The packet-81 bytes are
                // already ordered ahead of any released packet-65 work.
                SharedNpcWorldRelay
                    .flushAfterPlayer81(this);
            }
            return;
        }

        if(checkedBody.length>65535)
            throw new IllegalArgumentException(
                "varShort payload too large: "+
                checkedBody.length
            );

        synchronized(this){
            writeOpcode(opcode);
            pending.write(
                (checkedBody.length>>>8)&255
            );
            pending.write(
                checkedBody.length&255
            );
            pending.write(checkedBody);
            autoFlush();
        }
    }

    synchronized void varByte(int opcode, byte[] body) throws IOException {
        if(body==null)body=new byte[0];
        if(body.length>255)throw new IllegalArgumentException("varByte payload too large: "+body.length);
        writeOpcode(opcode);
        pending.write(body.length&255);
        pending.write(body);
        autoFlush();
    }

    synchronized void beginBatch(){
        beginBatchLocked();
    }

    private void beginBatchLocked(){
        if(batchDepth==0){
            if(pending.size()!=0)
                throw new IllegalStateException(
                    "packet batch requires idle pending buffer"
                );

            batchCipherCheckpoint=
                cipher.snapshot();
        }

        batchDepth++;
    }

    synchronized void abortBatch(){
        abortBatchLocked();
    }

    private void abortBatchLocked(){
        if(batchDepth<=0)
            throw new IllegalStateException(
                "no packet batch"
            );

        if(batchCipherCheckpoint==null)
            throw new IllegalStateException(
                "packet batch has no cipher checkpoint"
            );

        pending.reset();
        cipher.restore(
            batchCipherCheckpoint
        );
        batchDepth=0;
        batchCipherCheckpoint=null;
        batchPlayer81=null;
    }

    static AtomicPairBatch beginAtomicQueuePair(
        ServerPacketWriter first,
        int firstBytes,
        ServerPacketWriter second,
        int secondBytes
    )throws IOException{
        if(first==null||second==null)
            throw new NullPointerException(
                "pair writer"
            );
        if(first==second)
            throw new IllegalArgumentException(
                "atomic pair requires distinct writers"
            );
        if(firstBytes<0||secondBytes<0)
            throw new IllegalArgumentException(
                "atomic pair bytes"
            );

        if(first.queue==null||
           second.queue==null)
            return null;

        OutboundPacketQueue.PairReservation reservation=
            OutboundPacketQueue.reservePair(
                first.queue,
                firstBytes,
                second.queue,
                secondBytes
            );

        try{
            synchronized(ATOMIC_PAIR_LOCK){
                lockWriters(
                    first,
                    second,
                    ()->{
                        if(first.batchDepth!=0||
                           second.batchDepth!=0||
                           first.pending.size()!=0||
                           second.pending.size()!=0)
                            throw new IllegalStateException(
                                "atomic pair requires idle writers"
                            );

                        first.beginBatchLocked();
                        second.beginBatchLocked();
                    }
                );
            }
        }catch(IOException failure){
            reservation.release();
            throw failure;
        }catch(RuntimeException failure){
            reservation.release();
            throw failure;
        }catch(Error failure){
            reservation.release();
            throw failure;
        }

        return new AtomicPairBatch(
            first,
            firstBytes,
            second,
            secondBytes,
            reservation
        );
    }

    void endBatch() throws IOException {
        Player81WorldSync.BatchTransaction player81;

        synchronized(this){
            if(batchDepth<=0)
                throw new IllegalStateException(
                    "no packet batch"
                );

            if(batchDepth>1){
                batchDepth--;
                return;
            }

            /*
             * Keep the outer batch active until admission/write succeeds. If
             * flush fails, the caller can still abort the byte/cipher batch.
             */
            flush();
            player81=batchPlayer81;
            completeBatchLocked();
        }

        if(player81!=null){
            player81.commit();
            SharedNpcWorldRelay
                .flushAfterPlayer81(this);
        }
    }

    private void completeBatchLocked(){
        batchDepth=0;
        batchCipherCheckpoint=null;
        batchPlayer81=null;
    }

    synchronized void flush() throws IOException {
        if(pending.size()>0){
            byte[] bytes=pending.toByteArray();

            try{
                if(queue!=null)
                    queue.offer(bytes);
                else
                    out.write(bytes);

                if(out!=null)
                    out.flush();
            }catch(IOException failure){
                if(batchDepth==0)
                    pending.reset();
                throw failure;
            }catch(RuntimeException failure){
                if(batchDepth==0)
                    pending.reset();
                throw failure;
            }catch(Error failure){
                if(batchDepth==0)
                    pending.reset();
                throw failure;
            }

            pending.reset();
            return;
        }

        if(out!=null)
            out.flush();
    }

    private void autoFlush() throws IOException { if(batchDepth==0)flush(); }

    private static void lockWriters(
        ServerPacketWriter first,
        ServerPacketWriter second,
        PairWriterAction action
    )throws IOException{
        ServerPacketWriter lockFirst=
            System.identityHashCode(first)<
                System.identityHashCode(second)
                ?first
                :second;
        ServerPacketWriter lockSecond=
            lockFirst==first
                ?second
                :first;

        synchronized(lockFirst){
            synchronized(lockSecond){
                action.run();
            }
        }
    }

    private void writeOpcode(int opcode){
        if(opcode<0||opcode>255)throw new IllegalArgumentException("opcode out of range: "+opcode);
        pending.write((opcode+cipher.nextInt())&255);
    }
}

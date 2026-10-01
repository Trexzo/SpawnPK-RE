package spk.local;

import java.io.*;

/**
 * Exact R2 writer with one R3 hook: packet 81 bodies are offered to the
 * per-session multiplayer synchronizer before framing.  With no registered
 * synchronizer the byte stream is identical to R2.14.
 */
final class ServerPacketWriter {
    static final class StateSnapshot {
        final byte[] pending;
        final int batchDepth;
        final IsaacCipher.Snapshot cipher;

        StateSnapshot(
            byte[] pending,
            int batchDepth,
            IsaacCipher.Snapshot cipher
        ){
            this.pending=pending;
            this.batchDepth=batchDepth;
            this.cipher=cipher;
        }
    }

    private static final Object ATOMIC_PAIR_LOCK=
        new Object();

    private final OutputStream out;
    private final OutboundPacketQueue queue;
    private final IsaacCipher cipher;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream(4096);
    private int batchDepth;

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
            byte[] transformed=
                Player81WorldSync.transform(
                    this,
                    checkedBody
                );

            if(transformed.length>65535)
                throw new IllegalArgumentException(
                    "varShort payload too large: "+
                    transformed.length
                );

            synchronized(this){
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

            // Never call back into World/Player81 ownership while
            // holding the writer monitor.  The packet-81 bytes are
            // already ordered ahead of any released packet-65 work.
            SharedNpcWorldRelay
                .flushAfterPlayer81(this);
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

    synchronized StateSnapshot snapshotState(){
        return new StateSnapshot(
            pending.toByteArray(),
            batchDepth,
            cipher.snapshot()
        );
    }

    synchronized void restoreState(
        StateSnapshot snapshot
    ){
        if(snapshot==null)
            throw new NullPointerException("snapshot");

        pending.reset();
        pending.write(
            snapshot.pending,
            0,
            snapshot.pending.length
        );
        batchDepth=snapshot.batchDepth;
        cipher.restore(snapshot.cipher);
    }

    static void endBatchesAtomically(
        ServerPacketWriter a,
        ServerPacketWriter b
    )throws IOException{
        if(a==null||b==null)
            throw new NullPointerException("writer");
        if(a==b)
            throw new IllegalArgumentException(
                "atomic pair requires distinct writers"
            );

        synchronized(ATOMIC_PAIR_LOCK){
            int ah=System.identityHashCode(a);
            int bh=System.identityHashCode(b);

            if(ah<=bh){
                synchronized(a){
                    synchronized(b){
                        endBatchesAtomicallyLocked(
                            a,
                            b
                        );
                    }
                }
            }else{
                synchronized(b){
                    synchronized(a){
                        endBatchesAtomicallyLocked(
                            a,
                            b
                        );
                    }
                }
            }
        }
    }

    private static void endBatchesAtomicallyLocked(
        ServerPacketWriter a,
        ServerPacketWriter b
    )throws IOException{
        if(a.queue==null||b.queue==null)
            throw new IllegalStateException(
                "atomic paired batch requires queue-backed writers"
            );

        if(a.batchDepth!=1||b.batchDepth!=1)
            throw new IllegalStateException(
                "atomic paired batch depth expected 1/1 actual "+
                a.batchDepth+"/"+b.batchDepth
            );

        byte[] aBytes=
            a.pending.toByteArray();
        byte[] bBytes=
            b.pending.toByteArray();

        OutboundPacketQueue.offerPair(
            a.queue,
            aBytes,
            b.queue,
            bBytes
        );

        a.pending.reset();
        b.pending.reset();
        a.batchDepth=0;
        b.batchDepth=0;
    }

    synchronized void beginBatch(){batchDepth++;}

    synchronized void endBatch() throws IOException {
        if(batchDepth<=0)throw new IllegalStateException("no packet batch");
        batchDepth--;
        if(batchDepth==0)flush();
    }

    synchronized void flush() throws IOException {
        if(pending.size()>0){
            byte[] bytes=pending.toByteArray();
            pending.reset();
            if(queue!=null)queue.offer(bytes);else out.write(bytes);
        }
        if(out!=null)out.flush();
    }

    private void autoFlush() throws IOException { if(batchDepth==0)flush(); }

    private void writeOpcode(int opcode){
        if(opcode<0||opcode>255)throw new IllegalArgumentException("opcode out of range: "+opcode);
        pending.write((opcode+cipher.nextInt())&255);
    }
}

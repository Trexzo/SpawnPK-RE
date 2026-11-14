package spk.local;

import java.io.*;

/**
 * Exact R2 writer with one R3 hook: packet 81 bodies are offered to the
 * per-session multiplayer synchronizer before framing.  With no registered
 * synchronizer the byte stream is identical to R2.14.
 */
final class ServerPacketWriter {
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

    /**
     * R50: prevent new typed World actions after this session's outbound
     * connection has already failed. Direct in-memory test writers have no
     * socket queue and remain unchanged.
     */
    void requireGameActionAdmission()throws IOException{
        if(queue!=null)queue.requireGameActionAdmission();
    }

    /**
     * R54: no-op for direct test output streams; queue-backed live sessions
     * must retain a conservative budget before multi-packet commit.
     */
    void requireTradeCommitHeadroom(int reserveBytes)throws IOException{
        if(queue!=null)queue.requireCommitHeadroom(reserveBytes);
    }

    synchronized void beginBatch(){batchDepth++;}

    /**
     * R55: discard every unpublished byte belonging to an aborted trade.
     * On a live queued session fail closed permanently rather than trying
     * to resume a cipher stream whose pending packets were discarded.
     */
    synchronized void abortTradeBatchAfterFailure(Throwable failure){
        pending.reset();
        batchDepth=0;
        if(queue!=null)queue.failClosedAfterTradePublication(failure);
    }

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

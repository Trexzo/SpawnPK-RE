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

    synchronized void varShort(int opcode, byte[] body) throws IOException {
        if(body==null)body=new byte[0];
        boolean player81=opcode==81;
        if(player81)body=Player81WorldSync.transform(this,body);
        if(body.length>65535)throw new IllegalArgumentException("varShort payload too large: "+body.length);
        writeOpcode(opcode);
        pending.write((body.length>>>8)&255);
        pending.write(body.length&255);
        pending.write(body);
        // R3.2 ordering barrier: remote NPC/pet masks are appended only after this
        // viewer has consumed the matching remote player presentation event.
        if(player81)SharedNpcWorldRelay.flushAfterPlayer81(this);
        autoFlush();
    }

    synchronized void varByte(int opcode, byte[] body) throws IOException {
        if(body==null)body=new byte[0];
        if(body.length>255)throw new IllegalArgumentException("varByte payload too large: "+body.length);
        writeOpcode(opcode);
        pending.write(body.length&255);
        pending.write(body);
        autoFlush();
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

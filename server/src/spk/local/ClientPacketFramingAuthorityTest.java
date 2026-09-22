package spk.local;

import java.io.*;
import java.util.*;

/**
 * v5.2.2 framing authority: one continuous ISAAC stream containing every opcode
 * emitted by the exact pinned client must remain aligned through the final packet.
 */
public final class ClientPacketFramingAuthorityTest {
    private static final int[] OPS = {
        0,2,3,4,6,14,16,17,18,21,23,25,35,36,39,40,41,43,53,57,60,70,72,73,74,75,77,78,79,85,86,87,95,98,101,103,109,117,120,121,122,126,128,129,130,131,132,133,135,136,139,140,141,145,148,150,152,153,155,156,164,176,181,183,185,188,189,192,200,202,208,210,214,215,218,226,228,230,234,236,237,246,248,249,252,253
    };

    public static void main(String[] args) throws Exception {
        if (OPS.length != 86) throw new AssertionError("authority size="+OPS.length);
        int[] seed={0x01020304,0x11223344,0x55667788,0x99AABBCC};
        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        IsaacCipher enc=new IsaacCipher(seed.clone());
        for(int op:OPS) writePacket(wire,enc,op);

        ClientPacketProbe p=new ClientPacketProbe(new ByteArrayInputStream(wire.toByteArray()),new IsaacCipher(seed.clone()),"[framing86] ");
        for(int i=0;i<OPS.length;i++) {
            if(!p.readNextKnownPacket()) throw new AssertionError("paused at i="+i+" opcode="+OPS[i]);
            if(!p.isAligned()) throw new AssertionError("unaligned at opcode="+OPS[i]);
        }
        if(p.decodedCount()!=86) throw new AssertionError("decoded="+p.decodedCount());

        // Explicit historical blocker regression: widget -> idle logout -> movement.
        ByteArrayOutputStream blocker=new ByteArrayOutputStream();
        IsaacCipher e2=new IsaacCipher(seed.clone());
        writePacket(blocker,e2,185); writePacket(blocker,e2,202); writePacket(blocker,e2,164);
        ClientPacketProbe q=new ClientPacketProbe(new ByteArrayInputStream(blocker.toByteArray()),new IsaacCipher(seed.clone()),"[202-regression] ");
        if(!q.readNextKnownPacket()||!q.readNextKnownPacket()||!q.readNextKnownPacket()||!q.isAligned())
            throw new AssertionError("185->202->164 regression");
        ClientRequest blockerWidget=q.takeTypedRequest();
        if(!(blockerWidget instanceof WidgetActionClientRequest))
            throw new AssertionError("widget missing before movement: "+blockerWidget);
        ClientRequest blockerMovement=q.takeTypedRequest();
        if(!(blockerMovement instanceof MovementClientRequest))
            throw new AssertionError("movement missing after 202: "+blockerMovement);
        if(((MovementClientRequest)blockerMovement).movement().opcode!=164)
            throw new AssertionError("wrong movement after 202: "+blockerMovement);

        // Out-of-authority remains fail-closed.
        ByteArrayOutputStream bad=new ByteArrayOutputStream();
        IsaacCipher eb=new IsaacCipher(seed.clone());
        bad.write((5+eb.nextInt())&255);
        ClientPacketProbe r=new ClientPacketProbe(new ByteArrayInputStream(bad.toByteArray()),new IsaacCipher(seed.clone()),"[authority-negative] ");
        if(r.readNextKnownPacket() || r.isAligned()) throw new AssertionError("opcode5 did not fail closed");

        System.out.println("V522_C2S_86_OF_86_FRAMING_PASS opcodes=86 blocker185_202_164=true outOfAuthority5FailClosed=true");
    }

    private static void writePacket(OutputStream out, IsaacCipher c, int op) throws IOException {
        out.write((op+c.nextInt())&255);
        if(op==4||op==77||op==103||op==126||op==226||op==246||op==98||op==164||op==248){
            byte[] b;
            if(op==98||op==164) b=walk(false);
            else if(op==248){byte[] core=walk(false);b=new byte[core.length+14];System.arraycopy(core,0,b,0,core.length);}
            else if(op==103) b=new byte[]{'x',10};
            else if(op==4) b=new byte[]{(byte)128,(byte)128};
            else if(op==126) b=new byte[8];
            else b=new byte[0];
            out.write(b.length);out.write(b);return;
        }
        int n=dedicatedFixed(op);
        if(n<0)n=ClientPacketProbe.framingOnlyFixedLength(op);
        if(n<0)throw new AssertionError("no frame for "+op);
        out.write(new byte[n]);
    }

    private static int dedicatedFixed(int op){
        switch(op){
            case 0:case 121:case 130:case 202:return 0;
            case 3:return 1;
            case 40:case 72:case 155:case 185:return 2;
            case 95:return 3;
            case 36:case 208:return 4;
            case 41:case 43:case 75:case 87:case 117:case 129:case 132:case 135:case 140:case 145:return 6;
            case 60:case 74:case 133:case 188:case 215:return 8;
            case 214:return 7;
            case 141:case 218:return 10;
            case 53:return 12;
            case 101:return 13;
            default:return -1;
        }
    }

    private static byte[] walk(boolean run)throws IOException{
        int x=3088,y=3495;ByteArrayOutputStream b=new ByteArrayOutputStream();
        b.write((x+128)&255);b.write(x>>>8);b.write(y);b.write(y>>>8);b.write(run?255:0);return b.toByteArray();
    }
}

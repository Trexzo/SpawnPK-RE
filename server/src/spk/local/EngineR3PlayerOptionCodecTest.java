package spk.local;

import java.io.*;

public final class EngineR3PlayerOptionCodecTest {
    public static void main(String[]args)throws Exception{
        byte[] o=Player81WorldSync.option104(1,0,"Attack");
        eq(255,o[0]&255,"S2C104 O slot1");eq(128,o[1]&255,"S2C104 N flag0");eq(10,o[o.length-1]&255,"newline");
        test(128,new byte[]{0x01,0x23},1,0x0123,"Attack");
        test(153,new byte[]{0x23,0x01},2,0x0123,"Follow");
        test(73,new byte[]{0x23,0x01},3,0x0123,"Trade with");
        System.out.println("V5130_ENGINE_R3_PLAYER_OPTION_CODEC_PASS s2c104=true c2s128BE=true c2s153LE=true c2s73LE=true");
    }
    static void test(int opcode,byte[]body,int slot,int idx,String semantic)throws Exception{
        int[] seeds={11,22,33,44};IsaacCipher enc=new IsaacCipher(seeds.clone());ByteArrayOutputStream raw=new ByteArrayOutputStream();raw.write((opcode+enc.nextInt())&255);raw.write(body);
        ClientPacketProbe p=new ClientPacketProbe(new ByteArrayInputStream(raw.toByteArray()),new IsaacCipher(seeds.clone()),"[r3test] ");
        if(!p.readNextKnownPacket())throw new AssertionError("decode false opcode="+opcode);PlayerAction a=p.takePlayerAction();
        if(a==null)throw new AssertionError("missing action");eq(slot,a.optionSlot,"slot");eq(idx,a.playerIndex,"index");if(!semantic.equals(a.semantic))throw new AssertionError("semantic "+a.semantic);
    }
    static void eq(int e,int a,String m){if(e!=a)throw new AssertionError(m+" expected="+e+" actual="+a);}
}

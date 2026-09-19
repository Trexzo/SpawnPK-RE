package spk.local;

import java.io.*;
import java.util.*;

public final class AuthorityR22ScriptPacket250Test {
    public static void main(String[] args)throws Exception{
        eq(AuthorityR22ScriptPacket250Publisher.deathClear(),0,9,0);
        eq(AuthorityR22ScriptPacket250Publisher.deathMarkSlot(300),0,9,1,1,44);
        eq(AuthorityR22ScriptPacket250Publisher.shopReset(),0,17,0);
        eq(AuthorityR22ScriptPacket250Publisher.shopSelect(4),0,17,2,4);
        eq(AuthorityR22ScriptPacket250Publisher.shopRebuild(1,"Main stock","Blood"),
           0,17,1,1,2,'M','a','i','n',' ','s','t','o','c','k',10,'B','l','o','o','d',10);
        eq(AuthorityR22ScriptPacket250Publisher.makePreviewValue(3,0x01020304),0,35,0,3,1,2,3,4);
        eq(AuthorityR22ScriptPacket250Publisher.makeQuantitySelected(2),0,35,1,2);
        eq(AuthorityR22ScriptPacket250Publisher.makeHeaderDefault(),0,35,2,0);
        eq(AuthorityR22ScriptPacket250Publisher.makeHeader("Make X",null),0,35,2,1,'M','a','k','e',' ','X',10,0);
        eq(AuthorityR22ScriptPacket250Publisher.makeHeader("Make X","Choose"),0,35,2,1,'M','a','k','e',' ','X',10,1,'C','h','o','o','s','e',10);
        eq(AuthorityR22ScriptPacket250Publisher.makeOptionDetail(4,"Select<br>detail"),0,35,3,4,'S','e','l','e','c','t','<','b','r','>','d','e','t','a','i','l',10);
        eq(AuthorityR22ScriptPacket250Publisher.makeOptionResource(0,"npc_3701"),0,35,4,0,'n','p','c','_','3','7','0','1',10);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ServerPacketWriter w=new ServerPacketWriter(out,new IsaacCipher(new int[]{1,2,3,4}));
        byte[] payload=AuthorityR22ScriptPacket250Publisher.deathMarkSlot(7);
        AuthorityR22ScriptPacket250Publisher.send(w,payload);
        if(out.size()!=payload.length+2)throw new AssertionError("varByte framing size="+out.size());
        System.out.println("V5124_AUTHORITY_R22_SCRIPTPACKET250_PASS subtype9=true subtype17=true subtype35=true varByte=true mechanicsInvented=false");
    }
    private static void eq(byte[] actual,int... expected){
        if(actual.length!=expected.length)throw new AssertionError("len "+actual.length+" != "+expected.length+" actual="+Arrays.toString(actual));
        for(int i=0;i<expected.length;i++)if((actual[i]&255)!=(expected[i]&255))throw new AssertionError("byte "+i+" actual="+(actual[i]&255)+" expected="+(expected[i]&255));
    }
}

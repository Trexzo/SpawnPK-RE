package spk.local;

import java.util.Arrays;

public final class MakeoverAppearancePacket81Test {
    public static void main(String[] args) throws Exception {
        PlayerState p=new PlayerState();
        int[] kits={45,-1,56,61,67,70,79};
        int[] colours={11,15,14,5,23};
        if(!p.setCharacterAppearance(1,kits,colours))
            throw new AssertionError("setup rejected");

        byte[] block=BootstrapPackets.appearanceBlock(
            "makeovertest",
            null,
            p
        );

        int pos=0;
        if((block[pos++]&255)!=1)throw new AssertionError("gender not 1");
        pos+=4; // bd,bf,bg,bh
        pos+=2; // role aC

        int[] slots=new int[12];
        for(int i=0;i<slots.length;i++){
            int hi=block[pos++]&255;
            if(hi==0){
                slots[i]=0;
            }else{
                int lo=block[pos++]&255;
                slots[i]=(hi<<8)|lo;
            }
        }

        int[] kitSlots={8,11,4,6,9,7,10};
        for(int i=0;i<kitSlots.length;i++){
            int expected=kits[i]<0?0:256+kits[i];
            if(slots[kitSlots[i]]!=expected)
                throw new AssertionError(
                    "slot "+kitSlots[i]+" expected="+expected+" actual="+slots[kitSlots[i]]
                );
        }
        if(slots[11]!=0)throw new AssertionError("female jaw slot must be zero");

        int extraFlag=block[pos++]&255;
        if(extraFlag!=0)throw new AssertionError("unexpected extra appearance item");

        int[] decodedColours=new int[5];
        for(int i=0;i<5;i++)decodedColours[i]=block[pos++]&255;
        if(!Arrays.equals(decodedColours,colours))
            throw new AssertionError("colours "+Arrays.toString(decodedColours));

        byte[] npcHead=BootstrapPackets.interfaceNpcHead75(599,4883);
        if(!Arrays.equals(
                npcHead,
                new byte[]{(byte)0xD7,0x02,(byte)0x93,0x13}))
            throw new AssertionError("packet75 "+hex(npcHead));

        if(!Arrays.equals(
                BootstrapPackets.chatboxInterface164(4882),
                new byte[]{0x12,0x13}))
            throw new AssertionError("packet164 root4882");

        if(!Arrays.equals(
                BootstrapPackets.interface97(3559),
                new byte[]{0x0D,(byte)0xE7}))
            throw new AssertionError("packet97 root3559");

        System.out.println(
            "MAKEOVER_APPEARANCE_PACKET81_PASS gender=1 femaleJawSlot=0 coloursExact=true npcHead75=D7029313 chatbox4882=true designRoot3559=true"
        );
    }

    private static String hex(byte[] b){
        StringBuilder s=new StringBuilder();
        for(byte v:b)s.append(String.format("%02X",v&255));
        return s.toString();
    }
}

package spk.local;

import java.io.*;

public final class ScoobyBehemothDropParityTest {
    public static void main(String[] args)throws Exception{
        int[] items={24016,24017,24018,24019};
        int[] npcs={5160,5161,5162,5163};
        String[] visuals={"black_white","black_orange","white_blue","green_black"};
        MovementState m=new MovementState();
        ServerPacketWriter w=new ServerPacketWriter(new ByteArrayOutputStream(),new IsaacCipher(new int[]{3,1,4,1}));
        for(int i=0;i<items.length;i++){
            PetDefinitionRepository.Def d=PetDefinitionRepository.get(items[i]);
            if(d==null||d.npcId!=npcs[i])throw new AssertionError("mapping "+items[i]+" expected "+npcs[i]+" got "+d);
            NpcRegistry n=new NpcRegistry();
            String r=n.spawnPet(d,m,w);
            if(!r.startsWith("PET_SPAWN_OK")||n.pet()==null||n.pet().definitionId!=npcs[i])throw new AssertionError("drop-spawn "+items[i]+" r="+r);
        }
        System.out.println("V5125_SCOOBY_BEHEMOTH_DROP_PARITY_PASS 24016=5160_"+visuals[0]+" 24017=5161_"+visuals[1]+" 24018=5162_"+visuals[2]+" 24019=5163_"+visuals[3]);
    }
}

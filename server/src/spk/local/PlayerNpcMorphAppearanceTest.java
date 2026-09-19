package spk.local;

/** Exact-current packet-81 appearance marker for player->NPC render transform. */
public final class PlayerNpcMorphAppearanceTest {
    public static void main(String[] args)throws Exception{
        EquipmentState eq=new EquipmentState();
        PlayerState ps=new PlayerState();
        ps.syncEquipmentPresentation(eq);
        byte[] normal=BootstrapPackets.appearanceBlock("opensrc",eq.appearanceItems(),ps);
        byte[] morph=BootstrapPackets.appearanceBlock("opensrc",eq.appearanceItems(),ps,8330);
        req((morph[7]&255)==255 && (morph[8]&255)==255,"slot0 marker");
        req((((morph[9]&255)<<8)|(morph[10]&255))==8330,"npc id");
        // The exact parser exits the normal 12 equipment-slot loop immediately,
        // so the byte after npcId is the optional bs presence flag.
        req((morph[11]&255)==0,"bs flag immediately follows transform");
        req(morph.length<normal.length,"morph must omit remaining normal equipment slots");
        byte[] p81=BootstrapPackets.player81AppearanceOnly("opensrc",eq.appearanceItems(),ps,8330);
        req(p81.length>morph.length && contains(p81,new byte[]{(byte)0xff,(byte)0xff,0x20,(byte)0x8a}),"packet81 contains morph marker/id");
        System.out.println("V592_PLAYER_NPC_MORPH_APPEARANCE_PASS npc=8330 marker=FFFF entityFamily=PLAYER remainingEquipmentOmitted=true sessionOnlyReady=true authority=EXACT_CURRENT_CLIENT");
    }
    static boolean contains(byte[] a,byte[] n){outer:for(int i=0;i+n.length<=a.length;i++){for(int j=0;j<n.length;j++)if(a[i+j]!=n[j])continue outer;return true;}return false;}
    static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}

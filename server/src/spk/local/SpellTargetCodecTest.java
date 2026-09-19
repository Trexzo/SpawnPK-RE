package spk.local;

public final class SpellTargetCodecTest {
    private static void req(boolean c,String m){if(!c)throw new AssertionError(m);}
    private static byte[] beA(int v){return new byte[]{(byte)(v>>>8),(byte)((v+128)&255)};}
    private static byte[] leA(int v){return new byte[]{(byte)((v+128)&255),(byte)(v>>>8)};}
    private static byte[] be(int v){return new byte[]{(byte)(v>>>8),(byte)v};}
    private static byte[] le(int v){return new byte[]{(byte)v,(byte)(v>>>8)};}
    private static byte[] cat(byte[]...xs){int n=0;for(byte[]x:xs)n+=x.length;byte[]o=new byte[n];int p=0;for(byte[]x:xs){System.arraycopy(x,0,o,p,x.length);p+=x.length;}return o;}
    public static void main(String[]a){
        SpellTargetRequest p=ClientPacketProbe.decodeSpellTarget(249,cat(beA(321),le(1152)));
        req(p.kind==SpellTargetRequest.Kind.PLAYER&&p.targetIndex==321&&p.spellWidget==1152,"player249");
        SpellTargetRequest n=ClientPacketProbe.decodeSpellTarget(131,cat(leA(129),beA(1152)));
        req(n.kind==SpellTargetRequest.Kind.NPC&&n.targetIndex==129&&n.spellWidget==1152,"npc131");
        SpellTargetRequest o=ClientPacketProbe.decodeSpellTarget(35,cat(le(3087),beA(1162),beA(3495),le(26972)));
        req(o.kind==SpellTargetRequest.Kind.OBJECT&&o.worldX==3087&&o.worldY==3495&&o.targetId==26972&&o.spellWidget==1162,"object35");
        SpellTargetRequest g=ClientPacketProbe.decodeSpellTarget(181,cat(le(3495),be(995),le(3087),beA(1152)));
        req(g.kind==SpellTargetRequest.Kind.GROUND_ITEM&&g.worldX==3087&&g.worldY==3495&&g.targetId==995,"ground181");
        SpellTargetRequest i=ClientPacketProbe.decodeSpellTarget(237,cat(be(3),beA(995),be(3214),beA(30017)));
        req(i.kind==SpellTargetRequest.Kind.INVENTORY_ITEM&&i.targetSlot==3&&i.targetId==995&&i.targetWidget==3214&&i.spellWidget==30017,"item237");
        System.out.println("SPELL_TARGET_CODEC_TEST_PASS opcodes=249,131,35,181,237");
    }
}

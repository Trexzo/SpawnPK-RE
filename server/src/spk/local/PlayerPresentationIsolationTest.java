package spk.local;

/** Certifies independent player animation/GFX packet-81 primitives for nurse decomposition. */
public final class PlayerPresentationIsolationTest {
    public static void main(String[] args)throws Exception{
        byte[] anim=CombatSync.player81AnimationOnly(10184);
        byte[] gfx=CombatSync.player81GfxOnly(1310,0,0);
        byte[] both=CombatSync.player81AnimationAndGfx(10184,1310,0,0);
        req(anim.length==8&&u(anim[3])==0x08,"anim mask/len");
        req(le16(anim,4)==10184,"anim id");
        req(gfx.length==11&&u(gfx[3])==0x40&&u(gfx[4])==0x01,"gfx mask/len");
        req(le16(gfx,5)==1310,"gfx id");
        req(both.length==15&&u(both[3])==0x48&&u(both[4])==0x01,"combined mask/len");
        req(le16(both,5)==1310&&le16(both,11)==10184,"combined ids");
        System.out.println("V591_PLAYER_PRESENTATION_ISOLATION_PASS anim10184Only=true gfx1310Only=true combined=true packet81Masks=0x08,0x100,0x108 nurseComponentLiveTrialReady=true");
    }
    static int u(byte b){return b&255;} static int le16(byte[] b,int o){return u(b[o])|(u(b[o+1])<<8);} static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}

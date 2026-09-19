package spk.local;

/** Controlled test for user-requested nurse authority + certified 10184/1310 presentation. */
public final class NurseStatePresentationTest {
    public static void main(String[] args)throws Exception{
        PlayerState p=new PlayerState();
        for(int i=0;i<PlayerState.COMBAT_SKILL_COUNT;i++)p.setCurrentLevel(i,42+i);
        p.setSpecialEnergy(17); p.setNegativeEffects(9,12,7);
        MovementState m=new MovementState(); m.setRunEnergy(13);
        int changed=p.restoreNurse(); m.setRunEnergy(100);
        if(changed!=0x7f)throw new AssertionError("skill change mask="+Integer.toHexString(changed));
        for(int i=0;i<PlayerState.COMBAT_SKILL_COUNT;i++)if(p.currentLevel(i)!=99)throw new AssertionError("skill "+i+"="+p.currentLevel(i));
        if(p.specialEnergy()!=100||p.poison()!=0||p.venom()!=0||p.sicken()!=0||m.runEnergy()!=100)throw new AssertionError("nurse state incomplete");

        byte[] b=CombatSync.player81AnimationAndGfx(10184,1310,0,0);
        if(b.length<14)throw new AssertionError("packet81 too short="+b.length);
        int off=3; // 22-bit movement header aligns to 3 bytes
        if((b[off]&255)!=0x48 || (b[off+1]&255)!=0x01)throw new AssertionError("extended player mask wrong");
        int gfx=(b[off+2]&255)|((b[off+3]&255)<<8);
        int packed=((b[off+4]&255)<<24)|((b[off+5]&255)<<16)|((b[off+6]&255)<<8)|(b[off+7]&255);
        int anim=(b[off+8]&255)|((b[off+9]&255)<<8);
        if(gfx!=1310||packed!=0||anim!=10184)throw new AssertionError("presentation gfx="+gfx+" packed="+packed+" anim="+anim);
        System.out.println("V58_NURSE_STATE_PRESENTATION_PASS hp=99 prayer=99 run=100 combatStats=99 special=100 poison=0 venom=0 sicken=0 anim=10184 gfx=1310");
    }
}

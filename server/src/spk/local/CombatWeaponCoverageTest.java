package spk.local;

public final class CombatWeaponCoverageTest {
    public static void main(String[] args){
        int count=CombatWeaponRepository.count();
        if(count<400) throw new AssertionError("weapon coverage unexpectedly low="+count);
        CombatWeaponProfile blood=CombatWeaponRepository.resolve(28526);
        CombatWeaponProfile scorching=CombatWeaponRepository.resolve(28860);
        CombatWeaponProfile dark=CombatWeaponRepository.resolve(11235);
        CombatWeaponProfile tbow=CombatWeaponRepository.resolve(20483);
        CombatWeaponProfile blowpipe=CombatWeaponRepository.resolve(21577);
        CombatWeaponProfile unsafe=CombatWeaponRepository.resolve(28810);
        if(blood==null||blood.attackAnimation!=15552||blood.attackSpeedTicks!=3||blood.attackRange!=1)throw new AssertionError("Bloodrend M2="+blood);
        if(scorching==null||scorching.attackAnimation!=15624||scorching.attackSpeedTicks!=3||scorching.projectileId!=4136||scorching.gfxId!=4135)throw new AssertionError("Scorching M2="+scorching);
        if(dark==null||dark.attackAnimation!=15409||dark.projectileId!=1120||dark.gfxId!=1111||dark.attackRange!=10)throw new AssertionError("Dark bow R2="+dark);
        if(tbow==null||tbow.attackAnimation!=15409||!tbow.mechanicsResolved())throw new AssertionError("Twisted bow R2="+tbow);
        if(blowpipe==null||blowpipe.attackAnimation!=884||blowpipe.attackRange!=10||!blowpipe.mechanicsResolved())throw new AssertionError("Blowpipe R2="+blowpipe);
        if(unsafe==null)throw new AssertionError("Scorching base profile missing");
        if(unsafe.mechanicsResolved())throw new AssertionError("ordinary Scorching bow 28810 must remain crash-gated="+unsafe);
        int resolved=CombatWeaponRepository.mechanicsResolved();
        if(resolved!=145) throw new AssertionError("expected 145 presentation-capable dummy profiles resolved="+resolved);
        if(CombatWeaponRepository.r2AttackAuthorityCount()!=144)throw new AssertionError("R2 attack rows="+CombatWeaponRepository.r2AttackAuthorityCount());
        System.out.println("V594_COMBAT_WEAPON_COVERAGE_PASS weapons="+count+
            " r2Rows="+CombatWeaponRepository.r2AttackAuthorityCount()+
            " r2ProvenRuntime="+CombatWeaponRepository.r2ProvenRuntimeCount()+
            " mechanicsResolved="+resolved+
            " darkBow11235=15409/gfx1111/proj1120 twistedBow20483=15409 blowpipe21577=884"+
            " scorching28810=FAIL_CLOSED scorching28860=CRASH_GUARDED_M2 formulasDeferred=true fallbackTiming=4T_for_R2_candidates");
    }
}

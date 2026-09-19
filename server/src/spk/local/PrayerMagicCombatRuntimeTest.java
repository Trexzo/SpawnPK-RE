package spk.local;

import java.io.*;

public final class PrayerMagicCombatRuntimeTest {
    private static void req(boolean c,String m){if(!c)throw new AssertionError(m);}
    private static ServerPacketWriter writer(){return new ServerPacketWriter(new ByteArrayOutputStream(),new IsaacCipher(new int[]{1,2,3,4}));}
    public static void main(String[]a)throws Exception{
        req(PrayerDefinitionRepository.count()==51,"prayer count");
        PrayerDefinitionRepository.Def thick=PrayerDefinitionRepository.byWidget(5609);
        req(thick!=null && thick.varp==83 && thick.level==1 && "Thick Skin".equals(thick.name),"thick skin");
        PrayerDefinitionRepository.Def smite=PrayerDefinitionRepository.byWidget(685);
        req(smite!=null && smite.varp==100 && "Smite".equals(smite.name),"smite");
        PrayerDefinitionRepository.Def rigour=PrayerDefinitionRepository.byWidget(18045);
        req(rigour!=null && rigour.varp==609 && "Rigour".equals(rigour.name),"rigour");

        PrayerState ps=new PrayerState(); PlayerState player=new PlayerState();
        String on=ps.click(thick,player,writer()); req(on.contains("enabled=true"),"prayer on");
        String off=ps.click(thick,player,writer()); req(off.contains("enabled=false"),"prayer off");
        req(ps.switchBook("curses",writer()).contains("CURSES"),"curse switch");

        req(SpellDefinitionRepository.count()==126,"spell total");
        req(SpellDefinitionRepository.count(SpellDefinitionRepository.Book.MODERN)==65,"modern");
        req(SpellDefinitionRepository.count(SpellDefinitionRepository.Book.ANCIENT)==21,"ancient");
        req(SpellDefinitionRepository.count(SpellDefinitionRepository.Book.LUNAR)==40,"lunar");
        SpellDefinitionRepository.Spell wind=SpellDefinitionRepository.byWidget(1152);
        req(wind!=null && wind.level==1 && wind.targeted && (wind.targetMask&2)!=0,"wind strike");
        SpellDefinitionRepository.Spell airSurge=SpellDefinitionRepository.byWidget(19100);
        req(airSurge!=null && airSurge.targetMask==10,"air surge mask");
        SpellDefinitionRepository.Spell bake=SpellDefinitionRepository.byWidget(30017);
        req(bake!=null && bake.targetMask==16,"bake pie mask");

        req(CombatStyleRepository.rootCount()==18,"style roots");
        req(CombatStyleRepository.countStyles()==62,"style count");
        CombatStyleRepository.Style staff0=CombatStyleRepository.byWidget(328,336);
        CombatStyleRepository.Style staff1=CombatStyleRepository.byWidget(328,335);
        CombatStyleRepository.Style staff2=CombatStyleRepository.byWidget(328,334);
        req(staff0!=null&&staff0.value==0&&"Bash".equals(staff0.label),"staff bash");
        req(staff1!=null&&staff1.value==1&&"Pound".equals(staff1.label),"staff pound");
        req(staff2!=null&&staff2.value==2&&staff2.label.contains("Focus"),"staff focus");
        req(CombatStyleRepository.byWidget(5855,5860).value==0,"unarmed block weird value");
        req(CombatStyleRepository.byWidget(776,782).value==0,"scythe reap");

        CombatStyleState cs=new CombatStyleState();
        String sr=cs.click(1764,1771,writer()); req(sr.contains("Rapid")&&cs.value()==1,"ranged rapid");
        req(cs.reconcileRoot(5855,writer()).contains("STYLE_RETAINED")||cs.value()==0,"root reconcile");
        System.out.println("PRAYER_MAGIC_COMBAT_RUNTIME_TEST_PASS prayers=51 spells=126 combatRoots=18 styles=62");
    }
}

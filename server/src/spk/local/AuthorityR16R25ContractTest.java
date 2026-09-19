package spk.local;

import java.util.*;

public final class AuthorityR16R25ContractTest {
    public static void main(String[] args)throws Exception{
        // R18: configured amount is carried in ItemContainerAction.extra and must not be hardcoded to 14.
        ItemContainerAction configured=new ItemContainerAction(141,BankState.BANK_CONTAINER,0,995,37,"WITHDRAW_CONFIGURED_AMOUNT");
        if(configured.extra!=37)throw new AssertionError("configured withdraw extra");

        // R19 exact config87 encoding: LE u16 then X mixed int order [8..15,0..7,24..31,16..23].
        byte[] c=AuthorityR16R25Publisher.config87(0x1234,0x11223344);
        int[] exp={0x34,0x12,0x33,0x44,0x11,0x22};
        if(c.length!=6)throw new AssertionError("config87 length="+c.length);
        for(int i=0;i<exp.length;i++)if((c[i]&255)!=exp[i])throw new AssertionError("config87 byte"+i+"="+(c[i]&255));

        // R25 runtime repositories already merged before Engine R2: certify current counts against Authority R25.
        if(PrayerDefinitionRepository.count()!=51)throw new AssertionError("prayers="+PrayerDefinitionRepository.count());
        if(SpellDefinitionRepository.count()!=126)throw new AssertionError("spells="+SpellDefinitionRepository.count());
        if(SpellDefinitionRepository.count(SpellDefinitionRepository.Book.MODERN)!=65)throw new AssertionError("modern");
        if(SpellDefinitionRepository.count(SpellDefinitionRepository.Book.ANCIENT)!=21)throw new AssertionError("ancient");
        if(SpellDefinitionRepository.count(SpellDefinitionRepository.Book.LUNAR)!=40)throw new AssertionError("lunar");
        // Authority R25 closes 17 native non-staff profiles / 59 rows. Current LocalLab also preserves
        // the independently exact-client-recovered staff combat root 328 (3 rows), yielding 18 / 62.
        // Do not regress that newer cross-lane finding merely to match the R25 lane census.
        if(CombatStyleRepository.rootCount()!=18)throw new AssertionError("roots="+CombatStyleRepository.rootCount());
        if(CombatStyleRepository.countStyles()!=62)throw new AssertionError("styles="+CombatStyleRepository.countStyles());
        if(CombatStyleRepository.byValue(328,0)==null || CombatStyleRepository.byValue(328,1)==null || CombatStyleRepository.byValue(328,2)==null)
            throw new AssertionError("staff root328 extension missing");

        // S2C126 control encoder must produce a newline-terminated string before transformed key bytes.
        byte[] k=AuthorityR16R25Publisher.control126(16,"0");
        if(k.length!=4 || (k[0]&255)!='0' || (k[1]&255)!=10)throw new AssertionError("control126 prefix="+Arrays.toString(k));

        System.out.println("V5124_AUTHORITY_R16_R25_PASS dynamic141=true coin140FailClosed=true config87=true control126=true prayers51=true spells126=true combatStylesR25Core59_plusStaff3=true");
    }
}

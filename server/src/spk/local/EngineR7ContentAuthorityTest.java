package spk.local;

public final class EngineR7ContentAuthorityTest {
    public static void main(String[] args) {
        eq(605, AmmoAuthorityRepository.count(), "ammo rows");
        eq(107, SpecialAttackAuthorityRepository.count(), "special rows");
        eq(339, PetResearchAuthorityRepository.count(), "pet research rows");
        eq(51, PrayerDefinitionRepository.count(), "prayer rows");
        eq(126, SpellDefinitionRepository.count(), "spell rows");
        eq(18, CombatStyleRepository.rootCount(), "combat roots");
        eq(62, CombatStyleRepository.countStyles(), "combat styles");
        eq(30000, ItemAuthorityRepository.count(), "items");

        SpecialAttackAuthorityRepository.Entry barrel = SpecialAttackAuthorityRepository.get(7807);
        if (barrel == null) throw new AssertionError("missing Barrelchest war axe special metadata");
        if (!barrel.effectText.toLowerCase(java.util.Locale.ROOT).contains("prayer"))
            throw new AssertionError("barrel effect text missing prayer drain semantics: " + barrel.effectText);
        if (barrel.formulaResolved())
            throw new AssertionError("special formula must remain unresolved/server authority");

        PetResearchAuthorityRepository.Entry scooby = PetResearchAuthorityRepository.get(24016);
        if (scooby == null) throw new AssertionError("missing Scooby research row");
        if (!scooby.mappingCertainty.contains("CANDIDATE"))
            throw new AssertionError("research row unexpectedly promoted: " + scooby.mappingCertainty);
        PetDefinitionRepository.Def gameplay = PetDefinitionRepository.get(24016);
        if (gameplay == null) throw new AssertionError("existing gameplay pet mapping regressed");

        String summary = ContentAuthorityRepository.summary();
        has(summary, "items=30000");
        has(summary, "petsResearch=339");
        has(summary, "prayers=51");
        has(summary, "spells=126");
        has(summary, "ammoRows=605");
        has(summary, "specialRows=107");
        has(summary, "worldRegions=1279");

        String item = ContentAuthorityRepository.itemSummary(7807);
        has(item, "special=CURRENT_ITEM_EFFECT_TEXT_ONLY/formula=unknown");

        System.out.println("V5170_ENGINE_R7_CONTENT_AUTHORITY_PASS items=30000 petsResearch=339 petsGameplay="+
            PetDefinitionRepository.count()+" prayers=51 spells=126 combat=18/62 ammo=605 specials=107 specialFormulaGuard=true petCandidateGuard=true worldRegions=1279");
    }
    static void eq(int e,int a,String label){if(e!=a)throw new AssertionError(label+" expected="+e+" actual="+a);}
    static void has(String s,String needle){if(s==null||!s.contains(needle))throw new AssertionError("missing '"+needle+"' in "+s);}
}

package spk.local;

import java.util.Locale;

/**
 * Exact current-client combat-tab roots reconstructed from rs.n.c.aY.
 * This resolves presentation only: attack-style effects and spec combat semantics
 * remain server combat-engine work.
 */
final class CombatInterfaceRepository {
    static final int TAB_INDEX = 0;
    static final int UNARMED = 5855;      // Punch / Kick / Block
    static final int AXE = 1698;          // Chop / Hack / Smash / Block
    static final int DAGGER = 2276;       // Stab / Lunge / Slash / Block
    static final int SWORD = 2423;        // Chop / Slash / Lunge / Block
    static final int MACE = 3796;         // Pound / Pummel / Spike / Block
    static final int SPEAR = 4679;        // Lunge / Swipe / Pound / Block
    static final int TWO_HANDED = 4705;   // Chop / Slash / Smash / Block
    static final int PICKAXE = 5570;      // Spike / Impale / Smash / Block
    static final int CLAWS = 7762;        // claw-style four-option root
    static final int SCYTHE = 776;        // Reap / Chop / Jab / Block
    static final int MAUL = 425;           // Pound / Pummel / Block
    static final int BOW = 1764;           // Accurate / Rapid / Longrange
    static final int CROSSBOW = 1749;      // Accurate / Rapid / Longrange
    static final int THROWN = 4446;        // Accurate / Rapid / Longrange
    static final int STAFF = 328;          // Bash / Pound / Focus
    static final int WARHAMMER = 6103;     // Pound / Block
    static final int HALBERD = 8460;       // Jab / Swipe / Fend
    static final int WHIP = 12290;         // Flick / Lash / Deflect

    private CombatInterfaceRepository() {}

    static int forWeapon(int itemId) {
        if(itemId < 0) return UNARMED;
        ItemCatalog.Meta item=ItemDefinitionRepository.get(itemId);
        if(item==null || item.name==null) return SWORD;
        String n=" "+item.name.toLowerCase(Locale.ROOT).replace('_',' ')+" ";

        if(n.contains("scythe")) return SCYTHE;
        if(n.contains("whip") || n.contains("tentacle")) return WHIP;
        if(n.contains("claws")) return CLAWS;
        if(n.contains("halberd")) return HALBERD;
        if(n.contains("spear") || n.contains("hasta")) return SPEAR;
        if(n.contains("maul")) return MAUL;
        if(n.contains("warhammer") || n.contains(" hammer")) return WARHAMMER;
        if(n.contains("pickaxe")) return PICKAXE;
        if(n.contains("godsword") || n.contains("2h sword") || n.contains("two-handed")) return TWO_HANDED;
        if(n.contains("crossbow") || n.contains("c'bow") || n.contains("ballista")) return CROSSBOW;
        if(n.contains(" bow ") || n.endsWith(" bow ")) return BOW;
        if(n.contains("javelin") || n.contains("knife") || n.contains("dart") || n.contains("thrownaxe")) return THROWN;
        if(n.contains("staff") || n.contains("wand") || n.contains("trident") || n.contains("sceptre") || n.contains("scepter")) return STAFF;
        if(n.contains("dagger") || n.contains("rapier") || n.contains("keris")) return DAGGER;
        if(n.contains("mace") || n.contains("flail") || n.contains("cudgel")) return MACE;
        if(n.contains("axe")) return AXE;
        return SWORD;
    }

    static String describe(int itemId){
        int root=forWeapon(itemId);
        String name=itemId<0?"UNARMED":String.valueOf(ItemDefinitionRepository.get(itemId)==null?itemId:ItemDefinitionRepository.get(itemId).name);
        return "weapon="+itemId+" name="+name+" root="+root+" tab="+TAB_INDEX;
    }
}

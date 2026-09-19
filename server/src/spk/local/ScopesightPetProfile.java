package spk.local;

/**
 * Evidence ledger for the regular Scopesight Vasa pet.
 * Presentation/mapping values are exact current-client/runtime authority.
 * Numeric passive values are recovered SpawnPK effect authority and are kept
 * separate from Combat M2's intentionally fake 100/200 fixture formula.
 */
final class ScopesightPetProfile {
    static final int ITEM_ID=28888;
    static final int NPC_ID=8330;
    static final int STAND_ANIM=7416;
    static final int WALK_ANIM=7411;

    static final int MAINTAIN_RANGED=114; // 99 + 15
    static final int MAINTAIN_MAGIC=109;  // 99 + 10

    static final double RANGED_ACCURACY_BONUS_PCT=17.5;
    static final int SPELL_MAX_HIT_BONUS=6;
    static final double SNIPE_CHANCE_PCT=5.0;
    static final double SNIPE_PVP_DAMAGE_BONUS_PCT=10.0;
    static final double SNIPE_PVP_ACCURACY_BONUS_PCT=25.0;
    static final double SNIPE_PVM_DAMAGE_BONUS_PCT=40.0;
    static final double SNIPE_PVM_ACCURACY_BONUS_PCT=50.0;
    static final double PVM_MAGIC_RANGED_DAMAGE_BONUS_PCT=25.0;

    static final String NATIVE_TRIGGER_TEXT="SNIPE";

    private ScopesightPetProfile(){}
}

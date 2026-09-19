package spk.local;

/**
 * Local-only custom-content prototype built on the current Vasa pet asset family.
 *
 * This is NOT recovered SpawnPK production content.  R1 deliberately reuses the
 * exact client-known Vasa item/NPC/model/pose and composes a distinct presentation
 * through the already-proven pet particle selector + normal NPC mask paths.
 */
final class VoidglassPetProfile {
    static final String CONTENT_KEY="voidglass_nistirio";
    static final String DISPLAY_NAME="Voidglass Nistirio";

    // Existing exact-client Vasa family.  These definitions are never mutated.
    static final int BASE_ITEM_ID=22960;
    static final int BASE_NPC_ID=3701;
    static final int WORLD_MODEL_ID=32680;
    static final int STAND_ANIM=7416;
    static final int WALK_TURN_ANIM=7411;

    // Exact current-client particle selector contract.  Choice of selector 6 is
    // our LocalLab art direction; the selector mapping itself is client authority.
    static final int DEFAULT_PARTICLE_SELECTOR=6; // preset 20, #E600FF magenta
    static final int ALT_PARTICLE_SELECTOR=8;     // alternating cyan/pink

    // Existing safe player-side pet boost presentation used as an R1 prototype.
    static final int OWNER_PROC_GFX=1310;
    static final String PROC_TEXT="VOID RESONANCE";

    private VoidglassPetProfile(){}

    static boolean matches(PetState state,NpcEntity visiblePet){
        return state!=null && visiblePet!=null && state.active()
            && state.itemId()==BASE_ITEM_ID && state.npcId()==BASE_NPC_ID
            && visiblePet.petItemId==BASE_ITEM_ID && visiblePet.definitionId==BASE_NPC_ID;
    }

    static boolean allowedSelector(int selector){
        return selector==DEFAULT_PARTICLE_SELECTOR || selector==ALT_PARTICLE_SELECTOR;
    }
}

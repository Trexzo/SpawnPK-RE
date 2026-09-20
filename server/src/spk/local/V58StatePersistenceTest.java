package spk.local;

import java.util.*;

/** New v5.8 player-state fields survive account property round-trip. */
public final class V58StatePersistenceTest {
    public static void main(String[] args){
        PlayerState a=new PlayerState();
        a.setCurrentLevel(PlayerState.RANGED,73);
        a.setSpecialEnergy(42);
        a.setNegativeEffects(7,11,13);
        SortedMap<String,String> p=PersistenceSchemaTestSupport.capturePlayer(a);
        PlayerState b=new PlayerState();PersistenceSchemaTestSupport.restorePlayer(b,p);
        if(b.currentLevel(PlayerState.RANGED)!=73||b.specialEnergy()!=42||b.poison()!=7||b.venom()!=11||b.sicken()!=13)
            throw new AssertionError("state persistence failed");
        System.out.println("V58_STATE_PERSISTENCE_PASS ranged=73 special=42 poison=7 venom=11 sicken=13");
    }
}

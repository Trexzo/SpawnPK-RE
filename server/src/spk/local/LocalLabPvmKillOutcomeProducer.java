package spk.local;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * R9: semantic PvM NPC_KILL only after a server-verified, removed NPC death.
 *
 * No reconstructed SpawnPK HP/drop/XP or reward authority is inferred. The
 * producer accepts only the opt-in marked LocalLab actor. Outcome consumers
 * may later be bound to source-verified production PvM, separately.
 *
 * The World tick owns the instance (not a global static accumulator). A failed
 * observer never replays a committed kill or prevents existing loot/XP effects.
 */
final class LocalLabPvmKillOutcomeProducer {
    static final String AUTHORITY="CUSTOM_LOCALLAB_PVM_KILL_AFTER_REMOVED_LOOT_V1";
    private final CombatOutcomeObserver observer;
    private final Set<Integer> publishedScenes=new HashSet<>();

    /**
     * R78 OFFLINE TEST ONLY. Verify the actual attacker and World
     * generation before consuming a confirmed NPC-death scene.
     * The production producer still uses its original direct path.
     */
    @FunctionalInterface
    interface OfflineDeathAdmission {
        void requireAdmitted(WorldPlayer attacker);
    }

    private OfflineDeathAdmission offlineDeathAdmission;

    void installOfflineDeathAdmissionForTest(OfflineDeathAdmission hook){
        if(offlineDeathAdmission!=null||!publishedScenes.isEmpty())
            throw new IllegalStateException(
                "R78_DEATH_GATE_DENIED already used"
            );
        offlineDeathAdmission=Objects.requireNonNull(hook,"hook");
    }

    int publishedSceneCountForTest(){
        return publishedScenes.size();
    }

    LocalLabPvmKillOutcomeProducer(CombatOutcomeObserver observer){
        this.observer=Objects.requireNonNull(observer,"observer");
    }

    String publishCompletedDeath(
        NpcEntity dead,
        boolean sceneRemoved,
        WorldPlayer attacker,
        long worldTick
    ){
        if(!LocalLabMortalNpcFixture.enabled()||
           dead==null||
           !dead.localLabMortalFixture())
            return null;

        if(worldTick<0L)
            throw new IllegalArgumentException("worldTick");

        if(dead.definitionId!=LocalLabMortalNpcFixture.NPC_DEFINITION||
           dead.localLabMortalHp()!=0||
           !sceneRemoved)
            return "R9_PVM_KILL_OUTCOME_SKIPPED reason=NO_CONFIRMED_REMOVED_DEATH";

        OfflineDeathAdmission admission=offlineDeathAdmission;
        if(admission!=null)
            admission.requireAdmitted(
                Objects.requireNonNull(attacker,"attacker")
            );

        // An explicitly installed R78 hook checks BEFORE dedup. With
        // no hook, keep the exact original duplicate-before-null-owner
        // order; real producer behavior remains source-identical.
        if(!publishedScenes.add(dead.sceneIndex))
            return "R9_PVM_KILL_OUTCOME_DUPLICATE_DENIED scene="+dead.sceneIndex;

        WorldPlayer killer=Objects.requireNonNull(attacker,"attacker");
        String attackerRef=killer.id().toString();
        String victimRef="locallab:npc:def="+dead.definitionId+
            ":scene="+dead.sceneIndex;
        CombatOutcome fact=new CombatOutcome(
            attackerRef,
            victimRef,
            CombatOutcomeType.NPC_KILL,
            CombatOutcomeContext.NPC_PVM,
            worldTick,
            AUTHORITY
        );

        boolean delivered=true;
        try{
            observer.onCombatOutcome(fact);
        }catch(RuntimeException failure){
            delivered=false;
            System.err.println(
                "R9_PVM_KILL_OBSERVER_FAILURE_ISOLATED"+
                " scene="+dead.sceneIndex+
                " failure="+failure.getClass().getSimpleName()
            );
        }
        return "R9_PVM_KILL_OUTCOME_EMITTED"+
            " type="+fact.type()+
            " context="+fact.context()+
            " attacker="+fact.attacker()+
            " victim="+fact.victim()+
            " scene="+dead.sceneIndex+
            " def="+dead.definitionId+
            " worldTick="+fact.worldTick()+
            " delivery="+(delivered?"WORLD_DOMAIN_BUS":"OBSERVER_FAILED_ISOLATED")+
            " authority="+fact.sourceAuthority()+
            " originalSpawnpkKillCount=false";
    }
}

package spk.local;

import java.util.Objects;

/**
 * WorldPulse adapter for one canonical NPC combat AI instance.
 */
final class NpcCombatAiTickTarget
    implements WorldNpcTickTarget {

    private final WorldNpc attacker;
    private final NpcCombatAiService ai;
    private long callbacks;
    private NpcCombatAiService.Result lastResult;

    NpcCombatAiTickTarget(
        WorldNpc attacker,
        NpcCombatAiService ai
    ){
        this.attacker=
            Objects.requireNonNull(
                attacker,
                "attacker"
            );
        this.ai=
            Objects.requireNonNull(
                ai,
                "ai"
            );
    }

    @Override public EntityId npcId(){
        return attacker.id;
    }

    @Override public void onWorldNpcTick(
        long worldTick,
        long nowMillis
    )throws Exception{
        synchronized(this){
            callbacks++;
        }

        NpcCombatAiService.Result result=
            ai.tick(
                attacker,
                worldTick
            );

        synchronized(this){
            lastResult=result;
        }
    }

    synchronized long callbacks(){
        return callbacks;
    }

    synchronized NpcCombatAiService.Result
        lastResult(){
        return lastResult;
    }
}

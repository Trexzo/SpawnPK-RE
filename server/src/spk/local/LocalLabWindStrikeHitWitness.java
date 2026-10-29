package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * P5.33 in-JVM evidence from one already-completed source-only Wind
 * Strike joint settlement. NOT original SpawnPK kill credit, XP/loot
 * authorization, a durable receipt, projectile or account epoch.
 *
 * Only the joint settlement publishes this immutable observation,
 * AFTER queue admission, NPC HP/runes, player tick and attack phase
 * have successfully committed.
 */
final class LocalLabWindStrikeHitWitness {
    final World world;
    final WorldPlayer attacker;
    final long generation;
    final EntityId attackerId;
    final EntityId npcId;
    final int npcDefinitionId;
    final long worldTick;
    final Object npcRegistrationIdentity;
    final int appliedDamage;
    final int hitpointsAfter;
    final boolean newlyDied;
    final long deathTick;

    private LocalLabWindStrikeHitWitness(
        World world,WorldPlayer attacker,long generation,
        WorldNpc canonical,Object registrationIdentity,
        NpcLifecycleService.DamageResult damage
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.attacker=Objects.requireNonNull(attacker,"attacker");
        this.attackerId=attacker.id();
        WorldNpc npc=Objects.requireNonNull(canonical,"canonical");
        this.npcId=npc.id;
        this.npcDefinitionId=npc.definitionId;
        this.npcRegistrationIdentity=Objects.requireNonNull(
            registrationIdentity,"registrationIdentity");
        NpcLifecycleService.DamageResult hit=
            Objects.requireNonNull(damage,"damage");
        if(generation<0L||hit.worldTick<0L||
           !hit.npcId.equals(npc.id)||
           hit.appliedDamage!=LocalLabWindStrikeNpcSettlement.DAMAGE||
           hit.ignoredDead||hit.hitpointsAfter<0||
           (hit.newlyDied!=(hit.hitpointsAfter==0)))
            throw new IllegalArgumentException(
                "P533_INVALID_COMMITTED_WIND_STRIKE_WITNESS");
        this.generation=generation;
        this.worldTick=hit.worldTick;
        this.appliedDamage=hit.appliedDamage;
        this.hitpointsAfter=hit.hitpointsAfter;
        this.newlyDied=hit.newlyDied;
        this.deathTick=hit.newlyDied
            ?hit.worldTick:NpcLifecycleService.NO_DEATH_TICK;
    }

    /**
     * Called only after all semantic and source attack admission
     * commits. This factory is package-private evidence, not a
     * server-side reward permission for arbitrary callers.
     */
    static LocalLabWindStrikeHitWitness fromCommittedSettlement(
        World world,WorldPlayer attacker,long generation,
        WorldNpc npc,Object registrationIdentity,
        NpcLifecycleService.DamageResult damage
    ){
        return new LocalLabWindStrikeHitWitness(
            world,attacker,generation,npc,
            registrationIdentity,damage);
    }

    /**
     * Read-only, fail-closed check under exact current World/player
     * and canonical NPC/lifecycle registration guards. This DOES NOT
     * grant a kill, reward, XP, loot or persisted PvM receipt.
     * A stale/retired/re-registered NPC or player cannot pass.
     */
    boolean stillCurrentLethalRegistration(){
        if(!newlyDied||deathTick<0L)
            return false;
        final boolean[] checked={false};
        try{
            boolean owner=world.withOpenPlayerOwnershipIfCurrent(
                attacker,generation,()->{
                    WorldNpc npc=world.npcs().byId(npcId);
                    if(npc==null||npc.definitionId!=npcDefinitionId)
                        return;
                    world.npcs().withCurrentMutationOwnershipIfCurrent(
                        npc,()->{
                            synchronized(world.npcLifecycle()){
                                NpcLifecycleService.Snapshot now=
                                    world.npcLifecycle().get(npcId);
                                checked[0]=
                                    now!=null&&now.dead()&&
                                    now.hitpoints==0&&
                                    now.definitionId==npcDefinitionId&&
                                    now.deathTick==deathTick&&
                                    now.registrationIdentity==
                                        npcRegistrationIdentity;
                            }
                        });
                });
            return owner&&checked[0];
        }catch(IOException|RuntimeException failure){
            return false;
        }catch(Exception failure){
            return false;
        }
    }

    /**
     * Informational only: a recorded hit remains a historical object
     * after the World retires. Never use this alone to grant rewards.
     */
    boolean attackerStillCurrent(){
        return !world.closed()&&
            world.players().owns(attacker,generation);
    }
}

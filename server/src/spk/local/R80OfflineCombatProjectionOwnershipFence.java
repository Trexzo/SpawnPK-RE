package spk.local;

import java.util.Objects;

/**
 * R80 OFFLINE TEST ONLY. Revalidates an R79-attested PvP producer's
 * original two WorldPlayer instances/generations at each downstream
 * observer and each projection write.
 *
 * This fence does not attest raw CombatOutcome strings. It MUST only
 * be used after the real R79 offline PvP producer/domain proof gate.
 * It never gets installed into LocalSession, live score, or persistence.
 */
final class R80OfflineCombatProjectionOwnershipFence {
    private final World world;
    private final WorldPlayer attacker;
    private final long attackerGeneration;
    private final WorldPlayer target;
    private final long targetGeneration;
    private final R74OfflineCommandIngressFence terminal;
    private int deniedFanouts;

    R80OfflineCombatProjectionOwnershipFence(
        World world,WorldPlayer attacker,long attackerGeneration,
        WorldPlayer target,long targetGeneration,
        R74OfflineCommandIngressFence terminal
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.attacker=Objects.requireNonNull(attacker,"attacker");
        this.attackerGeneration=attackerGeneration;
        this.target=Objects.requireNonNull(target,"target");
        this.targetGeneration=targetGeneration;
        this.terminal=Objects.requireNonNull(terminal,"terminal");
        if(attacker==target||
           !world.players().owns(attacker,attackerGeneration)||
           !world.players().owns(target,targetGeneration))
            throw new IllegalStateException(
                "R80_PROJECTION_DENIED cannot bind stale actor pair"
            );
    }

    boolean permitsFanout(CombatOutcome fact,int index){
        try{
            requireAdmitted(fact);
            return true;
        }catch(IllegalStateException denied){
            deniedFanouts++;
            return false;
        }
    }

    void requireAdmitted(CombatOutcome fact){
        if(fact==null ||
           fact.context()!=CombatOutcomeContext.PLAYER_PVP ||
           !attacker.id().toString().equals(fact.attacker()) ||
           !target.id().toString().equals(fact.victim()))
            throw new IllegalStateException(
                "R80_PROJECTION_DENIED unexpected combat subject"
            );

        if(!world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "R80_PROJECTION_DENIED off-world projection"
            );

        if(!world.players().owns(attacker,attackerGeneration))
            throw new IllegalStateException(
                "R80_PROJECTION_DENIED stale attacker generation"
            );

        if(!world.players().owns(target,targetGeneration))
            throw new IllegalStateException(
                "R80_PROJECTION_DENIED stale target generation"
            );

        if(terminal.isQuiesced(attacker,attackerGeneration))
            throw new IllegalStateException(
                "R80_PROJECTION_DENIED attacker quiesced"
            );

        if(terminal.isQuiesced(target,targetGeneration))
            throw new IllegalStateException(
                "R80_PROJECTION_DENIED target quiesced"
            );
    }

    int deniedFanoutCountForTest(){return deniedFanouts;}
}

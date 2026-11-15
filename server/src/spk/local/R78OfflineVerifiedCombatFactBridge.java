package spk.local;

import java.io.IOException;
import java.util.Objects;
import spk.event.DomainEventBus;

/**
 * R78 OFFLINE TEST ONLY. Attest a real LocalLabPvmKillOutcomeProducer
 * CombatOutcome for the duration of one synchronous DomainEventBus publish.
 *
 * CombatOutcome itself intentionally remains a transport-neutral semantic
 * fact with no asserted login credential or World generation. This bridge
 * must be installed explicitly into an isolated World. Raw CombatOutcome
 * publications which lack the in-flight producer proof fail closed.
 *
 * Not deployed by LocalWorldTickCoordinator or LocalSession.
 */
final class R78OfflineVerifiedCombatFactBridge {
    private final World world;
    private final WorldPlayer attacker;
    private final long generation;
    private final R74OfflineCommandIngressFence commandFence;
    private final ThreadLocal<CombatOutcome> proof=new ThreadLocal<>();
    private final ThreadLocal<Boolean> insideListener=new ThreadLocal<>();
    private boolean installed;

    R78OfflineVerifiedCombatFactBridge(
        World world,WorldPlayer attacker,long generation,
        R74OfflineCommandIngressFence commandFence
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.attacker=Objects.requireNonNull(attacker,"attacker");
        this.generation=generation;
        this.commandFence=Objects.requireNonNull(
            commandFence,"commandFence"
        );
        if(!world.players().owns(attacker,generation))
            throw new IllegalStateException(
                "R78_COMBAT_DENIED attacker is not current World actor"
            );
    }

    synchronized LocalLabPvmKillOutcomeProducer
        installAndCreateProducerForTest(){
        if(installed)
            throw new IllegalStateException(
                "R78_COMBAT_DENIED duplicate bridge installation"
            );

        world.domainEvents().installOfflinePublicationAdmissionForTest(
            (event,callback)->{
                if(!(event instanceof CombatOutcome)){
                    // World-scoped noncombat facts remain unaffected.
                    callback.run();
                    return;
                }
                if(event!=proof.get())
                    throw new IOException(
                        "R78_COMBAT_DENIED unverified combat fact"
                    );
                if(Boolean.TRUE.equals(insideListener.get()))
                    throw new IOException(
                        "R78_COMBAT_DENIED nested combat publication"
                    );
                requireCurrentAttackerForListener(
                    (CombatOutcome)event,callback
                );
            }
        );

        LocalLabPvmKillOutcomeProducer producer=
            new LocalLabPvmKillOutcomeProducer(
                this::publishVerifiedFact
            );
        producer.installOfflineDeathAdmissionForTest(
            this::requireAdmittedAttacker
        );
        installed=true;
        return producer;
    }

    /**
     * Recheck the real producer's attacker and its originally bound
     * generation *before* its scene-dedup state is touched.
     */
    private void requireAdmittedAttacker(WorldPlayer candidate){
        if(candidate!=attacker||
           !world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "R78_COMBAT_DENIED unbound or off-world attacker"
            );

        try{
            boolean invoked=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    attacker,generation,()->{
                        if(commandFence.isQuiesced(attacker,generation))
                            throw new IllegalStateException(
                                "R78_COMBAT_DENIED owner quiesced before death"
                            );
                    }
                );
            if(!invoked)
                throw new IllegalStateException(
                    "R78_COMBAT_DENIED stale attacker generation"
                );
        }catch(IllegalStateException denied){
            throw denied;
        }catch(Exception failure){
            throw new IllegalStateException(
                "R78_COMBAT_DENIED preflight failed",failure
            );
        }
    }

    private void publishVerifiedFact(CombatOutcome fact){
        Objects.requireNonNull(fact,"combat fact");
        // The source is the *real* LocalLab PvM producer. Do not
        // infer identity merely because a player name happens to match.
        if(!fact.attacker().equals(attacker.id().toString())||
           !LocalLabPvmKillOutcomeProducer.AUTHORITY.equals(
                fact.sourceAuthority())||
           fact.type()!=CombatOutcomeType.NPC_KILL||
           fact.context()!=CombatOutcomeContext.NPC_PVM)
            throw new IllegalStateException(
                "R78_COMBAT_DENIED unexpected producer fact"
            );

        if(proof.get()!=null)
            throw new IllegalStateException(
                "R78_COMBAT_DENIED recursive source emission"
            );

        requireAdmittedAttacker(attacker);
        proof.set(fact);
        try{
            world.domainEvents().publish(fact);
        }catch(Exception failure){
            throw new IllegalStateException(
                "R78_COMBAT_DENIED observer delivery failed",failure
            );
        }finally{
            proof.remove();
        }
    }

    private void requireCurrentAttackerForListener(
        CombatOutcome fact,
        DomainEventBus.CheckedPublication callback
    )throws Exception{
        if(!world.pulse().inExecutionContext())
            throw new IOException(
                "R78_COMBAT_DENIED off-world listener"
            );

        boolean delivered=
            world.withOpenPlayerMutationOwnershipIfCurrent(
                attacker,generation,()->{
                    if(proof.get()!=fact)
                        throw new IOException(
                            "R78_COMBAT_DENIED proof expired"
                        );
                    if(commandFence.isQuiesced(attacker,generation))
                        throw new IOException(
                            "R78_COMBAT_DENIED owner quiesced before listener"
                        );
                    insideListener.set(Boolean.TRUE);
                    try{
                        callback.run();
                    }finally{
                        insideListener.remove();
                    }
                }
            );
        if(!delivered)
            throw new IOException(
                "R78_COMBAT_DENIED stale listener owner"
            );
    }
}

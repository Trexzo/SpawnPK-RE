package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import spk.event.DomainEventBus;

/**
 * R79 OFFLINE ONLY. Wraps the real PlayerCombatResolutionService's
 * resolveImmediateOwned route with one World lifecycle critical section,
 * both generation-bound player mutation locks, and independent rechecks
 * after damage calculation and before EACH domain listener.
 *
 * No LocalSession, production PvP entry point, or socket installs this.
 * The scope is one attacker + target on a disposable isolated World;
 * no R64 login authentication or transactional rollback is implied.
 */
final class R79OfflinePairedPvpOwnershipGate {
    private final World world;
    private final WorldPlayer attacker;
    private final long attackerGeneration;
    private final WorldPlayer target;
    private final long targetGeneration;
    private final R74OfflineCommandIngressFence terminalFence;
    private final ThreadLocal<CombatOutcome> producerProof=
        new ThreadLocal<>();
    private final ThreadLocal<Boolean> inListener=
        new ThreadLocal<>();
    private final AtomicInteger rejectedListeners=new AtomicInteger();
    private final PlayerCombatResolutionService resolver;

    /**
     * R82 OFFLINE ONLY: an ordinal is issued at this exact real
     * PlayerCombatResolutionService outcome callback, not fabricated
     * from attacker/victim strings or the World tick.
     * It is scoped only to this ONE R79 test producer, not durable.
     */
    @FunctionalInterface
    interface OfflineSourceEmissionIssuer {
        void onSourceEmission(CombatOutcome fact,long sourceOrdinal);
    }

    private OfflineSourceEmissionIssuer offlineSourceEmissionIssuer;
    private long sourceEmissionOrdinal;
    private boolean sourceEmissionStarted;

    synchronized void installOfflineSourceEmissionIssuerForTest(
        OfflineSourceEmissionIssuer issuer
    ){
        if(offlineSourceEmissionIssuer!=null||sourceEmissionStarted)
            throw new IllegalStateException(
                "R82_SOURCE_ISSUER_DENIED already installed or emitted"
            );
        offlineSourceEmissionIssuer=Objects.requireNonNull(
            issuer,"issuer"
        );
    }

    R79OfflinePairedPvpOwnershipGate(
        World world,WorldPlayer attacker,long attackerGeneration,
        WorldPlayer target,long targetGeneration,
        R74OfflineCommandIngressFence terminalFence,
        CombatDamageRules damageRules
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.attacker=Objects.requireNonNull(attacker,"attacker");
        this.attackerGeneration=attackerGeneration;
        this.target=Objects.requireNonNull(target,"target");
        this.targetGeneration=targetGeneration;
        this.terminalFence=Objects.requireNonNull(
            terminalFence,"terminalFence"
        );
        CombatDamageRules canonical=Objects.requireNonNull(
            damageRules,"damageRules"
        );

        if(attacker==target||
           !world.players().owns(attacker,attackerGeneration)||
           !world.players().owns(target,targetGeneration))
            throw new IllegalStateException(
                "R79_PVP_DENIED cannot bind stale or identical actors"
            );

        // The existing resolver invokes the damage rule after its
        // timing/hook preparation. Rechecking immediately after the
        // real damage calculation is the last caller-owned seam before
        // its actual PlayerLifecycleService HP mutation.
        CombatDamageRules checkedDamage=new CombatDamageRules(){
            @Override public Result calculate(Request request){
                Result result=canonical.calculate(request);
                requireCurrent("after damage calculation");
                return result;
            }
            @Override public String authority(){
                return canonical.authority();
            }
            @Override public String formula(){
                return canonical.formula();
            }
        };

        resolver=new PlayerCombatResolutionService(
            attacker,checkedDamage,
            CombatAttackTimingRules.recoveredCompatibility(),
            CombatSystemHooks.none(),this::publishFromRealResolver
        );

        world.domainEvents().installOfflinePublicationAdmissionForTest(
            (event,callback)->{
                if(!(event instanceof CombatOutcome)){
                    // Unrelated/global domain event still uses original
                    // semantics and remains outside PvP actor scope.
                    callback.run();
                    return;
                }
                CombatOutcome fact=(CombatOutcome)event;
                if(fact!=producerProof.get()){
                    rejectedListeners.incrementAndGet();
                    throw new IOException(
                        "R79_PVP_DENIED unverified combat fact"
                    );
                }
                if(Boolean.TRUE.equals(inListener.get())){
                    rejectedListeners.incrementAndGet();
                    throw new IOException(
                        "R79_PVP_DENIED nested combat publication"
                    );
                }
                try{
                    requireCurrent("before domain listener");
                }catch(IllegalStateException invalid){
                    rejectedListeners.incrementAndGet();
                    throw new IOException(
                        "R79_PVP_DENIED owner changed before listener",
                        invalid
                    );
                }
                inListener.set(Boolean.TRUE);
                try{
                    callback.run();
                }finally{
                    inListener.remove();
                }
            }
        );
    }

    PlayerCombatResolutionService.Result resolveForTest(
        int weaponId,CombatStyleRepository.Style style,long tick
    )throws Exception{
        // This explicit World-context check rules out off-thread
        // calls that merely possess two stale player references.
        requireCurrent("before paired World action");

        final PlayerCombatResolutionService.Result[] result=
            new PlayerCombatResolutionService.Result[1];
        boolean invoked=
            world.withOfflineDualPlayerMutationOwnershipForTest(
                attacker,attackerGeneration,target,targetGeneration,
                ()->{
                    requireCurrent("inside paired actor lock");
                    result[0]=resolver.resolveImmediateOwned(
                        world,attackerGeneration,target,
                        targetGeneration,weaponId,style,tick
                    );
                }
            );
        if(!invoked)
            throw new IllegalStateException(
                "R79_PVP_DENIED stale paired World generation"
            );
        return Objects.requireNonNull(
            result[0],"R79 PvP resolution did not complete"
        );
    }

    int rejectedListenerCountForTest(){
        return rejectedListeners.get();
    }

    /**
     * R81 OFFLINE ONLY: exact fact object can start a projection receipt
     * only while the real R79 DomainEventBus callback is currently
     * executing with its producer proof. Strings cannot forge this.
     * Does not create a reusable token or authorize a later replay.
     */
    boolean isVerifiedFactInCurrentListenerForTest(CombatOutcome fact){
        return fact!=null&&
            producerProof.get()==fact&&
            Boolean.TRUE.equals(inListener.get())&&
            world.pulse().inExecutionContext();
    }

    private void publishFromRealResolver(CombatOutcome fact){
        // Only real resolver-originated NPC-unrelated PvP facts are
        // allowed. Attacker and victim *strings* alone are never
        // sufficient proof: exact ThreadLocal object identity is
        // required by the domain-bus listener gate below.
        if(fact==null||
           !fact.attacker().equals(attacker.id().toString())||
           !fact.victim().equals(target.id().toString())||
           fact.context()!=CombatOutcomeContext.PLAYER_PVP||
           (fact.type()!=CombatOutcomeType.PLAYER_KILL&&
            fact.type()!=CombatOutcomeType.PLAYER_DEATH)||
           !PlayerLifecycleService.AUTHORITY.equals(
                fact.sourceAuthority()))
            throw new IllegalStateException(
                "R79_PVP_DENIED unexpected resolver fact"
            );

        requireCurrent("before outcome publication");
        if(producerProof.get()!=null)
            throw new IllegalStateException(
                "R79_PVP_DENIED recursively attested outcome"
            );

        // R82 proof issuance happens only after R79's exact source
        // verification. A default fixture with no R82 installation
        // retains the same original publication behavior.
        OfflineSourceEmissionIssuer issuer;
        long ordinal=0L;
        synchronized(this){
            sourceEmissionStarted=true;
            issuer=offlineSourceEmissionIssuer;
            if(issuer!=null){
                sourceEmissionOrdinal=Math.addExact(
                    sourceEmissionOrdinal,1L
                );
                ordinal=sourceEmissionOrdinal;
            }
        }
        if(issuer!=null)issuer.onSourceEmission(fact,ordinal);

        producerProof.set(fact);
        try{
            world.domainEvents().publish(fact);
        }catch(Exception failure){
            throw new IllegalStateException(
                "R79_PVP_DENIED domain listener rejected",failure
            );
        }finally{
            producerProof.remove();
        }
    }

    private void requireCurrent(String stage){
        if(!world.pulse().inExecutionContext())
            throw new IllegalStateException(
                "R79_PVP_DENIED off-world "+stage
            );
        if(!world.players().owns(attacker,attackerGeneration))
            throw new IllegalStateException(
                "R79_PVP_DENIED stale attacker "+stage
            );
        if(!world.players().owns(target,targetGeneration))
            throw new IllegalStateException(
                "R79_PVP_DENIED stale target "+stage
            );
        if(terminalFence.isQuiesced(
                attacker,attackerGeneration))
            throw new IllegalStateException(
                "R79_PVP_DENIED attacker quiesced "+stage
            );
        if(terminalFence.isQuiesced(target,targetGeneration))
            throw new IllegalStateException(
                "R79_PVP_DENIED target quiesced "+stage
            );
    }
}

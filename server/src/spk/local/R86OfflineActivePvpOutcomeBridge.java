package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import spk.event.DomainEventBus;

/**
 * R86 OFFLINE TEST ONLY. Bridges the genuine active LocalPlayerInteractionHandler
 * -> PlayerCombatResolutionService five-argument outcome callback to the REAL
 * World DomainEventBus. A verified exact CombatOutcome instance is scoped to
 * its synchronous producer callback and revalidated before EACH bus listener.
 *
 * Only the explicitly named offline constructor opts in. LocalSession still
 * selects the untouched four-argument resolver (NO_OUTCOME_OBSERVER).
 * This class is NOT an authenticated production gameplay source, durable
 * event identifier, account authority or cross-projection commit.
 */
final class R86OfflineActivePvpOutcomeBridge
    implements CombatOutcomeObserver {

    private final World world;
    private final WorldPlayer attacker;
    private final long attackerGeneration;
    private final R74OfflineCommandIngressFence terminal;
    private final ThreadLocal<CombatOutcome> currentProof=new ThreadLocal<>();
    private final ThreadLocal<Boolean> inListener=new ThreadLocal<>();
    private final ThreadLocal<Boolean> inVerifiedActiveResolve=
        new ThreadLocal<>();
    private final AtomicInteger refused=new AtomicInteger();
    private int admitted;
    private int issued;
    private WorldPlayer target;
    private long targetGeneration;
    private boolean installed;

    R86OfflineActivePvpOutcomeBridge(
        World world,WorldPlayer attacker,long attackerGeneration,
        R74OfflineCommandIngressFence terminal
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.attacker=Objects.requireNonNull(attacker,"attacker");
        this.attackerGeneration=attackerGeneration;
        this.terminal=Objects.requireNonNull(terminal,"terminal");
        if(attackerGeneration<=0L||
           !world.players().owns(attacker,attackerGeneration))
            throw new IllegalStateException(
                "R86_PVP_DENIED stale source actor binding"
            );
    }

    synchronized void bindTargetForTest(
        WorldPlayer next,long generation
    ){
        Objects.requireNonNull(next,"target");
        if(target!=null||next==attacker||generation<=0L||
           !world.players().owns(next,generation))
            throw new IllegalStateException(
                "R86_PVP_DENIED target already bound or stale"
            );
        target=next;
        targetGeneration=generation;
    }

    void requireBoundForTest(WorldPlayer next,long generation){
        if(next==null||next!=target||generation!=targetGeneration)
            throw new IllegalStateException(
                "R86_PVP_DENIED active interaction target not bound"
            );
        requireCurrent("at active PvP resolution");
    }

    /**
     * Only the active handler's owner-pair critical section can open
     * the real resolver callback scope. The direct CombatOutcomeObserver
     * method refuses callers without this scope, even with copied fields.
     */
    void withBoundResolverForTest(
        WorldPlayer next,long generation,World.OwnedPlayerAction action
    )throws Exception{
        Objects.requireNonNull(action,"action");
        requireBoundForTest(next,generation);
        if(Boolean.TRUE.equals(inVerifiedActiveResolve.get()))
            throw new IllegalStateException(
                "R86_PVP_DENIED nested active resolution"
            );
        inVerifiedActiveResolve.set(Boolean.TRUE);
        try{
            action.run();
        }finally{
            inVerifiedActiveResolve.remove();
        }
    }

    synchronized void installForTest(){
        if(installed)
            throw new IllegalStateException(
                "R86_PVP_DENIED already installed"
            );
        world.domainEvents().installOfflinePublicationAdmissionForTest(
            (event,callback)->{
                if(!(event instanceof CombatOutcome)){
                    callback.run(); // global domain events retain old semantics
                    return;
                }
                CombatOutcome fact=(CombatOutcome)event;
                if(fact!=currentProof.get()){
                    refused.incrementAndGet();
                    throw new IOException(
                        "R86_PVP_DENIED unverified combat fact"
                    );
                }
                if(Boolean.TRUE.equals(inListener.get())){
                    refused.incrementAndGet();
                    throw new IOException(
                        "R86_PVP_DENIED nested combat publication"
                    );
                }
                try{
                    requireCurrent("before each domain listener");
                }catch(IllegalStateException stale){
                    refused.incrementAndGet();
                    throw new IOException(
                        "R86_PVP_DENIED actor generation or terminal",
                        stale
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
        installed=true;
    }

    @Override public void onCombatOutcome(CombatOutcome fact){
        Objects.requireNonNull(fact,"fact");
        if(!installed)
            throw new IllegalStateException(
                "R86_PVP_DENIED uninstalled test publisher"
            );
        if(!Boolean.TRUE.equals(inVerifiedActiveResolve.get()))
            throw new IllegalStateException(
                "R86_PVP_DENIED no active verified resolver"
            );
        if(target==null||
           fact.context()!=CombatOutcomeContext.PLAYER_PVP||
           (fact.type()!=CombatOutcomeType.PLAYER_KILL&&
            fact.type()!=CombatOutcomeType.PLAYER_DEATH)||
           !fact.attacker().equals(attacker.id().toString())||
           !fact.victim().equals(target.id().toString())||
           !PlayerLifecycleService.AUTHORITY.equals(
               fact.sourceAuthority()))
            throw new IllegalStateException(
                "R86_PVP_DENIED unexpected source fact"
            );
        requireCurrent("before publication");
        if(currentProof.get()!=null)
            throw new IllegalStateException(
                "R86_PVP_DENIED nested producer publication"
            );
        currentProof.set(fact);
        issued++;
        try{
            world.domainEvents().publish(fact);
            admitted++;
        }catch(Exception error){
            throw new IllegalStateException(
                "R86_PVP_DENIED domain delivery failed",error
            );
        }finally{
            currentProof.remove();
        }
    }

    private void requireCurrent(String stage){
        if(!world.pulse().inExecutionContext()||
           !world.players().owns(attacker,attackerGeneration)||
           target==null||
           !world.players().owns(target,targetGeneration)||
           terminal.isQuiesced(attacker,attackerGeneration)||
           terminal.isQuiesced(target,targetGeneration))
            throw new IllegalStateException(
                "R86_PVP_DENIED stale or off-world "+stage
            );
    }

    /**
     * R87 OFFLINE ONLY: a projection receipt can be claimed only
     * within a current, exact-fact R86 DomainEventBus listener.
     * The return value is not reusable event identity.
     */
    boolean isVerifiedFactInCurrentListenerForTest(CombatOutcome fact){
        return fact!=null&&
            currentProof.get()==fact&&
            Boolean.TRUE.equals(inListener.get())&&
            Boolean.TRUE.equals(inVerifiedActiveResolve.get())&&
            world.pulse().inExecutionContext();
    }

    int issuedCountForTest(){return issued;}
    int admittedCountForTest(){return admitted;}
    int refusedCountForTest(){return refused.get();}
}

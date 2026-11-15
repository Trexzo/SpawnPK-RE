package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R66 OFFLINE-ONLY simulated gameplay-event queue/actor boundary.
 *
 * A captured event binds one in-memory WorldPlayer actor to one R64
 * owner UUID, epoch, R63 canonical generation/hash, and R59 journal
 * revision. At EXECUTION (not capture) the dispatcher takes R60's real
 * cross-process OS FileLock, verifies the account's on-disk session
 * ownership and generation, checks that the captured actor is STILL the
 * dispatcher-bound actor, then mutates a TEST-OWNED WorldPlayer bank.
 *
 * Stale queued events are rejected BEFORE any in-memory mutation; this
 * closes the model's earlier gap where R65 guarded only disk saves.
 *
 * No real packets, LocalSession, World mutation handlers or production
 * callbacks are wired to this class. The test item addition is synthetic,
 * not a network-authorized action or real player inventory operation.
 */
final class R66OfflineGameEventDispatch {
    @FunctionalInterface
    interface CriticalHook {
        void run()throws IOException;
    }

    static final class Actor {
        final UUID incarnation;
        final R64OfflineSessionOwnership.Token owner;
        final WorldPlayer player;
        final ServerPacketWriter packets;
        private final Set<UUID> claimedEvents=new HashSet<>();

        private Actor(
            R64OfflineSessionOwnership.Token owner,
            WorldPlayer player,ServerPacketWriter packets
        ){
            incarnation=UUID.randomUUID();
            this.owner=owner;
            this.player=player;
            this.packets=packets;
        }
    }

    static final class QueuedEvent {
        final UUID eventId;
        final Actor actor;
        final int simulatedItemId;
        private QueuedEvent(Actor actor,int item){
            eventId=UUID.randomUUID();
            this.actor=actor;
            simulatedItemId=item;
        }
    }

    static final class Outcome {
        final UUID eventId;
        final UUID actorIncarnation;
        final int countBefore;
        final int countAfter;
        Outcome(UUID eventId,UUID actorIncarnation,int before,int after){
            this.eventId=eventId;
            this.actorIncarnation=actorIncarnation;
            countBefore=before;
            countAfter=after;
        }
        boolean loginAuthorized(){return false;}
        boolean accountSaved(){return false;}
        boolean productionGameplayDispatched(){return false;}
    }

    private final Path journal,accounts,generations,sessions;
    private final ConcurrentHashMap<String,Actor> activeActors=
        new ConcurrentHashMap<>();

    R66OfflineGameEventDispatch(
        Path journal,Path accounts,Path generations,Path sessions
    ){
        this.journal=Objects.requireNonNull(journal,"journal");
        this.accounts=Objects.requireNonNull(accounts,"accounts");
        this.generations=Objects.requireNonNull(generations,"generations");
        this.sessions=Objects.requireNonNull(sessions,"sessions");
    }

    /**
     * Explicit offline actor attachment for a recorded current R64 owner.
     * A newer actor binding supersedes the local map entry, while the
     * shared OS lock guarantees it cannot interleave with a cooperating
     * event dispatch's owner check or in-memory mutation.
     */
    Actor bindCurrent(
        R64OfflineSessionOwnership.Token owner,
        WorldPlayer player,ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(owner,"owner");
        Objects.requireNonNull(player,"player");
        Objects.requireNonNull(packets,"packets");
        return R60TradePairCoordinator.withExclusive(journal,false,()->{
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,owner
            );
            Actor bound=new Actor(owner,player,packets);
            activeActors.put(owner.username,bound);
            return bound;
        });
    }

    /**
     * Queue capture is intentionally NOT a durable authorization.
     * The callback may sit on another Java thread while its owner changes.
     */
    QueuedEvent capture(Actor actor,int syntheticItemId)throws IOException{
        Objects.requireNonNull(actor,"actor");
        if(syntheticItemId<=0||syntheticItemId>32767)
            throw new IOException("R66_CAPTURE_DENIED invalid test item");
        if(actor.owner.phase!=R64OfflineSessionOwnership.Phase.ACTIVE ||
           activeActors.get(actor.owner.username)!=actor)
            throw new IOException(
                "R66_CAPTURE_DENIED inactive or superseded actor"
            );
        return new QueuedEvent(actor,syntheticItemId);
    }

    Outcome dispatch(QueuedEvent event)throws IOException{
        return dispatch(event,()->{});
    }

    /**
     * The hook is TEST-ONLY, used to pause *after* owner verification but
     * *before* in-memory mutation. A separate JVM tries an owner takeover
     * during this pause and MUST fail its cooperative OS tryLock.
     */
    Outcome dispatch(QueuedEvent event,CriticalHook afterValidation)
            throws IOException{
        Objects.requireNonNull(event,"event");
        Objects.requireNonNull(afterValidation,"afterValidation");
        return R60TradePairCoordinator.withExclusive(journal,false,()->{
            Actor actor=event.actor;
            // Check authoritative disk ownership FIRST so a queued event
            // from a replaced owner is denied even if the dispatcher has
            // not yet attached the replacement actor.
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,actor.owner
            );
            if(activeActors.get(actor.owner.username)!=actor)
                throw new IOException(
                    "R66_EVENT_DENIED stale actor incarnation"
                );
            afterValidation.run();
            // Fail-safe, in-process at-most-once event marking. Marking
            // before mutation favors refusal over reapplying an uncertain
            // event after an exception. It is NOT durable replay storage.
            if(!actor.claimedEvents.add(event.eventId))
                throw new IOException(
                    "R66_EVENT_DENIED duplicate queued event"
                );
            int before=actor.player.bank().inventoryCount(
                event.simulatedItemId
            );
            actor.player.bank().spawnItem(
                event.simulatedItemId,1,actor.packets
            );
            int after=actor.player.bank().inventoryCount(
                event.simulatedItemId
            );
            if(after!=before+1)
                throw new IOException(
                    "R66_EVENT_DENIED synthetic bank mutation not verified"
                );
            return new Outcome(
                event.eventId,actor.incarnation,before,after
            );
        });
    }
}

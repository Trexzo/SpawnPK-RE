package spk.local;

import java.util.*;

/**
 * Durable semantic global-event lifecycle above low-level tick scheduling.
 *
 * The service owns event identity and state. WorldEventQueue tasks may choose
 * when to call this service, but scheduler handles are never stored here and
 * never become event identity or lifecycle authority.
 */
final class GlobalEventService {
    static final long NO_TRANSITION_TICK=Long.MIN_VALUE;

    enum Lifecycle {
        SCHEDULED,
        ACTIVE,
        COMPLETED,
        CANCELLED
    }

    enum ChangeCause {
        START_DEADLINE,
        PHASE_DEADLINE,
        END_DEADLINE,
        EXPLICIT_CANCEL,
        EXPLICIT_COMPLETE
    }

    static final class TerminalHold {
        private final GlobalEventService owner;
        private final WorldEventId eventId;
        private final String ownerRef;

        private TerminalHold(
            GlobalEventService owner,
            WorldEventId eventId,
            String ownerRef
        ){
            this.owner=
                Objects.requireNonNull(
                    owner,
                    "owner"
                );
            this.eventId=
                Objects.requireNonNull(
                    eventId,
                    "eventId"
                );
            this.ownerRef=
                requireTerminalHoldLabel(
                    ownerRef
                );
        }

        @Override public String toString(){
            return "TerminalHold{"+
                eventId+
                ",ownerRef="+
                ownerRef+
                "}";
        }
    }

    static final class Snapshot {
        final WorldEventId id;
        final Lifecycle lifecycle;
        final long startTick;
        final long endTick;
        final String phaseKey;
        final long lastTransitionTick;
        final String sourceAuthority;

        Snapshot(Entry entry){
            this.id=entry.definition.id;
            this.lifecycle=entry.lifecycle;
            this.startTick=entry.definition.startTick;
            this.endTick=entry.definition.endTick;
            this.phaseKey=
                entry.phaseIndex<0
                    ?null
                    :entry.definition.phases
                        .get(entry.phaseIndex).key;
            this.lastTransitionTick=
                entry.lastTransitionTick;
            this.sourceAuthority=
                entry.definition.sourceAuthority;
        }

        boolean terminal(){
            return lifecycle==Lifecycle.COMPLETED||
                lifecycle==Lifecycle.CANCELLED;
        }

        boolean hasPhase(){
            return phaseKey!=null;
        }

        boolean hasTransitionTick(){
            return lastTransitionTick!=NO_TRANSITION_TICK;
        }

        @Override public String toString(){
            return "GlobalEventSnapshot{"+
                "id="+id+
                ",lifecycle="+lifecycle+
                ",startTick="+startTick+
                ",endTick="+endTick+
                ",phaseKey="+phaseKey+
                ",lastTransitionTick="+
                    (hasTransitionTick()
                        ?Long.toString(lastTransitionTick)
                        :"absent")+
                ",sourceAuthority="+sourceAuthority+
                "}";
        }
    }

    static final class Change {
        final Snapshot before;
        final Snapshot after;
        final ChangeCause cause;

        Change(
            Snapshot before,
            Snapshot after,
            ChangeCause cause
        ){
            this.before=before;
            this.after=after;
            this.cause=cause;
        }
    }

    static final class TickResult {
        final long worldTick;
        final List<Change> changes;

        TickResult(long worldTick,List<Change> changes){
            this.worldTick=worldTick;
            this.changes=immutableChanges(changes);
        }

        boolean changed(){
            return !changes.isEmpty();
        }
    }

    static final class MutationResult {
        final Snapshot snapshot;
        final List<Change> changes;

        MutationResult(Snapshot snapshot,List<Change> changes){
            this.snapshot=snapshot;
            this.changes=immutableChanges(changes);
        }

        boolean changed(){
            return !changes.isEmpty();
        }
    }

    private static final class Entry {
        final WorldEventDefinition definition;
        Lifecycle lifecycle=Lifecycle.SCHEDULED;
        int phaseIndex=-1;
        long lastTransitionTick=NO_TRANSITION_TICK;
        final Set<TerminalHold> terminalHolds=
            Collections.newSetFromMap(
                new IdentityHashMap<>()
            );

        Entry(WorldEventDefinition definition){
            this.definition=definition;
        }

        Snapshot snapshot(){
            return new Snapshot(this);
        }
    }

    private final LinkedHashMap<WorldEventId,Entry> entries=
        new LinkedHashMap<>();

    private long lastObservedTick=-1L;

    interface EventCompositionAction {
        void run() throws Exception;
    }

    /**
     * Holds the GlobalEventService mutation monitor for one caller-owned
     * composition action. The event entry is required to exist before the
     * action begins, but mutable Entry state is never exposed.
     *
     * Service methods are synchronized/reentrant, so the action may call
     * get/tick/complete/cancel while retaining this ownership boundary.
     */
    synchronized void withEventCompositionOwnership(
        WorldEventId eventId,
        EventCompositionAction action
    )throws Exception{
        require(
            Objects.requireNonNull(
                eventId,
                "eventId"
            )
        );
        Objects.requireNonNull(
            action,
            "action"
        ).run();
    }

    /**
     * Atomically publishes one new GlobalEvent together with caller-owned
     * consumer state. The event is visible reentrantly to the action while the
     * GlobalEventService monitor remains held, but no competing lifecycle
     * observer/mutator can cross the registration boundary.
     *
     * If caller publication fails, the exact newly-created event is removed
     * before releasing this monitor so no orphan GlobalEvent remains.
     */
    synchronized Snapshot registerWithCompositionOwnership(
        WorldEventDefinition definition,
        EventCompositionAction action
    )throws Exception{
        WorldEventDefinition checked=
            Objects.requireNonNull(
                definition,
                "definition"
            );
        EventCompositionAction checkedAction=
            Objects.requireNonNull(
                action,
                "action"
            );

        if(entries.containsKey(
                checked.id))
            throw new IllegalStateException(
                "duplicate world event id="+
                checked.id
            );

        Entry entry=
            new Entry(
                checked
            );

        entries.put(
            checked.id,
            entry
        );

        try{
            checkedAction.run();
            return entry.snapshot();
        }catch(RuntimeException failure){
            rollbackRegistration(
                entry,
                failure
            );
            throw failure;
        }catch(Error failure){
            rollbackRegistration(
                entry,
                failure
            );
            throw failure;
        }catch(Exception failure){
            rollbackRegistration(
                entry,
                failure
            );
            throw failure;
        }
    }

    private void rollbackRegistration(
        Entry entry,
        Throwable failure
    ){
        if(entries.remove(
                entry.definition.id,
                entry))
            return;

        failure.addSuppressed(
            new IllegalStateException(
                "GlobalEvent registration rollback ownership lost id="+
                entry.definition.id
            )
        );
    }

    synchronized TerminalHold acquireTerminalHold(
        WorldEventId eventId,
        String ownerRef
    ){
        Entry entry=
            require(
                Objects.requireNonNull(
                    eventId,
                    "eventId"
                )
            );

        if(entry.lifecycle!=Lifecycle.ACTIVE)
            throw new IllegalStateException(
                "terminal hold requires ACTIVE event "+
                eventId+
                " lifecycle="+
                entry.lifecycle
            );

        TerminalHold hold=
            new TerminalHold(
                this,
                entry.definition.id,
                ownerRef
            );

        if(!entry.terminalHolds.add(hold))
            throw new IllegalStateException(
                "terminal hold identity collision event="+
                eventId
            );

        return hold;
    }

    synchronized void releaseTerminalHold(
        TerminalHold hold
    ){
        TerminalHold checked=
            Objects.requireNonNull(
                hold,
                "hold"
            );

        if(checked.owner!=this)
            throw new IllegalStateException(
                "foreign GlobalEvent terminal hold"
            );

        Entry entry=
            require(
                checked.eventId
            );

        if(!entry.terminalHolds.remove(
                checked))
            throw new IllegalStateException(
                "missing/released terminal hold event="+
                checked.eventId
            );
    }

    synchronized int terminalHoldCount(
        WorldEventId eventId
    ){
        return require(
            Objects.requireNonNull(
                eventId,
                "eventId"
            )
        ).terminalHolds.size();
    }

    synchronized Snapshot register(
        WorldEventDefinition definition
    ){
        Objects.requireNonNull(definition,"definition");

        if(entries.containsKey(definition.id))
            throw new IllegalStateException(
                "duplicate world event id="+definition.id
            );

        Entry entry=new Entry(definition);
        entries.put(definition.id,entry);
        return entry.snapshot();
    }

    synchronized Snapshot get(WorldEventId id){
        Entry entry=entries.get(
            Objects.requireNonNull(id,"id")
        );
        return entry==null?null:entry.snapshot();
    }

    synchronized Snapshot get(String id){
        return get(WorldEventId.of(id));
    }

    synchronized int size(){
        return entries.size();
    }

    synchronized long lastObservedTick(){
        return lastObservedTick;
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<Entry> ordered=new ArrayList<>(
            entries.values()
        );

        ordered.sort(
            Comparator.comparing(
                entry->entry.definition.id
            )
        );

        ArrayList<Snapshot> out=new ArrayList<>();

        for(Entry entry:ordered)
            out.add(entry.snapshot());

        return Collections.unmodifiableList(out);
    }

    synchronized TickResult tick(long worldTick){
        observeTick(worldTick);

        ArrayList<Entry> ordered=new ArrayList<>(
            entries.values()
        );

        ordered.sort(
            Comparator.comparing(
                entry->entry.definition.id
            )
        );

        ArrayList<Change> changes=new ArrayList<>();

        for(Entry entry:ordered)
            advance(entry,worldTick,changes);

        return new TickResult(worldTick,changes);
    }

    synchronized MutationResult cancel(
        WorldEventId id,
        long worldTick
    ){
        Entry entry=require(id);

        if(entry.lifecycle!=Lifecycle.CANCELLED&&
           entry.lifecycle!=Lifecycle.COMPLETED)
            requireNoTerminalHolds(
                entry,
                "cancel"
            );

        observeTick(worldTick);

        if(entry.lifecycle==Lifecycle.CANCELLED)
            return new MutationResult(
                entry.snapshot(),
                Collections.emptyList()
            );

        if(entry.lifecycle==Lifecycle.COMPLETED)
            throw new IllegalStateException(
                "cannot cancel completed event "+id
            );

        ArrayList<Change> changes=new ArrayList<>();
        advance(entry,worldTick,changes);

        if(entry.lifecycle==Lifecycle.COMPLETED)
            throw new IllegalStateException(
                "cannot cancel event after end deadline "+id
            );

        Snapshot before=entry.snapshot();
        entry.lifecycle=Lifecycle.CANCELLED;
        entry.lastTransitionTick=worldTick;
        changes.add(
            new Change(
                before,
                entry.snapshot(),
                ChangeCause.EXPLICIT_CANCEL
            )
        );

        return new MutationResult(
            entry.snapshot(),
            changes
        );
    }

    synchronized MutationResult cancel(
        String id,
        long worldTick
    ){
        return cancel(
            WorldEventId.of(id),
            worldTick
        );
    }

    synchronized MutationResult complete(
        WorldEventId id,
        long worldTick
    ){
        Entry entry=require(id);

        if(entry.lifecycle!=Lifecycle.COMPLETED&&
           entry.lifecycle!=Lifecycle.CANCELLED)
            requireNoTerminalHolds(
                entry,
                "complete"
            );

        observeTick(worldTick);

        if(entry.lifecycle==Lifecycle.COMPLETED)
            return new MutationResult(
                entry.snapshot(),
                Collections.emptyList()
            );

        if(entry.lifecycle==Lifecycle.CANCELLED)
            throw new IllegalStateException(
                "cannot complete cancelled event "+id
            );

        ArrayList<Change> changes=new ArrayList<>();
        advance(entry,worldTick,changes);

        if(entry.lifecycle==Lifecycle.COMPLETED)
            return new MutationResult(
                entry.snapshot(),
                changes
            );

        if(entry.lifecycle!=Lifecycle.ACTIVE)
            throw new IllegalStateException(
                "cannot complete non-active event "+id
            );

        Snapshot before=entry.snapshot();
        entry.lifecycle=Lifecycle.COMPLETED;
        entry.lastTransitionTick=worldTick;
        changes.add(
            new Change(
                before,
                entry.snapshot(),
                ChangeCause.EXPLICIT_COMPLETE
            )
        );

        return new MutationResult(
            entry.snapshot(),
            changes
        );
    }

    synchronized MutationResult complete(
        String id,
        long worldTick
    ){
        return complete(
            WorldEventId.of(id),
            worldTick
        );
    }

    private static void requireNoTerminalHolds(
        Entry entry,
        String operation
    ){
        if(!entry.terminalHolds.isEmpty())
            throw new IllegalStateException(
                operation+
                " blocked by terminal holds event="+
                entry.definition.id+
                " holds="+
                entry.terminalHolds
            );
    }

    private static String requireTerminalHoldLabel(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "ownerRef"
            );

        String normalized=
            value.trim();

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "ownerRef blank"
            );

        return normalized;
    }

    private Entry require(WorldEventId id){
        WorldEventId key=Objects.requireNonNull(id,"id");
        Entry entry=entries.get(key);

        if(entry==null)
            throw new IllegalArgumentException(
                "unknown world event id="+key
            );

        return entry;
    }

    private void observeTick(long worldTick){
        if(worldTick<0)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );

        if(worldTick<lastObservedTick)
            throw new IllegalArgumentException(
                "world tick moved backwards "+
                worldTick+" < "+lastObservedTick
            );

        lastObservedTick=worldTick;
    }

    private static void advance(
        Entry entry,
        long worldTick,
        List<Change> changes
    ){
        if(entry.lifecycle==Lifecycle.CANCELLED||
           entry.lifecycle==Lifecycle.COMPLETED)
            return;

        WorldEventDefinition definition=
            entry.definition;

        if(entry.lifecycle==Lifecycle.SCHEDULED&&
           worldTick>=definition.startTick){
            Snapshot before=entry.snapshot();

            entry.lifecycle=Lifecycle.ACTIVE;
            entry.phaseIndex=
                definition.phaseIndexAt(
                    definition.startTick
                );
            entry.lastTransitionTick=
                definition.startTick;

            changes.add(
                new Change(
                    before,
                    entry.snapshot(),
                    ChangeCause.START_DEADLINE
                )
            );
        }

        if(entry.lifecycle==Lifecycle.ACTIVE){
            int next=entry.phaseIndex+1;

            while(next<definition.phases.size()){
                WorldEventDefinition.PhaseDefinition phase=
                    definition.phases.get(next);

                if(phase.startsAtTick>worldTick)
                    break;

                Snapshot before=entry.snapshot();
                entry.phaseIndex=next;
                entry.lastTransitionTick=
                    phase.startsAtTick;

                changes.add(
                    new Change(
                        before,
                        entry.snapshot(),
                        ChangeCause.PHASE_DEADLINE
                    )
                );

                next++;
            }

            if(worldTick>=definition.endTick&&
               entry.terminalHolds.isEmpty()){
                Snapshot before=entry.snapshot();
                entry.lifecycle=Lifecycle.COMPLETED;
                entry.lastTransitionTick=
                    definition.endTick;

                changes.add(
                    new Change(
                        before,
                        entry.snapshot(),
                        ChangeCause.END_DEADLINE
                    )
                );
            }
        }
    }

    private static List<Change> immutableChanges(
        List<Change> changes
    ){
        return Collections.unmodifiableList(
            new ArrayList<>(changes)
        );
    }
}

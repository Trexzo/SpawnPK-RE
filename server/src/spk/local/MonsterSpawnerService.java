package spk.local;

import java.util.*;

/**
 * Protocol-independent Monster Spawner gameplay service.
 *
 * The exact client proves a 22-row selectable presentation and a toggle action,
 * but original row mapping, activation count and spawn policy remain unknown.
 * This service therefore uses caller-defined catalog + budget policy and creates
 * ordinary canonical WorldNpc entities without reusing pet/item ownership fields.
 */
final class MonsterSpawnerService {
    static final int CLIENT_ROW_COUNT=22;

    static final class CatalogEntry {
        final int rowIndex;
        final String semanticKey;
        final int definitionId;

        CatalogEntry(
            int rowIndex,
            String semanticKey,
            int definitionId
        ){
            if(rowIndex<0||
               rowIndex>=CLIENT_ROW_COUNT)
                throw new IllegalArgumentException(
                    "Monster Spawner rowIndex="+
                    rowIndex
                );

            this.rowIndex=rowIndex;
            this.semanticKey=
                MatchRules.normalizeKey(
                    semanticKey,
                    "semanticKey"
                );

            // Reuse canonical NPC validation without assigning owner/item state.
            WorldNpc.validateSpawnParameters(
                definitionId,
                0,
                -1
            );

            this.definitionId=
                definitionId;
        }
    }

    static final class CatalogSnapshot {
        final String sourceAuthority;
        final List<CatalogEntry> entries;

        CatalogSnapshot(
            String sourceAuthority,
            Collection<CatalogEntry> entries
        ){
            this.sourceAuthority=
                sourceAuthority;
            this.entries=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        entries
                    )
                );
        }

        CatalogEntry row(int rowIndex){
            for(CatalogEntry entry:entries)
                if(entry.rowIndex==rowIndex)
                    return entry;

            return null;
        }
    }

    static final class SessionSnapshot {
        final String ownerRef;
        final String policyAuthority;
        final Integer selectedRowIndex;
        final String selectedSemanticKey;
        final Integer selectedDefinitionId;
        final boolean active;
        final int remainingSpawnBudget;
        final List<EntityId> spawnedNpcIds;

        SessionSnapshot(
            Session session,
            Map<Integer,CatalogEntry> catalog
        ){
            this.ownerRef=session.ownerRef;
            this.policyAuthority=
                session.policyAuthority;
            this.selectedRowIndex=
                session.selectedRowIndex;

            CatalogEntry selected=
                session.selectedRowIndex==null
                    ?null
                    :catalog.get(
                        session.selectedRowIndex
                    );

            this.selectedSemanticKey=
                selected==null
                    ?null
                    :selected.semanticKey;
            this.selectedDefinitionId=
                selected==null
                    ?null
                    :selected.definitionId;
            this.active=session.active;
            this.remainingSpawnBudget=
                session.remainingSpawnBudget;
            this.spawnedNpcIds=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        session.spawnedNpcs.keySet()
                    )
                );
        }

        boolean hasSelection(){
            return selectedRowIndex!=null;
        }

        boolean tracks(EntityId npcId){
            return spawnedNpcIds.contains(
                Objects.requireNonNull(
                    npcId,
                    "npcId"
                )
            );
        }
    }

    static final class SpawnResult {
        final WorldNpc npc;
        final SessionSnapshot session;

        SpawnResult(
            WorldNpc npc,
            SessionSnapshot session
        ){
            this.npc=
                Objects.requireNonNull(
                    npc,
                    "npc"
                );
            this.session=
                Objects.requireNonNull(
                    session,
                    "session"
                );
        }
    }

    interface SpawnedNpcCommitAction {
        void run(WorldNpc npc) throws Exception;
    }

    interface TrackedNpcDespawnCommit {
        SessionSnapshot commit() throws Exception;
    }

    interface TrackedNpcDespawnAction {
        SessionSnapshot run(
            WorldNpc npc,
            TrackedNpcDespawnCommit commit
        ) throws Exception;
    }

    private static final class Session {
        final String ownerRef;
        final String policyAuthority;
        final LinkedHashMap<EntityId,WorldNpc>
            spawnedNpcs=
                new LinkedHashMap<>();

        Integer selectedRowIndex;
        boolean active;
        int remainingSpawnBudget;

        Session(
            String ownerRef,
            String policyAuthority
        ){
            this.ownerRef=ownerRef;
            this.policyAuthority=
                policyAuthority;
        }
    }

    private final WorldNpcRegistry npcs;

    private LinkedHashMap<Integer,CatalogEntry>
        catalog=
            new LinkedHashMap<>();

    private String catalogAuthority=
        "UNCONFIGURED";

    private final LinkedHashMap<String,Session>
        sessions=
            new LinkedHashMap<>();

    MonsterSpawnerService(
        WorldNpcRegistry npcs
    ){
        this.npcs=
            Objects.requireNonNull(
                npcs,
                "npcs"
            );
    }

    boolean isBoundTo(
        WorldNpcRegistry expectedRegistry
    ){
        return npcs==
            Objects.requireNonNull(
                expectedRegistry,
                "expectedRegistry"
            );
    }

    synchronized CatalogSnapshot replaceCatalog(
        Collection<CatalogEntry> entries,
        String sourceAuthority
    ){
        Objects.requireNonNull(
            entries,
            "entries"
        );

        String authority=
            MatchRules.requireText(
                sourceAuthority,
                "sourceAuthority"
            );

        TreeMap<Integer,CatalogEntry> byRow=
            new TreeMap<>();
        HashSet<String> semanticKeys=
            new HashSet<>();

        for(CatalogEntry entry:entries){
            CatalogEntry checked=
                Objects.requireNonNull(
                    entry,
                    "catalog entry"
                );

            if(byRow.put(
                    checked.rowIndex,
                    checked)!=null)
                throw new IllegalArgumentException(
                    "duplicate Monster Spawner row "+
                    checked.rowIndex
                );

            if(!semanticKeys.add(
                    checked.semanticKey))
                throw new IllegalArgumentException(
                    "duplicate Monster Spawner semantic key "+
                    checked.semanticKey
                );
        }

        // Do not silently invalidate or retarget live selections.
        for(Session session:
                sessions.values()){
            if(session.selectedRowIndex==null)
                continue;

            CatalogEntry current=
                catalog.get(
                    session.selectedRowIndex
                );
            CatalogEntry replacementEntry=
                byRow.get(
                    session.selectedRowIndex
                );

            if(current==null||
               replacementEntry==null)
                throw new IllegalStateException(
                    "catalog replacement removes selected row "+
                    session.selectedRowIndex+
                    " owner="+session.ownerRef
                );

            if(current.definitionId!=
                    replacementEntry.definitionId||
               !current.semanticKey.equals(
                    replacementEntry.semanticKey))
                throw new IllegalStateException(
                    "catalog replacement changes selected row identity "+
                    session.selectedRowIndex+
                    " owner="+session.ownerRef+
                    " current="+current.semanticKey+
                    "/"+current.definitionId+
                    " replacement="+
                    replacementEntry.semanticKey+
                    "/"+replacementEntry.definitionId
                );
        }

        LinkedHashMap<Integer,CatalogEntry>
            replacement=
                new LinkedHashMap<>();

        for(Map.Entry<Integer,CatalogEntry> entry:
                byRow.entrySet())
            replacement.put(
                entry.getKey(),
                entry.getValue()
            );

        catalog=replacement;
        catalogAuthority=authority;

        return catalogSnapshot();
    }

    synchronized CatalogSnapshot catalog(){
        return catalogSnapshot();
    }

    synchronized SessionSnapshot openSession(
        String ownerRef,
        String policyAuthority
    ){
        String owner=
            PartyService.requireRef(
                ownerRef
            );

        if(sessions.containsKey(owner))
            throw new IllegalStateException(
                "Monster Spawner session already exists "+
                owner
            );

        Session session=
            new Session(
                owner,
                MatchRules.requireText(
                    policyAuthority,
                    "policyAuthority"
                )
            );

        sessions.put(
            owner,
            session
        );

        return snapshotOf(session);
    }

    synchronized SessionSnapshot selectRow(
        String ownerRef,
        int rowIndex
    ){
        Session session=
            requireSession(ownerRef);

        if(session.active)
            throw new IllegalStateException(
                "cannot change Monster Spawner selection while active"
            );

        CatalogEntry entry=
            catalog.get(rowIndex);

        if(entry==null)
            throw new IllegalArgumentException(
                "unconfigured Monster Spawner row "+
                rowIndex
            );

        session.selectedRowIndex=
            entry.rowIndex;

        return snapshotOf(session);
    }

    synchronized SessionSnapshot selectRowIfCurrent(
        String ownerRef,
        SessionSnapshot expectedSession,
        String expectedCatalogAuthority,
        CatalogEntry expectedEntry
    ){
        SessionSnapshot checkedSession=
            Objects.requireNonNull(
                expectedSession,
                "expectedSession"
            );
        String checkedAuthority=
            MatchRules.requireText(
                expectedCatalogAuthority,
                "expectedCatalogAuthority"
            );
        CatalogEntry checkedEntry=
            Objects.requireNonNull(
                expectedEntry,
                "expectedEntry"
            );

        Session session=
            requireSession(
                ownerRef
            );

        if(!session.ownerRef.equals(
                checkedSession.ownerRef))
            throw new IllegalArgumentException(
                "Monster Spawner expected session owner mismatch expected="+
                checkedSession.ownerRef+
                " actual="+session.ownerRef
            );

        SessionSnapshot current=
            snapshotOf(
                session
            );

        if(!sameSessionState(
                current,
                checkedSession))
            throw new IllegalStateException(
                "Monster Spawner session changed before row selection owner="+
                session.ownerRef
            );

        if(current.active)
            throw new IllegalStateException(
                "cannot change Monster Spawner selection while active"
            );

        if(!catalogAuthority.equals(
                checkedAuthority))
            throw new IllegalStateException(
                "Monster Spawner catalog authority changed before row selection expected="+
                checkedAuthority+
                " actual="+catalogAuthority
            );

        CatalogEntry currentEntry=
            catalog.get(
                checkedEntry.rowIndex
            );

        if(currentEntry==null||
           currentEntry.definitionId!=
                checkedEntry.definitionId||
           !currentEntry.semanticKey.equals(
                checkedEntry.semanticKey
           ))
            throw new IllegalStateException(
                "Monster Spawner catalog row changed before selection row="+
                checkedEntry.rowIndex
            );

        session.selectedRowIndex=
            currentEntry.rowIndex;

        return snapshotOf(
            session
        );
    }

    synchronized SessionSnapshot activate(
        String ownerRef,
        int spawnBudget
    ){
        Session session=
            requireSession(ownerRef);

        if(session.active)
            throw new IllegalStateException(
                "Monster Spawner already active "+
                session.ownerRef
            );

        if(session.selectedRowIndex==null)
            throw new IllegalStateException(
                "Monster Spawner has no selected row "+
                session.ownerRef
            );

        if(!catalog.containsKey(
                session.selectedRowIndex))
            throw new IllegalStateException(
                "selected Monster Spawner row disappeared "+
                session.selectedRowIndex
            );

        if(spawnBudget<=0)
            throw new IllegalArgumentException(
                "spawnBudget="+spawnBudget
            );

        session.remainingSpawnBudget=
            spawnBudget;
        session.active=true;

        return snapshotOf(session);
    }

    synchronized SessionSnapshot activateIfCurrent(
        String ownerRef,
        SessionSnapshot expected,
        int spawnBudget
    ){
        SessionSnapshot checked=
            Objects.requireNonNull(
                expected,
                "expected"
            );

        Session session=
            requireSession(
                ownerRef
            );

        if(!session.ownerRef.equals(
                checked.ownerRef))
            throw new IllegalArgumentException(
                "Monster Spawner expected session owner mismatch expected="+
                checked.ownerRef+
                " actual="+session.ownerRef
            );

        SessionSnapshot current=
            snapshotOf(
                session
            );

        if(!sameSessionState(
                current,
                checked))
            throw new IllegalStateException(
                "Monster Spawner session changed before activation owner="+
                session.ownerRef
            );

        if(current.active)
            throw new IllegalStateException(
                "Monster Spawner already active "+
                session.ownerRef
            );

        if(current.selectedRowIndex==null)
            throw new IllegalStateException(
                "Monster Spawner has no selected row "+
                session.ownerRef
            );

        if(spawnBudget<=0)
            throw new IllegalArgumentException(
                "spawnBudget="+spawnBudget
            );

        session.remainingSpawnBudget=
            spawnBudget;
        session.active=true;

        return snapshotOf(
            session
        );
    }

    synchronized SessionSnapshot deactivate(
        String ownerRef
    ){
        Session session=
            requireSession(ownerRef);

        if(!session.active)
            throw new IllegalStateException(
                "Monster Spawner not active "+
                session.ownerRef
            );

        session.active=false;
        session.remainingSpawnBudget=0;

        return snapshotOf(session);
    }

    synchronized SessionSnapshot deactivateIfCurrent(
        String ownerRef,
        SessionSnapshot expected
    ){
        SessionSnapshot checked=
            Objects.requireNonNull(
                expected,
                "expected"
            );

        Session session=
            requireSession(
                ownerRef
            );

        SessionSnapshot current=
            snapshotOf(
                session
            );

        if(!sameSessionState(
                current,
                checked))
            throw new IllegalStateException(
                "Monster Spawner session changed before deactivation owner="+
                session.ownerRef
            );

        if(!current.active)
            throw new IllegalStateException(
                "Monster Spawner not active "+
                session.ownerRef
            );

        session.active=false;
        session.remainingSpawnBudget=0;

        return snapshotOf(
            session
        );
    }

    synchronized SpawnResult spawnSelected(
        String ownerRef,
        int x,
        int y,
        int plane
    ){
        try{
            return spawnSelectedComposed(
                ownerRef,
                x,
                y,
                plane,
                npc->{}
            );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "unexpected Monster Spawner composed spawn failure",
                failure
            );
        }
    }

    synchronized SpawnResult spawnSelectedComposed(
        String ownerRef,
        int x,
        int y,
        int plane,
        SpawnedNpcCommitAction action
    )throws Exception{
        SpawnedNpcCommitAction checkedAction=
            Objects.requireNonNull(
                action,
                "action"
            );

        Session session=
            requireSession(ownerRef);

        if(!session.active||
           session.remainingSpawnBudget<=0)
            throw new IllegalStateException(
                "Monster Spawner not spawn-ready "+
                session.ownerRef
            );

        CatalogEntry selected=
            catalog.get(
                session.selectedRowIndex
            );

        if(selected==null)
            throw new IllegalStateException(
                "selected Monster Spawner row unavailable "+
                session.selectedRowIndex
            );

        final int budgetBefore=
            session.remainingSpawnBudget;
        final boolean activeBefore=
            session.active;
        final SessionSnapshot[] committed=
            new SessionSnapshot[1];

        final WorldNpc npc;

        try{
            npc=
                npcs.spawnWithMutationOwnership(
                    selected.definitionId,
                    x,
                    y,
                    plane,
                    spawned->{
                        if(session.spawnedNpcs.put(
                                spawned.id,
                                spawned)!=null)
                            throw new IllegalStateException(
                                "duplicate tracked Monster Spawner NPC "+
                                spawned.id
                            );

                        session.remainingSpawnBudget--;

                        if(session.remainingSpawnBudget==0)
                            session.active=false;

                        committed[0]=
                            snapshotOf(session);
                    }
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "unexpected Monster Spawner spawn ownership failure",
                failure
            );
        }

        try{
            checkedAction.run(
                npc
            );
        }catch(Throwable failure){
            Throwable rollbackFailure=
                rollbackFreshSpawn(
                    session,
                    npc,
                    budgetBefore,
                    activeBefore
                );

            if(rollbackFailure!=null&&
               rollbackFailure!=failure)
                failure.addSuppressed(
                    rollbackFailure
                );

            rethrow(
                failure
            );
        }

        return new SpawnResult(
            npc,
            Objects.requireNonNull(
                committed[0],
                "committed session snapshot"
            )
        );
    }

    private Throwable rollbackFreshSpawn(
        Session session,
        WorldNpc npc,
        int budgetBefore,
        boolean activeBefore
    ){
        try{
            boolean owned=
                npcs.withCurrentMutationOwnershipIfCurrent(
                    npc,
                    ()->{
                        WorldNpc tracked=
                            session.spawnedNpcs.get(
                                npc.id
                            );

                        if(tracked!=npc)
                            throw new IllegalStateException(
                                "Monster Spawner rollback tracked identity drifted "+
                                npc.id
                            );

                        if(!npcs.remove(
                                npc.id))
                            throw new IllegalStateException(
                                "Monster Spawner rollback canonical removal failed "+
                                npc.id
                            );

                        WorldNpc removed=
                            session.spawnedNpcs.remove(
                                npc.id
                            );

                        if(removed!=npc)
                            throw new IllegalStateException(
                                "Monster Spawner rollback tracked removal drifted "+
                                npc.id
                            );

                        session.remainingSpawnBudget=
                            budgetBefore;
                        session.active=
                            activeBefore;
                    }
                );

            if(!owned)
                throw new IllegalStateException(
                    "Monster Spawner rollback lost canonical NPC ownership "+
                    npc.id
                );

            return null;
        }catch(Throwable failure){
            return failure;
        }
    }

    private static void rethrow(
        Throwable failure
    )throws Exception{
        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;
        if(failure instanceof Error)
            throw (Error)failure;
        if(failure instanceof Exception)
            throw (Exception)failure;

        throw new RuntimeException(
            failure
        );
    }

    synchronized SessionSnapshot despawnTracked(
        String ownerRef,
        EntityId npcId
    ){
        try{
            return despawnTrackedComposed(
                ownerRef,
                npcId,
                (npc,commit)->
                    commit.commit()
            );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Error failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "unexpected Monster Spawner composed despawn failure",
                failure
            );
        }
    }

    synchronized SessionSnapshot despawnTrackedComposed(
        String ownerRef,
        EntityId npcId,
        TrackedNpcDespawnAction action
    )throws Exception{
        Session session=
            requireSession(ownerRef);
        EntityId id=
            Objects.requireNonNull(
                npcId,
                "npcId"
            );
        TrackedNpcDespawnAction checkedAction=
            Objects.requireNonNull(
                action,
                "action"
            );

        WorldNpc tracked=
            session.spawnedNpcs.get(id);

        if(tracked==null)
            throw new IllegalArgumentException(
                "NPC not tracked by Monster Spawner session "+
                session.ownerRef+
                " id="+id
            );

        final boolean[] committed={false};
        final SessionSnapshot[] result={null};

        TrackedNpcDespawnCommit commit=
            ()->{
                if(committed[0])
                    throw new IllegalStateException(
                        "Monster Spawner despawn commit already used "+
                        id
                    );

                final SessionSnapshot[] committedSnapshot=
                    new SessionSnapshot[1];

                boolean owned=
                    npcs.withCurrentMutationOwnershipIfCurrent(
                        tracked,
                        ()->{
                            WorldNpc current=
                                session.spawnedNpcs.get(
                                    id
                                );

                            if(current!=tracked)
                                throw new IllegalStateException(
                                    "Monster Spawner tracked NPC identity drifted "+
                                    id
                                );

                            /*
                             * Prevalidate everything that can fail before the
                             * two terminal mutations. Under the service monitor
                             * the tracked map cannot change until both complete.
                             */
                            if(npcs.byId(id)!=tracked)
                                throw new IllegalStateException(
                                    "Monster Spawner canonical NPC identity drifted "+
                                    id
                                );

                            if(!npcs.remove(id))
                                throw new IllegalStateException(
                                    "tracked Monster Spawner NPC disappeared during owned removal "+
                                    id
                                );

                            session.spawnedNpcs.remove(
                                id
                            );

                            committedSnapshot[0]=
                                snapshotOf(session);
                        }
                    );

                if(!owned)
                    throw new IllegalStateException(
                        "tracked Monster Spawner NPC is no longer exact canonical registry owner "+
                        id
                    );

                committed[0]=true;
                result[0]=
                    Objects.requireNonNull(
                        committedSnapshot[0],
                        "despawn committed snapshot"
                    );

                return result[0];
            };

        SessionSnapshot actionResult=
            checkedAction.run(
                tracked,
                commit
            );

        if(!committed[0])
            throw new IllegalStateException(
                "Monster Spawner despawn action returned without canonical commit "+
                id
            );

        if(actionResult==null)
            actionResult=result[0];

        return Objects.requireNonNull(
            actionResult,
            "despawn action result"
        );
    }

    synchronized SessionSnapshot getSession(
        String ownerRef
    ){
        Session session=
            sessions.get(
                PartyService.requireRef(
                    ownerRef
                )
            );

        return session==null
            ?null
            :snapshotOf(session);
    }

    synchronized int sessionCount(){
        return sessions.size();
    }

    private static boolean sameSessionState(
        SessionSnapshot left,
        SessionSnapshot right
    ){
        return left.ownerRef.equals(
                right.ownerRef
            )&&
            left.policyAuthority.equals(
                right.policyAuthority
            )&&
            Objects.equals(
                left.selectedRowIndex,
                right.selectedRowIndex
            )&&
            Objects.equals(
                left.selectedSemanticKey,
                right.selectedSemanticKey
            )&&
            Objects.equals(
                left.selectedDefinitionId,
                right.selectedDefinitionId
            )&&
            left.active==right.active&&
            left.remainingSpawnBudget==
                right.remainingSpawnBudget&&
            left.spawnedNpcIds.equals(
                right.spawnedNpcIds
            );
    }

    private CatalogSnapshot catalogSnapshot(){
        return new CatalogSnapshot(
            catalogAuthority,
            catalog.values()
        );
    }

    private SessionSnapshot snapshotOf(
        Session session
    ){
        return new SessionSnapshot(
            session,
            catalog
        );
    }

    private Session requireSession(
        String ownerRef
    ){
        String owner=
            PartyService.requireRef(
                ownerRef
            );

        Session session=
            sessions.get(owner);

        if(session==null)
            throw new IllegalArgumentException(
                "unknown Monster Spawner session "+
                owner
            );

        return session;
    }
}

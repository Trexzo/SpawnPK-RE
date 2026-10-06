package spk.local;

import java.util.*;

/**
 * World-owned LocalLab composition for the first playable Blood Slayer slice.
 *
 * Exact-current client authority proves the Blood Slayer mode vocabulary.
 * Task target, goal, assignment and progression policy below are explicitly
 * LocalLab-owned and must not be represented as recovered SpawnPK balance.
 */
final class LocalLabSlayerRuntime {
    static final String LEGACY_AUTHORITY=
        "LOCAL_LAB_POLICY_G4_BLOOD_SLAYER_MONSTER_HUNTER_V1";
    static final String AUTHORITY=
        "LOCAL_LAB_POLICY_G6_BLOOD_SLAYER_PVM_V2";
    static final String TASK_KEY=
        "locallab:blood-slayer:monster-hunter:definition-1";
    static final String BOSS_TASK_KEY=
        "locallab:blood-slayer:boss-hunter:region-16168-definition-1";
    static final String FAMILY_KEY=
        "blood-slayer";
    static final String TARGET_KEY=
        "npc-definition:1";
    static final String OBJECTIVE_KEY=
        "locallab:blood-slayer:monster-hunter:definition-1:kills";
    static final String BOSS_OBJECTIVE_KEY=
        "locallab:blood-slayer:boss-hunter:region-16168-definition-1:kills";
    static final int TARGET_DEFINITION_ID=1;
    static final int BOSS_REGION_ID=16168;
    static final long OBJECTIVE_GOAL=1L;

    static final class StatusSnapshot {
        final String playerRef;
        final BloodSlayerModeService.Mode selectedMode;
        final SlayerTaskService.Snapshot task;
        final long completions;
        final String authority;
        final String persistenceError;

        StatusSnapshot(
            String playerRef,
            BloodSlayerModeService.Mode selectedMode,
            SlayerTaskService.Snapshot task
        ){
            this(
                playerRef,
                selectedMode,
                task,
                0L,
                null
            );
        }

        StatusSnapshot(
            String playerRef,
            BloodSlayerModeService.Mode selectedMode,
            SlayerTaskService.Snapshot task,
            String persistenceError
        ){
            this(
                playerRef,
                selectedMode,
                task,
                0L,
                persistenceError
            );
        }

        StatusSnapshot(
            String playerRef,
            BloodSlayerModeService.Mode selectedMode,
            SlayerTaskService.Snapshot task,
            long completions,
            String persistenceError
        ){
            if(completions<0L)
                throw new IllegalArgumentException(
                    "Blood Slayer completions="+
                    completions
                );

            this.playerRef=playerRef;
            this.selectedMode=selectedMode;
            this.task=task;
            this.completions=completions;
            this.authority=AUTHORITY;
            this.persistenceError=persistenceError;
        }

        boolean persistenceValid(){
            return persistenceError==null;
        }

        boolean hasTask(){
            return task!=null;
        }

        boolean active(){
            return task!=null&&
                task.state==SlayerTaskService.State.ACTIVE;
        }

        boolean complete(){
            return task!=null&&
                task.state==SlayerTaskService.State.COMPLETED;
        }
    }

    static final class StartResult {
        final boolean created;
        final StatusSnapshot status;

        StartResult(
            boolean created,
            StatusSnapshot status
        ){
            this.created=created;
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
        }
    }

    static final class KillCreditResult {
        final boolean eligibleDefinition;
        final boolean activeTask;
        final boolean progressed;
        final boolean completedNow;
        final StatusSnapshot status;

        KillCreditResult(
            boolean eligibleDefinition,
            boolean activeTask,
            boolean progressed,
            boolean completedNow,
            StatusSnapshot status
        ){
            this.eligibleDefinition=eligibleDefinition;
            this.activeTask=activeTask;
            this.progressed=progressed;
            this.completedNow=completedNow;
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
        }
    }

    private final World world;
    private final LinkedHashMap<String,ObjectiveProgressService>
        ledgers=new LinkedHashMap<>();
    private final LinkedHashMap<String,SlayerTaskService.TaskId>
        latestTaskByPlayer=new LinkedHashMap<>();
    private final LinkedHashMap<String,Long>
        completionCountByPlayer=new LinkedHashMap<>();
    private final Set<WorldPlayer> hydratedPlayers=
        Collections.newSetFromMap(
            new IdentityHashMap<WorldPlayer,Boolean>()
        );
    private final IdentityHashMap<WorldPlayer,String>
        invalidPersistence=
            new IdentityHashMap<>();
    private final SlayerTaskService slayer;
    private final BloodSlayerModeService bloodSlayer;

    LocalLabSlayerRuntime(
        World world
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );

        slayer=
            new SlayerTaskService(
                this::ledgerIfPresent
            );

        bloodSlayer=
            new BloodSlayerModeService(
                slayer,
                (playerRef,mode)->{
                    if(mode==
                            BloodSlayerModeService.Mode
                                .MONSTER_HUNTER_PVM)
                        return TASK_KEY;
                    if(mode==
                            BloodSlayerModeService.Mode
                                .BOSS_HUNTER_PVM)
                        return BOSS_TASK_KEY;
                    throw new IllegalArgumentException(
                        "LocalLab Blood Slayer allocator does not own mode "+
                        mode
                    );
                },
                AUTHORITY
            );

        bloodSlayer.registerTaskDefinition(
            new SlayerTaskService.Definition(
                TASK_KEY,
                FAMILY_KEY,
                TARGET_KEY,
                OBJECTIVE_KEY,
                AUTHORITY
            ),
            Collections.singletonList(
                BloodSlayerModeService.Mode
                    .MONSTER_HUNTER_PVM
            )
        );

        bloodSlayer.registerTaskDefinition(
            new SlayerTaskService.Definition(
                BOSS_TASK_KEY,
                FAMILY_KEY,
                TARGET_KEY,
                BOSS_OBJECTIVE_KEY,
                AUTHORITY
            ),
            Collections.singletonList(
                BloodSlayerModeService.Mode
                    .BOSS_HUNTER_PVM
            )
        );
    }

    StartResult startMonsterHunter(
        String playerRef,
        long worldTick
    ){
        return startMode(
            playerRef,
            BloodSlayerModeService.Mode
                .MONSTER_HUNTER_PVM,
            worldTick
        );
    }

    StartResult startBossHunter(
        String playerRef,
        long worldTick
    ){
        return startMode(
            playerRef,
            BloodSlayerModeService.Mode
                .BOSS_HUNTER_PVM,
            worldTick
        );
    }

    private StartResult startMode(
        String playerRef,
        BloodSlayerModeService.Mode mode,
        long worldTick
    ){
        String player=normalizePlayer(playerRef);
        BloodSlayerModeService.Mode checkedMode=
            requireSupportedMode(mode);
        requireTick(worldTick);

        WorldPlayer owner=requireCurrentPlayer(player);
        long generation=owner.generation();
        final StartResult[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    generation,
                    ()->result[0]=
                        startOwned(
                            player,
                            checkedMode,
                            worldTick,
                            owner
                        )
                );

            if(!current)
                throw new IllegalStateException(
                    "stale Blood Slayer player "+
                    player
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Blood Slayer start ownership failed player="+
                player+
                " mode="+
                checkedMode,
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "Blood Slayer start result"
        );
    }

    StatusSnapshot status(
        String playerRef
    ){
        String player=normalizePlayer(playerRef);
        WorldPlayer owner=
            world.players().byName(
                player
            );

        if(owner==null)
            return localStatus(
                player,
                null
            );

        long generation=owner.generation();
        final StatusSnapshot[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    generation,
                    ()->{
                        ensureRestoredOwned(
                            player,
                            owner
                        );
                        result[0]=
                            localStatus(
                                player,
                                invalidPersistence
                                    .get(owner)
                            );
                    }
                );

            if(!current)
                return localStatus(
                    player,
                    "STALE_PLAYER"
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Blood Slayer status ownership failed player="+
                player,
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "Blood Slayer status result"
        );
    }

    StatusSnapshot selectMonsterHunterMode(
        String playerRef
    ){
        return selectMode(
            playerRef,
            BloodSlayerModeService.Mode
                .MONSTER_HUNTER_PVM
        );
    }

    StatusSnapshot selectBossHunterMode(
        String playerRef
    ){
        return selectMode(
            playerRef,
            BloodSlayerModeService.Mode
                .BOSS_HUNTER_PVM
        );
    }

    private StatusSnapshot selectMode(
        String playerRef,
        BloodSlayerModeService.Mode mode
    ){
        String player=normalizePlayer(playerRef);
        BloodSlayerModeService.Mode checkedMode=
            requireSupportedMode(mode);
        WorldPlayer owner=requireCurrentPlayer(player);
        long generation=owner.generation();
        final StatusSnapshot[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    generation,
                    ()->{
                        ensureRestoredOwned(
                            player,
                            owner
                        );

                        String invalid=
                            invalidPersistence.get(owner);

                        if(invalid!=null){
                            result[0]=
                                localStatus(
                                    player,
                                    invalid
                                );
                            return;
                        }

                        bloodSlayer.selectMode(
                            player,
                            checkedMode
                        );

                        result[0]=
                            localStatus(
                                player,
                                null
                            );
                    }
                );

            if(!current)
                return localStatus(
                    player,
                    "STALE_PLAYER"
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Blood Slayer mode selection ownership failed player="+
                player+
                " mode="+
                checkedMode,
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "Blood Slayer mode selection result"
        );
    }

    KillCreditResult recordMonsterSpawnerKill(
        String playerRef,
        int definitionId,
        long worldTick
    ){
        return recordMonsterSpawnerCredit(
            playerRef,
            definitionId,
            null,
            worldTick
        );
    }

    KillCreditResult recordMonsterSpawnerFinalization(
        String playerRef,
        int definitionId,
        Tile deathTile,
        long worldTick
    ){
        return recordMonsterSpawnerCredit(
            playerRef,
            definitionId,
            Objects.requireNonNull(
                deathTile,
                "deathTile"
            ),
            worldTick
        );
    }

    private KillCreditResult recordMonsterSpawnerCredit(
        String playerRef,
        int definitionId,
        Tile deathTile,
        long worldTick
    ){
        String player=normalizePlayer(playerRef);
        requireTick(worldTick);

        WorldPlayer owner=requireCurrentPlayer(player);
        long generation=owner.generation();
        final KillCreditResult[] result={null};

        try{
            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    owner,
                    generation,
                    ()->result[0]=
                        recordOwned(
                            player,
                            definitionId,
                            deathTile,
                            worldTick,
                            owner
                        )
                );

            if(!current)
                return new KillCreditResult(
                    definitionId==
                        TARGET_DEFINITION_ID,
                    false,
                    false,
                    false,
                    localStatus(
                        player,
                        "STALE_PLAYER"
                    )
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Blood Slayer kill ownership failed player="+
                player,
                failure
            );
        }

        return Objects.requireNonNull(
            result[0],
            "Blood Slayer kill result"
        );
    }

    SlayerTaskService slayer(){
        return slayer;
    }

    BloodSlayerModeService bloodSlayer(){
        return bloodSlayer;
    }

    private StartResult startOwned(
        String player,
        BloodSlayerModeService.Mode requestedMode,
        long worldTick,
        WorldPlayer owner
    ){
        BloodSlayerModeService.Mode mode=
            requireSupportedMode(
                requestedMode
            );

        ensureRestoredOwned(
            player,
            owner
        );

        String invalid=
            invalidPersistence.get(owner);

        if(invalid!=null)
            return new StartResult(
                false,
                localStatus(
                    player,
                    invalid
                )
            );

        SlayerTaskService.Snapshot active=
            slayer.active(player);

        if(active!=null){
            remember(
                player,
                active.taskId
            );

            StatusSnapshot status=
                snapshot(
                    player,
                    active
                );

            persistOwned(
                owner,
                status
            );

            return new StartResult(
                false,
                status
            );
        }

        String objectiveKey=
            objectiveKeyFor(mode);
        ObjectiveProgressService ledger=
            ensureLedger(player);
        ObjectiveProgressService.Snapshot
            renewalBefore=null;
        ObjectiveProgressService.Snapshot
            renewalReset=null;

        SlayerTaskService.TaskId latest=
            latestTask(player);

        if(latest!=null){
            SlayerTaskService.Snapshot prior=
                slayer.get(latest);

            if(prior!=null&&
               prior.terminal()&&
               prior.state!=
                    SlayerTaskService.State
                        .COMPLETED){
                StatusSnapshot status=
                    snapshot(
                        player,
                        prior
                    );

                persistOwned(
                    owner,
                    status
                );

                return new StartResult(
                    false,
                    status
                );
            }
        }

        ObjectiveProgressService.Snapshot
            desiredObjective=
                ledger.get(
                    objectiveKey
                );

        if(desiredObjective==null){
            ledger.define(
                new ObjectiveDefinition(
                    objectiveKey,
                    OBJECTIVE_GOAL,
                    AUTHORITY
                )
            );
        }else if(desiredObjective.complete){
            renewalBefore=
                desiredObjective;
            renewalReset=
                ledger.resetCompleted(
                    objectiveKey
                );

            if(renewalReset.complete||
               renewalReset.progress!=0L)
                throw new IllegalStateException(
                    "Blood Slayer completed objective did not reset"
                );
        }

        final BloodSlayerModeService.AssignmentResult
            assigned;

        try{
            bloodSlayer.selectMode(
                player,
                mode
            );

            assigned=
                bloodSlayer.requestTask(
                    player,
                    worldTick
                );
        }catch(RuntimeException failure){
            if(renewalReset!=null){
                try{
                    ledger.restoreIfUnchanged(
                        renewalReset,
                        renewalBefore
                    );
                }catch(RuntimeException rollbackFailure){
                    failure.addSuppressed(
                        rollbackFailure
                    );
                }
            }

            throw failure;
        }

        if(!taskKeyFor(mode).equals(
                assigned.task.definition.taskKey))
            throw new IllegalStateException(
                "Blood Slayer assigned task/mode mismatch mode="+
                mode+
                " task="+
                assigned.task.definition.taskKey
            );

        remember(
            player,
            assigned.task.taskId
        );

        StatusSnapshot status=
            snapshot(
                player,
                assigned.task
            );

        persistOwned(
            owner,
            status
        );

        return new StartResult(
            true,
            status
        );
    }

    private KillCreditResult recordOwned(
        String player,
        int definitionId,
        Tile deathTile,
        long worldTick,
        WorldPlayer owner
    ){
        ensureRestoredOwned(
            player,
            owner
        );

        String invalid=
            invalidPersistence.get(owner);

        if(invalid!=null)
            return new KillCreditResult(
                definitionId==
                    TARGET_DEFINITION_ID,
                false,
                false,
                false,
                localStatus(
                    player,
                    invalid
                )
            );

        SlayerTaskService.Snapshot active=
            slayer.active(player);

        if(definitionId!=TARGET_DEFINITION_ID)
            return new KillCreditResult(
                false,
                active!=null,
                false,
                false,
                localStatus(
                    player,
                    null
                )
            );

        if(active==null)
            return new KillCreditResult(
                true,
                false,
                false,
                false,
                localStatus(
                    player,
                    null
                )
            );

        BloodSlayerModeService.Mode mode=
            requireSupportedMode(
                bloodSlayer.get(player)
                    .selectedMode
            );

        if(!taskKeyFor(mode).equals(
                active.definition.taskKey))
            throw new IllegalStateException(
                "Blood Slayer active task/mode mismatch mode="+
                mode+
                " task="+
                active.definition.taskKey
            );

        boolean eligible=
            mode==
                BloodSlayerModeService.Mode
                    .MONSTER_HUNTER_PVM||
            (mode==
                BloodSlayerModeService.Mode
                    .BOSS_HUNTER_PVM&&
             deathTile!=null&&
             regionId(deathTile)==
                BOSS_REGION_ID);

        if(!eligible)
            return new KillCreditResult(
                true,
                true,
                false,
                false,
                snapshot(
                    player,
                    active
                )
            );

        long completionsBefore=
            completionCount(player);

        if(completionsBefore==Long.MAX_VALUE)
            throw new IllegalStateException(
                "Blood Slayer completion count exhausted player="+
                player
            );

        SlayerTaskService.KillResult result=
            slayer.recordValidatedKill(
                player,
                TARGET_KEY,
                1L,
                worldTick
            );

        remember(
            player,
            result.task.taskId
        );

        boolean taskCompletion=
            result.completedNow;
        long completionsAfter=
            taskCompletion
                ?Math.addExact(
                    completionsBefore,
                    1L
                )
                :completionsBefore;

        StatusSnapshot status=
            snapshot(
                player,
                result.task,
                completionsAfter
            );

        persistOwned(
            owner,
            status
        );

        if(taskCompletion)
            setCompletionCount(
                player,
                completionsAfter
            );

        return new KillCreditResult(
            true,
            true,
            result.progressed,
            result.completedNow,
            status
        );
    }

    private void ensureRestoredOwned(
        String player,
        WorldPlayer owner
    ){
        synchronized(this){
            if(hydratedPlayers.contains(owner))
                return;
        }

        SortedMap<String,String> persisted=
            owner.snapshotExtensions()
                .namespace(
                    LocalLabSlayerPersistence
                        .NAMESPACE
                );

        if(persisted.isEmpty()){
            synchronized(this){
                hydratedPlayers.add(owner);
            }
            return;
        }

        final LocalLabSlayerPersistence.Snapshot
            decoded;

        try{
            decoded=
                LocalLabSlayerPersistence
                    .decode(
                        persisted
                    );
        }catch(RuntimeException invalid){
            synchronized(this){
                invalidPersistence.put(
                    owner,
                    invalid.getMessage()==null
                        ?invalid.getClass()
                            .getSimpleName()
                        :invalid.getMessage()
                );
                hydratedPlayers.add(owner);
            }
            return;
        }

        if(decoded==null){
            synchronized(this){
                hydratedPlayers.add(owner);
            }
            return;
        }

        if(slayer.active(player)!=null||
           latestTask(player)!=null){
            synchronized(this){
                invalidPersistence.put(
                    owner,
                    "PERSISTED_STATE_COLLIDES_WITH_LIVE_RUNTIME"
                );
                hydratedPlayers.add(owner);
            }
            return;
        }

        BloodSlayerModeService.Mode mode=
            requireSupportedMode(
                decoded.mode
            );
        String objectiveKey=
            objectiveKeyFor(mode);

        ObjectiveProgressService ledger=
            ensureLedger(player);

        ledger.define(
            new ObjectiveDefinition(
                objectiveKey,
                OBJECTIVE_GOAL,
                AUTHORITY
            ),
            0L,
            false
        );

        bloodSlayer.selectMode(
            player,
            mode
        );

        long replayTick=
            world.clock().tick();

        BloodSlayerModeService.AssignmentResult
            assigned=
                bloodSlayer.requestTask(
                    player,
                    replayTick
                );

        if(!taskKeyFor(mode).equals(
                assigned.task.definition.taskKey))
            throw new IllegalStateException(
                "Blood Slayer persisted mode/task replay mismatch mode="+
                mode+
                " task="+
                assigned.task.definition.taskKey
            );

        remember(
            player,
            assigned.task.taskId
        );

        if(decoded.state==
                LocalLabSlayerPersistence
                    .State.COMPLETED){
            SlayerTaskService.KillResult completed=
                slayer.recordValidatedKill(
                    player,
                    TARGET_KEY,
                    1L,
                    replayTick
                );

            if(!completed.completedNow||
               completed.task.state!=
                    SlayerTaskService.State
                        .COMPLETED)
                throw new IllegalStateException(
                    "Blood Slayer completed-state replay failed"
                );

            remember(
                player,
                completed.task.taskId
            );
        }

        setCompletionCount(
            player,
            decoded.completions
        );

        StatusSnapshot restored=
            localStatus(
                player,
                null
            );

        if(restored.selectedMode!=mode||
           !taskKeyFor(mode).equals(
                restored.task.definition.taskKey)||
           !objectiveKey.equals(
                restored.task.objective.key))
            throw new IllegalStateException(
                "Blood Slayer persisted identity replay mismatch"
            );

        if(decoded.state==
                LocalLabSlayerPersistence
                    .State.ACTIVE&&
           (!restored.active()||
            restored.task.objective.progress!=0L))
            throw new IllegalStateException(
                "Blood Slayer active-state replay mismatch"
            );

        if(decoded.state==
                LocalLabSlayerPersistence
                    .State.COMPLETED&&
           (!restored.complete()||
            restored.task.objective.progress!=
                OBJECTIVE_GOAL))
            throw new IllegalStateException(
                "Blood Slayer completed-state replay mismatch"
            );

        synchronized(this){
            hydratedPlayers.add(owner);
        }
    }

    private void persistOwned(
        WorldPlayer owner,
        StatusSnapshot status
    ){
        owner.snapshotExtensions()
            .replaceNamespace(
                LocalLabSlayerPersistence.NAMESPACE,
                LocalLabSlayerPersistence
                    .encode(status)
            );

        synchronized(this){
            invalidPersistence.remove(owner);
            hydratedPlayers.add(owner);
        }
    }

    private StatusSnapshot localStatus(
        String player,
        String persistenceError
    ){
        if(persistenceError!=null)
            return new StatusSnapshot(
                player,
                null,
                null,
                persistenceError
            );

        SlayerTaskService.Snapshot active=
            slayer.active(player);

        if(active!=null){
            remember(
                player,
                active.taskId
            );
            return snapshot(
                player,
                active
            );
        }

        SlayerTaskService.TaskId latest=
            latestTask(player);

        return snapshot(
            player,
            latest==null
                ?null
                :slayer.get(latest)
        );
    }

    private StatusSnapshot snapshot(
        String player,
        SlayerTaskService.Snapshot task
    ){
        return snapshot(
            player,
            task,
            completionCount(player)
        );
    }

    private StatusSnapshot snapshot(
        String player,
        SlayerTaskService.Snapshot task,
        long completions
    ){
        BloodSlayerModeService.Snapshot blood=
            bloodSlayer.get(player);

        return new StatusSnapshot(
            player,
            blood.selectedMode,
            task,
            completions,
            null
        );
    }

    private WorldPlayer requireCurrentPlayer(
        String player
    ){
        WorldPlayer owner=
            world.players().byName(
                player
            );

        if(owner==null)
            throw new IllegalStateException(
                "Blood Slayer player is not registered "+
                player
            );

        return owner;
    }

    private synchronized ObjectiveProgressService
        ensureLedger(
            String player
        ){
        ObjectiveProgressService ledger=
            ledgers.get(player);

        if(ledger==null){
            ledger=
                new ObjectiveProgressService();
            ledgers.put(
                player,
                ledger
            );
        }

        return ledger;
    }

    private synchronized ObjectiveProgressService
        ledgerIfPresent(
            String player
        ){
        return ledgers.get(
            normalizePlayer(player)
        );
    }

    private synchronized void remember(
        String player,
        SlayerTaskService.TaskId taskId
    ){
        latestTaskByPlayer.put(
            player,
            Objects.requireNonNull(
                taskId,
                "taskId"
            )
        );
    }

    private synchronized SlayerTaskService.TaskId
        latestTask(
            String player
        ){
        return latestTaskByPlayer.get(player);
    }

    private synchronized long completionCount(
        String player
    ){
        Long value=
            completionCountByPlayer.get(player);

        return value==null
            ?0L
            :value.longValue();
    }

    private synchronized void setCompletionCount(
        String player,
        long value
    ){
        if(value<0L)
            throw new IllegalArgumentException(
                "Blood Slayer completions="+
                value
            );

        if(value==0L)
            completionCountByPlayer.remove(player);
        else
            completionCountByPlayer.put(
                player,
                value
            );
    }

    static boolean supportedMode(
        BloodSlayerModeService.Mode mode
    ){
        return mode==
                BloodSlayerModeService.Mode
                    .MONSTER_HUNTER_PVM||
            mode==
                BloodSlayerModeService.Mode
                    .BOSS_HUNTER_PVM;
    }

    static String taskKeyFor(
        BloodSlayerModeService.Mode mode
    ){
        BloodSlayerModeService.Mode checked=
            requireSupportedMode(mode);

        return checked==
                BloodSlayerModeService.Mode
                    .MONSTER_HUNTER_PVM
            ?TASK_KEY
            :BOSS_TASK_KEY;
    }

    static String objectiveKeyFor(
        BloodSlayerModeService.Mode mode
    ){
        BloodSlayerModeService.Mode checked=
            requireSupportedMode(mode);

        return checked==
                BloodSlayerModeService.Mode
                    .MONSTER_HUNTER_PVM
            ?OBJECTIVE_KEY
            :BOSS_OBJECTIVE_KEY;
    }

    static int regionId(
        Tile tile
    ){
        Tile checked=
            Objects.requireNonNull(
                tile,
                "tile"
            );

        return ((checked.x>>6)<<8)|
            (checked.y>>6);
    }

    private static BloodSlayerModeService.Mode
        requireSupportedMode(
            BloodSlayerModeService.Mode mode
        ){
        BloodSlayerModeService.Mode checked=
            Objects.requireNonNull(
                mode,
                "mode"
            );

        if(!supportedMode(checked))
            throw new IllegalArgumentException(
                "unsupported LocalLab Blood Slayer mode "+
                checked
            );

        return checked;
    }

    private static void requireTick(
        long worldTick
    ){
        if(worldTick<0L)
            throw new IllegalArgumentException(
                "worldTick="+worldTick
            );
    }

    private static String normalizePlayer(
        String playerRef
    ){
        if(playerRef==null)
            throw new NullPointerException(
                "playerRef"
            );

        String normalized=
            playerRef.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "playerRef blank"
            );

        return normalized;
    }
}

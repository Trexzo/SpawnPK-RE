package spk.local;

import java.util.*;

/**
 * Exact-current Blood Slayer mode composition over SlayerTaskService.
 *
 * The client proves four selector modes plus Get Task. Task catalogs,
 * assignment weights, points/rewards and kill-credit policy remain caller
 * authority.
 */
final class BloodSlayerModeService {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    enum Mode {
        MONSTER_HUNTER_PVM,
        BOSS_HUNTER_PVM,
        BOUNTY_HUNTER_PK,
        SLAUGHTER_PK;

        boolean pvp(){
            return this==
                    BOUNTY_HUNTER_PK||
                this==
                    SLAUGHTER_PK;
        }

        boolean pvm(){
            return !pvp();
        }
    }

    @FunctionalInterface
    interface TaskAllocator {
        String selectTaskKey(
            String playerRef,
            Mode mode
        );
    }

    static final class Snapshot {
        final String playerRef;
        final Mode selectedMode;
        final SlayerTaskService.Snapshot
            activeTask;
        final String policyAuthority;
        final String presentationAuthority;

        Snapshot(
            String playerRef,
            Mode selectedMode,
            SlayerTaskService.Snapshot activeTask,
            String policyAuthority
        ){
            this.playerRef=playerRef;
            this.selectedMode=
                selectedMode;
            this.activeTask=activeTask;
            this.policyAuthority=
                policyAuthority;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        boolean hasSelection(){
            return selectedMode!=null;
        }

        boolean hasActiveTask(){
            return activeTask!=null;
        }
    }

    static final class AssignmentResult {
        final Mode mode;
        final SlayerTaskService.Snapshot task;
        final Snapshot player;

        AssignmentResult(
            Mode mode,
            SlayerTaskService.Snapshot task,
            Snapshot player
        ){
            this.mode=
                Objects.requireNonNull(
                    mode,
                    "mode"
                );
            this.task=
                Objects.requireNonNull(
                    task,
                    "task"
                );
            this.player=
                Objects.requireNonNull(
                    player,
                    "player"
                );
        }
    }

    private static final class TaskBinding {
        final SlayerTaskService.Definition
            definition;
        final EnumSet<Mode> eligibleModes;

        TaskBinding(
            SlayerTaskService.Definition definition,
            EnumSet<Mode> eligibleModes
        ){
            this.definition=definition;
            this.eligibleModes=
                eligibleModes.clone();
        }
    }

    private final SlayerTaskService slayer;
    private final TaskAllocator allocator;
    private final String policyAuthority;

    private final LinkedHashMap<String,TaskBinding>
        tasks=
            new LinkedHashMap<>();

    private final LinkedHashMap<String,Mode>
        selectedByPlayer=
            new LinkedHashMap<>();

    private final HashSet<String>
        playersInFlight=
            new HashSet<>();

    BloodSlayerModeService(
        SlayerTaskService slayer,
        TaskAllocator allocator,
        String policyAuthority
    ){
        this.slayer=
            Objects.requireNonNull(
                slayer,
                "slayer"
            );
        this.allocator=
            Objects.requireNonNull(
                allocator,
                "allocator"
            );
        this.policyAuthority=
            MatchRules.requireText(
                policyAuthority,
                "policyAuthority"
            );
    }

    SlayerTaskService.Definition
        registerTaskDefinition(
            SlayerTaskService.Definition definition,
            Collection<Mode> eligibleModes
        ){
        SlayerTaskService.Definition checked=
            Objects.requireNonNull(
                definition,
                "definition"
            );

        if(!policyAuthority.equals(
                checked.sourceAuthority))
            throw new IllegalArgumentException(
                "Blood Slayer task authority mismatch task="+
                checked.taskKey
            );

        Objects.requireNonNull(
            eligibleModes,
            "eligibleModes"
        );

        EnumSet<Mode> modes=
            EnumSet.noneOf(
                Mode.class
            );

        for(Mode mode:eligibleModes)
            modes.add(
                Objects.requireNonNull(
                    mode,
                    "eligible mode"
                )
            );

        if(modes.isEmpty())
            throw new IllegalArgumentException(
                "Blood Slayer task requires eligible mode"
            );

        synchronized(this){
            if(tasks.containsKey(
                    checked.taskKey))
                throw new IllegalStateException(
                    "duplicate Blood Slayer task "+
                    checked.taskKey
                );
        }

        /*
         * Delegate registration may execute its own validation and locking.
         * Never hold the Blood Slayer monitor across that call.
         */
        SlayerTaskService.Definition registered=
            slayer.registerDefinition(
                checked
            );

        synchronized(this){
            if(tasks.containsKey(
                    registered.taskKey))
                throw new IllegalStateException(
                    "duplicate Blood Slayer task "+
                    registered.taskKey
                );

            tasks.put(
                registered.taskKey,
                new TaskBinding(
                    registered,
                    modes
                )
            );
        }

        return registered;
    }

    Snapshot selectMode(
        String playerRef,
        Mode mode
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        Mode checked=
            Objects.requireNonNull(
                mode,
                "mode"
            );

        final Mode current;

        synchronized(this){
            current=
                selectedByPlayer.get(
                    player
                );

            if(current!=checked)
                reservePlayer(
                    player
                );
        }

        if(current==checked)
            return snapshotFor(
                player,
                checked
            );

        SlayerTaskService.Snapshot active=null;
        RuntimeException failure=null;

        try{
            active=
                slayer.active(player);

            if(active!=null)
                throw new IllegalStateException(
                    "cannot switch Blood Slayer mode with active task player="+
                    player
                );
        }catch(RuntimeException error){
            failure=error;
        }

        synchronized(this){
            try{
                if(failure==null){
                    if(selectedByPlayer.get(
                            player)!=current)
                        throw new IllegalStateException(
                            "Blood Slayer mode changed during selection player="+
                            player
                        );

                    selectedByPlayer.put(
                        player,
                        checked
                    );
                }
            }finally{
                releasePlayer(
                    player
                );
            }
        }

        if(failure!=null)
            throw failure;

        return new Snapshot(
            player,
            checked,
            null,
            policyAuthority
        );
    }

    Snapshot clearMode(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        final Mode current;

        synchronized(this){
            current=
                selectedByPlayer.get(
                    player
                );

            if(current==null)
                return new Snapshot(
                    player,
                    null,
                    null,
                    policyAuthority
                );

            reservePlayer(
                player
            );
        }

        SlayerTaskService.Snapshot active=null;
        RuntimeException failure=null;

        try{
            active=
                slayer.active(player);

            if(active!=null)
                throw new IllegalStateException(
                    "cannot clear Blood Slayer mode with active task player="+
                    player
                );
        }catch(RuntimeException error){
            failure=error;
        }

        synchronized(this){
            try{
                if(failure==null){
                    if(selectedByPlayer.get(
                            player)!=current)
                        throw new IllegalStateException(
                            "Blood Slayer mode changed during clear player="+
                            player
                        );

                    selectedByPlayer.remove(
                        player
                    );
                }
            }finally{
                releasePlayer(
                    player
                );
            }
        }

        if(failure!=null)
            throw failure;

        return new Snapshot(
            player,
            null,
            null,
            policyAuthority
        );
    }

    AssignmentResult requestTask(
        String playerRef,
        long worldTick
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        final Mode mode;

        synchronized(this){
            reservePlayer(
                player
            );

            mode=
                selectedByPlayer.get(
                    player
                );

            if(mode==null){
                releasePlayer(
                    player
                );
                throw new IllegalStateException(
                    "Blood Slayer mode not selected player="+
                    player
                );
            }
        }

        SlayerTaskService.Snapshot task=null;
        RuntimeException failure=null;

        try{
            String allocated=
                allocator.selectTaskKey(
                    player,
                    mode
                );

            String taskKey=
                MatchRules.normalizeKey(
                    allocated,
                    "allocatedTaskKey"
                );

            final TaskBinding binding;

            synchronized(this){
                binding=
                    tasks.get(
                        taskKey
                    );
            }

            if(binding==null)
                throw new IllegalArgumentException(
                    "allocator returned unregistered Blood Slayer task "+
                    taskKey
                );

            if(!binding.eligibleModes
                    .contains(mode))
                throw new IllegalArgumentException(
                    "Blood Slayer task "+
                    taskKey+
                    " not eligible for mode "+
                    mode
                );

            if(!policyAuthority.equals(
                    binding.definition
                        .sourceAuthority))
                throw new IllegalStateException(
                    "Blood Slayer task authority drift "+
                    taskKey
                );

            task=
                slayer.assign(
                    player,
                    taskKey,
                    worldTick
                );
        }catch(RuntimeException error){
            failure=error;
        }

        synchronized(this){
            try{
                if(selectedByPlayer.get(
                        player)!=mode&&
                   failure==null)
                    throw new IllegalStateException(
                        "Blood Slayer mode changed during task request player="+
                        player
                    );
            }finally{
                releasePlayer(
                    player
                );
            }
        }

        if(failure!=null)
            throw failure;

        Snapshot playerSnapshot=
            new Snapshot(
                player,
                mode,
                task,
                policyAuthority
            );

        return new AssignmentResult(
            mode,
            task,
            playerSnapshot
        );
    }

    Snapshot get(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );
        final Mode selected;

        synchronized(this){
            selected=
                selectedByPlayer.get(
                    player
                );
        }

        return snapshotFor(
            player,
            selected
        );
    }

    synchronized int registeredTaskCount(){
        return tasks.size();
    }

    List<Snapshot> snapshot(){
        final ArrayList<Map.Entry<String,Mode>>
            selected=
                new ArrayList<>();

        synchronized(this){
            for(Map.Entry<String,Mode> entry:
                    selectedByPlayer.entrySet())
                selected.add(
                    new AbstractMap.SimpleImmutableEntry<>(
                        entry.getKey(),
                        entry.getValue()
                    )
                );
        }

        selected.sort(
            Comparator.comparing(
                Map.Entry::getKey
            )
        );

        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(Map.Entry<String,Mode> entry:
                selected)
            out.add(
                snapshotFor(
                    entry.getKey(),
                    entry.getValue()
                )
            );

        return Collections.unmodifiableList(
            out
        );
    }

    private Snapshot snapshotFor(
        String player,
        Mode selectedMode
    ){
        return new Snapshot(
            player,
            selectedMode,
            slayer.active(
                player
            ),
            policyAuthority
        );
    }

    private void reservePlayer(
        String player
    ){
        if(!playersInFlight.add(
                player))
            throw new IllegalStateException(
                "Blood Slayer operation already in flight player="+
                player
            );
    }

    private void releasePlayer(
        String player
    ){
        if(!playersInFlight.remove(
                player))
            throw new IllegalStateException(
                "Blood Slayer in-flight fence missing player="+
                player
            );
    }

    private static String normalizePlayer(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "playerRef"
            );

        String normalized=
            value.trim()
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

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

    synchronized SlayerTaskService.Definition
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

        if(tasks.containsKey(
                checked.taskKey))
            throw new IllegalStateException(
                "duplicate Blood Slayer task "+
                checked.taskKey
            );

        // Delegate registration happens only after all Blood Slayer-side
        // validation is complete. If the delegate rejects, this service has
        // not mutated its binding table.
        SlayerTaskService.Definition registered=
            slayer.registerDefinition(
                checked
            );

        tasks.put(
            registered.taskKey,
            new TaskBinding(
                registered,
                modes
            )
        );

        return registered;
    }

    synchronized Snapshot selectMode(
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

        Mode current=
            selectedByPlayer.get(
                player
            );

        if(current==checked)
            return snapshotOf(
                player
            );

        if(slayer.active(player)!=null)
            throw new IllegalStateException(
                "cannot switch Blood Slayer mode with active task player="+
                player
            );

        selectedByPlayer.put(
            player,
            checked
        );

        return snapshotOf(
            player
        );
    }

    synchronized Snapshot clearMode(
        String playerRef
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        if(slayer.active(player)!=null)
            throw new IllegalStateException(
                "cannot clear Blood Slayer mode with active task player="+
                player
            );

        selectedByPlayer.remove(
            player
        );

        return snapshotOf(
            player
        );
    }

    synchronized AssignmentResult requestTask(
        String playerRef,
        long worldTick
    ){
        String player=
            normalizePlayer(
                playerRef
            );

        Mode mode=
            selectedByPlayer.get(
                player
            );

        if(mode==null)
            throw new IllegalStateException(
                "Blood Slayer mode not selected player="+
                player
            );

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

        TaskBinding binding=
            tasks.get(
                taskKey
            );

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

        SlayerTaskService.Snapshot task=
            slayer.assign(
                player,
                taskKey,
                worldTick
            );

        return new AssignmentResult(
            mode,
            task,
            snapshotOf(
                player
            )
        );
    }

    synchronized Snapshot get(
        String playerRef
    ){
        return snapshotOf(
            normalizePlayer(
                playerRef
            )
        );
    }

    synchronized int registeredTaskCount(){
        return tasks.size();
    }

    synchronized List<Snapshot> snapshot(){
        ArrayList<String> players=
            new ArrayList<>(
                selectedByPlayer.keySet()
            );

        players.sort(
            Comparator.naturalOrder()
        );

        ArrayList<Snapshot> out=
            new ArrayList<>();

        for(String player:players)
            out.add(
                snapshotOf(player)
            );

        return Collections.unmodifiableList(
            out
        );
    }

    private Snapshot snapshotOf(
        String player
    ){
        return new Snapshot(
            player,
            selectedByPlayer.get(
                player
            ),
            slayer.active(
                player
            ),
            policyAuthority
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

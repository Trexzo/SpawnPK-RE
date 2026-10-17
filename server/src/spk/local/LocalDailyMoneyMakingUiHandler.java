package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Session-local LocalLab adapter for exact Daily Money Making controls.
 *
 * G18.3 preserves session-owned difficulty persistence. G18.4 resolves the
 * exact Track widget to one explicitly CUSTOM_LOCALLAB World-owned EASY PvM
 * objective. Medium/Hard activity and Teleport remain fail-closed.
 */
final class LocalDailyMoneyMakingUiHandler {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G182_DAILY_MONEY_MAKING_DIFFICULTY";

    static final class Result {
        final DailyMoneyMakingPresentation.InputKind input;
        final String status;
        final boolean stateChanged;
        final DailyMoneyMakingStateService.Snapshot snapshot;

        Result(
            DailyMoneyMakingPresentation.InputKind input,
            String status,
            boolean stateChanged,
            DailyMoneyMakingStateService.Snapshot snapshot
        ){
            this.input=
                Objects.requireNonNull(
                    input,
                    "input"
                );
            this.status=
                Objects.requireNonNull(
                    status,
                    "status"
                );
            this.stateChanged=stateChanged;
            this.snapshot=
                Objects.requireNonNull(
                    snapshot,
                    "snapshot"
                );
        }

        boolean succeeded(){
            return "DIFFICULTY_SELECTED".equals(status)||
                "TRACKING_UPDATED".equals(status);
        }
    }

    private final DailyMoneyMakingStateService state=
        new DailyMoneyMakingStateService(
            new ObjectiveProgressService()
        );
    private final WorldPlayer persistenceOwner;
    private final String invalidPersistence;
    private final World activityWorld;
    private final WorldPlayer activityOwner;
    private final long activityGeneration;
    private final String activityPlayer;

    LocalDailyMoneyMakingUiHandler(){
        this(null,null,-1L,null);
    }

    LocalDailyMoneyMakingUiHandler(
        WorldPlayer persistenceOwner
    ){
        this(null,persistenceOwner,-1L,null);
    }

    /**
     * Construct AFTER canonical login restore: difficulty is rehydrated from
     * exact WorldPlayer snapshot extensions, not the pre-login UI shell.
     */
    LocalDailyMoneyMakingUiHandler(
        World world,
        WorldPlayer persistenceOwner,
        long generation,
        String playerName
    ){
        this.persistenceOwner=persistenceOwner;
        this.activityWorld=world;
        this.activityOwner=world==null
            ?null
            :Objects.requireNonNull(persistenceOwner,"activity owner");
        if(world!=null&&generation<0L)
            throw new IllegalArgumentException("generation="+generation);
        this.activityGeneration=generation;
        this.activityPlayer=world==null
            ?null
            :Objects.requireNonNull(playerName,"activity player");

        String invalid=null;

        if(persistenceOwner!=null){
            try{
                DailyMoneyMakingStateService.Difficulty
                    restored=
                        LocalDailyMoneyMakingPersistence
                            .decode(
                                persistenceOwner
                                    .snapshotExtensions()
                                    .namespace(
                                        LocalDailyMoneyMakingPersistence
                                            .NAMESPACE
                                    )
                            );

                if(restored!=null)
                    state.selectDifficulty(
                        restored
                    );
            }catch(RuntimeException failure){
                invalid=
                    failure.getMessage()==null
                        ?failure.getClass()
                            .getSimpleName()
                        :failure.getMessage();
            }
        }

        this.invalidPersistence=
            invalid;
    }

    Result handle(
        DailyMoneyMakingPresentation.Input input,
        ServerPacketWriter packets
    )throws IOException{
        DailyMoneyMakingPresentation.Input checked=
            Objects.requireNonNull(
                input,
                "input"
            );

        if(invalidPersistence!=null)
            return new Result(
                checked.kind,
                "INVALID_PERSISTENCE",
                false,
                state.snapshot()
            );

        switch(checked.kind){
            case SELECT_EASY:
                return select(
                    DailyMoneyMakingStateService
                        .Difficulty.EASY,
                    checked,
                    packets
                );
            case SELECT_MEDIUM:
                return select(
                    DailyMoneyMakingStateService
                        .Difficulty.MEDIUM,
                    checked,
                    packets
                );
            case SELECT_HARD:
                return select(
                    DailyMoneyMakingStateService
                        .Difficulty.HARD,
                    checked,
                    packets
                );
            case TRACK:
                return track(checked,packets);
            case TELEPORT:
                return new Result(
                    checked.kind,
                    "NO_TELEPORT_AUTHORITY",
                    false,
                    state.snapshot()
                );
            default:
                throw new IllegalStateException(
                    "Unhandled Daily Money Making input "+
                    checked.kind
                );
        }
    }

    private Result track(
        DailyMoneyMakingPresentation.Input input,
        ServerPacketWriter packets
    )throws IOException{
        DailyMoneyMakingStateService.Snapshot selection=state.snapshot();

        // Keep unbound historical tests and non-live tools fail-closed.
        if(activityWorld==null)
            return new Result(input.kind,"NO_ACTIVITY_AUTHORITY",false,selection);

        // Stale session ownership takes precedence over an old difficulty
        // selection. Otherwise a replaced UI can leak NO_CONFIGURED_ACTIVITY
        // instead of consistently rejecting exact-generation ownership.
        if(!activityWorld.players().owns(activityOwner,activityGeneration)||
           activityWorld.players().byName(activityPlayer)!=activityOwner)
            return new Result(input.kind,"NO_CURRENT_PLAYER",false,selection);

        if(selection.selectedDifficulty==null)
            return new Result(input.kind,"NO_SELECTED_DIFFICULTY",false,selection);

        if(selection.selectedDifficulty!=DailyMoneyMakingStateService.Difficulty.EASY)
            return new Result(input.kind,"NO_CONFIGURED_ACTIVITY",false,selection);

        // The runtime re-checks exact owner, generation AND current name
        // under the same World/player mutation fence as activity creation.
        LocalLabDailyMoneyMakingRuntime.ActivationResult activation=
            activityWorld.localLabDailyMoneyMaking().activateEasyIfCurrent(
                activityPlayer,activityOwner,activityGeneration
            );
        if(activation==null)
            return new Result(input.kind,"NO_CURRENT_PLAYER",false,selection);
        LocalLabDailyMoneyMakingRuntime.Snapshot current=activation.snapshot;

        if(!LocalLabDailyMoneyMakingRuntime.OBJECTIVE_KEY.equals(
                current.state.trackedObjectiveKey))
            throw new IllegalStateException("Daily Money Making runtime objective drift");

        ApplicationControl126Service.dailyMoneyMakingTrackingText(
            Objects.requireNonNull(packets,"packets"),
            "LocalLab Easy PvM: "+current.objective.progress+"/"+
                LocalLabDailyMoneyMakingRuntime.GOAL
        );
        return new Result(input.kind,"TRACKING_UPDATED",activation.activatedNow,
            current.state);
    }

    DailyMoneyMakingStateService.Snapshot snapshot(){
        return state.snapshot();
    }

    private Result select(
        DailyMoneyMakingStateService.Difficulty difficulty,
        DailyMoneyMakingPresentation.Input input,
        ServerPacketWriter packets
    )throws IOException{
        if(activityWorld==null)
            return commitDifficultySelection(difficulty,input,packets);

        final Result[] admitted={null};
        try{
            boolean current=
                activityWorld.withOpenPlayerMutationOwnershipIfCurrent(
                    activityOwner,
                    activityGeneration,
                    ()->{
                        // Check username and exact owner while generation
                        // ownership is held; a stale adapter must not persist
                        // or publish a selection into a replacement session.
                        if(activityWorld.players().byName(activityPlayer)!=
                                activityOwner)
                            return;
                        admitted[0]=commitDifficultySelection(
                            difficulty,input,packets
                        );
                    }
                );

            if(!current||admitted[0]==null)
                return new Result(
                    input.kind,
                    "NO_CURRENT_PLAYER",
                    false,
                    state.snapshot()
                );

            return admitted[0];
        }catch(IOException failure){
            throw failure;
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "Daily Money Making difficulty selection ownership failed",
                failure
            );
        }
    }

    private Result commitDifficultySelection(
        DailyMoneyMakingStateService.Difficulty difficulty,
        DailyMoneyMakingPresentation.Input input,
        ServerPacketWriter packets
    )throws IOException{
        boolean changed=
            state.selectDifficulty(
                difficulty
            );

        if(persistenceOwner!=null)
            persistenceOwner
                .snapshotExtensions()
                .replaceNamespace(
                    LocalDailyMoneyMakingPersistence
                        .NAMESPACE,
                    LocalDailyMoneyMakingPersistence
                        .encode(
                            difficulty
                        )
                );

        ApplicationControl126Service
            .dailyMoneyMakingDifficulty(
                Objects.requireNonNull(
                    packets,
                    "packets"
                ),
                difficulty
            );

        return new Result(
            input.kind,
            "DIFFICULTY_SELECTED",
            changed,
            state.snapshot()
        );
    }
}

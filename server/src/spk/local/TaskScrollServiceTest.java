package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class TaskScrollServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_TASK_SCROLL";

    public static void main(String[] args){
        Map<String,ObjectiveProgressService>
            ledgers=
                new HashMap<>();

        ObjectiveProgressService a=
            ledger(
                "taskscroll:kills",
                3L
            );
        ObjectiveProgressService b=
            ledger(
                "taskscroll:kills",
                3L
            );

        ledgers.put("player:a",a);
        ledgers.put("player:b",b);

        TaskScrollService service=
            new TaskScrollService(
                ledgers::get
            );

        service.registerDefinition(
            new TaskScrollService.Definition(
                "scroll:test",
                "taskscroll:kills",
                Arrays.asList(
                    "Defeat three validated targets.",
                    "Reward settlement is external."
                ),
                POLICY
            )
        );

        TaskScrollService.Snapshot assigned=
            service.assign(
                " Player:A ",
                "scroll:test",
                10L
            );

        require(
            "player:a".equals(
                assigned.playerRef
            )&&
            assigned.state==
                TaskScrollService.State.ACTIVE&&
            assigned.definition
                .informationLines.size()==2&&
            !assigned.tracked&&
            TaskScrollService
                .PRESENTATION_AUTHORITY
                .equals(
                    assigned
                        .presentationAuthority
                ),
            "Task Scroll assignment"
        );

        expect(
            IllegalStateException.class,
            ()->service.assign(
                "player:a",
                "scroll:test",
                11L
            ),
            "second active Task Scroll"
        );

        TaskScrollService.Snapshot tracked=
            service.setTracked(
                assigned.taskScrollId,
                true,
                11L
            );

        require(
            tracked.tracked,
            "Task Scroll tracking"
        );

        TaskScrollService.ProgressResult first=
            service.recordValidatedProgress(
                "player:a",
                1L,
                12L
            );

        require(
            !first.completedNow&&
            first.task.objective.progress==1L&&
            b.get(
                "taskscroll:kills"
            ).progress==0L,
            "player-scoped Task Scroll progress"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.recordValidatedProgress(
                "player:a",
                1L,
                11L
            ),
            "stale Task Scroll progress"
        );

        TaskScrollService.ProgressResult complete=
            service.recordValidatedProgress(
                "player:a",
                99L,
                13L
            );

        require(
            complete.completedNow&&
            complete.task.objective.progress==3L&&
            complete.task.state==
                TaskScrollService
                    .State.COMPLETE_UNCLAIMED&&
            complete.task.claimable(),
            "Task Scroll completion"
        );

        TaskScrollService.Snapshot untracked=
            service.setTracked(
                assigned.taskScrollId,
                false,
                14L
            );

        require(
            !untracked.tracked&&
            untracked.claimable(),
            "completed Task Scroll track toggle"
        );

        TaskScrollService.ClaimResult claimed=
            service
                .confirmRewardSettledAndMarkClaimed(
                    assigned.taskScrollId,
                    15L
                );

        require(
            claimed.changed&&
            claimed.task.state==
                TaskScrollService.State.CLAIMED&&
            claimed.task.objective.claimed&&
            service.active(
                "player:a"
            )==null,
            "Task Scroll post-settlement claim"
        );

        TaskScrollService.ClaimResult duplicate=
            service
                .confirmRewardSettledAndMarkClaimed(
                    assigned.taskScrollId,
                    16L
                );

        require(
            !duplicate.changed&&
            duplicate.task.state==
                TaskScrollService.State.CLAIMED,
            "Task Scroll duplicate claim"
        );

        ObjectiveProgressService external=
            ledger(
                "taskscroll:external",
                2L
            );
        ledgers.put(
            "player:external",
            external
        );

        service.registerDefinition(
            new TaskScrollService.Definition(
                "scroll:external",
                "taskscroll:external",
                Collections.singletonList(
                    "Progress may come from another producer."
                ),
                POLICY
            )
        );

        TaskScrollService.Snapshot externalTask=
            service.assign(
                "player:external",
                "scroll:external",
                20L
            );

        external.advance(
            "taskscroll:external",
            2L
        );

        TaskScrollService.Snapshot refreshed=
            service.refresh(
                externalTask.taskScrollId,
                21L
            );

        require(
            refreshed.state==
                TaskScrollService
                    .State.COMPLETE_UNCLAIMED&&
            refreshed.objective.complete,
            "Task Scroll external progress refresh"
        );

        incompleteClaimGuard(
            service,
            ledgers
        );
        cancellationAndRelease(
            service,
            ledgers
        );
        definitionGuards();
        immutableSnapshot(service);
        protocolBoundary();

        System.out.println(
            "TASK_SCROLL_SERVICE_PASS "+
            "maxInfoLines20=true "+
            "playerScopedLedger=true "+
            "normalizedPlayerIdentity=true "+
            "oneNonterminalAssignment=true "+
            "validatedProgress=true "+
            "goalClamp=true "+
            "trackingBookkeeping=true "+
            "monotonicTick=true "+
            "externalProgressRefresh=true "+
            "incompleteClaimRejected=true "+
            "externalRewardSettlementRequired=true "+
            "claimIdempotent=true "+
            "claimReleasesPlayerSlot=true "+
            "cancelReleasesPlayerSlot=true "+
            "rewardPayloadAbsent=true "+
            "protocolIndependent=true"
        );
    }

    private static void incompleteClaimGuard(
        TaskScrollService service,
        Map<String,ObjectiveProgressService> ledgers
    ){
        ObjectiveProgressService ledger=
            ledger(
                "taskscroll:incomplete",
                2L
            );

        ledgers.put(
            "player:incomplete",
            ledger
        );

        service.registerDefinition(
            new TaskScrollService.Definition(
                "scroll:incomplete",
                "taskscroll:incomplete",
                Collections.singletonList(
                    "Incomplete claim test."
                ),
                POLICY
            )
        );

        TaskScrollService.Snapshot task=
            service.assign(
                "player:incomplete",
                "scroll:incomplete",
                30L
            );

        expect(
            IllegalStateException.class,
            ()->service
                .confirmRewardSettledAndMarkClaimed(
                    task.taskScrollId,
                    31L
                ),
            "incomplete Task Scroll claim"
        );

        require(
            service.active(
                "player:incomplete"
            )!=null&&
            !ledger.get(
                "taskscroll:incomplete"
            ).claimed,
            "incomplete claim mutated Task Scroll"
        );
    }

    private static void cancellationAndRelease(
        TaskScrollService service,
        Map<String,ObjectiveProgressService> ledgers
    ){
        ObjectiveProgressService ledger=
            ledger(
                "taskscroll:cancel",
                2L
            );

        ledgers.put(
            "player:cancel",
            ledger
        );

        service.registerDefinition(
            new TaskScrollService.Definition(
                "scroll:cancel",
                "taskscroll:cancel",
                Collections.singletonList(
                    "Cancellation test."
                ),
                POLICY
            )
        );

        TaskScrollService.Snapshot task=
            service.assign(
                "player:cancel",
                "scroll:cancel",
                40L
            );

        TaskScrollService.Snapshot cancelled=
            service.cancel(
                task.taskScrollId,
                41L
            );

        require(
            cancelled.state==
                TaskScrollService.State.CANCELLED&&
            service.active(
                "player:cancel"
            )==null&&
            ledger.get(
                "taskscroll:cancel"
            ).progress==0L,
            "Task Scroll cancellation"
        );

        TaskScrollService.Snapshot replacement=
            service.assign(
                "player:cancel",
                "scroll:cancel",
                42L
            );

        require(
            replacement.state==
                TaskScrollService.State.ACTIVE,
            "Task Scroll slot not released after cancel"
        );
    }

    private static void definitionGuards(){
        ArrayList<String> lines=
            new ArrayList<>();

        for(int i=0;i<21;i++)
            lines.add(
                "line "+i
            );

        expect(
            IllegalArgumentException.class,
            ()->new TaskScrollService.Definition(
                "scroll:too-many-lines",
                "objective:test",
                lines,
                POLICY
            ),
            "Task Scroll accepted >20 info lines"
        );
    }

    private static ObjectiveProgressService ledger(
        String key,
        long goal
    ){
        ObjectiveProgressService ledger=
            new ObjectiveProgressService();

        ledger.define(
            new ObjectiveDefinition(
                key,
                goal,
                POLICY
            )
        );

        return ledger;
    }

    private static void immutableSnapshot(
        TaskScrollService service
    ){
        boolean immutable=false;

        try{
            service.snapshot().clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Task Scroll snapshots mutable"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                TaskScrollService.class,
                TaskScrollService.Definition.class,
                TaskScrollService.Snapshot.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("interface")||
                   name.contains("rewarditem")||
                   name.contains("casketitem")||
                   name.contains("rewardamount"))
                    throw new AssertionError(
                        "protocol/reward payload leaked into Task Scroll "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private TaskScrollServiceTest(){}
}

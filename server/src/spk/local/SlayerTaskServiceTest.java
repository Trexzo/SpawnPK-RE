package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class SlayerTaskServiceTest {
    public static void main(String[] args){
        Map<String,ObjectiveProgressService> ledgers=
            new HashMap<>();

        ObjectiveProgressService a=
            ledger("slayer:kills",3L);
        ObjectiveProgressService b=
            ledger("slayer:kills",3L);
        ObjectiveProgressService c=
            ledger("slayer:kills",2L);

        ledgers.put("player:a",a);
        ledgers.put("player:b",b);
        ledgers.put("player:c",c);

        SlayerTaskService service=
            new SlayerTaskService(
                ledgers::get
            );

        service.registerDefinition(
            new SlayerTaskService.Definition(
                "task:blood-revenant",
                "family:blood",
                "npc:blood-revenant",
                "slayer:kills",
                "CUSTOM_LOCALLAB"
            )
        );

        SlayerTaskService.Snapshot taskA=
            service.assign(
                "player:a",
                "task:blood-revenant",
                10L
            );

        SlayerTaskService.Snapshot taskB=
            service.assign(
                "player:b",
                "task:blood-revenant",
                10L
            );

        require(
            taskA.state==
                SlayerTaskService.State.ACTIVE&&
            taskB.state==
                SlayerTaskService.State.ACTIVE&&
            taskA.objective.progress==0L&&
            taskB.objective.progress==0L,
            "independent task assignment"
        );

        SlayerTaskService.KillResult mismatch=
            service.recordValidatedKill(
                "player:a",
                "npc:other",
                1L,
                11L
            );

        require(
            !mismatch.matchedTarget&&
            !mismatch.progressed&&
            a.get("slayer:kills").progress==0L,
            "target mismatch progressed Slayer"
        );

        SlayerTaskService.KillResult first=
            service.recordValidatedKill(
                "player:a",
                "npc:blood-revenant",
                1L,
                12L
            );

        require(
            first.matchedTarget&&
            first.progressed&&
            !first.completedNow&&
            first.task.objective.progress==1L&&
            b.get("slayer:kills").progress==0L,
            "player-scoped Slayer progress"
        );

        SlayerTaskService.KillResult complete=
            service.recordValidatedKill(
                "player:a",
                "npc:blood-revenant",
                99L,
                13L
            );

        require(
            complete.completedNow&&
            complete.task.state==
                SlayerTaskService.State.COMPLETED&&
            complete.task.objective.progress==3L&&
            service.active("player:a")==null&&
            b.get("slayer:kills").progress==0L,
            "Slayer completion/clamp"
        );

        expect(
            IllegalStateException.class,
            ()->service.recordValidatedKill(
                "player:a",
                "npc:blood-revenant",
                1L,
                14L
            ),
            "completed task accepted kill"
        );

        b.advance(
            "slayer:kills",
            3L
        );

        SlayerTaskService.Snapshot refreshed=
            service.refresh(
                taskB.taskId,
                15L
            );

        require(
            refreshed.state==
                SlayerTaskService.State.COMPLETED&&
            refreshed.objective.complete&&
            service.active("player:b")==null,
            "external objective refresh"
        );

        SlayerTaskService.Snapshot taskC=
            service.assign(
                "player:c",
                "task:blood-revenant",
                20L
            );

        SlayerTaskService.Snapshot skipped=
            service.skip(
                taskC.taskId,
                21L
            );

        require(
            skipped.state==
                SlayerTaskService.State.SKIPPED&&
            service.skip(
                taskC.taskId,
                22L
            ).state==
                SlayerTaskService.State.SKIPPED&&
            c.get("slayer:kills").progress==0L,
            "Slayer skip bookkeeping"
        );

        SlayerTaskService.Snapshot taskC2=
            service.assign(
                "player:c",
                "task:blood-revenant",
                23L
            );

        SlayerTaskService.Snapshot cancelled=
            service.cancel(
                taskC2.taskId,
                24L
            );

        require(
            cancelled.state==
                SlayerTaskService.State.CANCELLED&&
            c.get("slayer:kills").progress==0L,
            "Slayer cancel bookkeeping"
        );

        assertGuards(service,ledgers);
        assertImmutable(service);
        assertProtocolBoundary();

        require(
            "family:blood".equals(
                taskA.definition.familyKey
            )&&
            "CUSTOM_LOCALLAB".equals(
                taskA.definition.sourceAuthority
            ),
            "Slayer semantic metadata"
        );

        System.out.println(
            "SLAYER_TASK_SERVICE_PASS "+
            "playerScopedLedger=true "+
            "oneActiveTask=true "+
            "sharedDefinitionIndependent=true "+
            "targetMismatchIgnored=true "+
            "validatedKillProgress=true "+
            "goalClamp=true "+
            "exactCompletion=true "+
            "crossPlayerLeak=false "+
            "externalProgressRefresh=true "+
            "skipBookkeeping=true "+
            "cancelBookkeeping=true "+
            "completeObjectiveAssignmentRejected=true "+
            "missingLedgerFailClosed=true "+
            "missingObjectiveFailClosed=true "+
            "duplicateDefinitionRejected=true "+
            "duplicateObjectiveBindingRejected=true "+
            "pointsMutation=false "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void assertGuards(
        SlayerTaskService service,
        Map<String,ObjectiveProgressService> ledgers
    ){
        ObjectiveProgressService completed=
            ledger("slayer:complete",1L);
        completed.advance(
            "slayer:complete",
            1L
        );
        ledgers.put(
            "player:done",
            completed
        );

        service.registerDefinition(
            new SlayerTaskService.Definition(
                "task:complete",
                "family:standard",
                "npc:test",
                "slayer:complete",
                "CUSTOM_LOCALLAB"
            )
        );

        expect(
            IllegalStateException.class,
            ()->service.assign(
                "player:done",
                "task:complete",
                30L
            ),
            "assign completed objective"
        );

        expect(
            IllegalStateException.class,
            ()->service.assign(
                "player:missing-ledger",
                "task:blood-revenant",
                30L
            ),
            "missing player ledger"
        );

        ObjectiveProgressService missingObjective=
            new ObjectiveProgressService();
        ledgers.put(
            "player:no-objective",
            missingObjective
        );

        expect(
            IllegalArgumentException.class,
            ()->service.assign(
                "player:no-objective",
                "task:blood-revenant",
                30L
            ),
            "missing objective"
        );

        ObjectiveProgressService activeLedger=
            ledger("slayer:active",2L);
        ledgers.put(
            "player:active",
            activeLedger
        );

        service.registerDefinition(
            new SlayerTaskService.Definition(
                "task:active",
                "family:standard",
                "npc:test",
                "slayer:active",
                "CUSTOM_LOCALLAB"
            )
        );

        service.assign(
            "player:active",
            "task:active",
            31L
        );

        expect(
            IllegalStateException.class,
            ()->service.assign(
                "player:active",
                "task:active",
                32L
            ),
            "second active task"
        );

        expect(
            IllegalStateException.class,
            ()->service.registerDefinition(
                new SlayerTaskService.Definition(
                    "task:active",
                    "family:other",
                    "npc:other",
                    "slayer:new",
                    "CUSTOM_LOCALLAB"
                )
            ),
            "duplicate task definition"
        );

        expect(
            IllegalStateException.class,
            ()->service.registerDefinition(
                new SlayerTaskService.Definition(
                    "task:duplicate-objective",
                    "family:other",
                    "npc:other",
                    "slayer:active",
                    "CUSTOM_LOCALLAB"
                )
            ),
            "duplicate objective binding"
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
                "CUSTOM_LOCALLAB"
            )
        );

        return ledger;
    }

    private static void assertImmutable(
        SlayerTaskService service
    ){
        boolean immutable=false;

        try{
            service.snapshot().clear();
        }catch(UnsupportedOperationException expected){
            immutable=true;
        }

        require(
            immutable,
            "Slayer snapshots mutable"
        );
    }

    private static void assertProtocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                SlayerTaskService.class,
                SlayerTaskService.Definition.class,
                SlayerTaskService.Snapshot.class
        }){
            for(Field field:type.getDeclaredFields()){
                String name=field.getName()
                    .toLowerCase(Locale.ROOT);

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("scene")||
                   name.contains("reward")||
                   name.contains("point"))
                    throw new AssertionError(
                        "protocol/reward identity leaked "+
                        type.getSimpleName()+
                        "."+field.getName()
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
                label+" wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private SlayerTaskServiceTest(){}
}

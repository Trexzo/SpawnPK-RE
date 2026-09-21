package spk.local;

public final class ObjectiveProgressBountyIntegrationTest {
    public static void main(String[] args){
        BountyHunterService.PlayerId hunterA=
            new BountyHunterService.PlayerId(
                "player:hunter-a"
            );
        BountyHunterService.PlayerId hunterB=
            new BountyHunterService.PlayerId(
                "player:hunter-b"
            );
        BountyHunterService.PlayerId hunterMissing=
            new BountyHunterService.PlayerId(
                "player:missing"
            );

        ObjectiveProgressService objectivesA=
            objectiveLedger();
        ObjectiveProgressService objectivesB=
            objectiveLedger();

        ObjectiveProgressBountyPort port=
            new ObjectiveProgressBountyPort(
                hunter->{
                    if(hunterA.equals(hunter))
                        return objectivesA;
                    if(hunterB.equals(hunter))
                        return objectivesB;
                    return null;
                }
            );

        BountyHunterService.ObjectiveReference objective=
            new BountyHunterService.ObjectiveReference(
                "BOUNTY:TEST_OBJECTIVE"
            );

        if(!port.exists(hunterA,objective)||
           !port.exists(hunterB,objective)||
           port.isComplete(hunterA,objective)||
           port.isComplete(hunterB,objective))
            throw new AssertionError(
                "hunter-scoped objective adapter initial state wrong"
            );

        BountyHunterService bounty=
            new BountyHunterService(port);

        BountyHunterService.TaskSnapshot taskA=
            bounty.assignTask(
                new BountyHunterService.TaskId(
                    "task:objective-a"
                ),
                hunterA,
                objective,
                10L
            );

        BountyHunterService.TaskSnapshot taskB=
            bounty.assignTask(
                new BountyHunterService.TaskId(
                    "task:objective-b"
                ),
                hunterB,
                objective,
                10L
            );

        objectivesA.advance(
            "bounty:test_objective",
            3L
        );

        if(bounty.refreshTask(
                taskA.id(),
                11L
            ).state()!=
                BountyHunterService.TaskState.COMPLETED)
            throw new AssertionError(
                "hunter A completed objective did not complete own task"
            );

        if(bounty.refreshTask(
                taskB.id(),
                11L
            ).state()!=
                BountyHunterService.TaskState.ACTIVE)
            throw new AssertionError(
                "hunter A objective completion leaked into hunter B task"
            );

        objectivesB.advance(
            "bounty:test_objective",
            2L
        );

        if(bounty.refreshTask(
                taskB.id(),
                12L
            ).state()!=
                BountyHunterService.TaskState.ACTIVE)
            throw new AssertionError(
                "incomplete hunter B objective completed task"
            );

        objectivesB.advance(
            "bounty:test_objective",
            1L
        );

        if(bounty.refreshTask(
                taskB.id(),
                13L
            ).state()!=
                BountyHunterService.TaskState.COMPLETED)
            throw new AssertionError(
                "hunter B own objective completion not observed"
            );

        boolean missingHunterRejected=false;

        try{
            bounty.assignTask(
                new BountyHunterService.TaskId(
                    "task:missing-hunter-ledger"
                ),
                hunterMissing,
                objective,
                20L
            );
        }catch(IllegalArgumentException expected){
            missingHunterRejected=true;
        }

        if(!missingHunterRejected)
            throw new AssertionError(
                "hunter without objective ledger accepted"
            );

        ObjectiveProgressService.Snapshot a=
            objectivesA.get(
                "bounty:test_objective"
            );
        ObjectiveProgressService.Snapshot b=
            objectivesB.get(
                "bounty:test_objective"
            );

        if(a==null||
           b==null||
           a.progress!=3L||
           b.progress!=3L||
           !a.complete||
           !b.complete||
           a.claimed||
           b.claimed)
            throw new AssertionError(
                "bounty adapter mutated objective ownership/state a="+
                a+" b="+b
            );

        System.out.println(
            "OBJECTIVE_PROGRESS_BOUNTY_INTEGRATION_PASS "+
            "hunterScopedLedgers=true "+
            "sameObjectiveKey=true "+
            "crossHunterLeak=false "+
            "readOnlyPort=true "+
            "incompleteTaskActive=true "+
            "completedTaskTransition=true "+
            "missingHunterLedgerFailClosed=true "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static ObjectiveProgressService objectiveLedger(){
        ObjectiveProgressService objectives=
            new ObjectiveProgressService();

        objectives.define(
            new ObjectiveDefinition(
                "bounty:test_objective",
                3L,
                "CUSTOM_LOCALLAB"
            )
        );

        return objectives;
    }

    private ObjectiveProgressBountyIntegrationTest(){}
}

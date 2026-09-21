package spk.local;

public final class ObjectiveProgressBountyIntegrationTest {
    public static void main(String[] args){
        ObjectiveProgressService objectives=
            new ObjectiveProgressService();

        objectives.define(
            new ObjectiveDefinition(
                "bounty:test_objective",
                3L,
                "CUSTOM_LOCALLAB"
            )
        );

        ObjectiveProgressBountyPort port=
            new ObjectiveProgressBountyPort(
                objectives
            );

        BountyHunterService.ObjectiveReference objective=
            new BountyHunterService.ObjectiveReference(
                "BOUNTY:TEST_OBJECTIVE"
            );

        if(!port.exists(objective)||
           port.isComplete(objective))
            throw new AssertionError(
                "objective adapter initial state wrong"
            );

        BountyHunterService bounty=
            new BountyHunterService(port);
        BountyHunterService.PlayerId hunter=
            new BountyHunterService.PlayerId(
                "player:hunter"
            );

        BountyHunterService.TaskSnapshot task=
            bounty.assignTask(
                new BountyHunterService.TaskId(
                    "task:objective-integration"
                ),
                hunter,
                objective,
                10L
            );

        if(task.state()!=
                BountyHunterService.TaskState.ACTIVE)
            throw new AssertionError(
                "new bounty task not active"
            );

        objectives.advance(
            "bounty:test_objective",
            2L
        );

        if(bounty.refreshTask(
                task.id(),
                11L
            ).state()!=
                BountyHunterService.TaskState.ACTIVE)
            throw new AssertionError(
                "incomplete objective completed bounty task"
            );

        objectives.advance(
            "bounty:test_objective",
            1L
        );

        if(!port.isComplete(objective))
            throw new AssertionError(
                "completed objective not visible through port"
            );

        if(bounty.refreshTask(
                task.id(),
                12L
            ).state()!=
                BountyHunterService.TaskState.COMPLETED)
            throw new AssertionError(
                "completed objective did not complete bounty task"
            );

        boolean missingRejected=false;

        try{
            bounty.assignTask(
                new BountyHunterService.TaskId(
                    "task:missing-objective"
                ),
                hunter,
                new BountyHunterService.ObjectiveReference(
                    "bounty:missing"
                ),
                20L
            );
        }catch(IllegalArgumentException expected){
            missingRejected=true;
        }

        if(!missingRejected)
            throw new AssertionError(
                "missing objective accepted"
            );

        ObjectiveProgressService.Snapshot snapshot=
            objectives.get(
                "bounty:test_objective"
            );

        if(snapshot==null||
           snapshot.progress!=3L||
           !snapshot.complete||
           snapshot.claimed)
            throw new AssertionError(
                "bounty adapter mutated objective state "+
                snapshot
            );

        System.out.println(
            "OBJECTIVE_PROGRESS_BOUNTY_INTEGRATION_PASS "+
            "sharedObjectiveLedger=true "+
            "readOnlyPort=true "+
            "incompleteTaskActive=true "+
            "completedTaskTransition=true "+
            "missingObjectiveFailClosed=true "+
            "rewardMutation=false "+
            "protocolIndependent=true"
        );
    }

    private ObjectiveProgressBountyIntegrationTest(){}
}

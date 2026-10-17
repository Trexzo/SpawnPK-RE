package spk.local;

/** G2022: stable recipient may legitimately reserve distinct stages. */
public final class G2022AttemptFenceForeignAndCompletionRefusalsTest {
    public static void main(String[] args)throws Exception{
        G2018TerminalAttemptFenceForeignWorldTest.main(args);
        try(G2017TerminalAttemptFenceRealDeathTest.Fixture f=
                new G2017TerminalAttemptFenceRealDeathTest.Fixture()){
            MonsterSpawnerPvmRuntime.FinalizeResult death=f.settle();
            LocalLabExperimentalTerminalAttemptFence fence=
                new LocalLabExperimentalTerminalAttemptFence(f.world);
            LocalLabExperimentalTerminalAttemptFence.Attempt g1=
                fence.reserve(death,LocalLabTerminalDeliveryAssessment.Consumer.G1);
            LocalLabExperimentalTerminalAttemptFence.Attempt g4=
                fence.reserve(death,LocalLabTerminalDeliveryAssessment.Consumer.G4);
            LocalLabExperimentalTerminalAttemptFence.Snapshot before=fence.snapshot();
            G2017TerminalAttemptFenceRealDeathTest.check(
                g1.observedCurrentGeneration==f.generation&&
                g4.observedCurrentGeneration==f.generation&&
                before.reserved==2&&before.entries.size()==2&&
                before.entries.get(0).observedCurrentGeneration==f.generation&&
                before.refusalCount(
                    LocalLabExperimentalTerminalAttemptFence.Refusal
                        .CONFLICTING_DEATH_EVIDENCE)==0L,
                "same legitimate generation accepts distinct consumers");
            fence.returned(g1);
            fence.threw(g4,new IllegalStateException("sensitive-not-retained"));
            LocalLabExperimentalTerminalAttemptFence.Snapshot after=fence.snapshot();
            G2017TerminalAttemptFenceRealDeathTest.check(
                before.observationCount(
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .RESERVED_UNCERTAIN)==2L&&
                after.observationCount(
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .RETURNED_UNACKNOWLEDGED)==1L&&
                after.observationCount(
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .THREW_MUTATION_UNCERTAIN)==1L&&
                !after.entries.get(1).failureType.contains("sensitive")&&
                f.world.pvmRecords().receiptCount()==1L&&
                !after.hasDurableGrantAcknowledgement(),
                "detached snapshot and two independent non-ACK outcomes");
        }
        System.out.println("G2022_ATTEMPT_FENCE_FOREIGN_COMPLETION_REFUSALS_PASS"+
            " sameGenerationDistinctStagesAllowed=true immutableSnapshots=true"+
            " failureClassOnly=true");
    }
    private G2022AttemptFenceForeignAndCompletionRefusalsTest(){}
}

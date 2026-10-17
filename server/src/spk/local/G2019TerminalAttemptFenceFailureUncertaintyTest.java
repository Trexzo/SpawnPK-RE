package spk.local;

/**
 * G20.1.9: reserving an attempt never implies the callback actually ran.
 * An exception observation is type-only and cannot permit retry.
 */
public final class G2019TerminalAttemptFenceFailureUncertaintyTest {
    public static void main(String[] args)throws Exception{
        try(G2017TerminalAttemptFenceRealDeathTest.Fixture f=
                new G2017TerminalAttemptFenceRealDeathTest.Fixture()){
            MonsterSpawnerPvmRuntime.FinalizeResult terminal=f.settle();
            LocalLabExperimentalTerminalAttemptFence fence=
                new LocalLabExperimentalTerminalAttemptFence(f.world);
            LocalLabExperimentalTerminalAttemptFence.Attempt g1=
                fence.reserve(
                    terminal,LocalLabTerminalDeliveryAssessment.Consumer.G1);
            String rejected=null;
            try{fence.threw(g1,null);}
            catch(NullPointerException expected){
                rejected=expected.getMessage();
            }
            G2017TerminalAttemptFenceRealDeathTest.check(
                "failure".equals(rejected)&&
                fence.snapshot().entries.get(0).observation==
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .RESERVED_UNCERTAIN,
                "missing failure cannot consume reservation");
            fence.threw(g1,new IllegalStateException("private-string-never-stored"));
            LocalLabExperimentalTerminalAttemptFence.Entry entry=
                fence.snapshot().entries.get(0);
            G2017TerminalAttemptFenceRealDeathTest.check(
                entry.observation==
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .THREW_MUTATION_UNCERTAIN&&
                IllegalStateException.class.getName().equals(entry.failureType)&&
                !entry.failureType.contains("private-string")&&
                !fence.snapshot().hasDurableGrantAcknowledgement()&&
                !fence.snapshot().safeToAutomaticallyReplay(),
                "exception only records class and uncertain mutation, no ACK");
            rejected=null;
            try{
                fence.reserve(
                    terminal,LocalLabTerminalDeliveryAssessment.Consumer.G1);
            }catch(IllegalStateException expected){
                rejected=expected.getMessage();
            }
            G2017TerminalAttemptFenceRealDeathTest.check(
                "TERMINAL_ATTEMPT_DUPLICATE_CONSUMER".equals(rejected),
                "THREW consumer cannot automatically retry");
            LocalLabExperimentalTerminalAttemptFence.Attempt g14=
                fence.reserve(
                    terminal,LocalLabTerminalDeliveryAssessment.Consumer.G14);
            LocalLabExperimentalTerminalAttemptFence.Snapshot stale=fence.snapshot();
            fence.returned(g14);
            G2017TerminalAttemptFenceRealDeathTest.check(
                stale.entries.get(1).observation==
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .RESERVED_UNCERTAIN&&
                fence.snapshot().entries.get(1).observation==
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .RETURNED_UNACKNOWLEDGED&&
                f.world.pvmRecords().receiptCount()==1L,
                "later unrelated consumer cannot mutate original death record");
        }
        System.out.println(
            "G2019_TERMINAL_ATTEMPT_FENCE_FAILURE_UNCERTAINTY_PASS"+
            " actualCanonicalDeath=true"+
            " exceptionClassOnly=true"+
            " duplicateAfterFailureDenied=true"+
            " laterConsumerIndependent=true"+
            " noDurableAck=true");
    }
    private G2019TerminalAttemptFenceFailureUncertaintyTest(){}
}

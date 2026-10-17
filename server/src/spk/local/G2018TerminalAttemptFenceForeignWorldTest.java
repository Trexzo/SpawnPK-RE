package spk.local;

/** G20.1.8: foreign Worlds and foreign fence instances cannot cross-use an attempt. */
public final class G2018TerminalAttemptFenceForeignWorldTest {
    public static void main(String[] args)throws Exception{
        try(G2017TerminalAttemptFenceRealDeathTest.Fixture f=
                new G2017TerminalAttemptFenceRealDeathTest.Fixture()){
            World foreign=World.isolatedForTest(60_000L);
            try{
                MonsterSpawnerPvmRuntime.FinalizeResult terminal=f.settle();
                LocalLabExperimentalTerminalAttemptFence source=
                    new LocalLabExperimentalTerminalAttemptFence(f.world);
                LocalLabExperimentalTerminalAttemptFence other=
                    new LocalLabExperimentalTerminalAttemptFence(foreign);
                String rejected=null;
                try{
                    other.reserve(
                        terminal,LocalLabTerminalDeliveryAssessment.Consumer.G1);
                }catch(IllegalStateException expected){
                    rejected=expected.getMessage();
                }
                G2017TerminalAttemptFenceRealDeathTest.check(
                    "SETTLED_DEATH_ENVELOPE_SOURCE_WORLD_MISMATCH".equals(rejected)
                    &&other.snapshot().reserved==0,
                    "foreign World refuses canonical source death");
                LocalLabExperimentalTerminalAttemptFence.Attempt token=
                    source.reserve(
                        terminal,LocalLabTerminalDeliveryAssessment.Consumer.G1);
                rejected=null;
                try{other.returned(token);}
                catch(IllegalStateException expected){
                    rejected=expected.getMessage();
                }
                G2017TerminalAttemptFenceRealDeathTest.check(
                    "TERMINAL_ATTEMPT_FOREIGN_FENCE".equals(rejected)&&
                    other.snapshot().reserved==0&&
                    source.snapshot().entries.get(0).observation==
                        LocalLabExperimentalTerminalAttemptFence.Observation
                            .RESERVED_UNCERTAIN,
                    "foreign instance cannot consume original token");
                source.returned(token);
                rejected=null;
                try{source.threw(token,new IllegalStateException("late"));}
                catch(IllegalStateException expected){
                    rejected=expected.getMessage();
                }
                G2017TerminalAttemptFenceRealDeathTest.check(
                    "TERMINAL_ATTEMPT_ALREADY_OBSERVED".equals(rejected)&&
                    source.snapshot().entries.get(0).observation==
                        LocalLabExperimentalTerminalAttemptFence.Observation
                            .RETURNED_UNACKNOWLEDGED&&
                    f.world.pvmRecords().receiptCount()==1L,
                    "double completion cannot rewrite prior unacknowledged result");
            }finally{
                foreign.close();
            }
        }
        System.out.println(
            "G2018_TERMINAL_ATTEMPT_FENCE_FOREIGN_WORLD_PASS"+
            " realSettledNPC=true"+
            " foreignWorldRefused=true"+
            " foreignFenceRefused=true"+
            " completionOneShot=true"+
            " grantReplayNeverEnabled=true");
    }
    private G2018TerminalAttemptFenceForeignWorldTest(){}
}

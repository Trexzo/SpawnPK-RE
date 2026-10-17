package spk.local;

/**
 * G20.2.0: 43 genuine canonical finalized NPCs generate 258 distinct
 * consumer reservations. The 256-limit must refuse the last two rather
 * than evict prior claims and accidentally permit old-event replay.
 */
public final class G2020TerminalAttemptFenceCapacityFailClosedTest {
    public static void main(String[] args)throws Exception{
        try(G2017TerminalAttemptFenceRealDeathTest.Fixture f=
                new G2017TerminalAttemptFenceRealDeathTest.Fixture()){
            LocalLabExperimentalTerminalAttemptFence fence=
                new LocalLabExperimentalTerminalAttemptFence(f.world);
            LocalLabExperimentalTerminalAttemptFence.Snapshot initial=null;
            int grantedReservations=0,refusals=0;
            MonsterSpawnerPvmRuntime.FinalizeResult earliest=null;
            for(int i=0;i<43;i++){
                MonsterSpawnerPvmRuntime.FinalizeResult terminal=f.settle();
                if(earliest==null)earliest=terminal;
                for(LocalLabTerminalDeliveryAssessment.Consumer stage:
                        LocalLabTerminalDeliveryAssessment.Consumer.values()){
                    try{
                        fence.reserve(terminal,stage);
                        grantedReservations++;
                        if(initial==null)initial=fence.snapshot();
                    }catch(IllegalStateException expected){
                        G2017TerminalAttemptFenceRealDeathTest.check(
                            "TERMINAL_ATTEMPT_CAPACITY_FAIL_CLOSED".equals(
                                expected.getMessage()),"only capacity blocks last two");
                        refusals++;
                    }
                }
            }
            LocalLabExperimentalTerminalAttemptFence.Snapshot after=
                fence.snapshot();
            G2017TerminalAttemptFenceRealDeathTest.check(
                grantedReservations==256&&refusals==2&&
                after.reserved==LocalLabExperimentalTerminalAttemptFence.MAX_ATTEMPTS&&
                after.capacityExhausted&&after.entries.size()==256&&
                initial!=null&&initial.reserved==1&&
                f.world.pvmRecords().receiptCount()==43L&&
                !after.hasDurableGrantAcknowledgement()&&
                !after.safeToAutomaticallyReplay(),
                "in-memory claim capacity strictly fail closed without evicting");
            String rejected=null;
            try{
                fence.reserve(
                    earliest,LocalLabTerminalDeliveryAssessment.Consumer.G1);
            }catch(IllegalStateException expected){
                rejected=expected.getMessage();
            }
            G2017TerminalAttemptFenceRealDeathTest.check(
                "TERMINAL_ATTEMPT_DUPLICATE_CONSUMER".equals(rejected)&&
                fence.snapshot().reserved==256,
                "first real death reservation retained after capacity");
            boolean immutable=false;
            try{after.entries.clear();}
            catch(UnsupportedOperationException expected){immutable=true;}
            G2017TerminalAttemptFenceRealDeathTest.check(immutable&&
                initial.entries.size()==1,
                "bounded snapshots remain immutable and detached");
        }
        System.out.println(
            "G2020_TERMINAL_ATTEMPT_FENCE_CAPACITY_FAIL_CLOSED_PASS"+
            " genuineCanonicalDeaths=43"+
            " capacity=256"+
            " lastTwoReservationsRejected=true"+
            " earliestDuplicateStillRejected=true"+
            " noEvictionOrGrantReplay=true");
    }
    private G2020TerminalAttemptFenceCapacityFailClosedTest(){}
}

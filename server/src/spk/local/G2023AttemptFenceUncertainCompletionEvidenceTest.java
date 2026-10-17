package spk.local;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** G2023: simultaneous duplicate reservations yield exactly one claimant. */
public final class G2023AttemptFenceUncertainCompletionEvidenceTest {
    public static void main(String[] args)throws Exception{
        G2019TerminalAttemptFenceFailureUncertaintyTest.main(args);
        try(G2017TerminalAttemptFenceRealDeathTest.Fixture f=
                new G2017TerminalAttemptFenceRealDeathTest.Fixture()){
            final MonsterSpawnerPvmRuntime.FinalizeResult death=f.settle();
            final LocalLabExperimentalTerminalAttemptFence fence=
                new LocalLabExperimentalTerminalAttemptFence(f.world);
            final int workers=12;
            final CountDownLatch ready=new CountDownLatch(workers);
            final CountDownLatch start=new CountDownLatch(1);
            final AtomicInteger accepted=new AtomicInteger();
            final AtomicInteger denied=new AtomicInteger();
            final AtomicReference<Throwable> unexpected=new AtomicReference<Throwable>();
            final AtomicReference<LocalLabExperimentalTerminalAttemptFence.Attempt>
                winner=new AtomicReference<LocalLabExperimentalTerminalAttemptFence.Attempt>();
            List<Thread> threads=new ArrayList<Thread>();
            for(int i=0;i<workers;i++){
                Thread worker=new Thread(()->{
                    ready.countDown();
                    try{
                        start.await();
                        try{
                            LocalLabExperimentalTerminalAttemptFence.Attempt token=
                                fence.reserve(
                                    death,LocalLabTerminalDeliveryAssessment.Consumer.G1);
                            accepted.incrementAndGet();
                            winner.set(token);
                        }catch(IllegalStateException expected){
                            if("TERMINAL_ATTEMPT_DUPLICATE_CONSUMER".equals(
                                    expected.getMessage()))denied.incrementAndGet();
                            else unexpected.compareAndSet(null,expected);
                        }
                    }catch(Throwable problem){unexpected.compareAndSet(null,problem);}
                },"g2023-reservation-"+i);
                threads.add(worker);
                worker.start();
            }
            G2017TerminalAttemptFenceRealDeathTest.check(
                ready.await(10L,java.util.concurrent.TimeUnit.SECONDS),
                "parallel reserve workers ready");
            start.countDown();
            for(Thread worker:threads){
                worker.join(10000L);
                G2017TerminalAttemptFenceRealDeathTest.check(
                    !worker.isAlive(),"parallel worker terminated");
            }
            LocalLabExperimentalTerminalAttemptFence.Snapshot before=fence.snapshot();
            G2017TerminalAttemptFenceRealDeathTest.check(
                unexpected.get()==null&&
                accepted.get()==1&&denied.get()==workers-1&&
                before.reserved==1&&
                before.refusalCount(
                    LocalLabExperimentalTerminalAttemptFence.Refusal
                        .DUPLICATE_CONSUMER)==workers-1&&
                before.observationCount(
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .RESERVED_UNCERTAIN)==1L&&
                f.world.pvmRecords().receiptCount()==1L,
                "exactly one synchronized claim; no duplicate settlement");
            fence.returned(winner.get());
            G2017TerminalAttemptFenceRealDeathTest.check(
                before.entries.get(0).observation==
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .RESERVED_UNCERTAIN&&
                fence.snapshot().entries.get(0).observation==
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .RETURNED_UNACKNOWLEDGED&&
                !fence.snapshot().safeToAutomaticallyReplay(),
                "late completion never upgrades existing snapshot to grant ACK");
        }
        System.out.println("G2023_ATTEMPT_FENCE_UNCERTAIN_COMPLETION_EVIDENCE_PASS"+
            " parallelSameStageWinner=1 parallelDuplicateDenials=11"+
            " historicalSnapshotDetached=true");
    }
    private G2023AttemptFenceUncertainCompletionEvidenceTest(){}
}

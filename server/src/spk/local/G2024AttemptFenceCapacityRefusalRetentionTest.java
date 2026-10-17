package spk.local;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** G2024: six distinct stages race pairwise without duplicate token issuance. */
public final class G2024AttemptFenceCapacityRefusalRetentionTest {
    public static void main(String[] args)throws Exception{
        G2020TerminalAttemptFenceCapacityFailClosedTest.main(args);
        try(G2017TerminalAttemptFenceRealDeathTest.Fixture f=
                new G2017TerminalAttemptFenceRealDeathTest.Fixture()){
            final MonsterSpawnerPvmRuntime.FinalizeResult death=f.settle();
            final LocalLabExperimentalTerminalAttemptFence fence=
                new LocalLabExperimentalTerminalAttemptFence(f.world);
            final LocalLabTerminalDeliveryAssessment.Consumer[] stages=
                LocalLabTerminalDeliveryAssessment.Consumer.values();
            final int workers=stages.length*2;
            final CountDownLatch ready=new CountDownLatch(workers);
            final CountDownLatch start=new CountDownLatch(1);
            final AtomicInteger accepted=new AtomicInteger();
            final AtomicInteger duplicates=new AtomicInteger();
            final AtomicReference<Throwable> unexpected=new AtomicReference<Throwable>();
            List<Thread> threads=new ArrayList<Thread>();
            for(LocalLabTerminalDeliveryAssessment.Consumer consumer:stages){
                for(int i=0;i<2;i++){
                    Thread worker=new Thread(()->{
                        ready.countDown();
                        try{
                            start.await();
                            try{
                                LocalLabExperimentalTerminalAttemptFence.Attempt token=
                                    fence.reserve(death,consumer);
                                accepted.incrementAndGet();
                                if(consumer==
                                    LocalLabTerminalDeliveryAssessment.Consumer.G14)
                                    fence.threw(token,new IllegalStateException("redacted"));
                                else fence.returned(token);
                            }catch(IllegalStateException expected){
                                if("TERMINAL_ATTEMPT_DUPLICATE_CONSUMER".equals(
                                        expected.getMessage()))duplicates.incrementAndGet();
                                else unexpected.compareAndSet(null,expected);
                            }
                        }catch(Throwable error){unexpected.compareAndSet(null,error);}
                    },"g2024-"+consumer+"-"+i);
                    threads.add(worker);
                    worker.start();
                }
            }
            G2017TerminalAttemptFenceRealDeathTest.check(
                ready.await(10L,java.util.concurrent.TimeUnit.SECONDS),
                "mixed-stage reservation workers ready");
            start.countDown();
            for(Thread worker:threads){
                worker.join(10000L);
                G2017TerminalAttemptFenceRealDeathTest.check(
                    !worker.isAlive(),"mixed-stage worker terminated");
            }
            LocalLabExperimentalTerminalAttemptFence.Snapshot report=fence.snapshot();
            G2017TerminalAttemptFenceRealDeathTest.check(
                unexpected.get()==null&&accepted.get()==6&&duplicates.get()==6&&
                report.reserved==6&&report.entries.size()==6&&
                report.refusalCount(
                    LocalLabExperimentalTerminalAttemptFence.Refusal
                        .DUPLICATE_CONSUMER)==6L&&
                report.observationCount(
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .RETURNED_UNACKNOWLEDGED)==5L&&
                report.observationCount(
                    LocalLabExperimentalTerminalAttemptFence.Observation
                        .THREW_MUTATION_UNCERTAIN)==1L&&
                f.world.pvmRecords().receiptCount()==1L&&
                !report.hasDurableGrantAcknowledgement()&&
                !report.safeToAutomaticallyReplay(),
                "one race winner per stage, no grant callback, no duplicated settlement");
        }
        System.out.println("G2024_ATTEMPT_FENCE_CAPACITY_REFUSAL_RETENTION_PASS"+
            " capacityGate43Deaths=true parallelSixStageWinners=6"+
            " parallelDuplicateDenials=6 noGrantAck=true");
    }
    private G2024AttemptFenceCapacityRefusalRetentionTest(){}
}

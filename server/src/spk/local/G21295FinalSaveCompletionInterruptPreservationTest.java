package spk.local;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** G21.295 real second-thread interruption of the production wait helper. */
public final class G21295FinalSaveCompletionInterruptPreservationTest {
    private G21295FinalSaveCompletionInterruptPreservationTest(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.295 "+why);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        CompletableFuture<Void> pending=new CompletableFuture<>();
        CountDownLatch entered=new CountDownLatch(1);
        AtomicBoolean interrupted=new AtomicBoolean(false);
        AtomicBoolean retained=new AtomicBoolean(false);
        AtomicReference<Throwable> problem=new AtomicReference<>();
        Thread caller=new Thread(()->{
            try{
                entered.countDown();
                LocalFinalSaveCompletionWait.awaitConfirmed(
                    pending,30_000L);
                problem.set(new AssertionError(
                    "interrupted waiter claimed a final save"));
            }catch(InterruptedException expected){
                interrupted.set(true);
                retained.set(Thread.currentThread().isInterrupted());
            }catch(Throwable unexpected){
                problem.set(unexpected);
            }
        },"g21295-final-save-waiter");
        caller.start();
        try{
            check(entered.await(3L,TimeUnit.SECONDS),"no waiter");
            caller.interrupt();
            caller.join(3_000L);
            check(!caller.isAlive()&&problem.get()==null&&
                interrupted.get()&&retained.get(),
                "interrupt not propagated and restored");
            check(!pending.isDone()&&!pending.isCancelled(),
                "worker completion was canceled by wait interruption");
            pending.complete(null);
            LocalFinalSaveCompletionWait.awaitConfirmed(pending,100L);
            System.out.println(
                "G21295_FINAL_SAVE_INTERRUPT_PRESERVATION_PASS"+
                " callerFlagRestored=true"+
                " falseConfirmationDenied=true"+
                " workerFutureUnaffected=true"+
                " durableOwnerEpoch=false");
        }finally{
            if(caller.isAlive()){
                caller.interrupt();
                caller.join(3_000L);
            }
        }
    }
}

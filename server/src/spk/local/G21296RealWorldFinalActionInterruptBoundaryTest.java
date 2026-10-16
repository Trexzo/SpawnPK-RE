package spk.local;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * G21.296 real World.submit action interrupted at the bounded
 * caller wait. A caller's interrupt cannot cancel the World action.
 */
public final class G21296RealWorldFinalActionInterruptBoundaryTest {
    private G21296RealWorldFinalActionInterruptBoundaryTest(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.296 "+why);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        World world=World.isolatedForTest(50L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        CountDownLatch actionEntered=new CountDownLatch(1);
        CountDownLatch actionRelease=new CountDownLatch(1);
        try{
            generation=world.registerPlayer(player,
                "g21296-world-final-action");
            world.start();
            CompletableFuture<Void> action=world.submit(
                player,generation,()->{
                    actionEntered.countDown();
                    try{
                        if(!actionRelease.await(10L,TimeUnit.SECONDS))
                            throw new IllegalStateException(
                                "World action release deadline");
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                            "World action unexpectedly interrupted",
                            interrupted);
                    }
                });
            check(actionEntered.await(5L,TimeUnit.SECONDS),
                "World command did not start");
            CountDownLatch waiterStarted=new CountDownLatch(1);
            AtomicBoolean intercepted=new AtomicBoolean(false);
            AtomicBoolean bitRestored=new AtomicBoolean(false);
            AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread waiter=new Thread(()->{
                try{
                    waiterStarted.countDown();
                    LocalFinalSaveCompletionWait.awaitConfirmed(
                        action,30_000L);
                    failure.set(new AssertionError(
                        "interrupted World action wait returned success"));
                }catch(InterruptedException expected){
                    intercepted.set(true);
                    bitRestored.set(Thread.currentThread().isInterrupted());
                }catch(Throwable unexpected){
                    failure.set(unexpected);
                }
            },"g21296-world-final-wait");
            waiter.start();
            try{
                check(waiterStarted.await(3L,TimeUnit.SECONDS),
                    "caller never entered World command wait");
                waiter.interrupt();
                waiter.join(3_000L);
                check(!waiter.isAlive()&&failure.get()==null&&
                    intercepted.get()&&bitRestored.get(),
                    "World command interrupt signal was lost");
                check(!action.isDone()&&!action.isCancelled(),
                    "bounded interrupted caller canceled running World action");
            }finally{
                actionRelease.countDown();
                if(waiter.isAlive()){
                    waiter.interrupt();
                    waiter.join(3_000L);
                }
            }
            action.get(5L,TimeUnit.SECONDS);
            check(!action.isCompletedExceptionally(),
                "independently owned World action did not complete");
            System.out.println(
                "G21296_REAL_WORLD_FINAL_ACTION_INTERRUPT_PASS"+
                " realWorldSubmit=true"+
                " callerInterruptRestored=true"+
                " worldActionNotCanceled=true"+
                " noRetroactiveFinalSaveAck=true");
        }finally{
            actionRelease.countDown();
            if(generation>0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }
}

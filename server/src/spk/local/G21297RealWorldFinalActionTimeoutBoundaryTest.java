package spk.local;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * G21.297 real in-flight World command outlives timed-out caller.
 * The exact-generation World command can still settle afterward;
 * no inference that a final save was committed by the earlier timeout.
 */
public final class G21297RealWorldFinalActionTimeoutBoundaryTest {
    private G21297RealWorldFinalActionTimeoutBoundaryTest(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.297 "+why);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        World world=World.isolatedForTest(50L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        CountDownLatch entered=new CountDownLatch(1);
        CountDownLatch release=new CountDownLatch(1);
        try{
            generation=world.registerPlayer(player,
                "g21297-world-final-timeout");
            world.start();
            CompletableFuture<Void> action=world.submit(
                player,generation,()->{
                    entered.countDown();
                    try{
                        if(!release.await(10L,TimeUnit.SECONDS))
                            throw new IllegalStateException(
                                "World action never released");
                    }catch(InterruptedException e){
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(
                            "Unexpected World interruption",e);
                    }
                });
            check(entered.await(5L,TimeUnit.SECONDS),
                "World command did not enter");
            boolean timedOut=false;
            try{
                LocalFinalSaveCompletionWait.awaitConfirmed(action,15L);
            }catch(TimeoutException expected){
                timedOut=true;
            }
            check(timedOut&&!action.isDone()&&!action.isCancelled(),
                "World action was canceled or falsely acknowledged");
            release.countDown();
            action.get(5L,TimeUnit.SECONDS);
            LocalFinalSaveCompletionWait.awaitConfirmed(action,100L);
            check(!action.isCompletedExceptionally(),
                "World action did not complete independently");
            System.out.println(
                "G21297_REAL_WORLD_FINAL_ACTION_TIMEOUT_PASS"+
                " realWorldSubmit=true"+
                " timedOutCallerDidNotCancel=true"+
                " independentLateCommandSettlement=true"+
                " finalSaveWorkerReceiptNotProven=true");
        }finally{
            release.countDown();
            if(generation>0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }
}

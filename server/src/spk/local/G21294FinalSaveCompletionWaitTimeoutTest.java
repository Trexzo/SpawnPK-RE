package spk.local;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** G21.294: no false confirmed final-save completion. */
public final class G21294FinalSaveCompletionWaitTimeoutTest {
    private G21294FinalSaveCompletionWaitTimeoutTest(){}
    private static void check(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.294 "+why);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        CompletableFuture<Void> pending=new CompletableFuture<>();
        boolean timeout=false;
        try{
            LocalFinalSaveCompletionWait.awaitConfirmed(pending,10L);
        }catch(TimeoutException expected){timeout=true;}
        check(timeout&&!pending.isDone()&&!pending.isCancelled(),
            "caller timeout canceled worker future or claimed success");
        // Settlement after caller timeout remains independently observable,
        // but cannot retroactively release an uncertain prior session.
        pending.complete(null);
        LocalFinalSaveCompletionWait.awaitConfirmed(pending,100L);
        CompletableFuture<Void> failed=new CompletableFuture<>();
        failed.completeExceptionally(new IOException("g21294-worker-failure"));
        boolean failure=false;
        try{
            LocalFinalSaveCompletionWait.awaitConfirmed(failed,100L);
        }catch(ExecutionException expected){
            failure=expected.getCause() instanceof IOException&&
                "g21294-worker-failure".equals(
                    expected.getCause().getMessage());
        }
        check(failure,"worker error incorrectly reported successful");
        CompletableFuture<Void> canceled=new CompletableFuture<>();
        canceled.cancel(false);
        boolean cancellation=false;
        try{
            LocalFinalSaveCompletionWait.awaitConfirmed(canceled,100L);
        }catch(java.util.concurrent.CancellationException expected){
            cancellation=true;
        }
        check(cancellation,"cancelled worker incorrectly successful");
        boolean invalid=false;
        try{
            LocalFinalSaveCompletionWait.awaitConfirmed(
                new CompletableFuture<Void>(),0L);
        }catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"nonpositive timeout not rejected");
        System.out.println(
            "G21294_FINAL_SAVE_WAIT_TIMEOUT_AND_FAILURE_PASS"+
            " timeoutDoesNotCancelWorker=true"+
            " laterSettlementIndependent=true"+
            " workerFailureAndCancellationReject=true"+
            " durableReceipt=false");
    }
}

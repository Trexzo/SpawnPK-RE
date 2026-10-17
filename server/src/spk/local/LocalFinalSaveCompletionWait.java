package spk.local;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * G21.294 bounded LocalSession final-save worker wait.
 * Worker future success != an authenticated, crash-durable save receipt.
 * No cancellation or retry is performed on timeout or interruption.
 */
final class LocalFinalSaveCompletionWait {
    private LocalFinalSaveCompletionWait(){}
    static void awaitConfirmed(
        CompletableFuture<Void> completion,long timeoutMillis
    )throws InterruptedException,ExecutionException,TimeoutException{
        Objects.requireNonNull(completion,"completion");
        if(timeoutMillis<=0L)
            throw new IllegalArgumentException(
                "G21.294 FINAL_SAVE_WAIT_TIMEOUT_MUST_BE_POSITIVE");
        try{
            completion.get(timeoutMillis,TimeUnit.MILLISECONDS);
        }catch(InterruptedException interrupted){
            // Future.get clears this flag. Keep shutdown/cancellation
            // visible to the LocalSession thread after its broad catch.
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }
}

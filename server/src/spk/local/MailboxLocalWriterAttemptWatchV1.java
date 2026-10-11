package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * G21.116: ephemeral negative-only SAME-JVM attempt monitor.
 *
 * Only callers entering G21.100 through this JVM notify this monitor.
 * It is NOT a filesystem fence, historical census, proof of all writer
 * generations, cross-JVM observer, or safe archival/settlement token.
 *
 * No persistent sidecars, retained account histories, timers or I/O.
 * Registry holds one bounded boolean per actively watched account;
 * each scope is removed in finally/AutoCloseable.close().
 */
final class MailboxLocalWriterAttemptWatchV1 {
    private static final Object MUTEX=new Object();
    private static final Map<Path,Scope> WATCHES=new HashMap<>();

    static final class Scope implements AutoCloseable {
        private final Path accountFile;
        private boolean attempted;
        private boolean closed;

        private Scope(Path file){accountFile=file;}

        boolean sawAttempt(){
            synchronized(MUTEX){return attempted;}
        }

        @Override public void close(){
            synchronized(MUTEX){
                if(closed)return;
                if(WATCHES.get(accountFile)!=this)
                    throw new IllegalStateException(
                        "G21.116 WATCH_IDENTITY_CHANGED_NO_AUTHORITY");
                WATCHES.remove(accountFile);
                closed=true;
            }
        }
    }

    static Scope arm(Path accountFile)throws IOException{
        Path selected=Objects.requireNonNull(accountFile,"account file")
            .toAbsolutePath().normalize();
        if(selected.getParent()==null)
            throw new IllegalArgumentException(
                "G21.116 account has no parent");
        synchronized(MUTEX){
            if(WATCHES.containsKey(selected))
                throw new IOException(
                    "G21.116 CONCURRENT_LOCAL_WATCH_NO_AUTHORITY");
            Scope watch=new Scope(selected);
            WATCHES.put(selected,watch);
            return watch;
        }
    }

    // Called with G21.100 lifecycle ownership. No nested filesystem
    // lease, disk read/write or user code while MUTEX is held.
    static void noteLocalAttempt(Path accountFile){
        Path selected=Objects.requireNonNull(accountFile,"account file")
            .toAbsolutePath().normalize();
        synchronized(MUTEX){
            Scope watch=WATCHES.get(selected);
            if(watch!=null)watch.attempted=true;
        }
    }

    static int activeLocalWatchCount(){
        synchronized(MUTEX){return WATCHES.size();}
    }

    /**
     * Explicit negative-only opt-in around the entire G21.115
     * five-plane observation. Exact account selected ONCE. Any
     * G21.100-cooperating writer attempt from THIS JVM while armed
     * vetoes, even if it fails or writes identical bytes.
     *
     * Another JVM, pre-arm in-flight writer, raw file mutation or
     * historical default writer may be completely invisible. Even
     * successful checks never prove future quiescence or rollover.
     */
    static MailboxWriterArchiveLiveReconciliationV1.Report
        comparePinnedTypedRecoveryLeavesWatchLocalAttempts(
        FilePlayerRepository.PathResolver resolver,
        String account,String v1Portable,String v1Sha,
        String accountSha,String preparedPin,String commitPin,
        String v2Sha,
        MailboxWriterArchiveLiveReconciliationV1.BetweenPasses between
    )throws IOException{
        Objects.requireNonNull(resolver,"resolver");
        Objects.requireNonNull(between,"between");
        if(account==null||!account.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException(
                "G21.116 INVALID_CANONICAL_ACCOUNT");
        final Path selected=Objects.requireNonNull(
            resolver.resolve(account),"account file")
            .toAbsolutePath().normalize();
        FilePlayerRepository.PathResolver exact=who->{
            if(!account.equals(who))
                throw new IllegalArgumentException(
                    "G21.116 ACCOUNT_RESOLVER_DRIFT");
            return selected;
        };
        try(Scope watch=arm(selected)){
            MailboxWriterArchiveLiveReconciliationV1.Report observed=
                MailboxWriterArchiveLiveReconciliationV1
                    .comparePinnedTypedRecoveryLeaves(
                        exact,account,v1Portable,v1Sha,
                        accountSha,preparedPin,commitPin,v2Sha,between);
            if(watch.sawAttempt())
                throw new IOException(
                    "G21.116 LOCAL_WRITER_ATTEMPT_SEEN_NO_AUTHORITY "+
                    "account="+account+
                    " lifetimeFence=false rollover=false cleanup=false");
            return observed;
        }
    }

    private MailboxLocalWriterAttemptWatchV1(){}
}

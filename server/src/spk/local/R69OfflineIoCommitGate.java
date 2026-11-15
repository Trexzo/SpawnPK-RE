package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * R69 OFFLINE ONLY: actual WorldPlayerPersistence worker-commit gate.
 *
 * An explicitly bound R64 owner token travels alongside one queued
 * immutable save sequence. The writer must acquire R60's OS lock and
 * revalidate R64/R63/R59 on THIS I/O THREAD before the actual repository
 * can be reached. The same repository is passed to R63 for the one
 * canonical write and verified before advancing account+owner records.
 *
 * No World/LocalSession installs this gate in production. Every unbound
 * queued write, including checkpoint coalescing, FAILS CLOSED while this
 * isolated test hook is active. It does not invent a session token.
 */
final class R69OfflineIoCommitGate
        implements WorldPlayerPersistence.OfflineIoWriteAdmission {
    static final class Pause {
        final CountDownLatch arrived=new CountDownLatch(1);
        final CountDownLatch release=new CountDownLatch(1);
    }

    private final Path journal,accounts,generations,sessions;
    private final ConcurrentHashMap<Long,R64OfflineSessionOwnership.Token>
        boundTokens=new ConcurrentHashMap<>();
    private final AtomicReference<Pause> pauseNext=new AtomicReference<>();
    private final AtomicInteger attempts=new AtomicInteger();
    private final AtomicInteger refusedUnbound=new AtomicInteger();
    private final AtomicInteger successful=new AtomicInteger();

    R69OfflineIoCommitGate(
        Path journal,Path accounts,Path generations,Path sessions
    ){
        this.journal=Objects.requireNonNull(journal,"journal");
        this.accounts=Objects.requireNonNull(accounts,"accounts");
        this.generations=Objects.requireNonNull(generations,"generations");
        this.sessions=Objects.requireNonNull(sessions,"sessions");
    }

    void bindForTest(
        WorldPlayerPersistence.CapturedSave captured,
        R64OfflineSessionOwnership.Token owner
    )throws IOException{
        Objects.requireNonNull(captured,"captured");
        Objects.requireNonNull(owner,"owner");
        if(!captured.ticket.snapshot.username().equals(owner.username)||
           owner.phase!=R64OfflineSessionOwnership.Phase.ACTIVE)
            throw new IOException(
                "R69_BIND_DENIED account/owner mismatch"
            );
        if(boundTokens.putIfAbsent(
                captured.ticket.sequence,owner)!=null)
            throw new IOException(
                "R69_BIND_DENIED duplicate save sequence"
            );
    }

    void pauseNextCommit(Pause pause){
        Objects.requireNonNull(pause,"pause");
        if(!pauseNext.compareAndSet(null,pause))
            throw new IllegalStateException(
                "R69_PAUSE_DENIED already armed"
            );
    }

    @Override public void persistAtCommit(
        long sequence,PlayerSnapshot snapshot,
        PlayerRepository actualRepository,boolean checkpoint
    )throws IOException{
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(actualRepository,"actual repository");
        attempts.incrementAndGet();

        // No automatic authority inference from username, World actor
        // generation, current owner record or snapshot content.
        R64OfflineSessionOwnership.Token expected=boundTokens.remove(sequence);
        if(checkpoint||expected==null){
            refusedUnbound.incrementAndGet();
            throw new IOException(
                "R69_IO_COMMIT_DENIED unbound checkpoint/save"
            );
        }
        if(!expected.username.equals(snapshot.username()))
            throw new IOException(
                "R69_IO_COMMIT_DENIED foreign captured snapshot"
            );

        Pause pause=pauseNext.getAndSet(null);
        if(pause!=null){
            pause.arrived.countDown();
            try{
                if(!pause.release.await(15L,TimeUnit.SECONDS))
                    throw new IOException(
                        "R69_IO_COMMIT_DENIED test-only writer timeout"
                    );
            }catch(InterruptedException e){
                Thread.currentThread().interrupt();
                throw new IOException(
                    "R69_IO_COMMIT_DENIED test worker interrupted",e
                );
            }
        }

        R60TradePairCoordinator.withExclusive(journal,false,()->{
            // The existing R64/R63 code rechecks persisted account,
            // session and journal evidence before it calls the actual
            // WorldPlayerPersistence repository's save method. It then
            // advances the generation and session-owner records while
            // still holding the SAME cross-process OS lock.
            R64OfflineSessionOwnership.saveOwnedUnderLock(
                journal,accounts,generations,sessions,
                expected,snapshot,false,actualRepository
            );
            successful.incrementAndGet();
            return null;
        });
    }

    int attempts(){return attempts.get();}
    int refusedUnbound(){return refusedUnbound.get();}
    int successful(){return successful.get();}
    int pendingTokens(){return boundTokens.size();}
}

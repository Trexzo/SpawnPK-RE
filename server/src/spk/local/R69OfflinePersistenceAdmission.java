package spk.local;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R69 OFFLINE ONLY: recheck the captured R64 owner at the actual
 * WorldPlayerPersistence IO-commit boundary, under R60's OS lock.
 *
 * The first queued write pauses before taking the lock so that a separate
 * JVM can replace its owner deterministically. After release, even a
 * formerly valid immutable snapshot is refused before the repository writer.
 *
 * This is deliberately test-only. An authorized raw repository write would
 * still need coordinated R63 generation and R64 owner-record publication.
 * Neither the live World nor LocalSession installs this hook.
 */
final class R69OfflinePersistenceAdmission
        implements WorldPlayerPersistence.OfflineSaveAdmission {
    private final Path journal;
    private final Path accounts;
    private final Path generations;
    private final Path sessions;
    private final R64OfflineSessionOwnership.Token owner;
    private final CountDownLatch firstWriteEntered;
    private final CountDownLatch releaseFirstWrite;
    private final AtomicInteger attempted=new AtomicInteger();
    private final AtomicInteger admitted=new AtomicInteger();

    R69OfflinePersistenceAdmission(
        Path journal,Path accounts,Path generations,Path sessions,
        R64OfflineSessionOwnership.Token owner,
        CountDownLatch firstWriteEntered,
        CountDownLatch releaseFirstWrite
    ){
        this.journal=Objects.requireNonNull(journal,"journal");
        this.accounts=Objects.requireNonNull(accounts,"accounts");
        this.generations=Objects.requireNonNull(generations,"generations");
        this.sessions=Objects.requireNonNull(sessions,"sessions");
        this.owner=Objects.requireNonNull(owner,"owner");
        this.firstWriteEntered=Objects.requireNonNull(
            firstWriteEntered,"firstWriteEntered"
        );
        this.releaseFirstWrite=Objects.requireNonNull(
            releaseFirstWrite,"releaseFirstWrite"
        );
    }

    @Override public void withAdmission(
        PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter writer
    )throws IOException{
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(writer,"writer");
        if(!owner.username.equals(snapshot.username()))
            throw new IOException(
                "R69_SAVE_DENIED cross-account snapshot"
            );

        int sequence=attempted.incrementAndGet();
        if(sequence==1){
            firstWriteEntered.countDown();
            try{
                if(!releaseFirstWrite.await(15L,TimeUnit.SECONDS))
                    throw new IOException(
                        "R69_SAVE_DENIED test writer release timed out"
                    );
            }catch(InterruptedException interrupted){
                Thread.currentThread().interrupt();
                throw new IOException(
                    "R69_SAVE_DENIED test writer interrupted",
                    interrupted
                );
            }
        }

        R60TradePairCoordinator.withExclusive(journal,false,()->{
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,owner
            );
            admitted.incrementAndGet();
            writer.run();
            return null;
        });
    }

    int attempted(){return attempted.get();}
    int admitted(){return admitted.get();}
}

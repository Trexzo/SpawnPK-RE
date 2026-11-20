package spk.local;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * R85 OFFLINE ONLY. A cooperating-writer admission wrapper around the R84
 * disposable single-file model. Does not change the R84 negative control,
 * install a live server hook, authenticate an account, or authorize replay.
 *
 * Hold a real OS FileLock from canonical re-read and freshness check THROUGH
 * R84's replacement and post-commit verification. An open writer is stale
 * after ANY other committed change, even by the same nominal owner epoch.
 *
 * The logical generation is a monotone property of the limited R84 fixture:
 * 0 initially, 1 after lethal commit, plus one per consumer checkpoint.
 * It is NOT a general-purpose journal revision, distributed lease or
 * production generation. A byte-exact CAS precondition additionally catches
 * same-generation different-state substitutions.
 *
 * All writers to this fixture MUST cooperate through this wrapper. The old
 * raw R84 API intentionally remains callable by R84's negative control and
 * can bypass this lock; no hostile-client security claim is made.
 */
final class R85OfflineFencedCombatRecord {
    private static final String CANONICAL="r84-offline-record.txt";
    private static final String LOCK="r85-offline-writer.lock";

    @FunctionalInterface private interface Checked<T> {
        T run()throws IOException;
    }
    @FunctionalInterface private interface Mutation {
        void apply(R84OfflineAtomicCombatRecord fresh)throws IOException;
    }

    private final Path root;
    private final long ownerEpoch;
    private byte[] expectedBytes;
    private long expectedGeneration;
    private R84OfflineAtomicCombatRecord.State cached;
    private boolean poisoned;

    private R85OfflineFencedCombatRecord(
        Path root,long ownerEpoch,byte[] bytes,
        R84OfflineAtomicCombatRecord.State state
    ){
        this.root=root;
        this.ownerEpoch=ownerEpoch;
        this.expectedBytes=bytes.clone();
        this.expectedGeneration=logicalGeneration(state);
        this.cached=state;
    }

    static Path lockFileForTest(Path root){
        return Objects.requireNonNull(root,"root").resolve(LOCK);
    }

    static R85OfflineFencedCombatRecord create(
        Path root,long ownerEpoch)throws IOException{
        Objects.requireNonNull(root,"root");
        if(ownerEpoch<1)throw new IllegalArgumentException("ownerEpoch");
        if(Files.isSymbolicLink(root))
            throw new IOException("R85_LOCK_DENIED symbolic root");
        Files.createDirectories(root);
        return withLock(root,()->{
            R84OfflineAtomicCombatRecord.create(root,ownerEpoch);
            R84OfflineAtomicCombatRecord.State state=
                validatedState(root,ownerEpoch);
            return new R85OfflineFencedCombatRecord(
                root,ownerEpoch,canonicalBytes(root),state
            );
        });
    }

    static R85OfflineFencedCombatRecord reopen(
        Path root,long ownerEpoch)throws IOException{
        Objects.requireNonNull(root,"root");
        return withLock(root,()->{
            R84OfflineAtomicCombatRecord.State state=
                validatedState(root,ownerEpoch);
            return new R85OfflineFencedCombatRecord(
                root,ownerEpoch,canonicalBytes(root),state
            );
        });
    }

    synchronized long observedGeneration(){
        requireUsable();
        return expectedGeneration;
    }

    synchronized R84OfflineAtomicCombatRecord.State observedState(){
        requireUsable();
        return cached;
    }

    synchronized void commitLethal(
        R84OfflineAtomicCombatRecord.Fault fault)throws IOException{
        Objects.requireNonNull(fault,"fault");
        commit(record->record.commitLethal(fault));
    }

    synchronized void commitConsumer(
        R84OfflineAtomicCombatRecord.Consumer consumer,
        R84OfflineAtomicCombatRecord.Fault fault)throws IOException{
        Objects.requireNonNull(consumer,"consumer");
        Objects.requireNonNull(fault,"fault");
        commit(record->record.commitConsumer(consumer,fault));
    }

    private void commit(Mutation mutation)throws IOException{
        requireUsable();
        withLock(root,()->{
            // No mutation, even an idempotent one, before canonical
            // identity + epoch + exact-generation freshness admission.
            try{
                R84OfflineAtomicCombatRecord.State latest=
                    validatedState(root,ownerEpoch);
                byte[] actual=canonicalBytes(root);
                long actualGeneration=logicalGeneration(latest);
                if(actualGeneration!=expectedGeneration ||
                   !Arrays.equals(actual,expectedBytes)){
                    poisoned=true;
                    throw new IOException(
                        "R85_STALE_GENERATION expected="+expectedGeneration+
                        " actual="+actualGeneration
                    );
                }
                R84OfflineAtomicCombatRecord fresh=
                    R84OfflineAtomicCombatRecord.reopen(root,ownerEpoch);
                mutation.apply(fresh);
                R84OfflineAtomicCombatRecord.State committed=
                    validatedState(root,ownerEpoch);
                long committedGeneration=logicalGeneration(committed);
                if(committedGeneration<expectedGeneration ||
                   committedGeneration>expectedGeneration+1L){
                    throw new IOException(
                        "R85_COMMIT_DENIED unexpected generation delta"
                    );
                }
                byte[] nextBytes=canonicalBytes(root);
                // No mutable reference escapes until all checks complete.
                expectedBytes=nextBytes.clone();
                expectedGeneration=committedGeneration;
                cached=committed;
            }catch(IOException|RuntimeException failure){
                // Mutation may have atomically renamed before throwing.
                // Fail closed until explicit reopen/reconciliation.
                poisoned=true;
                throw failure;
            }
            return null;
        });
    }

    private static R84OfflineAtomicCombatRecord.State validatedState(
        Path root,long epoch)throws IOException{
        R84OfflineAtomicCombatRecord.State state=
            R84OfflineAtomicCombatRecord.reopen(root,epoch).snapshot();
        byte[] actual=canonicalBytes(root);
        byte[] canonical=state.serialize().getBytes(StandardCharsets.UTF_8);
        if(!Arrays.equals(actual,canonical))
            throw new IOException(
                "R85_RECORD_QUARANTINED noncanonical source bytes"
            );
        return state;
    }

    private static byte[] canonicalBytes(Path root)throws IOException{
        Path file=root.resolve(CANONICAL);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
            throw new IOException(
                "R85_RECORD_QUARANTINED missing canonical record"
            );
        // R84's record is a tiny synthetic text file, never large live data.
        return Files.readAllBytes(file);
    }

    private static long logicalGeneration(
        R84OfflineAtomicCombatRecord.State s){
        return s.sequence+
            (s.objectiveCheckpoint?1L:0L)+
            (s.matchCheckpoint?1L:0L)+
            (s.collectionCheckpoint?1L:0L);
    }

    private static <T> T withLock(
        Path root,Checked<T> action)throws IOException{
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) ||
           Files.isSymbolicLink(root))
            throw new IOException("R85_LOCK_DENIED invalid fixture root");
        Path lock=lockFileForTest(root);
        if(Files.isSymbolicLink(lock))
            throw new IOException("R85_LOCK_DENIED symbolic lock file");
        // Never delete this lock file. Unlink/recreate while another process
        // holds the old inode/file could create disjoint lock domains.
        try(FileChannel channel=FileChannel.open(lock,
                StandardOpenOption.CREATE,StandardOpenOption.WRITE)){
            if(Files.isSymbolicLink(lock))
                throw new IOException("R85_LOCK_DENIED symbolic lock file");
            try(FileLock held=channel.tryLock()){
                if(held==null)
                    throw new IOException(
                        "R85_LOCK_DENIED another JVM holds writer lock"
                    );
                return action.run();
            }catch(OverlappingFileLockException overlap){
                throw new IOException(
                    "R85_LOCK_DENIED same JVM holds writer lock",overlap
                );
            }
        }
    }

    private void requireUsable(){
        if(poisoned)
            throw new IllegalStateException(
                "R85_REOPEN_REQUIRED stale or ambiguous writer"
            );
    }
}

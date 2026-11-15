package spk.local;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * R60 OFFLINE cooperative single-writer/read-admission authority for R59.
 *
 * All R60 registrations AND reads acquire an exclusive OS FileLock on a
 * stable sentinel adjacent to (but outside) the dedicated journal directory.
 * Busy, overlapping, missing, symlink or malformed locks refuse admission.
 * An interrupted registration leaves its manifest in the journal, which R59
 * then refuses to treat as a clean index revision.
 *
 * Crucially: R55-R59 direct APIs and real LocalSession remain uncoordinated.
 * No live hook, recovery, journal deletion or power-loss guarantee is added.
 */
final class R60TradePairCoordinator {
    static final String LOCK_SUFFIX=".r60-pair-coordinator.lock";

    @FunctionalInterface
    interface CriticalAction<T> {
        T run() throws IOException;
    }

    static void initializeEmpty(Path journalRoot)throws IOException{
        // This is a one-time *offline* initialization on an existing,
        // empty dedicated directory. Do not silently adopt an old lock or
        // an ambiguous pre-existing journal from another owner.
        withExclusive(journalRoot,true,()->{
            long next=R59TradePairIntentIndex.publishRevision(
                journalRoot,0L,Collections.emptyList()
            );
            if(next!=1L)
                throw new IOException("R60_INIT_DENIED index revision");
            return null;
        });
    }

    /**
     * Register a PREPARED pair while holding the same OS lock that an R60
     * admission lookup requires. Calls R57 publication BEFORE indexing;
     * a crash between them leaves an unindexed manifest and forces a denial.
     *
     * Caller must supply the full prior ID list. R59 independently checks
     * completeness and refuses stale, omitted, duplicate or retired IDs.
     */
    static long registerPrepared(
        Path journalRoot,long expectedRevision,
        List<UUID> priorIds,UUID id,
        PlayerSnapshot beforeA,PlayerSnapshot afterA,
        PlayerSnapshot beforeB,PlayerSnapshot afterB
    )throws IOException{
        Objects.requireNonNull(priorIds,"priorIds");
        Objects.requireNonNull(id,"id");
        Objects.requireNonNull(beforeA,"beforeA");
        Objects.requireNonNull(beforeB,"beforeB");

        return withExclusive(journalRoot,false,()->{
            R59TradePairIntentIndex.Result a=
                R59TradePairIntentIndex.inspect(
                    journalRoot,beforeA.username()
                );
            R59TradePairIntentIndex.Result b=
                R59TradePairIntentIndex.inspect(
                    journalRoot,beforeB.username()
                );
            if(a.status!=R59TradePairIntentIndex.Status.NO_INDEXED_PENDING_PAIR||
               b.status!=R59TradePairIntentIndex.Status.NO_INDEXED_PENDING_PAIR)
                throw new IOException(
                    "R60_REGISTER_DENIED existing/ambiguous participant"
                );
            if(a.revision!=expectedRevision||b.revision!=expectedRevision)
                throw new IOException(
                    "R60_REGISTER_DENIED stale index revision"
                );

            Path intent=journalRoot.toAbsolutePath().normalize().resolve(
                "trade-r57-"+id.toString()+".properties"
            );
            R57TradePairJournal.writePrepared(
                intent,id,beforeA,afterA,beforeB,afterB
            );
            List<UUID> combined=new ArrayList<>(priorIds);
            combined.add(id);
            // Failure here MUST NOT delete or roll back the PREPARED record:
            // leaving it unindexed makes a later R59 read globally refuse.
            return R59TradePairIntentIndex.publishRevision(
                journalRoot,expectedRevision,combined
            );
        });
    }

    /** Read with the *same* exclusive OS lock as cooperative registration. */
    static R59TradePairIntentIndex.Result inspect(
        Path journalRoot,String account
    ){
        try{
            return withExclusive(journalRoot,false,
                ()->R59TradePairIntentIndex.inspect(
                    journalRoot,account
                )
            );
        }catch(IOException|RuntimeException unavailable){
            return new R59TradePairIntentIndex.Result(
                R59TradePairIntentIndex.Status.BLOCK_INDETERMINATE_INDEX,
                -1L,Collections.emptyList(),
                "R60_COORDINATION_LOCK_UNAVAILABLE"
            );
        }
    }

    /**
     * Exposed only as an offline fault-injection/test seam. A lock is held
     * across the entire critical section, not inferred from lock-file age.
     */
    static <T> T withExclusive(
        Path journalRoot,boolean createLock,CriticalAction<T> action
    )throws IOException{
        Objects.requireNonNull(action,"action");
        if(journalRoot==null)
            throw new IOException("R60_LOCK_DENIED null directory");
        Path root=journalRoot.toAbsolutePath().normalize();
        Path parent=root.getParent();
        if(parent==null)
            throw new IOException("R60_LOCK_DENIED root has no parent");
        for(Path ancestor=root;ancestor!=null;
                ancestor=ancestor.getParent()){
            if(Files.isSymbolicLink(ancestor))
                throw new IOException("R60_LOCK_DENIED symlink ancestor");
        }
        BasicFileAttributes rootAttrs=Files.readAttributes(
            root,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!rootAttrs.isDirectory())
            throw new IOException("R60_LOCK_DENIED invalid journal directory");

        // Stable and OUTSIDE the indexed manifest directory. The sentinel
        // must remain in place after unlocking; its existence is NOT proof
        // of an actively held lock.
        Path path=root.resolveSibling(
            "."+root.getFileName().toString()+LOCK_SUFFIX
        );
        if(createLock){
            Files.createFile(path); // CREATE_NEW, refuse previous owner
        }
        BasicFileAttributes before=Files.readAttributes(
            path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!before.isRegularFile()||before.size()!=0)
            throw new IOException("R60_LOCK_DENIED bad lock sentinel");

        try(FileChannel channel=FileChannel.open(
                path,StandardOpenOption.READ,StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)){
            FileLock acquired;
            try{
                acquired=channel.tryLock(); // exclusive across JVMs
            }catch(OverlappingFileLockException overlapping){
                throw new IOException(
                    "R60_LOCK_BUSY_OR_OVERLAPPING",overlapping
                );
            }
            if(acquired==null)
                throw new IOException("R60_LOCK_BUSY_OR_OVERLAPPING");

            try(FileLock lock=acquired){
                BasicFileAttributes held=Files.readAttributes(
                    path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
                );
                if(!held.isRegularFile()||held.size()!=0||
                   (before.fileKey()!=null&&held.fileKey()!=null&&
                    !before.fileKey().equals(held.fileKey())))
                    throw new IOException(
                        "R60_LOCK_DENIED replaced sentinel"
                    );
                return action.run();
            }
        }
    }

    private R60TradePairCoordinator(){}
}

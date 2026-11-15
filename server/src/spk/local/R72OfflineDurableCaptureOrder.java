package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * R72 OFFLINE-ONLY: durable capture-order watermark composed with R60/R64
 * coordinated account saves. This is a disposable test-fixture policy, not
 * production login or an authenticated account-writer implementation.
 *
 * Marker creation, reads and commits hold R60's cooperative OS lock. A commit
 * saves account + R63 + R64 first, then atomically publishes the marker.
 * Crash before marker publication produces a detectable account/marker
 * mismatch and refuses resume; there is no automatic repair.
 *
 * The sidecar is NOT part of a power-loss-atomic multi-file transaction.
 * Only cooperative writers using this protocol are within its guarantees.
 */
final class R72OfflineDurableCaptureOrder
        implements WorldPlayerPersistence.OfflineOrderedSaveAdmission {
    private static final String FORMAT="SpawnPK-R72-CaptureOrder-v1";
    private static final int MAX_BYTES=4096;
    private static final Set<String> KEYS=new HashSet<>(Arrays.asList(
        "format","username","owner.id","owner.epoch",
        "capture.ordinal","account.generation","journal.revision",
        "snapshot.sha256"
    ));

    static final class Marker {
        final String name;
        final UUID ownerId;
        final long ownerEpoch;
        final long captureOrdinal;
        final long accountGeneration;
        final long journalRevision;
        final String snapshotDigest;

        Marker(R64OfflineSessionOwnership.Token owner,long ordinal){
            name=owner.username;
            ownerId=owner.owner;
            ownerEpoch=owner.epoch;
            captureOrdinal=ordinal;
            accountGeneration=owner.account.generation;
            journalRevision=owner.account.journalRevision;
            snapshotDigest=owner.account.snapshotDigest;
        }

        Marker(String name,UUID ownerId,long ownerEpoch,long ordinal,
               long generation,long journalRevision,String digest){
            this.name=name;
            this.ownerId=ownerId;
            this.ownerEpoch=ownerEpoch;
            captureOrdinal=ordinal;
            accountGeneration=generation;
            this.journalRevision=journalRevision;
            snapshotDigest=digest;
        }

        void requireMatching(R64OfflineSessionOwnership.Token owner)
                throws IOException{
            if(!name.equals(owner.username)||
               !ownerId.equals(owner.owner)||
               ownerEpoch!=owner.epoch||
               accountGeneration!=owner.account.generation||
               journalRevision!=owner.account.journalRevision||
               !snapshotDigest.equals(owner.account.snapshotDigest))
                throw new IOException(
                    "R72_ORDER_DENIED watermark/account/owner mismatch"
                );
        }
    }

    private final Path journal;
    private final Path accounts;
    private final Path generations;
    private final Path sessions;
    private final Path markerRoot;
    private R64OfflineSessionOwnership.Token current;
    private final boolean injectAfterAccountBeforeMarkerForTest;
    private final AtomicInteger committed=new AtomicInteger();
    private final AtomicInteger identical=new AtomicInteger();
    private final AtomicInteger rejected=new AtomicInteger();

    private R72OfflineDurableCaptureOrder(
        Path journal,Path accounts,Path generations,Path sessions,
        Path markerRoot,R64OfflineSessionOwnership.Token current,
        boolean injected
    ){
        this.journal=Objects.requireNonNull(journal,"journal");
        this.accounts=Objects.requireNonNull(accounts,"accounts");
        this.generations=Objects.requireNonNull(generations,"generations");
        this.sessions=Objects.requireNonNull(sessions,"sessions");
        this.markerRoot=Objects.requireNonNull(markerRoot,"markerRoot");
        this.current=Objects.requireNonNull(current,"owner");
        injectAfterAccountBeforeMarkerForTest=injected;
    }

    /** Initial marker is permitted only on a fresh first-generation fixture. */
    static void initializeFresh(
        Path journal,Path accounts,Path generations,Path sessions,
        Path markers,R64OfflineSessionOwnership.Token owner
    )throws IOException{
        Objects.requireNonNull(owner,"owner");
        R60TradePairCoordinator.withExclusive(journal,false,()->{
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,owner
            );
            validateMarkerRoot(markers,journal,accounts,generations,sessions);
            if(owner.account.generation!=1L||owner.epoch!=1L)
                throw new IOException(
                    "R72_INIT_DENIED not a fresh owned account"
                );
            Path file=markerPath(markers,owner.username);
            AtomicAccountPublisher.requireNoAmbiguousStage(file);
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))
                throw new IOException(
                    "R72_INIT_DENIED marker already exists"
                );
            publish(file,new Marker(owner,0L));
            return null;
        });
    }

    /** Restart binding is observational and requires the same R64 owner. */
    static R72OfflineDurableCaptureOrder resume(
        Path journal,Path accounts,Path generations,Path sessions,
        Path markers,R64OfflineSessionOwnership.Token owner,
        boolean injectAfterAccountBeforeMarkerForTest
    )throws IOException{
        Objects.requireNonNull(owner,"owner");
        R60TradePairCoordinator.withExclusive(journal,false,()->{
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,owner
            );
            validateMarkerRoot(markers,journal,accounts,generations,sessions);
            Marker prior=read(markerPath(markers,owner.username));
            prior.requireMatching(owner);
            return null;
        });
        return new R72OfflineDurableCaptureOrder(
            journal,accounts,generations,sessions,markers,
            owner,injectAfterAccountBeforeMarkerForTest
        );
    }

    /** Must be called before the replacement World starts or captures data. */
    synchronized long resumeCaptureFloor()throws IOException{
        return R60TradePairCoordinator.withExclusive(journal,false,()->{
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,current
            );
            validateMarkerRoot(markerRoot,journal,accounts,
                generations,sessions);
            Marker marker=read(markerPath(markerRoot,current.username));
            marker.requireMatching(current);
            return marker.captureOrdinal;
        });
    }

    /** Raw unversioned writes without trusted capture metadata are denied. */
    @Override public void withAdmission(
        PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter writer
    )throws IOException{
        throw new IOException("R72_ORDER_DENIED missing capture ordinal");
    }

    @Override public synchronized void withOrderedAdmission(
        long ordinal,PlayerSnapshot snapshot,
        WorldPlayerPersistence.CheckedSaveWriter rawWriter
    )throws IOException{
        Objects.requireNonNull(snapshot,"snapshot");
        Objects.requireNonNull(rawWriter,"raw writer");
        if(ordinal<=0L||!current.username.equals(snapshot.username()))
            throw new IOException(
                "R72_ORDER_DENIED invalid ordinal or foreign account"
            );

        R60TradePairCoordinator.withExclusive(journal,false,()->{
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,current
            );
            validateMarkerRoot(markerRoot,journal,accounts,
                generations,sessions);
            Path file=markerPath(markerRoot,current.username);
            Marker before=read(file);
            before.requireMatching(current);
            if(ordinal<=before.captureOrdinal){
                rejected.incrementAndGet();
                throw new IOException(
                    "R72_ORDER_DENIED stale durable capture ordinal"
                );
            }

            String digest=R57TradePairJournal.digest(snapshot);
            if(digest.equals(current.account.snapshotDigest)){
                // A newer identical snapshot is also a freshness event.
                // No R63 generation changes, but watermark advances.
                publish(file,new Marker(current,ordinal));
                identical.incrementAndGet();
                return null;
            }

            R64OfflineSessionOwnership.Token after=
                R64OfflineSessionOwnership.saveOwnedUnderLock(
                    journal,accounts,generations,sessions,current,
                    snapshot,false
                );
            if(injectAfterAccountBeforeMarkerForTest)
                throw new IOException(
                    "R72_INJECTED_AFTER_ACCOUNT_BEFORE_MARKER"
                );
            // If publication fails, existing marker and new R64 data
            // mismatch: future resume/commit rejects without overwriting.
            publish(file,new Marker(after,ordinal));
            current=after;
            committed.incrementAndGet();
            return null;
        });
    }

    /**
     * R73 OFFLINE ONLY. Called by a World actor after the final save future
     * succeeded and an IO drain sentinel completed. Revalidates the exact
     * final snapshot and durable ordinal under R60 before releasing R64.
     * The marker is intentionally preserved as immutable audit evidence.
     *
     * This is not authentication and cannot release another R64 owner.
     */
    synchronized R64OfflineSessionOwnership.Token
        releaseVerifiedFinalForTest(
            long finalCaptureOrdinal,
            PlayerSnapshot finalSnapshot
        )throws IOException{
        Objects.requireNonNull(finalSnapshot,"final snapshot");
        if(finalCaptureOrdinal<=0L||
           !current.username.equals(finalSnapshot.username()))
            throw new IOException(
                "R73_FINAL_RELEASE_DENIED missing final capture proof"
            );
        return R60TradePairCoordinator.withExclusive(journal,false,()->{
            R64OfflineSessionOwnership.requireActiveOwnerUnderLock(
                journal,accounts,generations,sessions,current
            );
            validateMarkerRoot(markerRoot,journal,accounts,
                generations,sessions);
            Marker committed=read(
                markerPath(markerRoot,current.username)
            );
            committed.requireMatching(current);
            if(committed.captureOrdinal!=finalCaptureOrdinal||
               !committed.snapshotDigest.equals(
                   R57TradePairJournal.digest(finalSnapshot)
               ))
                throw new IOException(
                    "R73_FINAL_RELEASE_DENIED final snapshot not committed"
                );
            R64OfflineSessionOwnership.Token released=
                R64OfflineSessionOwnership.releaseOwnerUnderLock(
                    journal,accounts,generations,sessions,current
                );
            current=released;
            return released;
        });
    }

    synchronized R64OfflineSessionOwnership.Token currentToken(){
        return current;
    }

    int committedCount(){return committed.get();}
    int identicalCount(){return identical.get();}
    int rejectedCount(){return rejected.get();}

    private static Path markerPath(Path root,String username){
        return root.toAbsolutePath().normalize().resolve(
            username+".r72-capture-order.properties"
        );
    }

    private static void validateMarkerRoot(
        Path markers,Path journal,Path accounts,
        Path generations,Path sessions
    )throws IOException{
        if(markers==null)
            throw new IOException("R72_ROOT_DENIED missing marker root");
        Path normalized=markers.toAbsolutePath().normalize();
        for(Path p=normalized;p!=null;p=p.getParent())
            if(Files.isSymbolicLink(p))
                throw new IOException(
                    "R72_ROOT_DENIED symlink ancestor"
                );
        BasicFileAttributes attrs=Files.readAttributes(
            normalized,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isDirectory())
            throw new IOException("R72_ROOT_DENIED not a directory");
        for(Path other:new Path[]{
                journal,accounts,generations,sessions}){
            Path p=other.toAbsolutePath().normalize();
            if(normalized.startsWith(p)||p.startsWith(normalized))
                throw new IOException(
                    "R72_ROOT_DENIED overlapping account roots"
                );
        }
    }

    private static Marker read(Path file)throws IOException{
        AtomicAccountPublisher.requireNoAmbiguousStage(file);
        BasicFileAttributes attrs=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
        );
        if(!attrs.isRegularFile()||attrs.size()<1L||
           attrs.size()>MAX_BYTES)
            throw new IOException("R72_ORDER_DENIED invalid marker file");
        Properties props=new Properties();
        try(InputStream input=Files.newInputStream(
                file,LinkOption.NOFOLLOW_LINKS)){
            props.load(input);
        }
        if(!props.stringPropertyNames().equals(KEYS)||
           !FORMAT.equals(props.getProperty("format")))
            throw new IOException("R72_ORDER_DENIED marker schema");
        String name=props.getProperty("username");
        if(name==null||!name.matches("[a-z][a-z0-9_]{0,31}"))
            throw new IOException("R72_ORDER_DENIED invalid username");
        UUID ownerId;
        try{
            ownerId=UUID.fromString(props.getProperty("owner.id"));
        }catch(RuntimeException invalid){
            throw new IOException("R72_ORDER_DENIED invalid owner UUID",invalid);
        }
        long epoch=positiveLong(props.getProperty("owner.epoch"));
        long ordinal=nonnegativeLong(props.getProperty("capture.ordinal"));
        long generation=positiveLong(
            props.getProperty("account.generation")
        );
        long journalRevision=nonnegativeLong(
            props.getProperty("journal.revision")
        );
        String hash=props.getProperty("snapshot.sha256");
        if(hash==null||!hash.matches("[0-9a-f]{64}"))
            throw new IOException("R72_ORDER_DENIED invalid snapshot hash");
        return new Marker(
            name,ownerId,epoch,ordinal,generation,journalRevision,hash
        );
    }

    private static long positiveLong(String value)throws IOException{
        long v=nonnegativeLong(value);
        if(v==0L)
            throw new IOException("R72_ORDER_DENIED expected positive");
        return v;
    }

    private static long nonnegativeLong(String value)throws IOException{
        if(value==null||!value.matches("(0|[1-9][0-9]*)"))
            throw new IOException("R72_ORDER_DENIED invalid integer");
        try{
            return Long.parseLong(value);
        }catch(NumberFormatException overflow){
            throw new IOException("R72_ORDER_DENIED integer overflow",overflow);
        }
    }

    private static void publish(Path file,Marker value)throws IOException{
        Properties props=new Properties();
        props.setProperty("format",FORMAT);
        props.setProperty("username",value.name);
        props.setProperty("owner.id",value.ownerId.toString());
        props.setProperty("owner.epoch",Long.toString(value.ownerEpoch));
        props.setProperty(
            "capture.ordinal",Long.toString(value.captureOrdinal)
        );
        props.setProperty(
            "account.generation",Long.toString(value.accountGeneration)
        );
        props.setProperty(
            "journal.revision",Long.toString(value.journalRevision)
        );
        props.setProperty("snapshot.sha256",value.snapshotDigest);
        AtomicAccountPublisher.publish(
            file,props,"R72 offline capture-order watermark"
        );
    }
}

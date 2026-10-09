package spk.local;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Schema-v1 file repository compatible with existing opensrc/src properties.
 *
 * All filesystem I/O occurs after the immutable PlayerSnapshot has been
 * captured. Saving retains the existing temp-file + replace discipline.
 */
final class FilePlayerRepository
    implements PlayerRepository {

    interface PathResolver {
        Path resolve(String username);
    }

    private final PathResolver paths;
    /**
     * G21.36 package-scoped deterministic test seam; it never relaxes the
     * normal before-replacement account review-fence check.
     */
    interface BeforeWorldReplace {
        void run(String account)throws IOException;
    }
    private final BeforeWorldReplace beforeWorldReplace;
    /** Test-only hook inside the exclusive last-check/replace section. */
    private final BeforeWorldReplace insideWorldPublication;

    FilePlayerRepository(){
        this(
            username->
                LocalAccountProfiles.accountFile(
                    username
                )
        );
    }

    FilePlayerRepository(PathResolver paths){
        this(paths,account->{});
    }

    FilePlayerRepository(
        PathResolver paths,BeforeWorldReplace beforeWorldReplace
    ){
        this(paths,beforeWorldReplace,account->{});
    }

    FilePlayerRepository(
        PathResolver paths,BeforeWorldReplace beforeWorldReplace,
        BeforeWorldReplace insideWorldPublication
    ){
        this.paths=Objects.requireNonNull(paths,"paths");
        this.beforeWorldReplace=Objects.requireNonNull(
            beforeWorldReplace,"beforeWorldReplace"
        );
        this.insideWorldPublication=Objects.requireNonNull(
            insideWorldPublication,"insideWorldPublication"
        );
    }

    @Override public Optional<PlayerSnapshot> load(
        String username
    )throws IOException{
        Path file=normalizedPath(username);

        if(!Files.isRegularFile(file))
            return Optional.empty();

        Properties properties=
            new Properties();

        try(InputStream input=
                Files.newInputStream(file)){
            properties.load(input);
        }

        final PlayerSnapshot snapshot;

        try{
            snapshot=
                PlayerSnapshot.fromLegacyProperties(
                    username,
                    properties
                );
        }catch(IllegalArgumentException e){
            throw new IOException(
                "invalid player snapshot file="+
                file+
                " error="+e.getMessage(),
                e
            );
        }

        if(!snapshot.username().equalsIgnoreCase(
                clean(username)))
            throw new IOException(
                "snapshot username mismatch requested="+
                clean(username)+
                " stored="+snapshot.username()+
                " file="+file
            );

        return Optional.of(snapshot);
    }

    /**
     * G21.32 negative review-fence check for the actual file-backed
     * account source. Presence blocks login regardless of account bytes;
     * unreadable marker metadata fails closed through IOException.
     */
    boolean hasUnresolvedMailboxReviewFence(
        String username
    )throws IOException{
        String account=clean(username);
        return new MailboxDurableReviewFence(paths).present(account)||
            new MailboxStrictUncertainFence(paths).present(account)||
            new MailboxStrictWriteIntentFence(paths).present(account);
    }

    /**
     * G21.48 active-session policy distinguishes permanent manual-review
     * markers from the short-lived intent created by this session's own
     * in-flight strict persistence worker. A stranded intent is still a
     * session veto once its worker is no longer demonstrably in flight.
     */
    boolean hasPermanentMailboxReviewFence(String username)
        throws IOException{
        String account=clean(username);
        return new MailboxDurableReviewFence(paths).present(account)||
            new MailboxStrictUncertainFence(paths).present(account);
    }

    /** Read-only: no account file I/O or intent cancellation. */
    boolean hasStrictWriteIntent(String username)throws IOException{
        return new MailboxStrictWriteIntentFence(paths).present(
            clean(username)
        );
    }

    /**
     * Only the production WorldPlayerPersistence normal save path uses
     * this guarded entry point. Legacy/manual/forensic direct save() is
     * intentionally unchanged and MUST NOT be used as an admitted World
     * save or interpreted as settling a native Mailbox claim.
     */
    void saveForWorld(PlayerSnapshot snapshot)throws IOException{
        saveInternal(snapshot,true);
    }

    @Override public void save(
        PlayerSnapshot snapshot
    )throws IOException{
        saveInternal(snapshot,false);
    }

    private void saveInternal(
        PlayerSnapshot snapshot,boolean enforceWorldAdmission
    )throws IOException{
        Objects.requireNonNull(
            snapshot,
            "snapshot"
        );

        if(snapshot.version()!=
                PlayerSnapshot.CURRENT_VERSION)
            throw new IOException(
                "unsupported snapshot version="+
                snapshot.version()
            );

        Path file=
            normalizedPath(
                snapshot.username()
            );

        // G21.57: the guarded World save uses ONE account-file
        // location for both the actual replacement and negative markers.
        // A mutable resolver must never make review checks look at B while
        // the account FileLock/ATOMIC_MOVE targets A.
        final String worldAccount=clean(snapshot.username());
        final PathResolver guardedMarkerPaths=requested->{
            if(!worldAccount.equals(clean(requested)))
                throw new IllegalArgumentException(
                    "G21.57 marker account identity changed"
                );
            return file;
        };

        if(enforceWorldAdmission){
            MailboxPreparedRestartAdmission.Decision admission=
                MailboxPreparedRestartAdmission.inspect(snapshot);
            if(!admission.admissionAllowed)
                throw new IOException(
                    "G21.36 MAILBOX_WORLD_SAVE_QUARANTINE"+
                    " account="+snapshot.username()+
                    " reason="+admission.state
                );
            requireUnfencedWorldSave(
                snapshot.username(),guardedMarkerPaths
            );
        }

        Path parent=file.getParent();
        if(parent!=null)
            Files.createDirectories(parent);

        Properties properties=
            snapshot.toLegacyProperties();

        properties.setProperty(
            "saved.at",
            Instant.now().toString()
        );

        Path tmp=
            file.resolveSibling(
                file.getFileName().toString()+
                ".tmp"
            );

        boolean completed=false;

        try{
            try(OutputStream output=
                    Files.newOutputStream(
                        tmp,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                    )){
                properties.store(
                    output,
                    "SpawnPK LocalLab localhost account state"
                );
            }

            if(enforceWorldAdmission){
                beforeWorldReplace.run(snapshot.username());
                // G21.38: the last review-fence check and account-file
                // replacement are one exclusive publication critical
                // section shared with cooperating G21.34 marker writers.
                // This does NOT coordinate direct manual save() calls.
                MailboxAccountPublicationCoordinator
                    .withExclusivePublication(file,()->{
                        requireUnfencedWorldSave(
                snapshot.username(),guardedMarkerPaths
            );
                        insideWorldPublication.run(snapshot.username());
                        replaceSnapshotTemp(tmp,file);
                        return null;
                    });
            }else{
                replaceSnapshotTemp(tmp,file);
            }

            completed=true;
        }finally{
            if(!completed)
                Files.deleteIfExists(tmp);
        }
    }

    private static void replaceSnapshotTemp(
        Path temp,Path file
    )throws IOException{
        try{
            Files.move(
                temp,file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
        }catch(AtomicMoveNotSupportedException unsupported){
            // Retain original account-file writer compatibility. This
            // fallback is NOT used for the no-clobber review marker.
            Files.move(
                temp,file,StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private void requireUnfencedWorldSave(
        String account,PathResolver markerPaths
    )throws IOException{
        // The guarded World save must check ALL four negative marker
        // names against the locked/replaced account file, not a second
        // independently resolved account root.
        if(new MailboxDurableReviewFence(markerPaths).present(account)||
           new MailboxStrictUncertainFence(markerPaths).present(account)||
           new MailboxStrictWriteIntentFence(markerPaths).present(account))
            throw new IOException(
                "G21.36 MAILBOX_DURABLE_REVIEW_SAVE_VETO"+
                " account="+account+" action=REJECT_WORLD_SAVE"
            );
    }

    /** G21.42: exact file-backed path bound to the World barrier. */
    Path accountFilePath(String username){
        return normalizedPath(username);
    }

    private Path normalizedPath(
        String username
    ){
        Path file=
            paths.resolve(
                clean(username)
            );

        if(file==null)
            throw new IllegalArgumentException(
                "repository path"
            );

        return file.toAbsolutePath().normalize();
    }

    private static String clean(
        String username
    ){
        return username==null
            ?""
            :username.trim().toLowerCase(
                Locale.ROOT
            );
    }
}

package spk.local;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.attribute.BasicFileAttributes;
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
    /** G21.58 test seam after bytes are read, before session marker recheck. */
    private final BeforeWorldReplace afterWorldSessionRead;

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
        this(paths,beforeWorldReplace,insideWorldPublication,account->{});
    }

    /** Package-scoped deterministic guarded-read injection only. */
    FilePlayerRepository(
        PathResolver paths,BeforeWorldReplace beforeWorldReplace,
        BeforeWorldReplace insideWorldPublication,
        BeforeWorldReplace afterWorldSessionRead
    ){
        this.paths=Objects.requireNonNull(paths,"paths");
        this.beforeWorldReplace=Objects.requireNonNull(
            beforeWorldReplace,"beforeWorldReplace"
        );
        this.insideWorldPublication=Objects.requireNonNull(
            insideWorldPublication,"insideWorldPublication"
        );
        this.afterWorldSessionRead=Objects.requireNonNull(
            afterWorldSessionRead,"afterWorldSessionRead"
        );
    }

    @Override public Optional<PlayerSnapshot> load(
        String username
    )throws IOException{
        return loadExactFile(username,normalizedPath(username));
    }

    /**
     * G21.58 session-only negative admission: read the very same account
     * file that supplies all four negative marker names. A resolver drift
     * before return is a refusal, not a fresh file-selection instruction.
     * Raw repository.load remains a non-admitting forensic primitive.
     */
    Optional<PlayerSnapshot> loadForWorldSession(
        String username
    )throws IOException{
        final String account=clean(username);
        final Path file=normalizedPath(account);
        final PathResolver markerPaths=requested->{
            if(!account.equals(clean(requested)))
                throw new IllegalArgumentException(
                    "G21.58 session marker account identity changed"
                );
            return file;
        };
        // G21.59: make the entire admitted account observation one
        // cooperating account-local publication critical section. A
        // marker or account writer holding this lock must run wholly
        // BEFORE or wholly AFTER the negative checks and snapshot read.
        // This is not an atomic disk snapshot or a lock on raw save().
        return MailboxAccountPublicationCoordinator
            .withExclusivePublication(file,()->{
                requireUnfencedSessionLoad(account,markerPaths);
                // G21.60: metadata describes the selected filesystem
                // object, not merely the path text. NOFOLLOW refuses
                // symlink roots even when they resolve to valid players.
                BasicFileAttributes before=
                    admittedAccountFileEvidence(account,file);
                // G21.61: bind the decoded byte stream to its own
                // SHA-256 fingerprint; G21.60 metadata alone cannot
                // detect a same-size rewrite with restored mtime.
                MessageDigest decodedHash=newAccountDigest();
                Optional<PlayerSnapshot> observed=
                    loadExactFile(account,file,true,decodedHash);
                afterWorldSessionRead.run(account);
                requireUnfencedSessionLoad(account,markerPaths);
                if(!file.equals(normalizedPath(account)))
                    throw new IOException(
                        "G21.58 MAILBOX_SESSION_ACCOUNT_PATH_CHANGED"+
                        " account="+account+" action=REJECT_SESSION"
                    );
                BasicFileAttributes after=
                    admittedAccountFileEvidence(account,file);
                if(!sameAdmittedAccountObject(before,after))
                    throw new IOException(
                        "G21.60 MAILBOX_SESSION_ACCOUNT_FILE_CHANGED"+
                        " account="+account+" action=REJECT_SESSION"
                    );
                if(before!=null){
                    // Re-read only the pinned admitted account path.
                    // The independent digest catches a content rewrite
                    // that preserves size, file key and timestamp.
                    if(!MessageDigest.isEqual(
                            decodedHash.digest(),
                            digestAdmittedAccountFile(file)))
                        throw new IOException(
                            "G21.61 MAILBOX_SESSION_ACCOUNT_BYTES_CHANGED"+
                            " account="+account+" action=REJECT_SESSION"
                        );
                    // Check identity again after the second read.
                    BasicFileAttributes afterDigest=
                        admittedAccountFileEvidence(account,file);
                    if(!sameAdmittedAccountObject(before,afterDigest))
                        throw new IOException(
                            "G21.60 MAILBOX_SESSION_ACCOUNT_FILE_CHANGED"+
                            " account="+account+" action=REJECT_SESSION"
                        );
                }
                return observed;
            });
    }

    private void requireUnfencedSessionLoad(
        String account,PathResolver markerPaths
    )throws IOException{
        if(new MailboxDurableReviewFence(markerPaths).present(account)||
           new MailboxStrictUncertainFence(markerPaths).present(account)||
           new MailboxStrictWriteIntentFence(markerPaths).present(account))
            throw new IOException(
                "G21.32 MAILBOX_DURABLE_REVIEW_FENCE"+
                " account="+account+" action=REJECT_SESSION"
            );
    }

    /**
     * NOFOLLOW evidence is only an admission gate. File keys and file
     * timestamps are not cryptographic identity or a power-loss proof.
     */
    private static BasicFileAttributes admittedAccountFileEvidence(
        String account,Path file
    )throws IOException{
        final BasicFileAttributes attrs;
        try{
            attrs=Files.readAttributes(
                file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS
            );
        }catch(NoSuchFileException missing){
            return null;
        }
        if(!attrs.isRegularFile())
            throw new IOException(
                "G21.60 MAILBOX_SESSION_ACCOUNT_NONREGULAR"+
                " account="+account+" action=REJECT_SESSION"
            );
        return attrs;
    }

    private static boolean sameAdmittedAccountObject(
        BasicFileAttributes first,BasicFileAttributes last
    ){
        if(first==null||last==null)return first==null&&last==null;
        return Objects.equals(first.fileKey(),last.fileKey())&&
            first.size()==last.size()&&
            Objects.equals(first.lastModifiedTime(),
                           last.lastModifiedTime());
    }

    private static MessageDigest newAccountDigest()throws IOException{
        try{
            return MessageDigest.getInstance("SHA-256");
        }catch(NoSuchAlgorithmException impossible){
            throw new IOException("SHA-256 unavailable",impossible);
        }
    }

    /** Second NOFOLLOW read; not an atomic snapshot or ABA proof. */
    private static byte[] digestAdmittedAccountFile(
        Path file
    )throws IOException{
        MessageDigest digest=newAccountDigest();
        try(InputStream input=new DigestInputStream(
                java.nio.channels.Channels.newInputStream(
                    java.nio.channels.FileChannel.open(
                        file,StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS
                    )),digest)){
            byte[] buffer=new byte[8192];
            while(input.read(buffer)!=-1){}
        }
        return digest.digest();
    }

    private Optional<PlayerSnapshot> loadExactFile(
        String username,Path file
    )throws IOException{
        return loadExactFile(username,file,false,null);
    }

    private Optional<PlayerSnapshot> loadExactFile(
        String username,Path file,boolean noFollow,
        MessageDigest contentDigest
    )throws IOException{
        if(!Files.isRegularFile(file))
            return Optional.empty();

        Properties properties=
            new Properties();

        // The admitted decoder never follows a substituted symbolic
        // link after its NOFOLLOW metadata check. Raw forensic reading
        // preserves its pre-existing compatibility semantics.
        try(InputStream input=noFollow
                ?java.nio.channels.Channels.newInputStream(
                    java.nio.channels.FileChannel.open(
                        file,StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS
                    ))
                :Files.newInputStream(file)){
            if(contentDigest==null)properties.load(input);
            else properties.load(new DigestInputStream(
                input,contentDigest
            ));
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

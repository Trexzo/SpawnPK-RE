package spk.local;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * G21.246 EXPERIMENTAL: unique, no-follow cooperating RAW account temp.
 *
 * This protects cooperating writers from the fixed historical .tmp leaf.
 * The last attribute observation is NOT an atomic external attack fence,
 * does not prove exact serialized bytes, and does not establish recovery.
 */
final class LocalOptInRawTemp {
    static final String PROPERTY=
        "spk.local.experimentalRawUniqueTempNoFollow";
    // G21.248: stricter filesystem API completion; opt-in ONLY.
    // Even a forced and verified account postimage is NOT an
    // authenticated final-save ACK or durable owner generation.
    static final String FORCE_PROPERTY=
        "spk.local.experimentalRawForceVerifiedPostimage";
    // G21.255: an indefinitely held G21.39 marker-publication lock
    // must not block cooperating forced RAW saves forever. The
    // deadline applies to ACQUISITION only, not the I/O while owned.
    static final String PUBLICATION_WAIT_PROPERTY=
        "spk.local.experimentalRawPublicationLockWaitMillis";
    static final long DEFAULT_PUBLICATION_WAIT_MILLIS=5000L;
    static long publicationWaitMillis()throws IOException{
        String configured=System.getProperty(PUBLICATION_WAIT_PROPERTY);
        if(configured==null)return DEFAULT_PUBLICATION_WAIT_MILLIS;
        try{
            long value=Long.parseLong(configured);
            if(value>=1L&&value<=60000L)return value;
        }catch(NumberFormatException invalid){
            // Fail closed: never fall back to an infinite lock wait.
        }
        throw new IOException(
            "G21.255 INVALID_FORCED_RAW_PUBLICATION_LOCK_WAIT");
    }
    // G21.248 deterministic test-only seam: publication may already
    // have replaced bytes when a later directory force fails.
    interface AfterMoveProbe {
        void afterMove(Path file)throws IOException;
    }
    private static volatile AfterMoveProbe afterMoveProbeForTest;
    static void setAfterMoveProbeForTest(AfterMoveProbe probe){
        afterMoveProbeForTest=probe;
    }
    // G21.252 deterministic marker/publication race test only.
    interface BeforeAccountMoveProbe{
        void beforeMove(Path file)throws IOException;
    }
    private static volatile BeforeAccountMoveProbe beforeMoveProbeForTest;
    static void setBeforeMoveProbeForTest(BeforeAccountMoveProbe probe){
        beforeMoveProbeForTest=probe;
    }

    private LocalOptInRawTemp(){}

    static boolean enabled()throws IOException{
        if(!Boolean.getBoolean(PROPERTY)){
            if(Boolean.getBoolean(FORCE_PROPERTY))
                throw new IOException(
                    "G21.248 FORCE_RAW_POSTIMAGE_REQUIRES_SAFE_TEMP");
            return false;
        }
        if(!Boolean.getBoolean(
                LocalJvmAccountSessionLease.PROPERTY)||
           !Boolean.getBoolean(
                LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY)||
           !Boolean.getBoolean(
                LocalCrossJvmAccountSessionLock.PROPERTY)||
           !Boolean.getBoolean(
                LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY))
            throw new IOException(
                "G21.246 SAFE_RAW_TEMP_REQUIRES_LOCKED_CONTINUITY_OPT_IN");
        return true;
    }

    static boolean forcePostimageEnabled()throws IOException{
        boolean requested=Boolean.getBoolean(FORCE_PROPERTY);
        if(requested&&!enabled())
            throw new IOException(
                "G21.248 FORCE_RAW_POSTIMAGE_REQUIRES_SAFE_TEMP");
        return requested;
    }

    static Path create(Path account)throws IOException{
        Objects.requireNonNull(account,"account");
        return Files.createTempFile(
            account.getParent(),
            account.getFileName().toString()+".g21246-raw-",
            ".tmp"
        );
    }

    static BasicFileAttributes observe(Path temp)throws IOException{
        final BasicFileAttributes attrs;
        try{
            attrs=Files.readAttributes(
                temp,BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        }catch(IOException problem){
            throw new IOException(
                "G21.246 SAFE_RAW_TEMP_OBJECT_CHANGED",problem);
        }
        if(!attrs.isRegularFile()||attrs.isSymbolicLink()||
           attrs.fileKey()==null)
            throw new IOException(
                "G21.246 SAFE_RAW_TEMP_OBJECT_UNSAFE");
        return attrs;
    }

    static void requireSame(Path temp,BasicFileAttributes original)
        throws IOException{
        if(original==null)return;
        BasicFileAttributes now=observe(temp);
        if(!Objects.equals(original.fileKey(),now.fileKey())||
           !Objects.equals(original.creationTime(),now.creationTime()))
            throw new IOException(
                "G21.246 SAFE_RAW_TEMP_OBJECT_CHANGED");
    }

    // G21.247: bounded NOFOLLOW read of the same originally allocated
    // temp inode. This detects in-place byte edits after serialization,
    // even if length and timestamps are restored.
    private static byte[] fingerprint(
        Path temp,BasicFileAttributes original
    )throws IOException{
        requireSame(temp,original);
        final MessageDigest digest;
        try{
            digest=MessageDigest.getInstance("SHA-256");
        }catch(NoSuchAlgorithmException impossible){
            throw new IOException(
                "G21.247 RAW_TEMP_SHA256_UNAVAILABLE",impossible);
        }
        final long maxBytes=64L*1024L*1024L;
        long count=0L;
        try(InputStream input=Files.newInputStream(
                temp,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)){
            byte[] buf=new byte[8192];
            for(int n;(n=input.read(buf))!=-1;){
                count+=n;
                if(count>maxBytes)
                    throw new IOException(
                        "G21.247 SAFE_RAW_TEMP_EXCEEDS_64MIB_BOUND");
                digest.update(buf,0,n);
            }
        }
        requireSame(temp,original);
        return digest.digest();
    }

    static byte[] seal(
        Path temp,BasicFileAttributes original
    )throws IOException{
        return fingerprint(temp,original);
    }

    static void requireSealed(
        Path temp,BasicFileAttributes original,byte[] expected
    )throws IOException{
        if(expected==null||
           !MessageDigest.isEqual(expected,fingerprint(temp,original)))
            throw new IOException(
                "G21.247 SAFE_RAW_TEMP_BYTES_CHANGED");
    }

    /**
     * Strict opt-in RAW publication: exact uniquely-owned temp bytes
     * forced before ATOMIC_MOVE, directory metadata forced afterward,
     * and matching NOFOLLOW account postimage re-read before success.
     * This is not an account-owner epoch nor atomic external fencing.
     * A failure AFTER Files.move has UNKNOWN outcome; never retry as
     * if rollback were proven or interpret as a final-session receipt.
     */
    static void forcePublish(
        Path temp,Path account,BasicFileAttributes identity,
        byte[] sealed,LocalJvmAccountSessionLease.RawWriteReservation raw,
        String accountUsername,AccountPreimage expectedPreimage
    )throws IOException{
        if(!forcePostimageEnabled()||raw==null)
            throw new IOException(
                "G21.248 FORCED_RAW_MISSING_OPTIN_RESERVATION");
        requireSealed(temp,identity,sealed);
        try(FileChannel channel=FileChannel.open(
                temp,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            channel.force(true);
        }
        requireSealed(temp,identity,sealed);
        // G21.252: coordinate forced RAW marker admission, atomic
        // replacement and directory/postimage verification under the
        // same account-local G21.39 lock as cooperating marker writers.
        // Held session OS lock precedes G21.39, as in World saves.
        MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                account,publicationWaitMillis(),()->{
            BasicFileAttributes publicationLockIdentity=
                observePublicationLock(account);
            MailboxAccountPublicationCoordinator
                .requireSafeWorldTempAncestry(account,false);
            LocalJvmAccountSessionLease.requireNonHardlinkedAccount(account);
            raw.requireLockObjectContinuity();
            // G21.250: the stricter forced RAW mode must never claim a
            // confirmed normal postimage over an account already marked
            // ACTIVE for manual review. Legacy/default RAW stays unchanged.
            LocalDurableAccountReviewIntentPrimitive
                .requireNoReviewMarker(account);
            // R38: a final-save NEGATIVE witness survives independently
            // of the ACTIVE review marker and of the current opt-in
            // property. A missing ACTIVE leaf is NOT permission to
            // overwrite an account that still has final-review evidence.
            LocalFinalSaveNegativeWitness.requireNoNegativeWitness(account);
            // G21.251: existing Mailbox durable/uncertain/write-ahead
            // sidecars are NEGATIVE, not overwritten by opt-in forced RAW.
            // Exact target account is pinned by this resolver. The G21.39
            // cooperating marker writers share G21.39 now, while
            // external hostile filesystem changes remain uncoordinated.
            FilePlayerRepository.PathResolver pinned=requested->{
                if(!accountUsername.equals(requested))
                    throw new IllegalArgumentException(
                        "G21.251 FORCED_RAW_ACCOUNT_IDENTITY_DRIFT");
                return account;
            };
            if(new MailboxDurableReviewFence(pinned)
                    .present(accountUsername)||
               new MailboxStrictUncertainFence(pinned)
                    .present(accountUsername)||
               new MailboxStrictWriteIntentFence(pinned)
                    .present(accountUsername))
                throw new IOException(
                    "G21.251 FORCED_RAW_MAILBOX_REVIEW_FENCE_PRESENT");
            raw.requireLockObjectContinuity();
            BeforeAccountMoveProbe moveProbe=beforeMoveProbeForTest;
            if(moveProbe!=null)moveProbe.beforeMove(account);
            // G21.262: the before-move seam may introduce a previously
            // absent NEGATIVE review marker, even when the earlier
            // publication-locked check was clear. Recheck every exact
            // account-local sidecar after this seam before replacing the
            // old account. This only narrows an observed race; foreign
            // uncooperative marker creation after the final check cannot
            // be atomically fenced by these filesystem probes.
            LocalDurableAccountReviewIntentPrimitive
                .requireNoReviewMarker(account);
            // R38: repeat AFTER the deterministic pre-move seam. A
            // newly inserted witness is a veto even if the initial
            // publication-locked read saw no evidence.
            LocalFinalSaveNegativeWitness.requireNoNegativeWitness(account);
            if(new MailboxDurableReviewFence(pinned)
                    .present(accountUsername)||
               new MailboxStrictUncertainFence(pinned)
                    .present(accountUsername)||
               new MailboxStrictWriteIntentFence(pinned)
                    .present(accountUsername))
                throw new IOException(
                    "G21.251 FORCED_RAW_MAILBOX_REVIEW_FENCE_PRESENT");
            requirePublicationLockName(account,publicationLockIdentity);
            requireAccountPreimageUnchanged(account,expectedPreimage);
            // R37: the strictly forced RAW postimage must not replace
            // an EXISTING account file for a different username just
            // because the same resolver path and bytes were pinned.
            // Revalidate the preimage AFTER the bounded NOFOLLOW
            // profile read, before the existing final nlink check.
            FilePlayerRepository.requireForcedRawSelectedProfile(
                account,accountUsername);
            requireAccountPreimageUnchanged(account,expectedPreimage);
            // G21.260: with the existing DEFAULT-OFF G21.230 nlink
            // gate, recheck after the before-move race seam. A new
            // hardlink alias can arise after the prior early preflight
            // without changing old account bytes or fileKey. This is
            // observed-only, not atomic exclusion of external links.
            LocalJvmAccountSessionLease
                .requireNonHardlinkedAccount(account);
            raw.requireLockObjectContinuity();
            try{
                Files.move(
                    temp,account,StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            }catch(AtomicMoveNotSupportedException unsupported){
                throw new IOException(
                    "G21.248 RAW_ATOMIC_MOVE_REQUIRED_NO_FALLBACK",
                    unsupported);
            }catch(IOException uncertain){
                throw new IOException(
                    "G21.248 RAW_ATOMIC_MOVE_UNCONFIRMED_NO_ACK",
                    uncertain);
            }
            try{
                AfterMoveProbe probe=afterMoveProbeForTest;
                if(probe!=null)probe.afterMove(account);
                try(FileChannel directory=FileChannel.open(
                        account.getParent(),StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS)){
                    directory.force(true);
                }
                requireSealed(account,identity,sealed);
                requirePublicationLockName(account,publicationLockIdentity);
                raw.requireLockObjectContinuity();
                // G21.266: an alias of the NEW published account inode
                // may appear after ATOMIC_MOVE, even when the old inode
                // passed the pre-move G21.260 nlink test. With the
                // existing DEFAULT-OFF G21.230 gate, detect this late
                // new-inode alias before ordinary RAW completion.
                // This is observed-only; the account may already be
                // replaced, so failure is UNCERTAIN, not rollback.
                LocalJvmAccountSessionLease
                    .requireNonHardlinkedAccount(account);
                // G21.264: an uncooperative marker may be introduced
                // AFTER account replacement (e.g. through the
                // deterministic after-move fault seam). Do not report
                // a normal forced RAW success while exact-account
                // manual-review/uncertain sidecars are observable.
                // This is a NEGATIVE after-publication observation:
                // bytes may already be replaced, so do not rollback,
                // delete markers, replay, or issue a final-save ACK.
                LocalDurableAccountReviewIntentPrimitive
                    .requireNoReviewMarker(account);
                // R38: bytes may already have moved. Late negative
                // witness evidence must produce an UNKNOWN/REVIEW
                // result, not a confirmed RAW success or rollback.
                LocalFinalSaveNegativeWitness
                    .requireNoNegativeWitness(account);
                if(new MailboxDurableReviewFence(pinned)
                        .present(accountUsername)||
                   new MailboxStrictUncertainFence(pinned)
                        .present(accountUsername)||
                   new MailboxStrictWriteIntentFence(pinned)
                        .present(accountUsername))
                    throw new IOException(
                        "G21.264 RAW_POST_MOVE_NEGATIVE_MARKER_PRESENT");
            }catch(IOException afterMove){
                throw new IOException(
                    "G21.248 RAW_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW",
                    afterMove);
            }
                return null;
            });
    }

    /**
     * G21.257: opt-in RAW observes the G21.39 publication lock's
     * original leaf identity while holding its cooperating OS lock.
     * A rename/recreate after that observation must veto account
     * replacement. This does NOT cryptographically bind FileChannel's
     * descriptor to the named leaf against a hostile race before the
     * first observation or after the final check.
     */
    /**
     * G21.258: optional, strictly NEGATIVE optimistic account-file
     * preimage witness before RAW serialization. Capturing absent
     * versus a bounded NOFOLLOW exact preexisting inode + digest lets
     * us veto an externally changed account at final publication.
     * The writer's caller is still responsible for stale semantic
     * snapshots prepared BEFORE this capture, and last-check/move
     * remains inherently vulnerable to an uncooperative actor.
     */
    static final class AccountPreimage{
        final BasicFileAttributes attributes;
        final byte[] sha256;
        AccountPreimage(BasicFileAttributes a,byte[] sha){
            attributes=a;sha256=sha;
        }
    }
    static AccountPreimage captureAccountPreimage(Path account)
        throws IOException{
        BasicFileAttributes attributes;
        try{
            attributes=Files.readAttributes(
                account,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        }catch(NoSuchFileException absent){
            return new AccountPreimage(null,null);
        }
        if(!attributes.isRegularFile()||
           attributes.isSymbolicLink()||attributes.fileKey()==null)
            throw new IOException(
                "G21.258 ACCOUNT_PREIMAGE_UNSAFE_NO_PUBLICATION");
        return new AccountPreimage(
            attributes,fingerprint(account,attributes));
    }

    static void requireAccountPreimageUnchanged(
        Path account,AccountPreimage original
    )throws IOException{
        if(original==null)
            throw new IOException(
                "G21.258 ACCOUNT_PREIMAGE_WITNESS_REQUIRED");
        AccountPreimage current=captureAccountPreimage(account);
        BasicFileAttributes before=original.attributes;
        BasicFileAttributes after=current.attributes;
        if((before==null)!=(after==null))
            throw new IOException(
                "G21.258 FORCED_RAW_PREIMAGE_DRIFT_NO_MOVE");
        if(before==null)return;
        if(!Objects.equals(before.fileKey(),after.fileKey())||
           !Objects.equals(before.creationTime(),after.creationTime())||
           before.size()!=after.size()||
           !Objects.equals(before.lastModifiedTime(),after.lastModifiedTime())||
           !MessageDigest.isEqual(original.sha256,current.sha256))
            throw new IOException(
                "G21.258 FORCED_RAW_PREIMAGE_DRIFT_NO_MOVE");
    }

    static BasicFileAttributes observePublicationLock(Path account)
        throws IOException{
        Path lock=MailboxAccountPublicationCoordinator.lockPath(account);
        BasicFileAttributes identity=Files.readAttributes(
            lock,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!identity.isRegularFile()||identity.isSymbolicLink()||
           identity.fileKey()==null)
            throw new IOException(
                "G21.257 RAW_PUBLICATION_LOCK_LEAF_UNSAFE");
        return identity;
    }

    static void requirePublicationLockName(
        Path account,BasicFileAttributes original
    )throws IOException{
        BasicFileAttributes now=observePublicationLock(account);
        if(original==null||
           !Objects.equals(original.fileKey(),now.fileKey())||
           !Objects.equals(original.creationTime(),now.creationTime()))
            throw new IOException(
                "G21.257 RAW_PUBLICATION_LOCK_NAME_CHANGED_NO_MOVE");
    }

    static boolean stillOwns(Path temp,BasicFileAttributes original){
        if(original==null)return false;
        try{
            requireSame(temp,original);
            return true;
        }catch(IOException problem){
            return false;
        }
    }
}

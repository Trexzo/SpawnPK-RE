package spk.local;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import java.util.Properties;

/**
 * G21.23 opt-in strict, synchronous account-snapshot writer. Not a
 * PlayerRepository replacement: existing autosaves could overwrite it.
 *
 * No ATOMIC_MOVE fallback, no silent directory-force downgrade. A returned
 * Receipt means file contents and directory metadata force calls returned.
 * This is a filesystem API contract, not a promise about every device or a
 * World-level crash-safe item grant. Never call this to settle widget 32181.
 */
final class StrictDurablePlayerSnapshotWriter {
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G2123_STRICT_FILE_BOUNDARY_ONLY";

    enum Phase {
        BEFORE_TEMP_CREATE,
        AFTER_TEMP_CREATE,
        AFTER_SERIALIZE,
        BEFORE_FILE_FORCE,
        BEFORE_ATOMIC_REPLACE,
        // G21.48: a write-ahead intent exists, but account bytes have
        // NOT yet been atomically replaced.
        AFTER_WRITE_AHEAD_INTENT,
        BEFORE_DIRECTORY_FORCE,
        AFTER_DIRECTORY_FORCE
    }

    interface FaultPoint {
        void check(Phase phase)throws IOException;
    }

    /**
     * G21.45: read-only, synchronous last-chance live-owner check.
     * The writer calls it after temp force, inside the cooperating
     * file-publication lock and immediately before atomic replacement.
     * It MUST NOT mutate player state or perform filesystem I/O.
     */
    interface BeforeWorldPublication {
        void requireStillCurrent()throws IOException;
    }

    /**
     * G21.46: post-move/parent-force read-only check before issuing a
     * strict receipt. A late mismatch is UNCONFIRMED: file replacement
     * may already be durable and must never be misreported as rollback.
     */
    interface AfterWorldPublication {
        void requireStillCurrent()throws IOException;
    }

    static final class UnconfirmedCommitException extends IOException {
        UnconfirmedCommitException(
            String message,Throwable cause
        ){
            super(message,cause);
        }
    }

    static final class Receipt {
        final String account;
        final Path file;
        final String authority;
        final int snapshotVersion;
        final String snapshotSha256;

        private Receipt(
            String account,Path file,int version,String sha256
        ){
            this.account=account;
            this.file=file;
            this.authority=AUTHORITY;
            this.snapshotVersion=version;
            this.snapshotSha256=sha256;
        }

        /** This proves which immutable snapshot this *operation* saved. */
        boolean matchesSnapshot(PlayerSnapshot snapshot){
            return snapshot!=null&&
                snapshot.version()==snapshotVersion&&
                account.equals(snapshot.username())&&
                snapshotSha256.equals(canonicalSnapshotSha256(snapshot));
        }
    }

    private final FilePlayerRepository.PathResolver resolver;
    private final FaultPoint faults;

    StrictDurablePlayerSnapshotWriter(
        FilePlayerRepository.PathResolver resolver
    ){
        this(resolver,phase->{});
    }

    StrictDurablePlayerSnapshotWriter(
        FilePlayerRepository.PathResolver resolver,
        FaultPoint faults
    ){
        this.resolver=Objects.requireNonNull(resolver,"resolver");
        this.faults=Objects.requireNonNull(faults,"faults");
    }

    synchronized Receipt saveStrict(
        PlayerSnapshot snapshot
    )throws IOException{
        return saveInternal(snapshot,null,null,null,false,false,null);
    }

    /**
     * G21.42: production strict PREPARED task through the exact
     * FilePlayerRepository account path. The historical direct
     * saveStrict() remains an explicit non-World/legacy primitive.
     */
    synchronized Receipt saveStrictForWorld(
        PlayerSnapshot snapshot,Path expectedRepositoryFile
    )throws IOException{
        return saveStrictForWorld(
            snapshot,expectedRepositoryFile,()->{},()->{}
        );
    }

    synchronized Receipt saveStrictForWorld(
        PlayerSnapshot snapshot,Path expectedRepositoryFile,
        BeforeWorldPublication publicationCheck
    )throws IOException{
        return saveStrictForWorld(
            snapshot,expectedRepositoryFile,publicationCheck,()->{}
        );
    }

    synchronized Receipt saveStrictForWorld(
        PlayerSnapshot snapshot,Path expectedRepositoryFile,
        BeforeWorldPublication publicationCheck,
        AfterWorldPublication postPublicationCheck
    )throws IOException{
        Objects.requireNonNull(publicationCheck,"publicationCheck");
        Objects.requireNonNull(
            postPublicationCheck,"postPublicationCheck"
        );
        return saveStrictForWorld(
            snapshot,expectedRepositoryFile,publicationCheck,
            postPublicationCheck,false
        );
    }

    /**
     * G21.48: the actual concrete World worker opts into a negative
     * in-progress WAL marker; compatibility/forensic entry points
     * retain their established explicit behavior.
     */
    synchronized Receipt saveStrictForWorld(
        PlayerSnapshot snapshot,Path expectedRepositoryFile,
        BeforeWorldPublication publicationCheck,
        AfterWorldPublication postPublicationCheck,
        boolean writeAheadIntent
    )throws IOException{
        Objects.requireNonNull(publicationCheck,"publicationCheck");
        Objects.requireNonNull(
            postPublicationCheck,"postPublicationCheck"
        );
        return saveInternal(
            snapshot,Objects.requireNonNull(
                expectedRepositoryFile,"expectedRepositoryFile"
            ).toAbsolutePath().normalize(),publicationCheck,
            postPublicationCheck,writeAheadIntent,false,null
        );
    }

    /**
     * G21.66: opt-in STRICT terminal account save. The caller's
     * publication callback must verify the active World reservation,
     * current full player preimage AND exact disk PREPARED account under
     * the same file lock immediately before the atomic replacement.
     *
     * Neither file-operation success nor marker cleanup grants rewards.
     * Native claim policy remains OFF and restart admission quarantines.
     */
    synchronized Receipt saveStrictTerminalForWorld(
        PlayerSnapshot terminal,Path expectedRepositoryFile,
        String preparedSha256,
        BeforeWorldPublication publicationCheck,
        AfterWorldPublication postPublicationCheck
    )throws IOException{
        Objects.requireNonNull(publicationCheck,"publicationCheck");
        Objects.requireNonNull(postPublicationCheck,"postPublicationCheck");
        if(preparedSha256==null||
           !preparedSha256.matches("[0-9a-f]{64}")||
           terminal==null||
           MailboxAtomicTerminalSnapshot.inspect(terminal).state!=
               MailboxAtomicTerminalSnapshot.State.COHERENT_TERMINAL_NO_GRANT||
           !preparedSha256.equals(terminal.value(
               "extension."+MailboxAtomicTerminalSnapshot.NAMESPACE+".before")))
            throw new IOException(
                "G21.66 STRICT_TERMINAL_SNAPSHOT_INVALID_NO_GRANT"
            );
        return saveInternal(
            terminal,Objects.requireNonNull(expectedRepositoryFile,
                "terminal repository file").toAbsolutePath().normalize(),
            publicationCheck,postPublicationCheck,true,true,preparedSha256
        );
    }

    // G21.100: lifetime acquired before ANY strict temp creation,
    // held across publication and cleanup; order lifetime -> account.
    private Receipt saveInternal(
        PlayerSnapshot snapshot,Path worldFile,
        BeforeWorldPublication publicationCheck,
        AfterWorldPublication postPublicationCheck,
        boolean writeAheadIntent,boolean terminalMode,
        String terminalPreparedSha256
    )throws IOException{
        PlayerSnapshot checked=Objects.requireNonNull(snapshot,"snapshot");
        Path selected=Objects.requireNonNull(
            resolver.resolve(checked.username()),"account file")
            .toAbsolutePath().normalize();
        return MailboxPublicationWriterLifecycle.withWriter(
            selected,()->saveInternalTracked(
                snapshot,worldFile,publicationCheck,
                postPublicationCheck,writeAheadIntent,terminalMode,
                terminalPreparedSha256,selected));
    }

    private Receipt saveInternalTracked(
        PlayerSnapshot snapshot,Path worldFile,
        BeforeWorldPublication publicationCheck,
        AfterWorldPublication postPublicationCheck,
        boolean writeAheadIntent,boolean terminalMode,
        String terminalPreparedSha256,Path pinnedAccountFile
    )throws IOException{
        PlayerSnapshot checked=Objects.requireNonNull(
            snapshot,"snapshot"
        );
        if(checked.version()!=PlayerSnapshot.CURRENT_VERSION)
            throw new IOException("unsupported account snapshot version");

        // G21.30: bind the opt-in strict-operation receipt to exact
        // canonical snapshot content. This is not a transaction ID.
        final String expectedSha256=canonicalSnapshotSha256(checked);
        String account=checked.username();
        // G21.100: the resolver may be stateful or adversarial. Use
        // EXACTLY the file selected before lifecycle acquisition; never
        // re-resolve it after obtaining the per-account lifetime lock.
        Path file=pinnedAccountFile;
        Path parent=file.getParent();
        if(parent==null)
            throw new IOException("no account parent directory");

        // G21.56: when the World repository supplies its exact account
        // file, every NEGATIVE marker in this strict operation must use
        // that same path. Do not independently re-resolve account markers
        // after the file is selected, locked, or atomically replaced.
        final FilePlayerRepository.PathResolver markerResolver=
            worldFile==null?resolver:requestedAccount->{
                if(!account.equals(requestedAccount))
                    throw new IllegalArgumentException(
                        "G21.56 strict marker account identity changed"
                    );
                return file;
            };

        if(worldFile!=null){
            if(!file.equals(worldFile))
                throw new IOException(
                    "G21.42 STRICT_WORLD_ACCOUNT_PATH_MISMATCH "+
                    account
                );
            if(terminalMode){
                if(MailboxAtomicTerminalSnapshot.inspect(checked).state!=
                        MailboxAtomicTerminalSnapshot.State
                            .COHERENT_TERMINAL_NO_GRANT)
                    throw new IOException(
                        "G21.66 STRICT_WORLD_TERMINAL_QUARANTINE "+
                        account
                    );
            }else if(MailboxPreparedRestartAdmission.inspect(checked).state!=
                    MailboxPreparedRestartAdmission.State
                        .VALID_PREPARED_UNCLAIMED)
                throw new IOException(
                    "G21.42 STRICT_WORLD_PREPARED_QUARANTINE "+
                    account
                );
            requireUnfenced(account,markerResolver);
        }

        final String markerSha256=terminalMode
            ?terminalPreparedSha256:expectedSha256;

        // Validation is completed before creating/changing a file.
        // This writer is an opt-in primitive and does not coordinate
        // pre-existing WorldPlayerPersistence workers.
        Properties properties=checked.toLegacyProperties();
        properties.setProperty(
            "saved.at",Instant.now().toString()
        );

        faults.check(Phase.BEFORE_TEMP_CREATE);
        Files.createDirectories(parent);

        Path temp=Files.createTempFile(
            parent,file.getFileName().toString()+".g2123-",".tmp"
        );
        final boolean[] replaced={false};
        try{
            faults.check(Phase.AFTER_TEMP_CREATE);
            try(FileChannel channel=FileChannel.open(
                    temp,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)){
                OutputStream output=Channels.newOutputStream(channel);
                properties.store(
                    output,"SpawnPK LocalLab strict account checkpoint"
                );
                output.flush();
                faults.check(Phase.AFTER_SERIALIZE);
                faults.check(Phase.BEFORE_FILE_FORCE);
                channel.force(true);
            }

            faults.check(Phase.BEFORE_ATOMIC_REPLACE);
            if(worldFile==null){
                // Preserve the historical unguarded strict primitive.
                publishStrictReplacement(
                    temp,file,parent,replaced
                );
            }else{
                // The marker publisher uses this SAME per-account lock.
                // The final check, atomic replace and metadata force are
                // serialized with its no-clobber hard-link publication.
                MailboxAccountPublicationCoordinator
                    .withExclusivePublication(worldFile,()->{
                        requireUnfenced(account,markerResolver);
                        if(!terminalMode){
                            // G21.66: same negative lock check as the
                            // ordinary guarded World save. A stale
                            // PREPARED strict barrier in a second JVM
                            // may not erase an existing terminal record.
                            FilePlayerRepository
                                .requireNoTerminalAccountPostimage(
                                    account,file
                                );
                        }
                        // G21.45: a World owner may have changed while
                        // the strict snapshot temp was being serialized
                        // or while this worker awaited this file lock.
                        // Check complete live state before Files.move,
                        // without holding its mutation lock over I/O.
                        publicationCheck.requireStillCurrent();
                        // G21.47: on an outcome which may ALREADY have
                        // replaced account bytes, publish the permanent
                        // NEGATIVE restart sidecar before releasing this
                        // account's publication FileLock. NEVER reacquire
                        // that lock inside this callback (deadlock).
                        try{
                            MailboxStrictWriteIntentFence intent=
                                writeAheadIntent
                                    ?new MailboxStrictWriteIntentFence(markerResolver)
                                    :null;
                            if(intent!=null){
                                intent.armInsidePublicationLock(
                                    account,markerSha256
                                );
                                // Test seam: failure at this point leaves
                                // intent published BEFORE any account move.
                                faults.check(Phase.AFTER_WRITE_AHEAD_INTENT);
                            }
                            publishStrictReplacement(
                                temp,file,parent,replaced
                            );
                            // G21.46: owner may have changed during
                            // replacement/force. Never emit a normal
                            // strict Receipt for a divergent owner.
                            try{
                                postPublicationCheck
                                    .requireStillCurrent();
                            }catch(IOException|RuntimeException divergent){
                                throw new UnconfirmedCommitException(
                                    "G21.46 STRICT_PREPARED_POSTPUBLICATION_UNCONFIRMED "+
                                    "account="+account+
                                    " action=MANUAL_REVIEW_NO_GRANT",
                                    divergent
                                );
                            }
                            if(intent!=null){
                                try{
                                    // Only the matching transient
                                    // IN-PROGRESS marker is removed once
                                    // account file and current owner are
                                    // verified. Permanent G21.32/G21.47
                                    // manual-review fences remain intact.
                                    intent.clearOnlyAfterConfirmedInsidePublicationLock(
                                        account,markerSha256
                                    );
                                }catch(IOException|RuntimeException badCleanup){
                                    throw new UnconfirmedCommitException(
                                        "G21.48 STRICT_WRITE_INTENT_CLEANUP_UNCONFIRMED "+
                                        "account="+account+
                                        " action=MANUAL_REVIEW_NO_GRANT",
                                        badCleanup
                                    );
                                }
                            }
                        }catch(UnconfirmedCommitException uncertain){
                            try{
                                new MailboxStrictUncertainFence(markerResolver)
                                    .armInsidePublicationLock(
                                        account,markerSha256
                                    );
                            }catch(IOException|RuntimeException markerFailure){
                                // Failure to publish a marker must never
                                // transform uncertainty into success.
                                // A crash or failed marker force may leave
                                // account state without a durable veto.
                                uncertain.addSuppressed(markerFailure);
                            }
                            throw uncertain;
                        }
                        return null;
                    });
            }

            return new Receipt(
                account,file,checked.version(),expectedSha256
            );
        }finally{
            if(!replaced[0])
                Files.deleteIfExists(temp);
        }
    }

    private void publishStrictReplacement(
        Path temp,Path file,Path parent,boolean[] replaced
    )throws IOException{
        // Deliberately NO AtomicMoveNotSupportedException fallback.
        Files.move(
            temp,file,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        );
        replaced[0]=true;

        try{
            faults.check(Phase.BEFORE_DIRECTORY_FORCE);
            try(FileChannel directory=FileChannel.open(
                    parent,StandardOpenOption.READ)){
                directory.force(true);
            }
            faults.check(Phase.AFTER_DIRECTORY_FORCE);
        }catch(IOException|RuntimeException failure){
            // After replacement the account may be the NEW file,
            // even when metadata durability was unconfirmed.
            throw new UnconfirmedCommitException(
                "atomic replacement occurred; durability unconfirmed",
                failure
            );
        }
    }

    private void requireUnfenced(
        String account,FilePlayerRepository.PathResolver markerResolver
    )throws IOException{
        // G21.88: no cooperating strict World replacement may erase
        // a recorded disk COMMIT, including when an uncooperative raw
        // write has restored a seemingly valid PREPARED account.
        Path selected=markerResolver.resolve(account)
            .toAbsolutePath().normalize();
        Path diskCommit=selected.resolveSibling(
            selected.getFileName().toString()+
            MailboxGuardedDiskCommitRecord.SUFFIX);
        if(Files.exists(diskCommit,java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new IOException(
                "G21.88 DISK_COMMIT_STRICT_SAVE_VETO account="+
                account+" action=REJECT_STRICT_SAVE");

        if(new MailboxDurableReviewFence(markerResolver).present(account)||
           new MailboxStrictUncertainFence(markerResolver).present(account)||
           new MailboxStrictWriteIntentFence(markerResolver).present(account))
            throw new IOException(
                "G21.42 STRICT_WORLD_MAILBOX_REVIEW_SAVE_VETO "+
                account+" action=REJECT_STRICT_SAVE"
            );
    }

    /**
     * Domain-separated, length-delimited UTF-8 encoding independent of
     * Properties.store order, timestamps and platform line endings.
     * Version, normalized account and *all* sorted gameplay keys are
     * covered, including Mailbox staged intent, inventory and claim state.
     */
    static String canonicalSnapshotSha256(PlayerSnapshot snapshot){
        PlayerSnapshot checked=Objects.requireNonNull(
            snapshot,"snapshot"
        );
        final MessageDigest digest;
        try{
            digest=MessageDigest.getInstance("SHA-256");
        }catch(NoSuchAlgorithmException unavailable){
            throw new IllegalStateException(
                "required SHA-256 digest unavailable",unavailable
            );
        }
        feedString(digest,"SPK.G2130.StrictSnapshotReceipt.v1");
        feedInt(digest,checked.version());
        feedString(digest,checked.username());
        feedInt(digest,checked.values().size());
        for(Map.Entry<String,String> item:checked.values().entrySet()){
            feedString(digest,item.getKey());
            feedString(digest,item.getValue());
        }
        byte[] bytes=digest.digest();
        char[] hex=new char[bytes.length*2];
        final char[] digits="0123456789abcdef".toCharArray();
        for(int i=0;i<bytes.length;i++){
            int unsigned=bytes[i]&0xff;
            hex[2*i]=digits[unsigned>>>4];
            hex[2*i+1]=digits[unsigned&0xf];
        }
        return new String(hex);
    }

    private static void feedString(MessageDigest digest,String value){
        byte[] utf8=Objects.requireNonNull(value,"digest field")
            .getBytes(StandardCharsets.UTF_8);
        feedInt(digest,utf8.length);
        digest.update(utf8);
    }

    private static void feedInt(MessageDigest digest,int n){
        digest.update((byte)(n>>>24));
        digest.update((byte)(n>>>16));
        digest.update((byte)(n>>>8));
        digest.update((byte)n);
    }
}


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
        return saveInternal(snapshot,null,null,null);
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
        return saveInternal(
            snapshot,Objects.requireNonNull(
                expectedRepositoryFile,"expectedRepositoryFile"
            ).toAbsolutePath().normalize(),publicationCheck,
            postPublicationCheck
        );
    }

    private Receipt saveInternal(
        PlayerSnapshot snapshot,Path worldFile,
        BeforeWorldPublication publicationCheck,
        AfterWorldPublication postPublicationCheck
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
        Path file=Objects.requireNonNull(
            resolver.resolve(account),"account path"
        ).toAbsolutePath().normalize();
        Path parent=file.getParent();
        if(parent==null)
            throw new IOException("no account parent directory");

        if(worldFile!=null){
            if(!file.equals(worldFile))
                throw new IOException(
                    "G21.42 STRICT_WORLD_ACCOUNT_PATH_MISMATCH "+
                    account
                );
            if(MailboxPreparedRestartAdmission.inspect(checked).state!=
                    MailboxPreparedRestartAdmission.State
                        .VALID_PREPARED_UNCLAIMED)
                throw new IOException(
                    "G21.42 STRICT_WORLD_PREPARED_QUARANTINE "+
                    account
                );
            requireUnfenced(account);
        }

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
                        requireUnfenced(account);
                        // G21.45: a World owner may have changed while
                        // the strict snapshot temp was being serialized
                        // or while this worker awaited this file lock.
                        // Check complete live state before Files.move,
                        // without holding its mutation lock over I/O.
                        publicationCheck.requireStillCurrent();
                        publishStrictReplacement(
                            temp,file,parent,replaced
                        );
                        // G21.46: if World-owned state changed while
                        // the file was being moved/forced, the filesystem
                        // may already contain this older snapshot.
                        // Never return an ordinary strict Receipt then.
                        try{
                            postPublicationCheck.requireStillCurrent();
                        }catch(IOException|RuntimeException divergent){
                            throw new UnconfirmedCommitException(
                                "G21.46 STRICT_PREPARED_POSTPUBLICATION_UNCONFIRMED "+
                                "account="+account+
                                " action=MANUAL_REVIEW_NO_GRANT",
                                divergent
                            );
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

    private void requireUnfenced(String account)throws IOException{
        if(new MailboxDurableReviewFence(resolver).present(account))
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


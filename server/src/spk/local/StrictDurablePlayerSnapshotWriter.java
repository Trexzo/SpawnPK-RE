package spk.local;

import java.io.IOException;
import java.io.OutputStream;
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

        private Receipt(String account,Path file){
            this.account=account;
            this.file=file;
            this.authority=AUTHORITY;
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
        PlayerSnapshot checked=Objects.requireNonNull(
            snapshot,"snapshot"
        );
        if(checked.version()!=PlayerSnapshot.CURRENT_VERSION)
            throw new IOException("unsupported account snapshot version");

        String account=checked.username();
        Path file=Objects.requireNonNull(
            resolver.resolve(account),"account path"
        ).toAbsolutePath().normalize();
        Path parent=file.getParent();
        if(parent==null)
            throw new IOException("no account parent directory");

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
        boolean replaced=false;
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
            // Deliberately NO AtomicMoveNotSupportedException fallback.
            Files.move(
                temp,file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            );
            replaced=true;

            try{
                faults.check(Phase.BEFORE_DIRECTORY_FORCE);
                // Not supported on every OS/filesystem; fail closed
                // instead of silently lowering the guarantee.
                try(FileChannel directory=FileChannel.open(
                        parent,StandardOpenOption.READ)){
                    directory.force(true);
                }
                faults.check(Phase.AFTER_DIRECTORY_FORCE);
            }catch(IOException | RuntimeException failure){
                // After atomic replace the file may already be the new
                // version. Cannot assert rollback, even on fsync failure.
                throw new UnconfirmedCommitException(
                    "atomic replacement occurred; durability unconfirmed",
                    failure
                );
            }

            return new Receipt(account,file);
        }finally{
            if(!replaced)
                Files.deleteIfExists(temp);
        }
    }
}

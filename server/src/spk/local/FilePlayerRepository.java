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

    FilePlayerRepository(){
        this(
            username->
                LocalAccountProfiles.accountFile(
                    username
                )
        );
    }

    FilePlayerRepository(PathResolver paths){
        this.paths=Objects.requireNonNull(
            paths,
            "paths"
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
        return new MailboxDurableReviewFence(paths).present(
            clean(username)
        );
    }

    @Override public void save(
        PlayerSnapshot snapshot
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

            try{
                Files.move(
                    tmp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            }catch(AtomicMoveNotSupportedException e){
                Files.move(
                    tmp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }

            completed=true;
        }finally{
            if(!completed)
                Files.deleteIfExists(tmp);
        }
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

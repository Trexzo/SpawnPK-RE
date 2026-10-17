package spk.local;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Schema-v1 file repository for explicit LocalLab world Shop state.
 * Uses the same temp-file + replace discipline as player persistence.
 */
final class FileLocalLabShopRepository
    implements LocalLabShopRepository {

    interface PathResolver {
        Path resolve();
    }

    private static final String VERSION_KEY=
        "version";
    private static final String ROCKTAIL_STOCK_KEY=
        "supplies.rocktail.stock";

    private final PathResolver paths;

    FileLocalLabShopRepository(){
        this(
            ()->{
                Path account=
                    LocalAccountProfiles.accountFile(
                        LocalAccountProfiles.PRIMARY
                    );
                Path root=account.getParent();

                if(root==null)
                    throw new IllegalStateException(
                        "account path has no parent: "+
                        account
                    );

                return root
                    .resolve("world")
                    .resolve(
                        "locallab-shop.properties"
                    );
            }
        );
    }

    FileLocalLabShopRepository(
        PathResolver paths
    ){
        this.paths=Objects.requireNonNull(
            paths,
            "paths"
        );
    }

    @Override public Optional<LocalLabShopSnapshot>
        load()throws IOException{
        Path file=normalizedPath();

        if(!Files.isRegularFile(file))
            return Optional.empty();

        Properties properties=
            new Properties();

        try(InputStream input=
                Files.newInputStream(file)){
            properties.load(input);
        }

        try{
            int version=
                Integer.parseInt(
                    require(
                        properties,
                        VERSION_KEY
                    )
                );
            long rocktailStock=
                Long.parseLong(
                    require(
                        properties,
                        ROCKTAIL_STOCK_KEY
                    )
                );

            return Optional.of(
                new LocalLabShopSnapshot(
                    version,
                    rocktailStock
                )
            );
        }catch(
            IllegalArgumentException|
            NullPointerException failure
        ){
            throw new IOException(
                "invalid LocalLab Shop snapshot file="+
                file+
                " error="+
                failure.getMessage(),
                failure
            );
        }
    }

    @Override public void save(
        LocalLabShopSnapshot snapshot
    )throws IOException{
        Objects.requireNonNull(
            snapshot,
            "snapshot"
        );

        Path file=normalizedPath();
        Path parent=file.getParent();

        if(parent!=null)
            Files.createDirectories(
                parent
            );

        Properties properties=
            new Properties();

        properties.setProperty(
            VERSION_KEY,
            Integer.toString(
                snapshot.version
            )
        );
        properties.setProperty(
            ROCKTAIL_STOCK_KEY,
            Long.toString(
                snapshot.rocktailStock
            )
        );
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
                    "SpawnPK LocalLab world Shop state"
                );
            }

            try{
                Files.move(
                    tmp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            }catch(
                AtomicMoveNotSupportedException
                    unsupported
            ){
                Files.move(
                    tmp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }

            completed=true;
        }finally{
            if(!completed)
                Files.deleteIfExists(
                    tmp
                );
        }
    }

    private Path normalizedPath(){
        Path file=paths.resolve();

        if(file==null)
            throw new IllegalArgumentException(
                "LocalLab Shop repository path"
            );

        return file
            .toAbsolutePath()
            .normalize();
    }

    private static String require(
        Properties properties,
        String key
    ){
        String value=
            properties.getProperty(key);

        if(value==null||
           value.trim().isEmpty())
            throw new IllegalArgumentException(
                "missing property "+key
            );

        return value.trim();
    }
}

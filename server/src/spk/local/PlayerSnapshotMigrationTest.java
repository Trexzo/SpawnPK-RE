package spk.local;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class PlayerSnapshotMigrationTest {
    public static void main(String[] args)throws Exception{
        Path dir=
            Files.createTempDirectory(
                "spk-snapshot-migration-"
            );

        try{
            testR85V1MigratesWithoutLoadRewrite(dir);
            testFutureVersionFailsClosed(dir);
            testMalformedMigratedSnapshotDoesNotMutateLivePlayer(dir);

            System.out.println(
                "PLAYER_SNAPSHOT_MIGRATION_PASS "+
                "v1ToV2=true "+
                "loadDoesNotRewrite=true "+
                "atomicUpgradeOnSave=true "+
                "futureFailsClosed=true "+
                "malformedNoLiveMutation=true"
            );
        }finally{
            try(java.util.stream.Stream<Path> stream=
                    Files.walk(dir)){
                stream.sorted(
                    Comparator.reverseOrder()
                ).forEach(path->{
                    try{
                        Files.deleteIfExists(path);
                    }catch(IOException ignored){}
                });
            }
        }
    }

    private static void testR85V1MigratesWithoutLoadRewrite(
        Path dir
    )throws Exception{
        Path file=
            dir.resolve(
                "opensrc-v1.properties"
            );

        WorldPlayer source=
            new WorldPlayer();

        source.movement()
            .setPersistentRun(true);
        source.movement()
            .setRunEnergy(67);
        source.equipment()
            .setWeapon(21566);
        source.playerState()
            .setSpecialEnergy(39);
        source.petAccessoryState()
            .setActiveItem(12345);

        SortedMap<String,String> v1Values=
            PlayerSnapshotSchemaV1.capture(
                source,
                source.petAccessoryState()
                    .activeItem()
            );

        Properties legacy=
            new Properties();

        legacy.setProperty(
            "format.version",
            Integer.toString(
                PlayerSnapshotMigrations
                    .R85_SCHEMA_VERSION
            )
        );
        legacy.setProperty(
            "username",
            "opensrc"
        );
        legacy.setProperty(
            "saved.at",
            "2026-01-01T00:00:00Z"
        );

        for(Map.Entry<String,String> entry:
                v1Values.entrySet())
            legacy.setProperty(
                entry.getKey(),
                entry.getValue()
            );

        // Unknown extension state is preserved by the version migration itself.
        legacy.setProperty(
            "extension.fixture",
            "keep-me"
        );

        writeProperties(
            file,
            legacy
        );

        byte[] before=
            Files.readAllBytes(file);

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->file
            );

        PlayerSnapshot migrated=
            repository.load(
                "opensrc"
            ).orElseThrow(
                ()->new AssertionError(
                    "v1 snapshot missing"
                )
            );

        if(migrated.version()!=
                PlayerSnapshot.CURRENT_VERSION)
            throw new AssertionError(
                "v1 did not migrate to current "+
                migrated
            );

        if(!"keep-me".equals(
                migrated.value(
                    "extension.fixture"
                )))
            throw new AssertionError(
                "migration lost extension key"
            );

        byte[] afterLoad=
            Files.readAllBytes(file);

        if(!Arrays.equals(
                before,
                afterLoad))
            throw new AssertionError(
                "repository load rewrote v1 file"
            );

        WorldPlayer live=
            new WorldPlayer();

        live.movement()
            .setRunEnergy(12);
        live.equipment()
            .setWeapon(4151);
        live.playerState()
            .setSpecialEnergy(7);

        LocalAccountLifecycle.LoadResult result=
            LocalAccountLifecycle.load(
                new LocalAccountLifecycle.Selection(
                    "opensrc",
                    true
                ),
                live,
                repository,
                item->item==12345,
                "[migration-test] "
            );

        if(!result.loaded||
           result.accessoryItem!=12345)
            throw new AssertionError(
                "migrated lifecycle load failed accessory="+
                result.accessoryItem+
                " loaded="+result.loaded
            );

        if(!live.movement()
                .persistentRun()||
           live.movement()
                .runEnergy()!=67||
           live.equipment()
                .weapon()!=21566||
           live.playerState()
                .specialEnergy()!=39)
            throw new AssertionError(
                "v1 gameplay state changed during migration"
            );

        repository.save(migrated);

        Properties upgraded=
            readProperties(file);

        if(!Integer.toString(
                PlayerSnapshot.CURRENT_VERSION
           ).equals(
                upgraded.getProperty(
                    "format.version"
                )))
            throw new AssertionError(
                "next save did not atomically upgrade version "+
                upgraded.getProperty(
                    "format.version"
                )
            );

        if(!"keep-me".equals(
                upgraded.getProperty(
                    "extension.fixture"
                )))
            throw new AssertionError(
                "upgrade save lost extension key"
            );

        if(Files.exists(
                file.resolveSibling(
                    file.getFileName().toString()+
                    ".tmp"
                )))
            throw new AssertionError(
                "upgrade left tmp file"
            );

        PlayerSnapshot reloaded=
            repository.load(
                "opensrc"
            ).orElseThrow(
                ()->new AssertionError(
                    "upgraded snapshot missing"
                )
            );

        if(reloaded.version()!=
                PlayerSnapshot.CURRENT_VERSION||
           !migrated.values().equals(
                reloaded.values()))
            throw new AssertionError(
                "v2 round-trip changed snapshot values"
            );
    }

    private static void testFutureVersionFailsClosed(
        Path dir
    )throws Exception{
        Path file=
            dir.resolve(
                "future.properties"
            );

        Properties future=
            new Properties();

        future.setProperty(
            "format.version",
            Integer.toString(
                PlayerSnapshot.CURRENT_VERSION+
                1
            )
        );
        future.setProperty(
            "username",
            "opensrc"
        );
        future.setProperty(
            "movement.runEnergy",
            "1"
        );

        writeProperties(
            file,
            future
        );

        byte[] before=
            Files.readAllBytes(file);

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->file
            );

        boolean failed=false;

        try{
            repository.load(
                "opensrc"
            );
        }catch(IOException expected){
            failed=
                expected.getMessage()
                    .contains(
                        "future snapshot version"
                    );
        }

        if(!failed)
            throw new AssertionError(
                "future snapshot version was accepted"
            );

        if(!Arrays.equals(
                before,
                Files.readAllBytes(file)))
            throw new AssertionError(
                "failed future-version load rewrote file"
            );
    }

    private static void testMalformedMigratedSnapshotDoesNotMutateLivePlayer(
        Path dir
    )throws Exception{
        Path file=
            dir.resolve(
                "malformed-v1.properties"
            );

        Properties malformed=
            new Properties();

        malformed.setProperty(
            "format.version",
            Integer.toString(
                PlayerSnapshotMigrations
                    .R85_SCHEMA_VERSION
            )
        );
        malformed.setProperty(
            "username",
            "opensrc"
        );
        malformed.setProperty(
            "bank.0",
            "not-a-stack"
        );
        malformed.setProperty(
            "movement.runEnergy",
            "1"
        );

        writeProperties(
            file,
            malformed
        );

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->file
            );

        // Version migration itself succeeds because it never interprets gameplay
        // keys. Full schema decode is staged against a detached WorldPlayer.
        PlayerSnapshot migrated=
            repository.load(
                "opensrc"
            ).orElseThrow(
                ()->new AssertionError(
                    "malformed v1 snapshot missing"
                )
            );

        if(migrated.version()!=
                PlayerSnapshot.CURRENT_VERSION)
            throw new AssertionError(
                "malformed v1 did not reach staged current snapshot"
            );

        WorldPlayer live=
            new WorldPlayer();

        live.movement()
            .setPersistentRun(true);
        live.movement()
            .setRunEnergy(88);
        live.equipment()
            .setWeapon(21566);
        live.playerState()
            .setSpecialEnergy(41);

        LocalAccountLifecycle.LoadResult result=
            LocalAccountLifecycle.load(
                new LocalAccountLifecycle.Selection(
                    "opensrc",
                    true
                ),
                live,
                repository,
                value->true,
                "[migration-test] "
            );

        if(result.loaded)
            throw new AssertionError(
                "malformed migrated snapshot reported loaded"
            );

        if(!live.movement()
                .persistentRun()||
           live.movement()
                .runEnergy()!=88||
           live.equipment()
                .weapon()!=21566||
           live.playerState()
                .specialEnergy()!=41)
            throw new AssertionError(
                "malformed migration partially mutated live player"
            );
    }

    private static Properties readProperties(
        Path file
    )throws IOException{
        Properties properties=
            new Properties();

        try(InputStream input=
                Files.newInputStream(file)){
            properties.load(input);
        }

        return properties;
    }

    private static void writeProperties(
        Path file,
        Properties properties
    )throws IOException{
        try(OutputStream output=
                Files.newOutputStream(
                    file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )){
            properties.store(
                output,
                "migration fixture"
            );
        }
    }
}

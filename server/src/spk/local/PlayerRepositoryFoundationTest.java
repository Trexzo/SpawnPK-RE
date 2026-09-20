package spk.local;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class PlayerRepositoryFoundationTest {
    public static void main(String[] args)throws Exception{
        Path dir=
            Files.createTempDirectory(
                "spk-player-repository-"
            );

        String oldAccountFile=
            System.getProperty(
                "spk.local.accountFile"
            );

        try{
            testSnapshotRoundTrip(dir);
            testLegacyProfileCompatibility(dir);
            testMalformedLoadDoesNotTouchPlayer(dir);
            testRepositorySubstitution();

            System.out.println(
                "PLAYER_REPOSITORY_FOUNDATION_PASS "+
                "immutableSnapshot=true "+
                "legacyV1Compatible=true "+
                "atomicFileRepository=true "+
                "failureNoLiveMutation=true "+
                "repositorySubstitutable=true"
            );
        }finally{
            if(oldAccountFile==null)
                System.clearProperty(
                    "spk.local.accountFile"
                );
            else
                System.setProperty(
                    "spk.local.accountFile",
                    oldAccountFile
                );

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

    private static void testSnapshotRoundTrip(
        Path dir
    )throws Exception{
        WorldPlayer source=
            new WorldPlayer();

        source.movement().setPersistentRun(true);
        source.movement().setRunEnergy(73);
        source.equipment().set(
            EquipmentSlot.HEAD,
            27034
        );
        source.equipment().setWeapon(21566);
        source.playerState().setCurrentLevel(
            PlayerState.RANGED,
            73
        );
        source.playerState().setSpecialEnergy(42);
        source.playerState().setNegativeEffects(
            7,
            11,
            13
        );

        PetDefinitionRepository.Def pet=
            PetDefinitionRepository.get(20776);

        if(pet==null)
            throw new AssertionError(
                "pet fixture missing"
            );

        source.petState().activate(pet);

        PlayerSnapshot snapshot=
            PlayerSnapshotCodec.capture(
                "opensrc",
                source
            );

        if(snapshot.version()!=1||
           !"opensrc".equals(
                snapshot.username()))
            throw new AssertionError(
                "snapshot identity "+
                snapshot
            );

        SortedMap<String,String> captured=
            new TreeMap<>(
                snapshot.values()
            );

        source.movement().setRunEnergy(12);
        source.equipment().set(
            EquipmentSlot.HEAD,
            -1
        );

        if(!captured.equals(
                snapshot.values()))
            throw new AssertionError(
                "snapshot mutated with live state"
            );

        Path file=
            dir.resolve(
                "roundtrip.properties"
            );

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->file
            );

        repository.save(snapshot);

        if(!Files.isRegularFile(file))
            throw new AssertionError(
                "repository save missing file"
            );

        if(Files.exists(
                file.resolveSibling(
                    file.getFileName()+
                    ".tmp"
                )))
            throw new AssertionError(
                "repository left tmp file"
            );

        Properties raw=new Properties();
        try(InputStream input=
                Files.newInputStream(file)){
            raw.load(input);
        }

        if(!"1".equals(
                raw.getProperty(
                    "format.version"
                ))||
           !"opensrc".equals(
                raw.getProperty(
                    "username"
                ))||
           raw.getProperty(
                "saved.at"
           )==null)
            throw new AssertionError(
                "repository metadata "+
                raw
            );

        PlayerSnapshot loaded=
            repository.load(
                "opensrc"
            ).orElseThrow(
                ()->new AssertionError(
                    "snapshot missing"
                )
            );

        if(!snapshot.values().equals(
                loaded.values()))
            throw new AssertionError(
                "snapshot values changed after file round-trip"
            );

        WorldPlayer restored=
            new WorldPlayer();

        PlayerSnapshotCodec.applyLegacy(
            loaded,
            restored
        );

        if(restored.equipment().itemAt(
                EquipmentSlot.HEAD)!=27034||
           restored.equipment().weapon()!=21566||
           !restored.movement().persistentRun()||
           restored.movement().runEnergy()!=73||
           restored.playerState().currentLevel(
                PlayerState.RANGED)!=73||
           restored.playerState().specialEnergy()!=42||
           restored.playerState().poison()!=7||
           restored.playerState().venom()!=11||
           restored.playerState().sicken()!=13||
           !restored.petState().active()||
           restored.petState().itemId()!=20776)
            throw new AssertionError(
                "snapshot compatibility apply failed"
            );
    }

    private static void testLegacyProfileCompatibility(
        Path dir
    )throws Exception{
        Path primary=
            dir.resolve(
                "opensrc.properties"
            );

        System.setProperty(
            "spk.local.accountFile",
            primary.toString()
        );

        WorldPlayer oldStyle=
            new WorldPlayer();

        oldStyle.equipment().setWeapon(
            21566
        );
        oldStyle.movement().setPersistentRun(
            true
        );
        oldStyle.movement().setRunEnergy(
            61
        );
        oldStyle.playerState().setSpecialEnergy(
            37
        );

        String saved=
            LocalAccountProfiles.save(
                "src",
                oldStyle.bank(),
                oldStyle.equipment(),
                oldStyle.movement(),
                oldStyle.petState(),
                oldStyle.playerState()
            );

        Path src=
            dir.resolve(
                "src.properties"
            );

        if(!Files.isRegularFile(src))
            throw new AssertionError(
                "legacy src file missing "+
                saved
            );

        FilePlayerRepository repository=
            new FilePlayerRepository();

        PlayerSnapshot migrated=
            repository.load(
                "src"
            ).orElseThrow(
                ()->new AssertionError(
                    "legacy src snapshot missing"
                )
            );

        WorldPlayer restored=
            new WorldPlayer();

        PlayerSnapshotCodec.applyLegacy(
            migrated,
            restored
        );

        if(restored.equipment().weapon()!=21566||
           !restored.movement().persistentRun()||
           restored.movement().runEnergy()!=61||
           restored.playerState().specialEnergy()!=37)
            throw new AssertionError(
                "legacy v1 file changed through repository"
            );
    }

    private static void testMalformedLoadDoesNotTouchPlayer(
        Path dir
    )throws Exception{
        Path bad=
            dir.resolve(
                "bad.properties"
            );

        Properties properties=
            new Properties();

        properties.setProperty(
            "format.version",
            "999"
        );
        properties.setProperty(
            "username",
            "opensrc"
        );
        properties.setProperty(
            "movement.runEnergy",
            "1"
        );

        try(OutputStream output=
                Files.newOutputStream(bad)){
            properties.store(
                output,
                "bad test"
            );
        }

        FilePlayerRepository repository=
            new FilePlayerRepository(
                username->bad
            );

        WorldPlayer live=
            new WorldPlayer();

        live.movement().setRunEnergy(
            88
        );
        live.equipment().setWeapon(
            21566
        );

        boolean failed=false;

        try{
            repository.load(
                "opensrc"
            );
        }catch(IOException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "malformed repository load accepted"
            );

        if(live.movement().runEnergy()!=88||
           live.equipment().weapon()!=21566)
            throw new AssertionError(
                "repository failure mutated live player"
            );
    }

    private static void testRepositorySubstitution()
        throws Exception{
        final Map<String,PlayerSnapshot> memory=
            new HashMap<>();

        PlayerRepository repository=
            new PlayerRepository(){
                @Override public Optional<PlayerSnapshot> load(
                    String username
                ){
                    return Optional.ofNullable(
                        memory.get(username)
                    );
                }

                @Override public void save(
                    PlayerSnapshot snapshot
                ){
                    memory.put(
                        snapshot.username(),
                        snapshot
                    );
                }
            };

        WorldPlayer player=
            new WorldPlayer();

        player.movement().setRunEnergy(
            55
        );

        PlayerSnapshot snapshot=
            PlayerSnapshotCodec.capture(
                "opensrc",
                player
            );

        repository.save(snapshot);

        PlayerSnapshot loaded=
            repository.load(
                "opensrc"
            ).orElseThrow(
                ()->new AssertionError(
                    "memory repository failed"
                )
            );

        if(!snapshot.values().equals(
                loaded.values()))
            throw new AssertionError(
                "repository interface leaked file semantics"
            );
    }
}

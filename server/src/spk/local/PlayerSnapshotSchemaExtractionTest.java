package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class PlayerSnapshotSchemaExtractionTest {
    public static void main(String[] args)throws Exception{
        testSchemaRoundTripAndAccessory();
        testGameplayClassesDoNotExposePropertiesApi();
        testZeroAccessoryIsAbsent();

        System.out.println(
            "PLAYER_SNAPSHOT_SCHEMA_EXTRACTION_PASS "+
            "roundTrip=true "+
            "accessorySingleSnapshot=true "+
            "gameplayPropertiesApi=false "+
            "zeroAccessoryAbsent=true"
        );
    }

    private static void testSchemaRoundTripAndAccessory(){
        WorldPlayer source=
            new WorldPlayer();

        source.movement().setPersistentRun(
            true
        );
        source.movement().setRunEnergy(
            63
        );
        source.equipment().setWeapon(
            21566
        );
        source.playerState().setCurrentLevel(
            PlayerState.RANGED,
            73
        );
        source.playerState().setXp(
            PlayerState.RANGED,
            8_765_432
        );
        source.playerState().setSpecialEnergy(
            44
        );
        source.playerState().setNegativeEffects(
            5,
            6,
            7
        );

        PetDefinitionRepository.Def pet=
            PetDefinitionRepository.get(
                20776
            );

        if(pet==null)
            throw new AssertionError(
                "pet fixture missing"
            );

        source.petState().activate(
            pet
        );

        final int accessory=12345;

        PlayerSnapshot snapshot=
            PlayerSnapshotCodec.capture(
                "opensrc",
                source,
                accessory
            );

        if(!Integer.toString(accessory).equals(
                snapshot.value(
                    "pet.accessoryItem"
                )))
            throw new AssertionError(
                "accessory missing from snapshot "+
                snapshot.values()
            );

        if(!"63".equals(
                snapshot.value(
                    "movement.runEnergy"
                ))||
           !"21566".equals(
                snapshot.value(
                    "equipment.3"
                ))||
           !"44".equals(
                snapshot.value(
                    "combat.special.energy"
                )))
            throw new AssertionError(
                "schema-v1 keys changed "+
                snapshot.values()
            );

        PlayerSnapshot normalized=
            PlayerSnapshotCodec
                .validateAndNormalize(
                    snapshot
                );

        if(PlayerSnapshotCodec.accessoryItem(
                normalized)!=accessory)
            throw new AssertionError(
                "normalization lost accessory"
            );

        WorldPlayer restored=
            new WorldPlayer();

        PlayerSnapshotCodec.applyLegacy(
            normalized,
            restored
        );

        if(!restored.movement().persistentRun()||
           restored.movement().runEnergy()!=63||
           restored.equipment().weapon()!=21566||
           restored.playerState().currentLevel(
                PlayerState.RANGED)!=73||
           restored.playerState().xp(
                PlayerState.RANGED)!=8_765_432||
           restored.playerState().specialEnergy()!=44||
           restored.playerState().poison()!=5||
           restored.playerState().venom()!=6||
           restored.playerState().sicken()!=7||
           !restored.petState().active()||
           restored.petState().itemId()!=20776)
            throw new AssertionError(
                "schema round-trip mismatch"
            );
    }

    private static void testGameplayClassesDoNotExposePropertiesApi(){
        Class<?>[] gameplay={
            BankState.class,
            EquipmentState.class,
            MovementState.class,
            PetState.class,
            PlayerState.class
        };

        for(Class<?> type:gameplay)
            for(Method method:
                    type.getDeclaredMethods()){
                if(method.getReturnType()==
                        java.util.Properties.class)
                    throw new AssertionError(
                        "Properties return leaked from "+
                        type.getSimpleName()+"."+
                        method.getName()
                    );

                for(Class<?> parameter:
                        method.getParameterTypes())
                    if(parameter==
                            java.util.Properties.class)
                        throw new AssertionError(
                            "Properties parameter leaked from "+
                            type.getSimpleName()+"."+
                            method.getName()
                        );
            }
    }

    private static void testZeroAccessoryIsAbsent(){
        PlayerSnapshot snapshot=
            PlayerSnapshotCodec.capture(
                "opensrc",
                new WorldPlayer(),
                0
            );

        if(snapshot.value(
                "pet.accessoryItem"
           )!=null)
            throw new AssertionError(
                "zero accessory should not be encoded"
            );

        if(PlayerSnapshotCodec.accessoryItem(
                snapshot)!=0)
            throw new AssertionError(
                "zero accessory decode mismatch"
            );
    }
}

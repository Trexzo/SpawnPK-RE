package spk.local;

import java.util.*;

/**
 * Regression-test adapter for exercising schema-v1 component persistence without
 * reintroducing storage/Properties APIs on gameplay state classes.
 */
final class PersistenceSchemaTestSupport {
    static SortedMap<String,String> captureMovement(
        MovementState movement
    ){
        return PlayerSnapshotSchemaV1.capture(
            new BankState(),
            new EquipmentState(),
            movement,
            null,
            null,
            0
        );
    }

    static void restoreMovement(
        MovementState movement,
        Map<String,String> values
    ){
        PlayerSnapshotSchemaV1.apply(
            values,
            new BankState(),
            new EquipmentState(),
            movement,
            null,
            null
        );
    }

    static SortedMap<String,String> capturePlayer(
        PlayerState player
    ){
        return PlayerSnapshotSchemaV1.capture(
            new BankState(),
            new EquipmentState(),
            new MovementState(),
            null,
            player,
            0
        );
    }

    static void restorePlayer(
        PlayerState player,
        Map<String,String> values
    ){
        PlayerSnapshotSchemaV1.apply(
            values,
            new BankState(),
            new EquipmentState(),
            new MovementState(),
            null,
            player
        );
    }

    static void restoreBank(
        BankState bank,
        Map<String,String> values
    ){
        PlayerSnapshotSchemaV1.apply(
            values,
            bank,
            new EquipmentState(),
            new MovementState(),
            null,
            null
        );
    }

    static SortedMap<String,String> values(
        String... keyValues
    ){
        if(keyValues==null||
           (keyValues.length&1)!=0)
            throw new IllegalArgumentException(
                "key/value pairs"
            );

        TreeMap<String,String> values=
            new TreeMap<>();

        for(int i=0;
            i<keyValues.length;
            i+=2)
            values.put(
                keyValues[i],
                keyValues[i+1]
            );

        return values;
    }

    private PersistenceSchemaTestSupport(){}
}

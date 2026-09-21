package spk.local;

import java.util.Arrays;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Snapshot recovery proof for Make-over appearance.
 *
 * PlayerSnapshotSchemaV1 is intentionally tolerant of malformed legacy scalar
 * values. In-range identity-kit ids that belong to the wrong gender/body slot
 * must therefore fall back to the exact default for that slot instead of
 * aborting the entire account restore.
 */
public final class MakeoverAppearanceSnapshotRecoveryTest {
    public static void main(String[] args){
        verifyWrongSlotKitFallsBack();
        verifyMissingAppearanceKeysRemainBackwardCompatible();

        System.out.println(
            "MAKEOVER_APPEARANCE_SNAPSHOT_RECOVERY_PASS "+
            "wrongSlotFallback=true missingKeysDefault=true accountRestoreSurvives=true"
        );
    }

    private static void verifyWrongSlotKitFallsBack(){
        WorldPlayer seed=new WorldPlayer();
        PlayerSnapshot base=
            PlayerSnapshotCodec.capture(
                "makeover_recovery",
                seed
            );

        SortedMap<String,String> values=
            new TreeMap<>(
                base.values()
            );

        values.put(
            "appearance.gender",
            Integer.toString(
                CharacterDesignProfile.FEMALE
            )
        );

        int[] validFemale={
            45,-1,56,61,67,70,79
        };
        int[] colours={
            11,15,14,5,23
        };

        for(int i=0;i<validFemale.length;i++)
            values.put(
                "appearance.kit."+i,
                Integer.toString(
                    validFemale[i]
                )
            );

        for(int i=0;i<colours.length;i++)
            values.put(
                "appearance.colour."+i,
                Integer.toString(
                    colours[i]
                )
            );

        // Numerically legal, semantically illegal for the female jaw slot.
        values.put(
            "appearance.kit.1",
            "10"
        );

        PlayerSnapshot malformed=
            new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                "makeover_recovery",
                values
            );

        WorldPlayer restored=
            new WorldPlayer();

        PlayerSnapshot normalized=
            PlayerSnapshotCodec.applyValidated(
                malformed,
                restored
            );

        int[] actual=
            restored.playerState()
                .characterKits();

        if(!Arrays.equals(
                actual,
                validFemale))
            throw new AssertionError(
                "wrong-slot fallback="+
                Arrays.toString(actual)+
                " expected="+
                Arrays.toString(validFemale)
            );

        if(!"-1".equals(
                normalized.value(
                    "appearance.kit.1"
                )))
            throw new AssertionError(
                "normalized female jaw="+
                normalized.value(
                    "appearance.kit.1"
                )
            );
    }

    private static void verifyMissingAppearanceKeysRemainBackwardCompatible(){
        WorldPlayer seed=new WorldPlayer();
        PlayerSnapshot base=
            PlayerSnapshotCodec.capture(
                "makeover_legacy",
                seed
            );

        SortedMap<String,String> values=
            new TreeMap<>(
                base.values()
            );

        values.keySet().removeIf(
            key->key.startsWith(
                "appearance."
            )
        );

        PlayerSnapshot legacy=
            new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,
                "makeover_legacy",
                values
            );

        WorldPlayer restored=
            new WorldPlayer();

        PlayerSnapshot normalized=
            PlayerSnapshotCodec.applyValidated(
                legacy,
                restored
            );

        PlayerState player=
            restored.playerState();

        int[] expectedKits=
            CharacterDesignProfile.defaultKits(
                CharacterDesignProfile.MALE
            );

        if(player.characterGender()!=
                CharacterDesignProfile.MALE)
            throw new AssertionError(
                "legacy gender="+
                player.characterGender()
            );

        if(!Arrays.equals(
                player.characterKits(),
                expectedKits))
            throw new AssertionError(
                "legacy kits="+
                Arrays.toString(
                    player.characterKits()
                )
            );

        if(!Arrays.equals(
                player.characterColours(),
                CharacterDesignProfile
                    .defaultColours()))
            throw new AssertionError(
                "legacy colours="+
                Arrays.toString(
                    player.characterColours()
                )
            );

        if(normalized.value(
                "appearance.gender")==null)
            throw new AssertionError(
                "normalized snapshot did not materialize appearance defaults"
            );
    }
}

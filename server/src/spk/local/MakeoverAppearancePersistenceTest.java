package spk.local;

import java.util.Arrays;

public final class MakeoverAppearancePersistenceTest {
    public static void main(String[] args){
        WorldPlayer source=new WorldPlayer();
        int[] kits={45,-1,56,61,67,70,79};
        int[] colours={11,15,14,5,23};

        if(!source.playerState().setCharacterAppearance(
                CharacterDesignProfile.FEMALE,
                kits,
                colours))
            throw new AssertionError("setup rejected");

        PlayerSnapshot snapshot=
            PlayerSnapshotCodec.capture(
                "makeovertest",
                source
            );

        if(!"1".equals(snapshot.value("appearance.gender")))
            throw new AssertionError("gender not captured");
        if(!"-1".equals(snapshot.value("appearance.kit.1")))
            throw new AssertionError("female jaw not captured");
        if(!"23".equals(snapshot.value("appearance.colour.4")))
            throw new AssertionError("skin colour not captured");

        WorldPlayer restored=new WorldPlayer();
        PlayerSnapshot normalized=
            PlayerSnapshotCodec.applyValidated(
                snapshot,
                restored
            );

        PlayerState p=restored.playerState();
        if(p.characterGender()!=CharacterDesignProfile.FEMALE)
            throw new AssertionError("gender restore");
        if(!Arrays.equals(p.characterKits(),kits))
            throw new AssertionError("kits restore "+Arrays.toString(p.characterKits()));
        if(!Arrays.equals(p.characterColours(),colours))
            throw new AssertionError("colours restore "+Arrays.toString(p.characterColours()));
        if(normalized.version()!=PlayerSnapshot.CURRENT_VERSION)
            throw new AssertionError("version drift");

        System.out.println(
            "MAKEOVER_APPEARANCE_PERSISTENCE_PASS gender=1 femaleJaw=-1 kits=7 colours=5 snapshotVersion="+
            normalized.version()
        );
    }
}

package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import spk.content.api.ContentDialoguePresentation;
import spk.content.builtin.MakeoverMageDialogueContent;

public final class MakeoverDialoguePresentationOwnershipTest {
    public static void main(String[] args)throws Exception{
        introParity();
        optionsParity();

        System.out.println(
            "MAKEOVER_DIALOGUE_PRESENTATION_OWNERSHIP_PASS "+
            "introContentOwned=true "+
            "twoOptionsContentOwned=true "+
            "wireParity=true "+
            "rawWidgetIdentity=false"
        );
    }

    private static void introParity()
        throws Exception
    {
        ByteArrayOutputStream expectedWire=
            new ByteArrayOutputStream();
        ByteArrayOutputStream actualWire=
            new ByteArrayOutputStream();

        ServerPacketWriter expected=
            writer(expectedWire);
        ServerPacketWriter actual=
            writer(actualWire);

        StandardDialoguePresentationAdapter
            .openNamedNpc(
                expected,
                599,
                "Make-over Mage",
                Collections.singletonList(
                    "How may I help you?"
                )
            );

        ContentDialoguePresentation presentation=
            ContentRuntimeAdapters
                .presentation(actual)
                .dialogue();

        MakeoverMageDialogueContent
            .presentIntro(
                presentation
            );

        expected.flush();
        actual.flush();

        require(
            Arrays.equals(
                expectedWire.toByteArray(),
                actualWire.toByteArray()
            ),
            "intro wire parity"
        );
    }

    private static void optionsParity()
        throws Exception
    {
        ByteArrayOutputStream expectedWire=
            new ByteArrayOutputStream();
        ByteArrayOutputStream actualWire=
            new ByteArrayOutputStream();

        ServerPacketWriter expected=
            writer(expectedWire);
        ServerPacketWriter actual=
            writer(actualWire);

        StandardDialoguePresentationAdapter
            .openTwoOptions(
                expected,
                "Select an Option",
                Arrays.asList(
                    "I'd like to change my look.",
                    "Nevermind."
                )
            );

        ContentDialoguePresentation presentation=
            ContentRuntimeAdapters
                .presentation(actual)
                .dialogue();

        MakeoverMageDialogueContent
            .presentOptions(
                presentation
            );

        expected.flush();
        actual.flush();

        require(
            Arrays.equals(
                expectedWire.toByteArray(),
                actualWire.toByteArray()
            ),
            "two-option wire parity"
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{91,92,93,94}
            )
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private MakeoverDialoguePresentationOwnershipTest(){}
}

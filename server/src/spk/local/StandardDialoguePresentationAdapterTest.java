package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;

public final class StandardDialoguePresentationAdapterTest {
    public static void main(String[] args)throws Exception{
        namedNpcMakeoverParity();
        exactFamilies();
        twoOptionNormalization();
        augmentedOptionCloseNormalization();
        statementProjection();

        System.out.println(
            "STANDARD_DIALOGUE_PRESENTATION_ADAPTER_PASS "+
            "namedNpc1to4=true "+
            "statement1to5=true "+
            "twoOption2459=true "+
            "augmentedClose54195=true "+
            "keyboardContinue4907=true "+
            "makeoverIntroWireParity=true "+
            "makeoverOptionsWireParity=true"
        );
    }

    private static void namedNpcMakeoverParity()
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

        expected.varShort(
            126,
            BootstrapPackets.widgetText126(
                4884,
                "Make-over Mage"
            )
        );
        expected.varShort(
            126,
            BootstrapPackets.widgetText126(
                4885,
                "How may I help you?"
            )
        );
        expected.fixed(
            75,
            BootstrapPackets.interfaceNpcHead75(
                599,
                4883
            )
        );
        expected.fixed(
            164,
            BootstrapPackets.chatboxInterface164(
                4882
            )
        );

        StandardDialoguePresentationAdapter
            .openNamedNpc(
                actual,
                599,
                "Make-over Mage",
                Collections.singletonList(
                    "How may I help you?"
                )
            );

        expected.flush();
        actual.flush();

        require(
            Arrays.equals(
                expectedWire.toByteArray(),
                actualWire.toByteArray()
            ),
            "Make-over intro wire parity"
        );

        expectedWire.reset();
        actualWire.reset();

        expected=
            writer(expectedWire);
        actual=
            writer(actualWire);

        expected.varShort(
            126,
            BootstrapPackets.widgetText126(
                2460,
                "Select an Option"
            )
        );
        expected.varShort(
            126,
            BootstrapPackets.widgetText126(
                2461,
                "I'd like to change my look."
            )
        );
        expected.varShort(
            126,
            BootstrapPackets.widgetText126(
                2462,
                "Nevermind."
            )
        );
        expected.fixed(
            164,
            BootstrapPackets.chatboxInterface164(
                2459
            )
        );

        StandardDialoguePresentationAdapter
            .openTwoOptions(
                actual,
                "Select an Option",
                Arrays.asList(
                    "I'd like to change my look.",
                    "Nevermind."
                )
            );

        expected.flush();
        actual.flush();

        require(
            Arrays.equals(
                expectedWire.toByteArray(),
                actualWire.toByteArray()
            ),
            "Make-over options wire parity"
        );
    }

    private static void exactFamilies(){
        int[] npcRoots={
            4882,4887,4893,4900
        };
        int[] npcContinue={
            4886,4892,4899,4907
        };
        int[] statements={
            356,359,363,368,374
        };
        int[] statementContinue={
            358,362,367,373,380
        };

        for(int lines=1;lines<=4;lines++){
            require(
                StandardDialoguePresentationAdapter
                    .namedNpcRoot(lines)==
                    npcRoots[lines-1],
                "named NPC root lines="+lines
            );
            require(
                StandardDialoguePresentationAdapter
                    .namedNpcContinueWidget(lines)==
                    npcContinue[lines-1],
                "named NPC Continue lines="+lines
            );
            require(
                StandardDialoguePresentationAdapter
                    .acceptsNamedNpcContinue(
                        lines,
                        npcContinue[lines-1]
                    ),
                "mouse Continue lines="+lines
            );
            require(
                StandardDialoguePresentationAdapter
                    .acceptsNamedNpcContinue(
                        lines,
                        StandardDialoguePresentationAdapter
                            .KEYBOARD_CONTINUE_WIDGET
                    ),
                "keyboard Continue lines="+lines
            );
        }

        for(int lines=1;lines<=5;lines++){
            require(
                StandardDialoguePresentationAdapter
                    .statementRoot(lines)==
                    statements[lines-1],
                "statement root lines="+lines
            );
            require(
                StandardDialoguePresentationAdapter
                    .statementContinueWidget(lines)==
                    statementContinue[lines-1],
                "statement Continue lines="+lines
            );
        }
    }

    private static void twoOptionNormalization(){
        require(
            StandardDialoguePresentationAdapter
                .twoOptionRoot()==2459,
            "two-option root"
        );
        require(
            StandardDialoguePresentationAdapter
                .twoOptionWidget(1)==2461&&
            StandardDialoguePresentationAdapter
                .twoOptionWidget(2)==2462,
            "two-option widgets"
        );
        require(
            StandardDialoguePresentationAdapter
                .twoOptionIndexForWidget(2461)==1&&
            StandardDialoguePresentationAdapter
                .twoOptionIndexForWidget(2462)==2&&
            StandardDialoguePresentationAdapter
                .twoOptionIndexForWidget(2463)==0,
            "two-option normalization"
        );
    }

    private static void augmentedOptionCloseNormalization(){
        require(
            StandardDialoguePresentationAdapter
                .isAugmentedOptionCloseWidget(
                    54195
                ),
            "augmented option close"
        );
        require(
            !StandardDialoguePresentationAdapter
                .isAugmentedOptionCloseWidget(
                    54194
                )&&
            !StandardDialoguePresentationAdapter
                .isAugmentedOptionCloseWidget(
                    54196
                ),
            "augmented option close exactness"
        );
    }

    private static void statementProjection()
        throws Exception
    {
        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        ServerPacketWriter packets=
            writer(wire);

        StandardDialoguePresentationAdapter
            .openStatement(
                packets,
                Arrays.asList(
                    "One",
                    "Two",
                    "Three",
                    "Four",
                    "Five"
                )
            );

        packets.flush();

        require(
            wire.size()>0,
            "statement packets"
        );
    }

    private static ServerPacketWriter writer(
        ByteArrayOutputStream wire
    ){
        return new ServerPacketWriter(
            wire,
            new IsaacCipher(
                new int[]{41,42,43,44}
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

    private StandardDialoguePresentationAdapterTest(){}
}

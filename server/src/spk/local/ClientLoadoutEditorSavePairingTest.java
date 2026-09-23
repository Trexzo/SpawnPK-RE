package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ClientLoadoutEditorSavePairingTest {
    private static final int[] SEED={
        0x24681357,
        0x10293847,
        0x11223344,
        0x55667788
    };

    public static void main(String[] args)throws Exception{
        exactPairThroughProbe();
        boundedPairing();
        malformedHalves();
        exactEquipmentProjectionOrder();

        System.out.println(
            "CLIENT_LOADOUT_EDITOR_SAVE_PAIRING_PASS "+
            "cld1Inventory28=true "+
            "cld2Equipment14=true "+
            "consecutivePair=true "+
            "singleTypedSnapshot=true "+
            "genericCommandPreserved=true "+
            "boundedStage=true "+
            "interveningPacketExpires=true "+
            "placeholderGuard=true "+
            "domainMutationFailClosed=true"
        );
    }

    private static void exactPairThroughProbe()
        throws Exception{
        String inventory=inventoryPayload();
        String equipment=equipmentPayload();

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();
        IsaacCipher encoder=
            new IsaacCipher(SEED.clone());

        writeCommand(
            wire,
            encoder,
            "cld1 "+inventory
        );
        writeCommand(
            wire,
            encoder,
            "cld2 "+equipment
        );
        writeCommand(
            wire,
            encoder,
            "ordinary_after_loadout"
        );

        ClientPacketProbe probe=
            new ClientPacketProbe(
                new ByteArrayInputStream(
                    wire.toByteArray()
                ),
                new IsaacCipher(SEED.clone()),
                "[loadout-pair] "
            );

        require(
            probe.readNextKnownPacket(),
            "decode cld1"
        );
        require(
            probe.typedRequestCount()==0,
            "cld1 must not publish half request"
        );

        require(
            probe.readNextKnownPacket(),
            "decode cld2"
        );
        require(
            probe.typedRequestCount()==1,
            "complete pair should publish one request"
        );

        ClientRequest raw=
            probe.takeTypedRequest();
        require(
            raw instanceof
                LoadoutEditorSaveClientRequest,
            "paired request type"
        );

        LoadoutEditorSaveClientRequest paired=
            (LoadoutEditorSaveClientRequest)raw;

        require(
            paired.inventory().size()==28&&
            paired.equipment().size()==14,
            "paired entry counts"
        );
        require(
            paired.inventoryPacketSequence()==1L&&
            paired.equipmentPacketSequence()==2L,
            "paired packet sequence"
        );

        for(int i=0;i<28;i++){
            LoadoutEditorSaveClientRequest.WireEntry e=
                paired.inventory().get(i);
            require(
                e.itemId==100+i&&
                e.amount==1+i,
                "inventory entry "+i
            );
        }

        int[] source=
            LoadoutEditorCompatibilityPairer
                .EQUIPMENT_SOURCE_INDICES;
        for(int i=0;i<source.length;i++){
            LoadoutEditorSaveClientRequest.WireEntry e=
                paired.equipment().get(i);
            if(source[i]<0){
                require(
                    e.itemId==0&&e.amount==0,
                    "equipment placeholder "+i
                );
            }else{
                require(
                    e.itemId==2000+source[i]&&
                    e.amount==20+source[i],
                    "equipment entry "+i
                );
            }
        }

        ClientRequestMetadata metadata=
            paired.metadata();
        require(
            metadata.opcode==103&&
            "PAIRED_VAR_BYTE_CLD1_INVENTORY28_CLD2_EQUIPMENT14"
                .equals(metadata.schema)&&
            "V308_CLIENT_LOADOUT_EDITOR_SAVE_PAIR"
                .equals(metadata.source)&&
            metadata.provenance==
                ClientRequestProvenance
                    .EXACT_CURRENT_CLIENT,
            "paired metadata"
        );

        assertDiagnostic(
            LocalPendingRequestDispatcher
                .loadoutEditorSaveFailClosedDiagnostic(
                    paired
                )
        );

        require(
            probe.readNextKnownPacket(),
            "decode generic command"
        );
        ClientRequest generic=
            probe.takeTypedRequest();
        require(
            generic instanceof
                CommandClientRequest,
            "ordinary C2S103 preserved"
        );
        require(
            "ordinary_after_loadout".equals(
                ((CommandClientRequest)generic)
                    .command()
            ),
            "ordinary command text"
        );

        require(
            probe.takeTypedRequest()==null&&
            probe.isAligned(),
            "queue/alignment completion"
        );
    }

    private static void boundedPairing(){
        LoadoutEditorCompatibilityPairer pairer=
            new LoadoutEditorCompatibilityPairer();

        LoadoutEditorCompatibilityPairer.Outcome first=
            pairer.accept(
                "cld1 "+inventoryPayload(),
                10L
            );
        require(
            first.kind==
                LoadoutEditorCompatibilityPairer
                    .Kind.INVENTORY_STAGED&&
            pairer.hasPendingInventory(),
            "inventory stage"
        );

        String expired=
            pairer.expireForInterveningPacket(
                11L
            );
        require(
            expired!=null&&
            !pairer.hasPendingInventory(),
            "intervening packet expiry"
        );

        LoadoutEditorCompatibilityPairer.Outcome orphan=
            pairer.accept(
                "cld2 "+equipmentPayload(),
                12L
            );
        require(
            orphan.kind==
                LoadoutEditorCompatibilityPairer
                    .Kind.REJECTED&&
            orphan.reason.contains(
                "ORPHAN_CLD2"
            ),
            "orphan cld2 rejection"
        );

        pairer.accept(
            "cld1 "+inventoryPayload(),
            20L
        );
        LoadoutEditorCompatibilityPairer.Outcome restage=
            pairer.accept(
                "cld1 "+inventoryPayload(),
                21L
            );
        require(
            restage.kind==
                LoadoutEditorCompatibilityPairer
                    .Kind.INVENTORY_STAGED&&
            "PREVIOUS_INVENTORY_HALF_REPLACED"
                .equals(restage.reason),
            "single-slot restaging"
        );
    }

    private static void malformedHalves(){
        LoadoutEditorCompatibilityPairer pairer=
            new LoadoutEditorCompatibilityPairer();

        LoadoutEditorCompatibilityPairer.Outcome missing=
            pairer.accept(
                "cld1",
                1L
            );
        require(
            missing.kind==
                LoadoutEditorCompatibilityPairer
                    .Kind.REJECTED,
            "missing cld1 payload"
        );

        String[] equipment=
            equipmentPayload().split(" ");
        equipment[6]="999,1";

        pairer.accept(
            "cld1 "+inventoryPayload(),
            2L
        );
        LoadoutEditorCompatibilityPairer.Outcome badPlaceholder=
            pairer.accept(
                "cld2 "+
                String.join(" ",equipment),
                3L
            );

        require(
            badPlaceholder.kind==
                LoadoutEditorCompatibilityPairer
                    .Kind.REJECTED&&
            badPlaceholder.reason.contains(
                "PLACEHOLDER_6"
            ),
            "equipment placeholder guard"
        );

        LoadoutEditorCompatibilityPairer.Outcome upper=
            pairer.accept(
                "CLD1 "+inventoryPayload(),
                4L
            );
        require(
            upper.kind==
                LoadoutEditorCompatibilityPairer
                    .Kind.UNRECOGNIZED,
            "non-exact case stays generic"
        );
    }

    private static void exactEquipmentProjectionOrder(){
        int[] expected={
            1,3,4,6,7,8,-1,10,-1,12,13,-1,14,5
        };

        require(
            Arrays.equals(
                expected,
                LoadoutEditorCompatibilityPairer
                    .EQUIPMENT_SOURCE_INDICES
            ),
            "exact equipment projection order"
        );
    }

    private static String inventoryPayload(){
        ArrayList<String> entries=
            new ArrayList<>();
        for(int i=0;i<28;i++)
            entries.add(
                (100+i)+","+(1+i)
            );
        return String.join(" ",entries);
    }

    private static String equipmentPayload(){
        ArrayList<String> entries=
            new ArrayList<>();
        for(int source:
                LoadoutEditorCompatibilityPairer
                    .EQUIPMENT_SOURCE_INDICES){
            entries.add(
                source<0
                ?"0,0"
                :(2000+source)+","+(20+source)
            );
        }
        return String.join(" ",entries);
    }

    private static void assertDiagnostic(
        String value
    ){
        require(
            value!=null&&
            value.startsWith(
                "LOADOUT_EDITOR_SAVE_FAIL_CLOSED"
            )&&
            value.contains(
                "inventoryEntries=28"
            )&&
            value.contains(
                "equipmentEntries=14"
            )&&
            value.contains(
                "reason=LOADOUT_EDITOR_TARGET_ADAPTER_UNPROVEN"
            )&&
            value.contains(
                "stateMutation=false"
            )&&
            value.contains(
                "authority=EXACT_CURRENT_CLIENT"
            ),
            "dispatcher diagnostic"
        );
    }

    private static void writeCommand(
        OutputStream output,
        IsaacCipher cipher,
        String command
    )throws IOException{
        byte[] text=
            command.getBytes(
                StandardCharsets.ISO_8859_1
            );

        int length=text.length+1;
        if(length>255)
            throw new IllegalArgumentException(
                "test command too long length="+
                length
            );

        output.write(
            (103+cipher.nextInt())&255
        );
        output.write(length);
        output.write(text);
        output.write(10);
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private ClientLoadoutEditorSavePairingTest(){}
}

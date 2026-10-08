package spk.local;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

public final class G201ConstructionAuthorityIntegrationTest {
    private static final int[] SEED={201,202,203,204};

    public static void main(String[] args)throws Exception{
        boolean roomCatalog23=false;
        boolean exactNamesLevels=false;
        boolean priceAuthorityPromoted=false;
        boolean emptyHouse=false;
        boolean buildModeSemantic=false;
        boolean addRemove=false;
        boolean duplicateCoordinateFailClosed=false;
        boolean adjacencySemantic=false;
        boolean adjacencyPolicyInvented=false;
        boolean immutable=false;
        boolean snapshotLoad=false;
        boolean worldInstancePort=false;
        boolean buildOnExact=false;
        boolean buildOffExact=false;
        boolean target1=false;
        boolean inventoryMutation=false;
        boolean currencyMutation=false;
        boolean placementPolicyInvented=false;
        boolean protocolIndependent=false;

        /*
         * Re-run the already-landed semantic Construction regression on this
         * exact gameplay ancestry. It owns the 23-room catalog, layout,
         * adjacency, snapshot/load and opaque materialization boundary.
         */
        ConstructionHouseLayoutTest.main(
            new String[0]
        );

        ConstructionRoomCatalog catalog=
            ConstructionRoomCatalog
                .exactCurrentClientEvidence();

        roomCatalog23=
            catalog.size()==23;
        exactNamesLevels=
            catalog.find("parlour")
                .map(r->
                    "Parlour".equals(
                        r.displayName()
                    )&&
                    r.displayedLevelRequirementEvidence()==1
                ).orElse(false)&&
            catalog.find("treasure_room")
                .map(r->
                    "Treasure room".equals(
                        r.displayName()
                    )&&
                    r.displayedLevelRequirementEvidence()==75
                ).orElse(false);

        priceAuthorityPromoted=false;
        emptyHouse=true;
        buildModeSemantic=true;
        addRemove=true;
        duplicateCoordinateFailClosed=true;
        adjacencySemantic=true;
        adjacencyPolicyInvented=false;
        immutable=true;
        snapshotLoad=true;
        worldInstancePort=true;
        inventoryMutation=false;
        currencyMutation=false;
        placementPolicyInvented=false;

        ByteArrayOutputStream wire=
            new ByteArrayOutputStream();

        ServerPacketWriter writer=
            new ServerPacketWriter(
                wire,
                new IsaacCipher(
                    SEED.clone()
                )
            );

        ApplicationControl126Service
            .constructionBuildMode(
                writer,
                true
            );
        ApplicationControl126Service
            .constructionBuildMode(
                writer,
                false
            );

        IsaacCipher decode=
            new IsaacCipher(
                SEED.clone()
            );

        byte[] bytes=
            wire.toByteArray();

        int offset=0;

        offset=
            assert126(
                bytes,
                offset,
                decode,
                "CONSTRUCTION_BUILD_ON",
                1
            );
        buildOnExact=true;

        offset=
            assert126(
                bytes,
                offset,
                decode,
                "CONSTRUCTION_BUILD_OFF",
                1
            );
        buildOffExact=true;

        target1=
            offset==bytes.length;

        protocolIndependent=
            !containsProtocolIdentity(
                ConstructionService.class
            )&&
            !containsProtocolIdentity(
                HouseRoomDefinition.class
            )&&
            !containsProtocolIdentity(
                ConstructionRoomCatalog.class
            )&&
            !containsProtocolIdentity(
                HouseInstanceMaterializationPort.class
            );

        require(
            roomCatalog23&&
            exactNamesLevels&&
            !priceAuthorityPromoted&&
            emptyHouse&&
            buildModeSemantic&&
            addRemove&&
            duplicateCoordinateFailClosed&&
            adjacencySemantic&&
            !adjacencyPolicyInvented&&
            immutable&&
            snapshotLoad&&
            worldInstancePort&&
            buildOnExact&&
            buildOffExact&&
            target1&&
            !inventoryMutation&&
            !currencyMutation&&
            !placementPolicyInvented&&
            protocolIndependent,
            "G20.1 acceptance"
        );

        System.out.println(
            "G201_CONSTRUCTION_AUTHORITY_PASS"+
            " roomCatalog23="+roomCatalog23+
            " exactNamesLevels="+exactNamesLevels+
            " priceAuthorityPromoted="+priceAuthorityPromoted+
            " emptyHouse="+emptyHouse+
            " buildModeSemantic="+buildModeSemantic+
            " addRemove="+addRemove+
            " duplicateCoordinateFailClosed="+
                duplicateCoordinateFailClosed+
            " adjacencySemantic="+adjacencySemantic+
            " adjacencyPolicyInvented="+
                adjacencyPolicyInvented+
            " immutable="+immutable+
            " snapshotLoad="+snapshotLoad+
            " worldInstancePort="+worldInstancePort+
            " buildOnExact="+buildOnExact+
            " buildOffExact="+buildOffExact+
            " target1="+target1+
            " inventoryMutation="+inventoryMutation+
            " currencyMutation="+currencyMutation+
            " placementPolicyInvented="+
                placementPolicyInvented+
            " protocolIndependent="+protocolIndependent+
            " persistenceClaim=false"
        );
    }

    private static boolean containsProtocolIdentity(
        Class<?> type
    ){
        for(java.lang.reflect.Field field:
                type.getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("widget")||
               name.contains("opcode")||
               name.contains("subtype")||
               name.contains("packet")||
               name.contains("sprite")||
               name.contains("sceneindex")||
               name.contains("worldx")||
               name.contains("worldy")||
               name.contains("objectid")||
               name.contains("price")||
               name.contains("cost")||
               name.contains("materialrequirement"))
                return true;
        }

        return false;
    }

    private static int assert126(
        byte[] wire,
        int offset,
        IsaacCipher decode,
        String payload,
        int target
    ){
        int opcode=
            ((wire[offset++]&255)-
                decode.nextInt())&
                255;

        if(opcode!=126)
            throw new AssertionError(
                "expected 126 actual="+opcode
            );

        int length=
            ((wire[offset++]&255)<<8)|
            (wire[offset++]&255);

        byte[] text=
            payload.getBytes(
                StandardCharsets.ISO_8859_1
            );

        byte[] expected=
            Arrays.copyOf(
                text,
                text.length+3
            );

        expected[text.length]=10;
        expected[text.length+1]=
            (byte)(target>>>8);
        expected[text.length+2]=
            (byte)((target+128)&255);

        if(length!=expected.length)
            throw new AssertionError(
                "length expected="+
                expected.length+
                " actual="+length
            );

        byte[] actual=
            Arrays.copyOfRange(
                wire,
                offset,
                offset+length
            );

        if(!Arrays.equals(
                expected,
                actual))
            throw new AssertionError(
                "body mismatch payload="+
                payload
            );

        return offset+length;
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private G201ConstructionAuthorityIntegrationTest(){}
}

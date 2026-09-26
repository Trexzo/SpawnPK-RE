package spk.local;

import java.lang.reflect.Method;
import java.util.Locale;

public final class MonsterSpawnerPresentationTest {
    public static void main(String[] args)
        throws Exception{
        exactConstants();
        exactInputRouting();
        exactPacketCompatibility();
        unattachedControlsStayUnowned();
        policyStillUnowned();

        System.out.println(
            "MONSTER_SPAWNER_PRESENTATION_PASS "+
            "root41000=true "+
            "toggle41007=true "+
            "rows22=true "+
            "selectedNpc41019=true "+
            "scroll41020=true "+
            "distanced41017Unattached=true "+
            "x3_41018Unattached=true "+
            "catalogOwned=false "+
            "spawnPolicyOwned=false"
        );
    }

    private static void exactConstants(){
        require(
            MonsterSpawnerPresentation.ROOT==41000,
            "root"
        );
        require(
            MonsterSpawnerPresentation
                .TOGGLE_WIDGET==41007,
            "toggle"
        );
        require(
            MonsterSpawnerPresentation
                .X5_PRESENTATION_WIDGET==41016,
            "x5 presentation"
        );
        require(
            MonsterSpawnerPresentation
                .SELECTED_NPC_TEXT_WIDGET==41019,
            "selected npc text"
        );
        require(
            MonsterSpawnerPresentation
                .SCROLL_ROOT==41020,
            "scroll root"
        );
        require(
            MonsterSpawnerPresentation.ROWS==22&&
            MonsterSpawnerPresentation
                .rowWidget(0)==41021&&
            MonsterSpawnerPresentation
                .rowWidget(21)==41042,
            "row range"
        );
        require(
            MonsterSpawnerPresentation
                .TOGGLE_WIDGET==0xa02f&&
            MonsterSpawnerPresentation
                .FIRST_ROW_WIDGET==0xa03d&&
            MonsterSpawnerPresentation
                .LAST_ROW_WIDGET==0xa052,
            "exact C2S185 bodies"
        );
        require(
            MonsterSpawnerPresentation
                .WIDGET_ACTION_OPCODE==185,
            "C2S185"
        );
    }

    private static void exactInputRouting(){
        MonsterSpawnerPresentation.Input toggle=
            MonsterSpawnerPresentation
                .resolveWidget(41007);

        require(
            toggle!=null&&
            toggle.kind==
                MonsterSpawnerPresentation
                    .InputKind.TOGGLE&&
            toggle.rowIndex==-1,
            "toggle input"
        );

        MonsterSpawnerPresentation.Input first=
            MonsterSpawnerPresentation
                .resolveWidget(41021);
        MonsterSpawnerPresentation.Input last=
            MonsterSpawnerPresentation
                .resolveWidget(41042);

        require(
            first!=null&&
            first.kind==
                MonsterSpawnerPresentation
                    .InputKind.SELECT_ROW&&
            first.rowIndex==0,
            "first row"
        );
        require(
            last!=null&&last.rowIndex==21,
            "last row"
        );

        require(
            MonsterSpawnerPresentation
                .resolveWidget(41020)==null&&
            MonsterSpawnerPresentation
                .resolveWidget(41043)==null,
            "non-action neighbors"
        );
    }

    private static void exactPacketCompatibility()
        throws Exception{
        byte[] root=
            BootstrapPackets.interface97(
                MonsterSpawnerPresentation.ROOT
            );

        require(
            root.length==2&&
            readU16(root)==41000,
            "S2C97 root"
        );

        assertTextTarget(
            MonsterSpawnerPresentation
                .SELECTED_NPC_TEXT_WIDGET,
            "You have selected: @yel@NPC"
        );
        assertTextTarget(
            MonsterSpawnerPresentation
                .rowWidget(0),
            "NPC IDX @yel@0 (41021)"
        );
        assertTextTarget(
            MonsterSpawnerPresentation
                .rowWidget(21),
            "NPC IDX @yel@21 (41042)"
        );
    }

    private static void unattachedControlsStayUnowned(){
        require(
            MonsterSpawnerPresentation
                .UNATTACHED_DISTANCED_WIDGET==41017&&
            MonsterSpawnerPresentation
                .UNATTACHED_X3_WIDGET==41018,
            "unattached identities"
        );
        require(
            MonsterSpawnerPresentation
                .resolveWidget(41017)==null&&
            MonsterSpawnerPresentation
                .resolveWidget(41018)==null,
            "unattached controls must not route"
        );
    }

    private static void policyStillUnowned(){
        for(Method method:
                MonsterSpawnerPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("catalog")||
               name.contains("npcid")||
               name.contains("spawncount")||
               name.contains("distance")||
               name.contains("limit")||
               name.contains("owner")||
               name.contains("persist"))
                throw new AssertionError(
                    "unowned Monster Spawner policy method "+
                    method.getName()
                );
        }
    }

    private static void assertTextTarget(
        int widgetId,
        String text
    )throws Exception{
        byte[] body=
            BootstrapPackets.widgetText126(
                widgetId,
                text
            );
        int n=body.length;
        int target=
            ((body[n-2]&255)<<8)|
            (((body[n-1]&255)-128)&255);

        require(
            target==widgetId,
            "S2C126 target widget="+widgetId
        );
    }

    private static int readU16(byte[] body){
        return ((body[0]&255)<<8)|
            (body[1]&255);
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private MonsterSpawnerPresentationTest(){}
}

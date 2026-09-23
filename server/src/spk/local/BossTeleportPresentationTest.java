package spk.local;

import java.util.Arrays;
import java.util.Collections;

public final class BossTeleportPresentationTest {
    public static void main(String[] args){
        exactInputRouting();
        semanticRowResolution();
        dropPreviewShape();
        exactConstants();

        System.out.println(
            "BOSS_TELEPORT_PRESENTATION_PASS "+
            "root18616=true "+
            "rows=13 "+
            "teleport60448=true "+
            "viewAll39873=true "+
            "dropPreview12=true "+
            "rowBossIdentitySemantic=true "+
            "coordinatesOwned=false "+
            "requirementsOwned=false "+
            "dropMechanicsOwned=false"
        );
    }

    private static void exactInputRouting(){
        BossTeleportPresentation.Input first=
            BossTeleportPresentation.resolveWidget(
                60412
            );
        BossTeleportPresentation.Input last=
            BossTeleportPresentation.resolveWidget(
                60424
            );

        require(
            first!=null&&
            first.kind==
                BossTeleportPresentation
                    .InputKind
                    .SELECT_ROW&&
            first.rowIndex==0,
            "first row"
        );
        require(
            last!=null&&
            last.rowIndex==12,
            "last row"
        );

        require(
            BossTeleportPresentation
                .resolveWidget(60448)
                .kind==
                BossTeleportPresentation
                    .InputKind
                    .TELEPORT,
            "teleport action"
        );
        require(
            BossTeleportPresentation
                .resolveWidget(39873)
                .kind==
                BossTeleportPresentation
                    .InputKind
                    .VIEW_FULL_DROP_TABLE,
            "view full drop table action"
        );

        require(
            BossTeleportPresentation
                .resolveWidget(60449)==null&&
            BossTeleportPresentation
                .resolveWidget(39874)==null,
            "hover widgets are not actions"
        );
    }

    private static void semanticRowResolution(){
        BossTeleportService service=
            new BossTeleportService(
                (player,entry)->
                    BossTeleportService
                        .EligibilityDecision
                        .allow(),
                (player,target,authority)->
                    BossTeleportService
                        .ExecutionResult
                        .success()
            );

        service.replaceCatalog(
            Arrays.asList(
                new BossTeleportService.Entry(
                    "boss:one",
                    "Boss One",
                    "Description One",
                    "teleport:one",
                    "drops:one",
                    Collections.emptyList(),
                    "LOCAL_LAB_POLICY_BOSS_TELEPORT"
                ),
                new BossTeleportService.Entry(
                    "boss:two",
                    "Boss Two",
                    "Description Two",
                    "teleport:two",
                    null,
                    Collections.emptyList(),
                    "LOCAL_LAB_POLICY_BOSS_TELEPORT"
                )
            )
        );

        BossTeleportService.Snapshot snapshot=
            service.snapshot();

        require(
            "boss:one".equals(
                BossTeleportPresentation
                    .rowBossKey(snapshot,0)
            )&&
            "boss:two".equals(
                BossTeleportPresentation
                    .rowBossKey(snapshot,1)
            ),
            "row order -> semantic boss keys"
        );

        expect(
            IllegalStateException.class,
            ()->BossTeleportPresentation
                .rowBossKey(snapshot,2),
            "unpopulated row"
        );

        BossTeleportService.PlayerSnapshot p1=
            service.selectBoss(
                "player:a",
                "boss:one"
            );

        require(
            "drops:one".equals(
                BossTeleportPresentation
                    .selectedFullDropTableKey(p1)
            ),
            "semantic full drop table key"
        );

        BossTeleportService.PlayerSnapshot p2=
            service.selectBoss(
                "player:b",
                "boss:two"
            );

        expect(
            IllegalStateException.class,
            ()->BossTeleportPresentation
                .selectedFullDropTableKey(p2),
            "missing full drop table"
        );
    }

    private static void dropPreviewShape(){
        int[] ids=new int[12];
        int[] qty=new int[12];
        Arrays.fill(ids,-1);

        try{
            byte[] body=
                BootstrapPackets.itemContainer53(
                    BossTeleportPresentation
                        .DROP_PREVIEW_WIDGET,
                    ids,
                    qty
                );

            require(
                body.length>=4&&
                (body[0]&255)==0xec&&
                (body[1]&255)==0x1f&&
                (body[2]&255)==0x00&&
                (body[3]&255)==0x0c,
                "drop preview packet53 header"
            );
        }catch(Exception failure){
            throw new AssertionError(
                "drop preview body",
                failure
            );
        }

        expect(
            IllegalArgumentException.class,
            ()->{
                try{
                    BossTeleportPresentation
                        .publishDropPreview(
                            null,
                            new int[11],
                            new int[11]
                        );
                }catch(java.io.IOException impossible){
                    throw new AssertionError(
                        impossible
                    );
                }
            },
            "drop preview exact slot count"
        );
    }

    private static void exactConstants(){
        require(
            BossTeleportPresentation.ROOT==18616,
            "root"
        );
        require(
            BossTeleportPresentation.MAX_ROWS==
                BossTeleportService.MAX_ROWS,
            "row capacity parity"
        );
        require(
            BossTeleportPresentation
                .DROP_PREVIEW_WIDGET==60447&&
            BossTeleportPresentation
                .DROP_PREVIEW_SLOTS==12,
            "drop preview"
        );
        require(
            BossTeleportPresentation
                .WIDGET_ACTION_OPCODE==185,
            "widget opcode"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                BossTeleportPresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );

        byte[] root=
            BootstrapPackets.interface97(
                BossTeleportPresentation.ROOT
            );
        require(
            root.length==2&&
            (root[0]&255)==0x48&&
            (root[1]&255)==0xb8,
            "root S2C97 body"
        );
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;
            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }
        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private BossTeleportPresentationTest(){}
}

package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class PkRatingsServiceTest {
    private static final AtomicTransactionService.SourceAuthority
        POLICY=
            AtomicTransactionService
                .SourceAuthority
                .CUSTOM_LOCALLAB;

    public static void main(String[] args){
        ArrayList<String> selections=
            new ArrayList<>();
        ArrayList<String> navigation=
            new ArrayList<>();

        PkRatingsService service=
            new PkRatingsService(
                (player,row,authority)->{
                    selections.add(
                        player+"|"+
                        row.rowKey+"|"+
                        authority
                    );

                    if("player:denied".equals(
                            player))
                        return PkRatingsService
                            .ActionResult.failure(
                                "caller-owned row action denied"
                            );

                    return PkRatingsService
                        .ActionResult.success(
                            "caller-owned row action"
                        );
                },
                (player,target,authority)->{
                    navigation.add(
                        player+"|"+
                        target+"|"+
                        authority
                    );

                    return PkRatingsService
                        .ActionResult.success();
                },
                POLICY
            );

        authorityFence();

        require(
            service.size()==0&&
            service.snapshot()
                .datasetRevision==0L,
            "initial PK Ratings state"
        );

        PkRatingsService.Snapshot first=
            service.replaceRows(
                Arrays.asList(
                    row(
                        "entry:alice",
                        "Alice - caller text",
                        true
                    ),
                    row(
                        "entry:separator",
                        "-----",
                        false
                    ),
                    row(
                        "entry:bob",
                        "Bob - caller text",
                        true
                    )
                )
            );

        require(
            first.rows.size()==3&&
            first.datasetRevision==1L&&
            first.row(
                "ENTRY:ALICE"
            )!=null&&
            first.row(
                "entry:separator"
            ).selectable==false&&
            first.policyAuthority==POLICY&&
            PkRatingsService
                .PRESENTATION_AUTHORITY
                .equals(
                    first
                        .presentationAuthority
                ),
            "PK Ratings initial replacement"
        );

        expect(
            UnsupportedOperationException.class,
            ()->first.rows.clear(),
            "PK Ratings rows mutable"
        );

        PkRatingsService.Snapshot updated=
            service.updateDisplayText(
                "entry:alice",
                "Alice - refreshed caller text"
            );

        require(
            updated.datasetRevision==2L&&
            "Alice - refreshed caller text"
                .equals(
                    updated.row(
                        "entry:alice"
                    ).displayText
                ),
            "PK Ratings semantic row update"
        );

        PkRatingsService.Snapshot retry=
            service.updateDisplayText(
                "entry:alice",
                "Alice - refreshed caller text"
            );

        require(
            retry.datasetRevision==2L,
            "same row text update idempotent"
        );

        expect(
            IllegalStateException.class,
            ()->service.selectRow(
                "player:alice",
                "entry:separator"
            ),
            "nonselectable PK Ratings row selected"
        );

        require(
            selections.isEmpty(),
            "nonselectable row reached executor"
        );

        PkRatingsService.ActionResult selected=
            service.selectRow(
                " Player:Alice ",
                "ENTRY:BOB"
            );

        require(
            selected.succeeded&&
            "caller-owned row action"
                .equals(selected.detail)&&
            selections.equals(
                Collections.singletonList(
                    "player:alice|entry:bob|"+
                        POLICY
                )
            ),
            "selectable PK Ratings row delegation"
        );

        PkRatingsService.ActionResult denied=
            service.selectRow(
                "player:denied",
                "entry:alice"
            );

        require(
            !denied.succeeded&&
            "caller-owned row action denied"
                .equals(denied.detail)&&
            service.snapshot()
                .datasetRevision==2L,
            "row action denial does not mutate feed"
        );

        service.requestNavigation(
            "PLAYER:ALICE",
            PkRatingsService
                .Navigation.DAILY_PK
        );
        service.requestNavigation(
            "player:alice",
            PkRatingsService
                .Navigation.TOURNAMENT_PK
        );

        require(
            navigation.equals(
                Arrays.asList(
                    "player:alice|DAILY_PK|"+
                        POLICY,
                    "player:alice|TOURNAMENT_PK|"+
                        POLICY
                )
            ),
            "PK leaderboard navigation delegation"
        );

        failureAtomicity(service);
        protocolBoundary();
        mechanicsBoundary();

        System.out.println(
            "PK_RATINGS_SERVICE_PASS "+
            "maxRows=50 "+
            "orderedSemanticRows=true "+
            "completeReplacement=true "+
            "semanticTextUpdate=true "+
            "selectableRowsDelegated=true "+
            "nonselectableRowsRejected=true "+
            "dailyPkNavigation=true "+
            "tournamentPkNavigation=true "+
            "ratingFormulaOwned=false "+
            "scoreFieldsOwned=false "+
            "sortPolicyOwned=false "+
            "resetPolicyOwned=false "+
            "rewardsOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void authorityFence(){
        PkRatingsService.SelectionExecutor
            selection=
                (player,row,authority)->
                    PkRatingsService
                        .ActionResult.success();

        PkRatingsService.NavigationExecutor
            navigation=
                (player,target,authority)->
                    PkRatingsService
                        .ActionResult.success();

        expect(
            IllegalArgumentException.class,
            ()->new PkRatingsService(
                selection,
                navigation,
                AtomicTransactionService
                    .SourceAuthority
                    .EXACT_CURRENT_CLIENT
            ),
            "exact client used as PK Ratings policy"
        );

        expect(
            IllegalArgumentException.class,
            ()->new PkRatingsService(
                selection,
                navigation,
                AtomicTransactionService
                    .SourceAuthority
                    .UNKNOWN_SERVER_AUTHORITY
            ),
            "unknown authority used as PK Ratings policy"
        );
    }

    private static void failureAtomicity(
        PkRatingsService service
    ){
        PkRatingsService.Snapshot before=
            service.snapshot();

        ArrayList<PkRatingsService.Row> tooMany=
            new ArrayList<>();

        for(int i=0;i<51;i++)
            tooMany.add(
                row(
                    "overflow:"+i,
                    "row "+i,
                    false
                )
            );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceRows(
                tooMany
            ),
            "PK Ratings over-50 replacement"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceRows(
                Arrays.asList(
                    row(
                        "duplicate",
                        "one",
                        true
                    ),
                    row(
                        "DUPLICATE",
                        "two",
                        true
                    )
                )
            ),
            "PK Ratings duplicate row key"
        );

        PkRatingsService.Row wrongAuthority=
            new PkRatingsService.Row(
                "wrong:authority",
                "wrong",
                true,
                AtomicTransactionService
                    .SourceAuthority
                    .EXACT_CURRENT_CLIENT
            );

        expect(
            IllegalArgumentException.class,
            ()->service.replaceRows(
                Collections.singletonList(
                    wrongAuthority
                )
            ),
            "PK Ratings wrong row authority"
        );

        PkRatingsService.Snapshot after=
            service.snapshot();

        require(
            after.datasetRevision==
                before.datasetRevision&&
            after.rows.size()==
                before.rows.size()&&
            after.row(
                "entry:alice"
            ).displayText.equals(
                before.row(
                    "entry:alice"
                ).displayText
            ),
            "failed PK Ratings replacement mutated state"
        );
    }

    private static PkRatingsService.Row row(
        String key,
        String text,
        boolean selectable
    ){
        return new PkRatingsService.Row(
            key,
            text,
            selectable,
            POLICY
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                PkRatingsService.class,
                PkRatingsService.Row.class,
                PkRatingsService.Snapshot.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("widget")||
                   name.contains("opcode")||
                   name.contains("packet")||
                   name.contains("subtype")||
                   name.contains("root")||
                   name.contains("fontindex")||
                   name.contains("rowindex"))
                    throw new AssertionError(
                        "protocol identity leaked into PK Ratings "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void mechanicsBoundary(){
        for(Field field:
                PkRatingsService.Row.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("score")||
               name.contains("elo")||
               name.contains("mmr")||
               name.contains("kills")||
               name.contains("reward")||
               name.contains("rank"))
                throw new AssertionError(
                    "unrecovered rating mechanic leaked into row "+
                    field.getName()
                );
        }

        for(Method method:
                PkRatingsService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("calculate")||
               name.contains("resetdaily")||
               name.contains("reward")||
               name.contains("persist"))
                throw new AssertionError(
                    "unrecovered rating mechanic leaked into method "+
                    method.getName()
                );
        }
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
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private PkRatingsServiceTest(){}
}

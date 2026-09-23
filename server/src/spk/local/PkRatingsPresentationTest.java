package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;

public final class PkRatingsPresentationTest {
    public static void main(String[] args){
        exactWireBodies();
        exactInputRouting();
        semanticRowResolution();
        authorityBoundary();

        System.out.println(
            "PK_RATINGS_PRESENTATION_PASS "+
            "subtype16=true "+
            "clear=true "+
            "append=true "+
            "update=true "+
            "rows=50 "+
            "tab32017=true "+
            "daily61002=true "+
            "tournament61005=true "+
            "rowSelection=true "+
            "rowMeaningOwned=false "+
            "ratingFormulaOwned=false"
        );
    }

    private static void exactWireBodies(){
        requireBytes(
            ApplicationPacket250Writer.encode(
                PkRatingsPresentation.APPLICATION_SUBTYPE,
                PkRatingsPresentation.clearBody()
            ),
            new int[]{0x00,0x10,0x00},
            "clear"
        );

        requireBytes(
            ApplicationPacket250Writer.encode(
                PkRatingsPresentation.APPLICATION_SUBTYPE,
                PkRatingsPresentation.appendBody(
                    2,
                    true,
                    "Alice"
                )
            ),
            new int[]{
                0x00,0x10,
                0x01,0x02,0x01,
                'A','l','i','c','e',0x0a
            },
            "append"
        );

        requireBytes(
            ApplicationPacket250Writer.encode(
                PkRatingsPresentation.APPLICATION_SUBTYPE,
                PkRatingsPresentation.updateBody(
                    49,
                    "Z"
                )
            ),
            new int[]{
                0x00,0x10,
                0x02,0x00,0x31,
                'Z',0x0a
            },
            "update"
        );

        require(
            PkRatingsPresentation.rowWidget(0)==40405&&
            PkRatingsPresentation.rowWidget(49)==40454,
            "row widget bounds"
        );

        expect(
            IllegalArgumentException.class,
            ()->PkRatingsPresentation.appendBody(
                256,
                true,
                "x"
            ),
            "font index"
        );
        expect(
            IllegalArgumentException.class,
            ()->PkRatingsPresentation.updateBody(
                50,
                "x"
            ),
            "row index"
        );
        expect(
            IllegalArgumentException.class,
            ()->PkRatingsPresentation.appendBody(
                0,
                true,
                "a\nb"
            ),
            "embedded newline"
        );
        expect(
            IllegalArgumentException.class,
            ()->PkRatingsPresentation.appendBody(
                0,
                true,
                "\u0100"
            ),
            "non ISO-8859-1"
        );
        expect(
            IllegalArgumentException.class,
            ()->PkRatingsPresentation.appendBody(
                0,
                true,
                repeat('x',250)
            ),
            "VAR_BYTE overflow"
        );
    }

    private static void exactInputRouting(){
        PkRatingsPresentation.Input tab=
            PkRatingsPresentation.resolveWidget(
                32017
            );
        require(
            tab!=null&&
            tab.kind==
                PkRatingsPresentation
                    .InputKind
                    .OPEN_RATINGS_TAB&&
            tab.rowIndex==-1&&
            tab.navigation==null,
            "PK Ratings tab"
        );

        PkRatingsPresentation.Input daily=
            PkRatingsPresentation.resolveWidget(
                61002
            );
        require(
            daily!=null&&
            daily.kind==
                PkRatingsPresentation
                    .InputKind
                    .NAVIGATE&&
            daily.navigation==
                PkRatingsService
                    .Navigation
                    .DAILY_PK,
            "Daily PK navigation"
        );

        PkRatingsPresentation.Input tournament=
            PkRatingsPresentation.resolveWidget(
                61005
            );
        require(
            tournament!=null&&
            tournament.navigation==
                PkRatingsService
                    .Navigation
                    .TOURNAMENT_PK,
            "Tournament PK navigation"
        );

        PkRatingsPresentation.Input first=
            PkRatingsPresentation.resolveWidget(
                40405
            );
        PkRatingsPresentation.Input last=
            PkRatingsPresentation.resolveWidget(
                40454
            );

        require(
            first!=null&&
            first.kind==
                PkRatingsPresentation
                    .InputKind
                    .SELECT_ROW&&
            first.rowIndex==0&&
            last!=null&&
            last.rowIndex==49,
            "row action range"
        );

        require(
            PkRatingsPresentation.resolveWidget(
                40455
            )==null,
            "outside row range"
        );
    }

    private static void semanticRowResolution(){
        PkRatingsService service=
            new PkRatingsService(
                (player,row,authority)->
                    PkRatingsService
                        .ActionResult
                        .success(),
                (player,navigation,authority)->
                    PkRatingsService
                        .ActionResult
                        .success(),
                AtomicTransactionService
                    .SourceAuthority
                    .CUSTOM_LOCALLAB
            );

        PkRatingsService.Snapshot snapshot=
            service.replaceRows(
                Arrays.asList(
                    new PkRatingsService.Row(
                        "player:alice",
                        "Alice - 2000",
                        true,
                        AtomicTransactionService
                            .SourceAuthority
                            .CUSTOM_LOCALLAB
                    ),
                    new PkRatingsService.Row(
                        "separator",
                        "-----",
                        false,
                        AtomicTransactionService
                            .SourceAuthority
                            .CUSTOM_LOCALLAB
                    )
                )
            );

        require(
            "player:alice".equals(
                PkRatingsPresentation
                    .selectableRowKey(
                        snapshot,
                        0
                    )
            ),
            "semantic selectable row key"
        );

        expect(
            IllegalStateException.class,
            ()->PkRatingsPresentation
                .selectableRowKey(
                    snapshot,
                    1
                ),
            "nonselectable row"
        );

        expect(
            IllegalStateException.class,
            ()->PkRatingsPresentation
                .selectableRowKey(
                    snapshot,
                    2
                ),
            "unpopulated row"
        );
    }

    private static void authorityBoundary(){
        require(
            PkRatingsPresentation.MAX_ROWS==
                PkRatingsService.MAX_ROWS,
            "presentation/domain capacity parity"
        );

        for(Field field:
                PkRatingsPresentation.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("ratingformula")||
               name.contains("reward")||
               name.contains("timezone")||
               name.contains("season")||
               name.contains("antifarm")||
               name.contains("minimumactivity"))
                throw new AssertionError(
                    "unowned PK Ratings policy field "+
                    field.getName()
                );
        }

        for(Method method:
                PkRatingsPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("calculate")||
               name.contains("rankplayers")||
               name.contains("reward")||
               name.contains("resetdaily"))
                throw new AssertionError(
                    "unowned PK Ratings policy method "+
                    method.getName()
                );
        }
    }

    private static String repeat(
        char value,
        int count
    ){
        char[] chars=new char[count];
        Arrays.fill(chars,value);
        return new String(chars);
    }

    private static void requireBytes(
        byte[] actual,
        int[] expected,
        String label
    ){
        require(
            actual.length==expected.length,
            label+" length actual="+
                actual.length+
                " expected="+
                expected.length
        );

        for(int i=0;i<expected.length;i++)
            require(
                (actual[i]&255)==expected[i],
                label+
                " byte["+
                i+
                "] actual="+
                (actual[i]&255)+
                " expected="+
                expected[i]
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

    private PkRatingsPresentationTest(){}
}

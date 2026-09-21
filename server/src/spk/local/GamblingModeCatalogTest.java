package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class GamblingModeCatalogTest {
    public static void main(String[] args){
        assertExactModes();
        assertExactWidgetRange();
        assertReadOnlyBoundary();

        System.out.println(
            "GAMBLING_MODE_CATALOG_PASS "+
            "entries=6 "+
            "widgets=59853..59858 "+
            "exactNames=true "+
            "authority=EXACT_CURRENT_CLIENT "+
            "mechanicsInvented=false"
        );
    }

    private static void assertExactModes(){
        assertMode(
            0,
            59853,
            "55x2 (P1 host)"
        );

        assertMode(
            1,
            59854,
            "55x2 (P2 host)"
        );

        assertMode(
            2,
            59855,
            "BJ (P1 host)"
        );

        assertMode(
            3,
            59856,
            "BJ (P2 host)"
        );

        assertMode(
            4,
            59857,
            "Dice duel"
        );

        assertMode(
            5,
            59858,
            "Flower poker"
        );

        if(GamblingModeCatalog.size()!=6)
            throw new AssertionError(
                "size="+
                GamblingModeCatalog.size()
            );

        if(GamblingModeCatalog
                .byClientIndex(-1)!=null||
           GamblingModeCatalog
                .byClientIndex(6)!=null)
            throw new AssertionError(
                "out-of-range client index resolved"
            );

        boolean unknownRejected=false;

        try{
            GamblingModeCatalog
                .requireByClientIndex(6);
        }catch(IllegalArgumentException expected){
            unknownRejected=true;
        }

        if(!unknownRejected)
            throw new AssertionError(
                "unknown client index not rejected"
            );
    }

    private static void assertExactWidgetRange(){
        List<GamblingModeCatalog.Definition>
            all=
                GamblingModeCatalog.all();

        for(int i=0;
            i<all.size();
            i++){
            GamblingModeCatalog.Definition definition=
                all.get(i);

            int expectedWidget=
                GamblingModeCatalog
                    .FIRST_WIDGET_ID+i;

            if(definition.clientIndex!=i||
               definition.sourceWidgetId!=
                    expectedWidget)
                throw new AssertionError(
                    "range mismatch "+
                    definition
                );

            if(GamblingModeCatalog
                    .bySourceWidgetId(
                        expectedWidget)!=
                    definition)
                throw new AssertionError(
                    "widget lookup mismatch "+
                    expectedWidget
                );
        }

        if(GamblingModeCatalog
                .bySourceWidgetId(59852)!=null||
           GamblingModeCatalog
                .bySourceWidgetId(59859)!=null)
            throw new AssertionError(
                "out-of-range widget resolved"
            );
    }

    private static void assertReadOnlyBoundary(){
        List<GamblingModeCatalog.Definition>
            all=
                GamblingModeCatalog.all();

        boolean immutable=false;

        try{
            all.clear();
        }catch(UnsupportedOperationException expected){
            immutable=true;
        }

        if(!immutable)
            throw new AssertionError(
                "catalog mutable"
            );

        Set<String> forbidden=
            new HashSet<>(
                Arrays.asList(
                    "bet",
                    "wager",
                    "currency",
                    "winner",
                    "payout",
                    "refund",
                    "rng",
                    "odds",
                    "escrow",
                    "balance"
                )
            );

        for(GamblingModeCatalog.Definition definition:
                all)
            if(!GamblingModeCatalog
                    .AUTHORITY
                    .equals(
                        definition.authority))
                throw new AssertionError(
                    "authority="+
                    definition.authority
                );

        for(Field field:
                GamblingModeCatalog
                    .Definition.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(forbidden.contains(name))
                throw new AssertionError(
                    "invented gambling mechanics field "+
                    field.getName()
                );
        }
    }

    private static void assertMode(
        int clientIndex,
        int widgetId,
        String name
    ){
        GamblingModeCatalog.Definition definition=
            GamblingModeCatalog
                .requireByClientIndex(
                    clientIndex
                );

        if(definition.sourceWidgetId!=
                widgetId||
           !name.equals(
               definition.name))
            throw new AssertionError(
                "mode "+clientIndex+
                " = "+
                definition
            );
    }
}

package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class BloodFountainSelectablePerkCatalogTest {
    public static void main(String[] args){
        assertExactCatalog();
        assertExactWidgetJoin();
        assertReadOnlyAuthorityBoundary();

        System.out.println(
            "BLOOD_FOUNTAIN_SELECTABLE_PERK_CATALOG_PASS "+
            "entries=44 "+
            "perkIds=0..43 "+
            "widgets=45602..45645 "+
            "widgetMinusBaseEqualsPerkId=true "+
            "authority=EXACT_CURRENT_CLIENT "+
            "mechanicsInvented=false"
        );
    }

    private static void assertExactCatalog(){
        if(BloodFountainSelectablePerkCatalog
                .size()!=44)
            throw new AssertionError(
                "size="+
                BloodFountainSelectablePerkCatalog
                    .size()
            );

        assertPerk(
            0,
            45602,
            "Blood vengeance I"
        );

        assertPerk(
            4,
            45606,
            "Blood whip"
        );

        assertPerk(
            17,
            45619,
            "Augury"
        );

        assertPerk(
            18,
            45620,
            "Rigour"
        );

        assertPerk(
            27,
            45629,
            "Vampiric accuracy"
        );

        assertPerk(
            34,
            45636,
            "Death's accomplice"
        );

        assertPerk(
            39,
            45641,
            "Blood alchemy III"
        );

        assertPerk(
            43,
            45645,
            "Escape Artist"
        );

        if(BloodFountainSelectablePerkCatalog
                .byId(-1)!=null||
           BloodFountainSelectablePerkCatalog
                .byId(44)!=null)
            throw new AssertionError(
                "out-of-range perk id resolved"
            );

        boolean unknownRejected=false;

        try{
            BloodFountainSelectablePerkCatalog
                .requireById(44);
        }catch(IllegalArgumentException expected){
            unknownRejected=true;
        }

        if(!unknownRejected)
            throw new AssertionError(
                "unknown perk id not rejected"
            );
    }

    private static void assertExactWidgetJoin(){
        List<BloodFountainSelectablePerkCatalog.Definition>
            all=
                BloodFountainSelectablePerkCatalog
                    .all();

        for(int i=0;
            i<all.size();
            i++){
            BloodFountainSelectablePerkCatalog.Definition
                definition=
                    all.get(i);

            if(definition.perkId!=i)
                throw new AssertionError(
                    "id order mismatch "+
                    definition
                );

            int expectedWidget=
                BloodFountainSelectablePerkCatalog
                    .FIRST_WIDGET_ID+
                    definition.perkId;

            if(definition.sourceWidgetId!=
                    expectedWidget)
                throw new AssertionError(
                    "widget join mismatch "+
                    definition
                );

            if(BloodFountainSelectablePerkCatalog
                    .bySourceWidgetId(
                        expectedWidget)!=
                    definition)
                throw new AssertionError(
                    "widget lookup mismatch "+
                    expectedWidget
                );
        }

        if(BloodFountainSelectablePerkCatalog
                .bySourceWidgetId(45601)!=null||
           BloodFountainSelectablePerkCatalog
                .bySourceWidgetId(45646)!=null)
            throw new AssertionError(
                "out-of-range widget resolved"
            );
    }

    private static void assertReadOnlyAuthorityBoundary(){
        List<BloodFountainSelectablePerkCatalog.Definition>
            all=
                BloodFountainSelectablePerkCatalog
                    .all();

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

        for(BloodFountainSelectablePerkCatalog.Definition
                definition:all){
            if(!BloodFountainSelectablePerkCatalog
                    .AUTHORITY
                    .equals(
                        definition.authority))
                throw new AssertionError(
                    "authority="+
                    definition.authority
                );
        }

        Set<String> forbidden=
            new HashSet<>(
                Arrays.asList(
                    "cost",
                    "price",
                    "effect",
                    "requirement",
                    "prerequisite",
                    "unlocked",
                    "owned",
                    "active",
                    "damage",
                    "reward"
                )
            );

        for(Field field:
                BloodFountainSelectablePerkCatalog
                    .Definition.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(forbidden.contains(name))
                throw new AssertionError(
                    "invented mechanics field "+
                    field.getName()
                );
        }
    }

    private static void assertPerk(
        int perkId,
        int widgetId,
        String name
    ){
        BloodFountainSelectablePerkCatalog.Definition
            definition=
                BloodFountainSelectablePerkCatalog
                    .requireById(
                        perkId
                    );

        if(definition.sourceWidgetId!=
                widgetId||
           !name.equals(
               definition.name))
            throw new AssertionError(
                "perk "+perkId+
                " = "+
                definition
            );
    }
}

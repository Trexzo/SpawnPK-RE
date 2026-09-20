package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class ItemMechanicsEvidenceQueryServiceTest {
    public static void main(String[] args){
        ItemMechanicsEvidenceQueryService service=
            new ItemMechanicsEvidenceQueryService();

        assertCorpus(service);
        assertKnownEvidence(service);
        assertSearchAndFilters(service);
        assertPaginationAndImmutability(service);
        assertAuthorityBoundary();

        System.out.println(
            "ITEM_MECHANICS_EVIDENCE_QUERY_PASS "+
            "rows=99 "+
            "uniqueItemIds=99 "+
            "source=CURRENT_I_BIN "+
            "authority=EXACT_CURRENT_CLIENT "+
            "exactLookup=true "+
            "nameSearch=true "+
            "categoryFilter=true "+
            "deterministicById=true "+
            "pagination=true "+
            "immutable=true "+
            "numericMentionsUninterpreted=true "+
            "fullBonusTableClaimed=false "+
            "target24PayloadClaimed=false"
        );
    }

    private static void assertCorpus(
        ItemMechanicsEvidenceQueryService service
    ){
        if(service.count()!=99)
            throw new AssertionError(
                "count="+
                service.count()
            );

        if(service.byItemId(
                Integer.MAX_VALUE)!=null)
            throw new AssertionError(
                "unknown item has evidence"
            );
    }

    private static void assertKnownEvidence(
        ItemMechanicsEvidenceQueryService service
    ){
        ItemMechanicsEvidenceQueryService.Evidence shadowrend=
            service.byItemId(
                27485
            );

        if(shadowrend==null||
           !"Scythe of shadowrend".equals(
               shadowrend.itemName)||
           !shadowrend.wield||
           shadowrend.wear||
           !"CURRENT_I_BIN".equals(
               shadowrend.sourceDataset)||
           !"hover".equals(
               shadowrend.sourceField)||
           !"EXACT_CURRENT_CLIENT".equals(
               shadowrend.sourceAuthority))
            throw new AssertionError(
                "shadowrend="+
                shadowrend
            );

        if(!shadowrend.hasCategory(
                "CORE_COMBAT_BONUS_MENTION")||
           !shadowrend.hasCategory(
                "ATTACK_SPEED_OR_TICK_TIMING")||
           !shadowrend.coreTerms.contains(
                "STRENGTH_BONUS"))
            throw new AssertionError(
                "shadowrend classification="+
                shadowrend.categories+
                " terms="+
                shadowrend.coreTerms
            );

        if(!shadowrend.numericMentions.equals(
                Arrays.asList(
                    "90",
                    "4",
                    "1",
                    "3",
                    "2.5x",
                    "1-25"
                )))
            throw new AssertionError(
                "numeric mentions="+
                shadowrend.numericMentions
            );

        if(!shadowrend.text.contains(
                "90 str bonus"))
            throw new AssertionError(
                "raw hover text changed"
            );
    }

    private static void assertSearchAndFilters(
        ItemMechanicsEvidenceQueryService service
    ){
        ItemMechanicsEvidenceQueryService.Page noxious=
            service.search(
                "NoXiOuS",
                null,
                0,
                100
            );

        if(noxious.items.isEmpty())
            throw new AssertionError(
                "noxious search empty"
            );

        int previous=-1;

        for(ItemMechanicsEvidenceQueryService.Evidence evidence:
                noxious.items){
            if(evidence.itemId<=previous)
                throw new AssertionError(
                    "non-deterministic order "+
                    previous+
                    " -> "+
                    evidence.itemId
                );

            previous=evidence.itemId;

            if(!evidence.itemName
                    .toLowerCase(
                        Locale.ROOT
                    )
                    .contains(
                        "noxious"
                    ))
                throw new AssertionError(
                    "name filter leak "+
                    evidence
                );
        }

        ItemMechanicsEvidenceQueryService.Page specials=
            service.search(
                "",
                "special_attack",
                0,
                1000
            );

        if(specials.items.isEmpty())
            throw new AssertionError(
                "special-attack category empty"
            );

        for(ItemMechanicsEvidenceQueryService.Evidence evidence:
                specials.items)
            if(!evidence.hasCategory(
                    "SPECIAL_ATTACK"))
                throw new AssertionError(
                    "category filter leak "+
                    evidence
                );
    }

    private static void assertPaginationAndImmutability(
        ItemMechanicsEvidenceQueryService service
    ){
        ItemMechanicsEvidenceQueryService.Page first=
            service.search(
                "",
                null,
                0,
                10
            );

        ItemMechanicsEvidenceQueryService.Page second=
            service.search(
                "",
                null,
                10,
                10
            );

        if(first.items.size()!=10||
           second.items.size()!=10||
           first.totalMatches!=99||
           second.totalMatches!=99||
           !first.hasMore())
            throw new AssertionError(
                "pagination mismatch"
            );

        if(first.items.get(9).itemId>=
           second.items.get(0).itemId)
            throw new AssertionError(
                "page overlap/order failure"
            );

        boolean pageImmutable=false;

        try{
            first.items.clear();
        }catch(UnsupportedOperationException expected){
            pageImmutable=true;
        }

        if(!pageImmutable)
            throw new AssertionError(
                "page items mutable"
            );

        ItemMechanicsEvidenceQueryService.Evidence sample=
            first.items.get(0);

        boolean categoriesImmutable=false;

        try{
            sample.categories.clear();
        }catch(UnsupportedOperationException expected){
            categoriesImmutable=true;
        }

        if(!categoriesImmutable)
            throw new AssertionError(
                "categories mutable"
            );

        boolean numericImmutable=false;

        try{
            sample.numericMentions.clear();
        }catch(UnsupportedOperationException expected){
            numericImmutable=true;
        }

        if(!numericImmutable)
            throw new AssertionError(
                "numeric mentions mutable"
            );

        expectIllegalArgument(
            ()->service.search(
                "",
                null,
                -1,
                1
            )
        );

        expectIllegalArgument(
            ()->service.search(
                "",
                null,
                0,
                0
            )
        );
    }

    private static void assertAuthorityBoundary(){
        for(Field field:
                ItemMechanicsEvidenceQueryService
                    .Evidence.class
                    .getDeclaredFields()){
            if(field.getType()==
                    ItemCatalog.Meta.class)
                throw new AssertionError(
                    "raw ItemCatalog.Meta leaked"
                );

            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("widget")||
               name.contains("packet")||
               name.contains("opcode")||
               name.contains("subtype")||
               name.contains("target24")||
               name.contains("attackbonusarray")||
               name.contains("defencebonusarray"))
                throw new AssertionError(
                    "unsupported identity/mechanics field "+
                    field.getName()
                );
        }
    }

    private static void expectIllegalArgument(
        Runnable action
    ){
        boolean failed=false;

        try{
            action.run();
        }catch(IllegalArgumentException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "expected IllegalArgumentException"
            );
    }
}

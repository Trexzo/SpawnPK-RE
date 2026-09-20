package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class ItemCatalogQueryServiceTest {
    public static void main(String[] args){
        ItemCatalogQueryService service=
            new ItemCatalogQueryService();

        assertExactLookup(service);
        assertDeterministicSearch(service);
        assertFilters(service);
        assertPagination(service);
        assertBoundary(service);

        System.out.println(
            "ITEM_CATALOG_QUERY_SERVICE_PASS "+
            "catalogCount="+
                ItemDefinitionRepository.count()+
            " exactLookup=true "+
            "nameSearch=true "+
            "deterministicById=true "+
            "pagination=true "+
            "tradeableFilter=true "+
            "equipFilter=true "+
            "immutableResults=true "+
            "rawMetaLeak=false "+
            "protocolIdentity=false"
        );
    }

    private static void assertExactLookup(
        ItemCatalogQueryService service
    ){
        ItemCatalogQueryService.ItemSummary coins=
            service.byId(995);

        if(coins==null||
           coins.itemId!=995||
           coins.name==null||
           coins.name.isEmpty()||
           !coins.stackable)
            throw new AssertionError(
                "coins="+coins
            );

        if(service.byId(
                Integer.MAX_VALUE)!=null)
            throw new AssertionError(
                "unknown id resolved"
            );
    }

    private static void assertDeterministicSearch(
        ItemCatalogQueryService service
    ){
        ItemCatalogQueryService.Page page=
            service.search(
                "abyssal",
                0,
                100,
                false,
                false
            );

        if(page.totalMatches<=0||
           page.items.isEmpty())
            throw new AssertionError(
                "abyssal search empty"
            );

        int previous=-1;
        boolean foundWhip=false;

        for(ItemCatalogQueryService.ItemSummary item:
                page.items){
            if(item.itemId<=previous)
                throw new AssertionError(
                    "non-deterministic id order "+
                    previous+
                    " -> "+
                    item.itemId
                );

            previous=item.itemId;

            if(item.itemId==4151)
                foundWhip=true;
        }

        if(!foundWhip)
            throw new AssertionError(
                "abyssal whip 4151 absent"
            );

        ItemCatalogQueryService.Page caseFold=
            service.search(
                "AbYsSaL",
                0,
                100,
                false,
                false
            );

        if(caseFold.totalMatches!=
                page.totalMatches)
            throw new AssertionError(
                "case folding mismatch"
            );
    }

    private static void assertFilters(
        ItemCatalogQueryService service
    ){
        ItemCatalogQueryService.Page tradeable=
            service.search(
                "",
                0,
                200,
                true,
                false
            );

        if(tradeable.items.isEmpty())
            throw new AssertionError(
                "tradeable query empty"
            );

        for(ItemCatalogQueryService.ItemSummary item:
                tradeable.items)
            if(!item.tradeable)
                throw new AssertionError(
                    "non-tradeable leaked "+
                    item
                );

        ItemCatalogQueryService.Page equip=
            service.search(
                "whip",
                0,
                100,
                false,
                true
            );

        if(equip.items.isEmpty())
            throw new AssertionError(
                "equip query empty"
            );

        for(ItemCatalogQueryService.ItemSummary item:
                equip.items)
            if(!item.equipCapable())
                throw new AssertionError(
                    "non-equip item leaked "+
                    item
                );
    }

    private static void assertPagination(
        ItemCatalogQueryService service
    ){
        ItemCatalogQueryService.Page first=
            service.search(
                "",
                0,
                25,
                false,
                false
            );

        ItemCatalogQueryService.Page second=
            service.search(
                "",
                25,
                25,
                false,
                false
            );

        if(first.items.size()!=25||
           second.items.size()!=25||
           first.totalMatches!=
                second.totalMatches||
           !first.hasMore())
            throw new AssertionError(
                "pagination sizes first="+
                first+
                " second="+
                second
            );

        if(first.items.get(24).itemId>=
           second.items.get(0).itemId)
            throw new AssertionError(
                "page ordering overlaps"
            );

        ItemCatalogQueryService.Page beyond=
            service.search(
                "",
                first.totalMatches+10,
                25,
                false,
                false
            );

        if(!beyond.items.isEmpty()||
           beyond.hasMore())
            throw new AssertionError(
                "beyond-end page="+
                beyond
            );
    }

    private static void assertBoundary(
        ItemCatalogQueryService service
    ){
        ItemCatalogQueryService.Page page=
            service.search(
                "",
                0,
                1,
                false,
                false
            );

        boolean immutable=false;

        try{
            page.items.clear();
        }catch(UnsupportedOperationException expected){
            immutable=true;
        }

        if(!immutable)
            throw new AssertionError(
                "result page mutable"
            );

        expectIllegalArgument(
            ()->service.search(
                "",
                -1,
                1,
                false,
                false
            )
        );

        expectIllegalArgument(
            ()->service.search(
                "",
                0,
                0,
                false,
                false
            )
        );

        for(Field field:
                ItemCatalogQueryService
                    .ItemSummary.class
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
               name.contains("cachefile"))
                throw new AssertionError(
                    "protocol/cache identity leaked: "+
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

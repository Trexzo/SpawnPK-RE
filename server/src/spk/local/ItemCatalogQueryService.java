package spk.local;

import java.util.*;

/**
 * Read-only semantic query boundary over the recovered item catalogue.
 *
 * Internal ItemCatalog.Meta rows never escape this service. Query consumers get
 * immutable summaries suitable for content/UI/domain lookups without direct
 * access to cache/config representation.
 */
final class ItemCatalogQueryService {
    static final class ItemSummary {
        final int itemId;
        final String name;
        final boolean tradeable;
        final boolean stackable;
        final String stackabilityEvidence;
        final String equipAction;

        ItemSummary(
            int itemId,
            String name,
            boolean tradeable,
            boolean stackable,
            String stackabilityEvidence,
            String equipAction
        ){
            this.itemId=itemId;
            this.name=name;
            this.tradeable=tradeable;
            this.stackable=stackable;
            this.stackabilityEvidence=
                stackabilityEvidence;
            this.equipAction=equipAction;
        }

        boolean equipCapable(){
            return !"none".equals(
                equipAction
            );
        }

        @Override public String toString(){
            return "ItemSummary{"+
                "itemId="+itemId+
                ",name="+name+
                ",tradeable="+tradeable+
                ",stackable="+stackable+
                ",equipAction="+equipAction+
                "}";
        }
    }

    static final class Page {
        final List<ItemSummary> items;
        final int totalMatches;
        final int offset;
        final int limit;

        Page(
            List<ItemSummary> items,
            int totalMatches,
            int offset,
            int limit
        ){
            this.items=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        items
                    )
                );
            this.totalMatches=
                totalMatches;
            this.offset=offset;
            this.limit=limit;
        }

        boolean hasMore(){
            return (long)offset+
                items.size()<
                totalMatches;
        }

        @Override public String toString(){
            return "ItemQueryPage{"+
                "items="+items.size()+
                ",totalMatches="+
                    totalMatches+
                ",offset="+offset+
                ",limit="+limit+
                ",hasMore="+hasMore()+
                "}";
        }
    }

    ItemSummary byId(
        int itemId
    ){
        ItemCatalog.Meta meta=
            ItemDefinitionRepository.get(
                itemId
            );

        return meta==null
            ?null
            :summary(meta);
    }

    Page search(
        String nameNeedle,
        int offset,
        int limit,
        boolean tradeableOnly,
        boolean equipCapableOnly
    ){
        if(offset<0)
            throw new IllegalArgumentException(
                "offset="+offset
            );

        if(limit<=0)
            throw new IllegalArgumentException(
                "limit="+limit
            );

        String needle=
            normalizeNeedle(
                nameNeedle
            );

        ArrayList<ItemCatalog.Meta> matches=
            new ArrayList<>();

        for(ItemCatalog.Meta meta:
                ItemCatalog.all()){
            if(!needle.isEmpty()&&
               !safeName(meta)
                    .toLowerCase(
                        Locale.ROOT
                    )
                    .contains(
                        needle
                    ))
                continue;

            if(tradeableOnly&&
               !meta.tradeable)
                continue;

            if(equipCapableOnly&&
               "none".equals(
                   ItemDefinitionRepository
                       .equipActionEvidence(
                           meta.id
                       )))
                continue;

            matches.add(meta);
        }

        matches.sort(
            Comparator.comparingInt(
                meta->
                    meta.id
            )
        );

        int total=
            matches.size();

        if(offset>=total)
            return new Page(
                Collections.emptyList(),
                total,
                offset,
                limit
            );

        int toIndex=
            (int)Math.min(
                (long)total,
                (long)offset+limit
            );

        ArrayList<ItemSummary> page=
            new ArrayList<>(
                toIndex-offset
            );

        for(int i=offset;
            i<toIndex;
            i++)
            page.add(
                summary(
                    matches.get(i)
                )
            );

        return new Page(
            page,
            total,
            offset,
            limit
        );
    }

    private static ItemSummary summary(
        ItemCatalog.Meta meta
    ){
        return new ItemSummary(
            meta.id,
            safeName(meta),
            meta.tradeable,
            ItemDefinitionRepository
                .isStackable(
                    meta.id
                ),
            ItemDefinitionRepository
                .stackabilityEvidence(
                    meta.id
                ),
            ItemDefinitionRepository
                .equipActionEvidence(
                    meta.id
                )
        );
    }

    private static String normalizeNeedle(
        String value
    ){
        return value==null
            ?""
            :value.trim()
                .toLowerCase(
                    Locale.ROOT
                );
    }

    private static String safeName(
        ItemCatalog.Meta meta
    ){
        return meta.name==null
            ?""
            :meta.name;
    }
}

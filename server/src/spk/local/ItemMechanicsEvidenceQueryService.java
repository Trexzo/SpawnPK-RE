package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Read-only semantic query boundary over recovered current-client item
 * mechanics/hover evidence.
 *
 * This is evidence text/classification, not a complete authoritative combat
 * bonus table and not an S2C126 target-24 payload model.
 */
final class ItemMechanicsEvidenceQueryService {
    static final String SOURCE_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final String SOURCE_DATASET=
        "CURRENT_I_BIN";

    static final String RESOURCE=
        "/spk/local/data/research_r82/equipment_current_equippable_stat_r82.tsv";

    static final class Evidence {
        final int itemId;
        final String itemName;
        final boolean wield;
        final boolean wear;
        final String sourceDataset;
        final String sourceField;
        final Set<String> categories;
        final Set<String> coreTerms;
        final List<String> numericMentions;
        final String text;
        final String sourceAuthority;

        Evidence(
            int itemId,
            String itemName,
            boolean wield,
            boolean wear,
            String sourceDataset,
            String sourceField,
            Set<String> categories,
            Set<String> coreTerms,
            List<String> numericMentions,
            String text
        ){
            this.itemId=itemId;
            this.itemName=itemName;
            this.wield=wield;
            this.wear=wear;
            this.sourceDataset=sourceDataset;
            this.sourceField=sourceField;
            this.categories=
                Collections.unmodifiableSet(
                    new LinkedHashSet<>(
                        categories
                    )
                );
            this.coreTerms=
                Collections.unmodifiableSet(
                    new LinkedHashSet<>(
                        coreTerms
                    )
                );
            this.numericMentions=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        numericMentions
                    )
                );
            this.text=text;
            this.sourceAuthority=
                SOURCE_AUTHORITY;
        }

        boolean hasCategory(
            String category
        ){
            if(category==null)
                return false;

            return categories.contains(
                category.trim()
                    .toUpperCase(
                        Locale.ROOT
                    )
            );
        }

        @Override public String toString(){
            return "ItemMechanicsEvidence{"+
                "itemId="+itemId+
                ",itemName="+itemName+
                ",categories="+categories+
                ",sourceDataset="+
                    sourceDataset+
                "}";
        }
    }

    static final class Page {
        final List<Evidence> items;
        final int totalMatches;
        final int offset;
        final int limit;

        Page(
            List<Evidence> items,
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
    }

    private final Map<Integer,Evidence> byItemId;
    private final List<Evidence> all;

    ItemMechanicsEvidenceQueryService(){
        LinkedHashMap<Integer,Evidence> loaded=
            load();

        this.byItemId=
            Collections.unmodifiableMap(
                loaded
            );

        ArrayList<Evidence> ordered=
            new ArrayList<>(
                loaded.values()
            );

        ordered.sort(
            Comparator.comparingInt(
                value->
                    value.itemId
            )
        );

        this.all=
            Collections.unmodifiableList(
                ordered
            );
    }

    int count(){
        return all.size();
    }

    Evidence byItemId(
        int itemId
    ){
        return byItemId.get(
            itemId
        );
    }

    Page search(
        String nameNeedle,
        String category,
        int offset,
        int limit
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
            nameNeedle==null
                ?""
                :nameNeedle.trim()
                    .toLowerCase(
                        Locale.ROOT
                    );

        String normalizedCategory=
            category==null||
            category.trim().isEmpty()
                ?null
                :category.trim()
                    .toUpperCase(
                        Locale.ROOT
                    );

        ArrayList<Evidence> matches=
            new ArrayList<>();

        for(Evidence evidence:
                all){
            if(!needle.isEmpty()&&
               !evidence.itemName
                    .toLowerCase(
                        Locale.ROOT
                    )
                    .contains(
                        needle
                    ))
                continue;

            if(normalizedCategory!=null&&
               !evidence.categories
                    .contains(
                        normalizedCategory
                    ))
                continue;

            matches.add(
                evidence
            );
        }

        int total=
            matches.size();

        if(offset>=total)
            return new Page(
                Collections.emptyList(),
                total,
                offset,
                limit
            );

        int to=
            (int)Math.min(
                (long)total,
                (long)offset+limit
            );

        return new Page(
            matches.subList(
                offset,
                to
            ),
            total,
            offset,
            limit
        );
    }

    private static LinkedHashMap<Integer,Evidence> load(){
        LinkedHashMap<Integer,Evidence> out=
            new LinkedHashMap<>();

        try(InputStream raw=
                ItemMechanicsEvidenceQueryService.class
                    .getResourceAsStream(
                        RESOURCE
                    )){
            if(raw==null)
                throw new IllegalStateException(
                    "missing resource "+
                    RESOURCE
                );

            try(BufferedReader reader=
                    new BufferedReader(
                        new InputStreamReader(
                            raw,
                            StandardCharsets.UTF_8
                        )
                    )){
                String header=
                    reader.readLine();

                if(!"item_id\titem_name\twield\twear\tsource\tfield\tcategories\tcore_terms\tnumeric_mentions\ttext"
                        .equals(header))
                    throw new IllegalStateException(
                        "unexpected mechanics header"
                    );

                String line;

                while((line=
                        reader.readLine())!=null){
                    if(line.isEmpty())
                        continue;

                    String[] p=
                        line.split(
                            "\t",
                            -1
                        );

                    if(p.length!=10)
                        throw new IllegalStateException(
                            "mechanics columns="+
                            p.length+
                            " line="+line
                        );

                    int itemId=
                        Integer.parseInt(
                            p[0]
                        );

                    if(out.containsKey(
                            itemId))
                        throw new IllegalStateException(
                            "duplicate item mechanics id "+
                            itemId
                        );

                    if(!SOURCE_DATASET.equals(
                            p[4]))
                        throw new IllegalStateException(
                            "unexpected source "+
                            p[4]+
                            " for "+itemId
                        );

                    if(!"hover".equals(
                            p[5]))
                        throw new IllegalStateException(
                            "unexpected field "+
                            p[5]+
                            " for "+itemId
                        );

                    Evidence evidence=
                        new Evidence(
                            itemId,
                            p[1],
                            Boolean.parseBoolean(
                                p[2]
                            ),
                            Boolean.parseBoolean(
                                p[3]
                            ),
                            p[4],
                            p[5],
                            splitSet(
                                p[6]
                            ),
                            splitSet(
                                p[7]
                            ),
                            splitList(
                                p[8]
                            ),
                            p[9]
                        );

                    out.put(
                        itemId,
                        evidence
                    );
                }
            }
        }catch(IOException e){
            throw new ExceptionInInitializerError(
                e
            );
        }

        if(out.size()!=99)
            throw new IllegalStateException(
                "expected 99 mechanics rows, got "+
                out.size()
            );

        return out;
    }

    private static Set<String> splitSet(
        String value
    ){
        LinkedHashSet<String> out=
            new LinkedHashSet<>();

        if(value==null||
           value.isEmpty())
            return out;

        for(String part:
                value.split(
                    "\\|",
                    -1
                )){
            String normalized=
                part.trim()
                    .toUpperCase(
                        Locale.ROOT
                    );

            if(!normalized.isEmpty())
                out.add(
                    normalized
                );
        }

        return out;
    }

    private static List<String> splitList(
        String value
    ){
        ArrayList<String> out=
            new ArrayList<>();

        if(value==null||
           value.isEmpty())
            return out;

        for(String part:
                value.split(
                    "\\|",
                    -1
                )){
            String normalized=
                part.trim();

            if(!normalized.isEmpty())
                out.add(
                    normalized
                );
        }

        return out;
    }
}

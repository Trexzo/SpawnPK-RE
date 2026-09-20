package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exact-current v308 client authority for the native Blood Fountain selectable
 * perk tree.
 *
 * Source: rs.n.c.aD in exact-current client SHA-256
 * 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6.
 *
 * The native tree creates widgets 45602..45645. Its selector emits
 * selectperk(widgetId - 45602), establishing exact selectable ids 0..43.
 *
 * This is presentation/selection identity only. It intentionally contains no
 * perk mechanics, costs, prerequisites, ownership or persistence policy.
 */
final class BloodFountainSelectablePerkCatalog {
    static final String AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final int FIRST_WIDGET_ID=
        45602;

    static final int LAST_WIDGET_ID=
        45645;

    static final String RESOURCE=
        "/spk/local/data/research_r85/blood_fountain_selectable_perks_v308.csv";

    static final class Definition {
        final int perkId;
        final int sourceWidgetId;
        final String name;
        final String authority;

        Definition(
            int perkId,
            int sourceWidgetId,
            String name
        ){
            this.perkId=perkId;
            this.sourceWidgetId=
                sourceWidgetId;
            this.name=name;
            this.authority=AUTHORITY;
        }

        @Override public String toString(){
            return "BloodFountainSelectablePerk{"+
                "perkId="+perkId+
                ",sourceWidgetId="+
                    sourceWidgetId+
                ",name="+name+
                ",authority="+authority+
                "}";
        }
    }

    private static final List<Definition> ALL=
        load();

    private static final Map<Integer,Definition>
        BY_ID=
            indexById(ALL);

    private static final Map<Integer,Definition>
        BY_WIDGET=
            indexByWidget(ALL);

    static int size(){
        return ALL.size();
    }

    static List<Definition> all(){
        return ALL;
    }

    static Definition byId(
        int perkId
    ){
        return BY_ID.get(
            perkId
        );
    }

    static Definition requireById(
        int perkId
    ){
        Definition definition=
            byId(perkId);

        if(definition==null)
            throw new IllegalArgumentException(
                "unknown Blood Fountain perk id="+
                perkId
            );

        return definition;
    }

    static Definition bySourceWidgetId(
        int widgetId
    ){
        return BY_WIDGET.get(
            widgetId
        );
    }

    private static List<Definition> load(){
        ArrayList<Definition> out=
            new ArrayList<>();

        try(InputStream in=
                BloodFountainSelectablePerkCatalog.class
                    .getResourceAsStream(
                        RESOURCE
                    )){
            if(in==null)
                throw new IllegalStateException(
                    "missing resource "+
                    RESOURCE
                );

            try(BufferedReader reader=
                    new BufferedReader(
                        new InputStreamReader(
                            in,
                            StandardCharsets.UTF_8
                        )
                    )){
                String header=
                    reader.readLine();

                if(!"perk_id,source_widget_id,name"
                        .equals(header))
                    throw new IllegalStateException(
                        "unexpected Blood Fountain catalog header"
                    );

                String line;

                while((line=
                        reader.readLine())!=null){
                    if(line.trim().isEmpty())
                        continue;

                    String[] columns=
                        line.split(",",3);

                    if(columns.length!=3)
                        throw new IllegalStateException(
                            "invalid Blood Fountain row: "+
                            line
                        );

                    out.add(
                        new Definition(
                            Integer.parseInt(
                                columns[0]
                            ),
                            Integer.parseInt(
                                columns[1]
                            ),
                            columns[2]
                        )
                    );
                }
            }
        }catch(IOException e){
            throw new ExceptionInInitializerError(
                e
            );
        }

        validate(out);

        return Collections.unmodifiableList(
            out
        );
    }

    private static void validate(
        List<Definition> definitions
    ){
        if(definitions.size()!=44)
            throw new IllegalStateException(
                "expected 44 selectable perks, got "+
                definitions.size()
            );

        HashSet<String> names=
            new HashSet<>();

        for(int i=0;
            i<definitions.size();
            i++){
            Definition definition=
                definitions.get(i);

            if(definition.perkId!=i)
                throw new IllegalStateException(
                    "non-contiguous perk id "+
                    definition.perkId+
                    " at row "+i
                );

            int expectedWidget=
                FIRST_WIDGET_ID+i;

            if(definition.sourceWidgetId!=
                    expectedWidget)
                throw new IllegalStateException(
                    "perk "+i+
                    " expected widget "+
                    expectedWidget+
                    " got "+
                    definition.sourceWidgetId
                );

            if(definition.name==null||
               definition.name.trim().isEmpty())
                throw new IllegalStateException(
                    "blank perk name at id "+
                    i
                );

            if(!names.add(
                    definition.name))
                throw new IllegalStateException(
                    "duplicate perk name "+
                    definition.name
                );
        }

        if(definitions
                .get(definitions.size()-1)
                .sourceWidgetId!=
                    LAST_WIDGET_ID)
            throw new IllegalStateException(
                "terminal widget mismatch"
            );
    }

    private static Map<Integer,Definition>
        indexById(
            List<Definition> definitions
        ){
        LinkedHashMap<Integer,Definition>
            out=
                new LinkedHashMap<>();

        for(Definition definition:
                definitions)
            out.put(
                definition.perkId,
                definition
            );

        return Collections.unmodifiableMap(
            out
        );
    }

    private static Map<Integer,Definition>
        indexByWidget(
            List<Definition> definitions
        ){
        LinkedHashMap<Integer,Definition>
            out=
                new LinkedHashMap<>();

        for(Definition definition:
                definitions)
            out.put(
                definition.sourceWidgetId,
                definition
            );

        return Collections.unmodifiableMap(
            out
        );
    }

    private BloodFountainSelectablePerkCatalog(){}
}

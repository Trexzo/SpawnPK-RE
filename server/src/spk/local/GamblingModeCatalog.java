package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exact-current v308 client authority for native gambling mode selection rows.
 *
 * Source: rs.n.c.V in exact-current client SHA-256
 * 854f26ff9f134b0317572e7ac1688e6f40a231d5a4c66f8db5d655b7f45ce7c6.
 *
 * This catalog intentionally records client presentation/selection identity
 * only. It is not authority for wager ownership, host permissions, RNG,
 * settlement, payout/refund policy or persistence.
 */
final class GamblingModeCatalog {
    static final String AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final int FIRST_WIDGET_ID=
        59853;

    static final int LAST_WIDGET_ID=
        59858;

    static final String RESOURCE=
        "/spk/local/data/research_r85/gambling_modes_v308.csv";

    static final class Definition {
        final int clientIndex;
        final int sourceWidgetId;
        final String name;
        final String authority;

        Definition(
            int clientIndex,
            int sourceWidgetId,
            String name
        ){
            this.clientIndex=
                clientIndex;
            this.sourceWidgetId=
                sourceWidgetId;
            this.name=name;
            this.authority=
                AUTHORITY;
        }

        @Override public String toString(){
            return "GamblingModeDefinition{"+
                "clientIndex="+
                    clientIndex+
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
        BY_INDEX=
            indexByClientIndex(
                ALL
            );

    private static final Map<Integer,Definition>
        BY_WIDGET=
            indexByWidget(
                ALL
            );

    static int size(){
        return ALL.size();
    }

    static List<Definition> all(){
        return ALL;
    }

    static Definition byClientIndex(
        int clientIndex
    ){
        return BY_INDEX.get(
            clientIndex
        );
    }

    static Definition bySourceWidgetId(
        int sourceWidgetId
    ){
        return BY_WIDGET.get(
            sourceWidgetId
        );
    }

    static Definition requireByClientIndex(
        int clientIndex
    ){
        Definition definition=
            byClientIndex(
                clientIndex
            );

        if(definition==null)
            throw new IllegalArgumentException(
                "unknown gambling client index="+
                clientIndex
            );

        return definition;
    }

    private static List<Definition> load(){
        ArrayList<Definition> out=
            new ArrayList<>();

        try(InputStream in=
                GamblingModeCatalog.class
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

                if(!"client_index,source_widget_id,name"
                        .equals(header))
                    throw new IllegalStateException(
                        "unexpected gambling catalog header"
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
                            "invalid gambling row: "+
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
        if(definitions.size()!=6)
            throw new IllegalStateException(
                "expected 6 gambling modes, got "+
                definitions.size()
            );

        HashSet<String> names=
            new HashSet<>();

        for(int i=0;
            i<definitions.size();
            i++){
            Definition definition=
                definitions.get(i);

            if(definition.clientIndex!=i)
                throw new IllegalStateException(
                    "non-contiguous client index "+
                    definition.clientIndex+
                    " at row "+i
                );

            int expectedWidget=
                FIRST_WIDGET_ID+i;

            if(definition.sourceWidgetId!=
                    expectedWidget)
                throw new IllegalStateException(
                    "client index "+i+
                    " expected widget "+
                    expectedWidget+
                    " got "+
                    definition.sourceWidgetId
                );

            if(definition.name==null||
               definition.name.trim().isEmpty())
                throw new IllegalStateException(
                    "blank gambling mode name "+
                    i
                );

            if(!names.add(
                    definition.name))
                throw new IllegalStateException(
                    "duplicate gambling mode name "+
                    definition.name
                );
        }

        if(definitions
                .get(definitions.size()-1)
                .sourceWidgetId!=
                    LAST_WIDGET_ID)
            throw new IllegalStateException(
                "terminal gambling widget mismatch"
            );
    }

    private static Map<Integer,Definition>
        indexByClientIndex(
            List<Definition> definitions
        ){
        LinkedHashMap<Integer,Definition>
            out=
                new LinkedHashMap<>();

        for(Definition definition:
                definitions)
            out.put(
                definition.clientIndex,
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

    private GamblingModeCatalog(){}
}

package spk.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Exact-current timed-effect presentation vocabulary recovered from client/cache research.
 *
 * Definitions describe client presentation identity only. They do not imply
 * server-side duration, stacking, eligibility, damage, stat or reward mechanics.
 */
final class TimedEffectCatalog {
    static final String PRESENTATION_AUTHORITY=
        "EXACT_CURRENT_CLIENT";

    static final String RESOURCE=
        "/spk/local/data/research_r83/01_TIMED_POPUP_EFFECT_CATALOG.csv";

    static final class Definition {
        final int ordinal;
        final String key;
        final String description;
        final String popupAsset;
        final int[] rawInts;
        final String constructorSignature;
        final String presentationAuthority;

        Definition(
            int ordinal,
            String key,
            String description,
            String popupAsset,
            int[] rawInts,
            String constructorSignature
        ){
            this.ordinal=ordinal;
            this.key=key;
            this.description=description;
            this.popupAsset=popupAsset;
            this.rawInts=rawInts.clone();
            this.constructorSignature=
                constructorSignature;
            this.presentationAuthority=
                PRESENTATION_AUTHORITY;
        }

        int[] rawInts(){
            return rawInts.clone();
        }

        @Override public String toString(){
            return "TimedEffectDefinition{"+
                "ordinal="+ordinal+
                ",key="+key+
                ",authority="+
                    presentationAuthority+
                "}";
        }
    }

    private static final List<Definition> ALL=
        load();

    private static final Map<String,Definition> BY_KEY=
        byKey(ALL);

    private static final Map<Integer,Definition> BY_ORDINAL=
        byOrdinal(ALL);

    static int size(){
        return ALL.size();
    }

    static List<Definition> all(){
        return ALL;
    }

    static Definition byKey(String key){
        if(key==null)
            return null;

        return BY_KEY.get(
            normalizeKey(key)
        );
    }

    static Definition require(String key){
        Definition definition=
            byKey(key);

        if(definition==null)
            throw new IllegalArgumentException(
                "unknown timed effect key="+
                key
            );

        return definition;
    }

    static Definition byOrdinal(int ordinal){
        return BY_ORDINAL.get(
            ordinal
        );
    }

    static String normalizeKey(String key){
        if(key==null)
            throw new NullPointerException(
                "key"
            );

        String normalized=
            key.trim()
                .toUpperCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "blank timed effect key"
            );

        return normalized;
    }

    private static List<Definition> load(){
        ArrayList<Definition> out=
            new ArrayList<>();

        try(InputStream in=
                TimedEffectCatalog.class
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

                if(header==null||
                   !header.startsWith(
                       "ordinal,enum,"))
                    throw new IllegalStateException(
                        "unexpected timed effect header"
                    );

                String line;

                while((line=
                        reader.readLine())!=null){
                    if(line.trim().isEmpty())
                        continue;

                    String[] row=
                        parseCsv(line);

                    if(row.length!=6)
                        throw new IllegalStateException(
                            "timed effect row columns="+
                            row.length+
                            " line="+line
                        );

                    int ordinal=
                        Integer.parseInt(
                            row[0]
                        );

                    String key=
                        normalizeKey(
                            row[1]
                        );

                    out.add(
                        new Definition(
                            ordinal,
                            key,
                            row[2],
                            row[3],
                            parseRawInts(
                                row[4]
                            ),
                            row[5]
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
        if(definitions.size()!=64)
            throw new IllegalStateException(
                "expected 64 timed effects, got "+
                definitions.size()
            );

        HashSet<String> keys=
            new HashSet<>();

        HashSet<Integer> ordinals=
            new HashSet<>();

        for(int i=0;
            i<definitions.size();
            i++){
            Definition definition=
                definitions.get(i);

            if(definition.ordinal!=i)
                throw new IllegalStateException(
                    "non-contiguous timed effect ordinal "+
                    definition.ordinal+
                    " at row "+i
                );

            if(!keys.add(
                    definition.key))
                throw new IllegalStateException(
                    "duplicate timed effect key "+
                    definition.key
                );

            if(!ordinals.add(
                    definition.ordinal))
                throw new IllegalStateException(
                    "duplicate timed effect ordinal "+
                    definition.ordinal
                );
        }

        if(!"DYNAMIC".equals(
                definitions.get(0).key))
            throw new IllegalStateException(
                "ordinal 0 must remain DYNAMIC"
            );
    }

    private static Map<String,Definition> byKey(
        List<Definition> definitions
    ){
        LinkedHashMap<String,Definition> out=
            new LinkedHashMap<>();

        for(Definition definition:
                definitions)
            out.put(
                definition.key,
                definition
            );

        return Collections.unmodifiableMap(
            out
        );
    }

    private static Map<Integer,Definition> byOrdinal(
        List<Definition> definitions
    ){
        LinkedHashMap<Integer,Definition> out=
            new LinkedHashMap<>();

        for(Definition definition:
                definitions)
            out.put(
                definition.ordinal,
                definition
            );

        return Collections.unmodifiableMap(
            out
        );
    }

    private static int[] parseRawInts(
        String raw
    ){
        if(raw==null||
           raw.trim().isEmpty())
            return new int[0];

        String[] parts=
            raw.split(
                "\\|"
            );

        int[] out=
            new int[parts.length];

        for(int i=0;
            i<parts.length;
            i++)
            out[i]=Integer.parseInt(
                parts[i]
            );

        return out;
    }

    private static String[] parseCsv(
        String line
    ){
        ArrayList<String> cells=
            new ArrayList<>();

        StringBuilder current=
            new StringBuilder();

        boolean quoted=false;

        for(int i=0;
            i<line.length();
            i++){
            char c=line.charAt(i);

            if(c=='"'){
                if(quoted&&
                   i+1<line.length()&&
                   line.charAt(i+1)=='"'){
                    current.append('"');
                    i++;
                }else{
                    quoted=!quoted;
                }
            }else if(c==','&&!quoted){
                cells.add(
                    current.toString()
                );
                current.setLength(0);
            }else{
                current.append(c);
            }
        }

        if(quoted)
            throw new IllegalStateException(
                "unterminated csv quote: "+
                line
            );

        cells.add(
            current.toString()
        );

        return cells.toArray(
            new String[0]
        );
    }

    private TimedEffectCatalog(){}
}

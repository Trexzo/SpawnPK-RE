package spk.local;

import java.util.*;

/**
 * Opaque persistence-owned state for domain snapshot extensions.
 *
 * Core gameplay schema keys remain owned by PlayerSnapshotSchemaV1. Additional
 * domains may persist values only under the explicit "extension." namespace so
 * validation can carry them forward without interpreting their semantics.
 */
final class PlayerSnapshotExtensionState {
    static final String PREFIX="extension.";

    private SortedMap<String,String> values=
        Collections.emptySortedMap();

    synchronized SortedMap<String,String> snapshot(){
        return Collections.unmodifiableSortedMap(
            new TreeMap<>(values)
        );
    }

    synchronized SortedMap<String,String> namespace(
        String namespace
    ){
        String prefix=
            namespacePrefix(namespace);
        TreeMap<String,String> result=
            new TreeMap<>();

        for(Map.Entry<String,String> entry:
                values.entrySet()){
            if(!entry.getKey().startsWith(prefix))
                continue;

            result.put(
                entry.getKey().substring(
                    prefix.length()
                ),
                entry.getValue()
            );
        }

        return Collections.unmodifiableSortedMap(
            result
        );
    }

    synchronized void replaceNamespace(
        String namespace,
        Map<String,String> replacement
    ){
        String prefix=
            namespacePrefix(namespace);
        Objects.requireNonNull(
            replacement,
            "replacement"
        );

        TreeMap<String,String> checked=
            new TreeMap<>();

        for(Map.Entry<String,String> entry:
                replacement.entrySet()){
            String key=entry.getKey();
            String value=entry.getValue();

            validateRelativeKey(key);

            if(value==null)
                throw new IllegalArgumentException(
                    "null snapshot extension value namespace="+
                    namespace+
                    " key="+key
                );

            checked.put(
                prefix+key,
                value
            );
        }

        TreeMap<String,String> next=
            new TreeMap<>(values);

        for(Iterator<String> iterator=
                next.keySet().iterator();
                iterator.hasNext();){
            if(iterator.next().startsWith(prefix))
                iterator.remove();
        }

        next.putAll(checked);
        values=
            Collections.unmodifiableSortedMap(
                next
            );
    }

    synchronized void replace(
        Map<String,String> replacement
    ){
        SortedMap<String,String> checked=
            checkedCopy(replacement);
        values=checked;
    }

    static SortedMap<String,String> extract(
        Map<String,String> source
    ){
        Objects.requireNonNull(source,"source");

        TreeMap<String,String> extracted=
            new TreeMap<>();

        for(Map.Entry<String,String> entry:
                source.entrySet()){
            String key=entry.getKey();

            if(key==null||
               !key.startsWith(PREFIX))
                continue;

            String value=entry.getValue();

            validateKey(key);

            if(value==null)
                throw new IllegalArgumentException(
                    "null snapshot extension value key="+
                    key
                );

            extracted.put(key,value);
        }

        return Collections.unmodifiableSortedMap(
            extracted
        );
    }

    private static SortedMap<String,String>
        checkedCopy(
            Map<String,String> source
        ){
        Objects.requireNonNull(source,"source");

        TreeMap<String,String> checked=
            new TreeMap<>();

        for(Map.Entry<String,String> entry:
                source.entrySet()){
            String key=entry.getKey();
            String value=entry.getValue();

            validateKey(key);

            if(value==null)
                throw new IllegalArgumentException(
                    "null snapshot extension value key="+
                    key
                );

            checked.put(key,value);
        }

        return Collections.unmodifiableSortedMap(
            checked
        );
    }

    private static String namespacePrefix(
        String namespace
    ){
        if(namespace==null)
            throw new NullPointerException(
                "namespace"
            );

        String clean=
            namespace.trim().toLowerCase(
                Locale.ROOT
            );

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "snapshot extension namespace blank"
            );

        for(int i=0;i<clean.length();i++){
            char c=clean.charAt(i);
            boolean valid=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='_'||
                c=='-';

            if(!valid)
                throw new IllegalArgumentException(
                    "invalid snapshot extension namespace="+
                    namespace
                );
        }

        return PREFIX+clean+".";
    }

    private static void validateRelativeKey(
        String key
    ){
        if(key==null||
           key.isEmpty()||
           key.startsWith(".")||
           key.endsWith(".")||
           key.indexOf("..")>=0)
            throw new IllegalArgumentException(
                "invalid snapshot extension relative key="+
                key
            );
    }

    private static void validateKey(
        String key
    ){
        if(key==null||
           !key.startsWith(PREFIX)||
           key.length()<=PREFIX.length())
            throw new IllegalArgumentException(
                "invalid snapshot extension key="+
                key
            );

        String suffix=
            key.substring(PREFIX.length());

        if(suffix.startsWith(".")||
           suffix.endsWith(".")||
           suffix.indexOf("..")>=0)
            throw new IllegalArgumentException(
                "invalid snapshot extension key="+
                key
            );
    }
}

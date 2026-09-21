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

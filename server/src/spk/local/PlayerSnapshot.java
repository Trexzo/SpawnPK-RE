package spk.local;

import java.util.*;

/**
 * Immutable versioned gameplay-state snapshot.
 *
 * Snapshot v2 is the domain-owned repository format. It deliberately preserves
 * the recovered schema-v1 gameplay keys; PlayerSnapshotMigrations owns version
 * transitions and PlayerSnapshotSchemaV1 owns gameplay key encoding/decoding.
 * Legacy Properties conversion remains only at repository/compatibility
 * boundaries.
 */
final class PlayerSnapshot {
    static final int CURRENT_VERSION=2;

    private final int version;
    private final String username;
    private final SortedMap<String,String> values;

    PlayerSnapshot(
        int version,
        String username,
        Map<String,String> values
    ){
        if(version<=0)
            throw new IllegalArgumentException(
                "version="+version
            );

        String clean=
            username==null
                ?""
                :username.trim().toLowerCase(
                    Locale.ROOT
                );

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "username"
            );

        TreeMap<String,String> copy=
            new TreeMap<>();

        if(values!=null){
            for(Map.Entry<String,String> entry:
                    values.entrySet()){
                String key=entry.getKey();
                String value=entry.getValue();

                if(key==null||
                   key.isEmpty()||
                   value==null)
                    throw new IllegalArgumentException(
                        "snapshot property"
                    );

                if("format.version".equals(key)||
                   "username".equals(key)||
                   "saved.at".equals(key))
                    continue;

                copy.put(key,value);
            }
        }

        this.version=version;
        this.username=clean;
        this.values=
            Collections.unmodifiableSortedMap(
                copy
            );
    }

    int version(){return version;}
    String username(){return username;}
    SortedMap<String,String> values(){
        return values;
    }

    String value(String key){
        return values.get(key);
    }

    Properties toLegacyProperties(){
        Properties properties=
            new Properties();

        properties.setProperty(
            "format.version",
            Integer.toString(version)
        );
        properties.setProperty(
            "username",
            username
        );

        for(Map.Entry<String,String> entry:
                values.entrySet())
            properties.setProperty(
                entry.getKey(),
                entry.getValue()
            );

        return properties;
    }

    static PlayerSnapshot fromLegacyProperties(
        String requestedUsername,
        Properties properties
    ){
        return PlayerSnapshotMigrations
            .fromLegacyProperties(
                requestedUsername,
                properties
            );
    }

    @Override public String toString(){
        return "PlayerSnapshot{version="+version+
            ",username="+username+
            ",keys="+values.size()+"}";
    }
}

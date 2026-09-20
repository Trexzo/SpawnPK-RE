package spk.local;

import java.util.*;

/**
 * Immutable versioned gameplay-state snapshot.
 *
 * Schema v1 intentionally carries the exact established property keys so the
 * repository boundary can be introduced before component codecs are extracted
 * from the legacy state classes.
 */
final class PlayerSnapshot {
    static final int CURRENT_VERSION=1;

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
        if(properties==null)
            throw new NullPointerException(
                "properties"
            );

        int version=parseInt(
            properties.getProperty(
                "format.version"
            ),
            -1
        );

        if(version!=CURRENT_VERSION)
            throw new IllegalArgumentException(
                "unsupported snapshot version="+
                version
            );

        String stored=
            properties.getProperty(
                "username",
                requestedUsername
            );

        TreeMap<String,String> values=
            new TreeMap<>();

        for(String key:
                properties.stringPropertyNames())
            if(!"format.version".equals(key)&&
               !"username".equals(key)&&
               !"saved.at".equals(key))
                values.put(
                    key,
                    properties.getProperty(key)
                );

        return new PlayerSnapshot(
            version,
            stored,
            values
        );
    }

    private static int parseInt(
        String value,
        int fallback
    ){
        try{
            return Integer.parseInt(value);
        }catch(Exception e){
            return fallback;
        }
    }

    @Override public String toString(){
        return "PlayerSnapshot{version="+version+
            ",username="+username+
            ",keys="+values.size()+"}";
    }
}

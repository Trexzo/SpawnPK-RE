package spk.local;

import java.util.*;

/**
 * Explicit snapshot-version migration plan.
 *
 * R8.5 local profile files are schema v1. Snapshot v2 is the first domain-owned
 * repository format; it intentionally preserves the established gameplay keys
 * byte-for-key so migration does not reinterpret recovered LocalLab state.
 */
final class PlayerSnapshotMigrations {
    static final int R85_SCHEMA_VERSION=1;

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

        if(version<=0)
            throw new IllegalArgumentException(
                "invalid snapshot version="+
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

        return migrate(
            new PlayerSnapshot(
                version,
                stored,
                values
            )
        );
    }

    static PlayerSnapshot migrate(
        PlayerSnapshot source
    ){
        Objects.requireNonNull(
            source,
            "source"
        );

        if(source.version()>
                PlayerSnapshot.CURRENT_VERSION)
            throw new IllegalArgumentException(
                "future snapshot version="+
                source.version()+
                " current="+
                PlayerSnapshot.CURRENT_VERSION
            );

        PlayerSnapshot current=source;

        while(current.version()<
                PlayerSnapshot.CURRENT_VERSION){
            switch(current.version()){
                case R85_SCHEMA_VERSION:
                    current=migrateV1ToV2(
                        current
                    );
                    break;
                default:
                    throw new IllegalArgumentException(
                        "no snapshot migration from version="+
                        current.version()+
                        " to current="+
                        PlayerSnapshot.CURRENT_VERSION
                    );
            }
        }

        return current;
    }

    /**
     * v1 -> v2 is deliberately key-preserving.
     *
     * The version boundary records the architectural ownership change from the
     * historical component/Properties format to the immutable repository
     * snapshot. Recovered gameplay keys and values remain untouched.
     */
    private static PlayerSnapshot migrateV1ToV2(
        PlayerSnapshot source
    ){
        return new PlayerSnapshot(
            2,
            source.username(),
            source.values()
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

    private PlayerSnapshotMigrations(){}
}

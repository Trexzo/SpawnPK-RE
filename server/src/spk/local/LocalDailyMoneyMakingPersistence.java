package spk.local;

import java.util.*;

/**
 * Versioned PlayerSnapshot extension for G18 CUSTOM_LOCALLAB Daily Money
 * Making difficulty selection only.
 *
 * Tracked-objective state, progress, teleport, rewards and reset cadence are
 * deliberately absent.
 */
final class LocalDailyMoneyMakingPersistence {
    static final String NAMESPACE=
        "daily-money-making-g18";
    static final String VERSION="1";
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G183_DAILY_MONEY_MAKING_DIFFICULTY_PERSISTENCE";

    static SortedMap<String,String> encode(
        DailyMoneyMakingStateService.Difficulty difficulty
    ){
        DailyMoneyMakingStateService.Difficulty checked=
            Objects.requireNonNull(
                difficulty,
                "difficulty"
            );

        TreeMap<String,String> values=
            new TreeMap<>();

        values.put(
            "version",
            VERSION
        );
        values.put(
            "authority",
            AUTHORITY
        );
        values.put(
            "selected-difficulty",
            checked.name()
        );

        return Collections.unmodifiableSortedMap(
            values
        );
    }

    static DailyMoneyMakingStateService.Difficulty decode(
        SortedMap<String,String> values
    ){
        if(values==null||
           values.isEmpty())
            return null;

        TreeSet<String> expected=
            new TreeSet<>(
                Arrays.asList(
                    "version",
                    "authority",
                    "selected-difficulty"
                )
            );

        if(!expected.equals(
                new TreeSet<>(
                    values.keySet()
                )))
            throw invalid(
                "key-set",
                values.keySet().toString()
            );

        require(
            VERSION,
            values.get("version"),
            "version"
        );
        require(
            AUTHORITY,
            values.get("authority"),
            "authority"
        );

        String raw=
            cleanRequired(
                values.get(
                    "selected-difficulty"
                ),
                "selected-difficulty"
            );

        try{
            return DailyMoneyMakingStateService
                .Difficulty.valueOf(
                    raw
                );
        }catch(RuntimeException failure){
            throw invalid(
                "selected-difficulty",
                raw
            );
        }
    }

    private static void require(
        String expected,
        String actual,
        String key
    ){
        if(!expected.equals(
                clean(actual)
            ))
            throw invalid(
                key,
                actual
            );
    }

    private static String cleanRequired(
        String value,
        String key
    ){
        String clean=
            clean(value);

        if(clean==null)
            throw invalid(
                key,
                value
            );

        return clean;
    }

    private static String clean(
        String value
    ){
        if(value==null)
            return null;

        String clean=
            value.trim();

        return clean.isEmpty()
            ?null
            :clean;
    }

    private static IllegalArgumentException invalid(
        String key,
        String value
    ){
        return new IllegalArgumentException(
            "invalid Daily Money Making persistence "+
            key+
            "="+
            value
        );
    }

    private LocalDailyMoneyMakingPersistence(){}
}

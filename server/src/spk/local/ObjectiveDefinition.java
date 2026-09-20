package spk.local;

import java.util.Locale;
import java.util.Objects;

/**
 * Protocol-independent semantic objective definition.
 *
 * Goal/source authority belong to the authoritative domain definition.
 * Client render ids, widget ids and packet identities deliberately do not.
 */
final class ObjectiveDefinition {
    final String key;
    final long goal;
    final String sourceAuthority;

    ObjectiveDefinition(
        String key,
        long goal,
        String sourceAuthority
    ){
        this.key=normalizeKey(key);

        if(goal<=0)
            throw new IllegalArgumentException(
                "goal="+goal
            );

        this.goal=goal;
        this.sourceAuthority=
            requireAuthority(
                sourceAuthority
            );
    }

    static String normalizeKey(
        String key
    ){
        Objects.requireNonNull(
            key,
            "key"
        );

        String normalized=
            key.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "blank objective key"
            );

        for(int i=0;
            i<normalized.length();
            i++){
            char c=normalized.charAt(i);

            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||
                c=='_'||
                c=='-'||
                c==':';

            if(!ok)
                throw new IllegalArgumentException(
                    "invalid objective key="+
                    key
                );
        }

        return normalized;
    }

    private static String requireAuthority(
        String authority
    ){
        if(authority==null||
           authority.trim().isEmpty())
            throw new IllegalArgumentException(
                "sourceAuthority"
            );

        return authority.trim();
    }

    boolean sameContract(
        ObjectiveDefinition other
    ){
        return other!=null&&
            key.equals(other.key)&&
            goal==other.goal&&
            sourceAuthority.equals(
                other.sourceAuthority
            );
    }

    @Override public String toString(){
        return "ObjectiveDefinition{"+
            "key="+key+
            ",goal="+goal+
            ",sourceAuthority="+
                sourceAuthority+
            "}";
    }
}

package spk.local;

import java.util.Locale;
import java.util.Objects;

/**
 * Protocol-independent semantic usage-quota definition.
 *
 * A definition owns only semantic identity, finite limit and source authority.
 * Reset cadence, eligibility and presentation are deliberately external.
 */
final class UsageQuotaDefinition {
    final String key;
    final long limit;
    final String sourceAuthority;

    UsageQuotaDefinition(
        String key,
        long limit,
        String sourceAuthority
    ){
        this.key=normalizeKey(key);

        if(limit<=0)
            throw new IllegalArgumentException(
                "limit="+limit
            );

        this.limit=limit;
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
                "blank quota key"
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
                    "invalid quota key="+key
                );
        }

        return normalized;
    }

    static String normalizeWindowKey(
        String key
    ){
        Objects.requireNonNull(
            key,
            "windowKey"
        );

        String normalized=
            key.trim();

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "blank windowKey"
            );

        if(normalized.length()>128)
            throw new IllegalArgumentException(
                "windowKey too long"
            );

        return normalized;
    }

    boolean sameContract(
        UsageQuotaDefinition other
    ){
        return other!=null&&
            key.equals(other.key)&&
            limit==other.limit&&
            sourceAuthority.equals(
                other.sourceAuthority
            );
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

    @Override public String toString(){
        return "UsageQuotaDefinition{"+
            "key="+key+
            ",limit="+limit+
            ",sourceAuthority="+
                sourceAuthority+
            "}";
    }
}

package spk.local;

import java.util.Locale;
import java.util.Objects;

/** Stable protocol-independent semantic match team identity. */
final class MatchTeamId implements Comparable<MatchTeamId> {
    private final String value;

    MatchTeamId(String value){
        this.value=normalize(value);
    }

    static MatchTeamId of(String value){
        return new MatchTeamId(value);
    }

    String value(){
        return value;
    }

    private static String normalize(String value){
        Objects.requireNonNull(value,"value");
        String normalized=value.trim().toLowerCase(Locale.ROOT);

        if(normalized.isEmpty())
            throw new IllegalArgumentException("blank match team id");
        if(normalized.length()>128)
            throw new IllegalArgumentException("match team id too long");

        for(int i=0;i<normalized.length();i++){
            char c=normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||c=='_'||c=='-'||c==':';
            if(!ok)
                throw new IllegalArgumentException(
                    "invalid match team id="+value
                );
        }

        return normalized;
    }

    @Override public int compareTo(MatchTeamId other){
        return value.compareTo(other.value);
    }

    @Override public boolean equals(Object other){
        return this==other||
            other instanceof MatchTeamId&&
            value.equals(((MatchTeamId)other).value);
    }

    @Override public int hashCode(){
        return value.hashCode();
    }

    @Override public String toString(){
        return value;
    }
}

package spk.local;

import java.util.Locale;
import java.util.Objects;

/** Stable protocol-independent semantic party identity. */
final class PartyId implements Comparable<PartyId> {
    private final String value;

    PartyId(String value){
        this.value=normalize(value);
    }

    static PartyId of(String value){
        return new PartyId(value);
    }

    String value(){
        return value;
    }

    private static String normalize(String value){
        Objects.requireNonNull(value,"value");
        String normalized=value.trim().toLowerCase(Locale.ROOT);

        if(normalized.isEmpty())
            throw new IllegalArgumentException("blank party id");
        if(normalized.length()>128)
            throw new IllegalArgumentException("party id too long");

        for(int i=0;i<normalized.length();i++){
            char c=normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||c=='_'||c=='-'||c==':';
            if(!ok)
                throw new IllegalArgumentException(
                    "invalid party id="+value
                );
        }

        return normalized;
    }

    @Override public int compareTo(PartyId other){
        return value.compareTo(other.value);
    }

    @Override public boolean equals(Object other){
        return this==other||
            other instanceof PartyId&&
            value.equals(((PartyId)other).value);
    }

    @Override public int hashCode(){
        return value.hashCode();
    }

    @Override public String toString(){
        return value;
    }
}

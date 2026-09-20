package spk.local;

import java.util.Locale;
import java.util.Objects;

/** Stable protocol-independent identity for a semantic global/world event. */
final class WorldEventId implements Comparable<WorldEventId> {
    private final String value;

    WorldEventId(String value){
        this.value=normalize(value);
    }

    static WorldEventId of(String value){
        return new WorldEventId(value);
    }

    String value(){
        return value;
    }

    static String normalize(String value){
        Objects.requireNonNull(value,"value");

        String normalized=value.trim().toLowerCase(Locale.ROOT);

        if(normalized.isEmpty())
            throw new IllegalArgumentException("blank world event id");

        if(normalized.length()>128)
            throw new IllegalArgumentException("world event id too long");

        for(int i=0;i<normalized.length();i++){
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
                    "invalid world event id="+value
                );
        }

        return normalized;
    }

    @Override public int compareTo(WorldEventId other){
        return value.compareTo(other.value);
    }

    @Override public boolean equals(Object other){
        return this==other||
            other instanceof WorldEventId&&
            value.equals(((WorldEventId)other).value);
    }

    @Override public int hashCode(){
        return value.hashCode();
    }

    @Override public String toString(){
        return value;
    }
}

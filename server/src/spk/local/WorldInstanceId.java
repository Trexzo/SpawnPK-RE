package spk.local;

import java.util.Locale;
import java.util.Objects;

/** Stable protocol-independent semantic world instance identity. */
final class WorldInstanceId implements Comparable<WorldInstanceId> {
    private final String value;

    WorldInstanceId(String value){
        this.value=normalize(value);
    }

    static WorldInstanceId of(String value){
        return new WorldInstanceId(value);
    }

    String value(){
        return value;
    }

    private static String normalize(String value){
        Objects.requireNonNull(value,"value");
        String normalized=value.trim().toLowerCase(Locale.ROOT);

        if(normalized.isEmpty())
            throw new IllegalArgumentException("blank world instance id");
        if(normalized.length()>128)
            throw new IllegalArgumentException("world instance id too long");

        for(int i=0;i<normalized.length();i++){
            char c=normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||c=='_'||c=='-'||c==':';
            if(!ok)
                throw new IllegalArgumentException(
                    "invalid world instance id="+value
                );
        }

        return normalized;
    }

    @Override public int compareTo(WorldInstanceId other){
        return value.compareTo(other.value);
    }

    @Override public boolean equals(Object other){
        return this==other||
            other instanceof WorldInstanceId&&
            value.equals(((WorldInstanceId)other).value);
    }

    @Override public int hashCode(){
        return value.hashCode();
    }

    @Override public String toString(){
        return value;
    }
}

package spk.local;

import java.util.Locale;
import java.util.Objects;

/** Stable protocol-independent identity for one player-owned loadout. */
final class PlayerLoadoutId implements Comparable<PlayerLoadoutId> {
    private final String value;

    PlayerLoadoutId(String value){
        this.value=normalize(value);
    }

    static PlayerLoadoutId of(String value){
        return new PlayerLoadoutId(value);
    }

    String value(){
        return value;
    }

    static String normalize(String value){
        Objects.requireNonNull(value,"value");

        String normalized=
            value.trim().toLowerCase(Locale.ROOT);

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                "blank loadout id"
            );

        if(normalized.length()>128)
            throw new IllegalArgumentException(
                "loadout id too long"
            );

        for(int i=0;i<normalized.length();i++){
            char c=normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||c=='_'||c=='-'||c==':';

            if(!ok)
                throw new IllegalArgumentException(
                    "invalid loadout id="+value
                );
        }

        return normalized;
    }

    @Override public int compareTo(PlayerLoadoutId other){
        return value.compareTo(other.value);
    }

    @Override public boolean equals(Object other){
        return this==other||
            other instanceof PlayerLoadoutId&&
            value.equals(
                ((PlayerLoadoutId)other).value
            );
    }

    @Override public int hashCode(){
        return value.hashCode();
    }

    @Override public String toString(){
        return value;
    }
}

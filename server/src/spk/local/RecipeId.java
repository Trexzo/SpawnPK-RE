package spk.local;

import java.util.Locale;
import java.util.Objects;

/** Stable protocol-independent semantic recipe identity. */
final class RecipeId implements Comparable<RecipeId> {
    private final String value;

    RecipeId(String value){
        this.value=normalize(value);
    }

    static RecipeId of(String value){
        return new RecipeId(value);
    }

    String value(){
        return value;
    }

    static String normalize(String value){
        Objects.requireNonNull(value,"value");
        String normalized=value.trim().toLowerCase(Locale.ROOT);

        if(normalized.isEmpty())
            throw new IllegalArgumentException("blank recipe id");
        if(normalized.length()>128)
            throw new IllegalArgumentException("recipe id too long");

        for(int i=0;i<normalized.length();i++){
            char c=normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||c=='_'||c=='-'||c==':';
            if(!ok)
                throw new IllegalArgumentException(
                    "invalid recipe id="+value
                );
        }

        return normalized;
    }

    @Override public int compareTo(RecipeId other){
        return value.compareTo(other.value);
    }

    @Override public boolean equals(Object other){
        return this==other||
            other instanceof RecipeId&&
            value.equals(((RecipeId)other).value);
    }

    @Override public int hashCode(){
        return value.hashCode();
    }

    @Override public String toString(){
        return value;
    }
}

package spk.local;

import java.util.Locale;
import java.util.Objects;

/** Stable protocol-independent semantic queue identity. */
final class QueueId implements Comparable<QueueId> {
    private final String value;

    QueueId(String value){
        this.value=normalize(value);
    }

    static QueueId of(String value){
        return new QueueId(value);
    }

    String value(){
        return value;
    }

    private static String normalize(String value){
        Objects.requireNonNull(value,"value");
        String normalized=value.trim().toLowerCase(Locale.ROOT);

        if(normalized.isEmpty())
            throw new IllegalArgumentException("blank queue id");
        if(normalized.length()>128)
            throw new IllegalArgumentException("queue id too long");

        for(int i=0;i<normalized.length();i++){
            char c=normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||c=='_'||c=='-'||c==':';
            if(!ok)
                throw new IllegalArgumentException(
                    "invalid queue id="+value
                );
        }

        return normalized;
    }

    @Override public int compareTo(QueueId other){
        return value.compareTo(other.value);
    }

    @Override public boolean equals(Object other){
        return this==other||
            other instanceof QueueId&&
            value.equals(((QueueId)other).value);
    }

    @Override public int hashCode(){
        return value.hashCode();
    }

    @Override public String toString(){
        return value;
    }
}

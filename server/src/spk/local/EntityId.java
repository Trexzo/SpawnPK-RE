package spk.local;

import java.util.concurrent.atomic.AtomicLong;

final class EntityId {
    private static final AtomicLong IDS=new AtomicLong(1L);

    static EntityId next(){
        return new EntityId(IDS.getAndIncrement());
    }

    final long value;

    EntityId(long value){
        if(value<=0)throw new IllegalArgumentException("value="+value);
        this.value=value;
    }

    @Override public boolean equals(Object o){
        return o instanceof EntityId&&
            ((EntityId)o).value==value;
    }

    @Override public int hashCode(){
        return Long.hashCode(value);
    }

    @Override public String toString(){
        return Long.toUnsignedString(value);
    }
}

package spk.local;

final class EntityId {
    final long value;
    EntityId(long value){ if(value<=0)throw new IllegalArgumentException("value="+value); this.value=value; }
    @Override public boolean equals(Object o){ return o instanceof EntityId && ((EntityId)o).value==value; }
    @Override public int hashCode(){ return Long.hashCode(value); }
    @Override public String toString(){ return Long.toUnsignedString(value); }
}

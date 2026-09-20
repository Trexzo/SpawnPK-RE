package spk.local;

/** Monotonic semantic revision for a PlayerLoadout definition. */
final class PlayerLoadoutVersion implements Comparable<PlayerLoadoutVersion> {
    final long value;

    PlayerLoadoutVersion(long value){
        if(value<=0)
            throw new IllegalArgumentException(
                "version="+value
            );
        this.value=value;
    }

    static PlayerLoadoutVersion of(long value){
        return new PlayerLoadoutVersion(value);
    }

    PlayerLoadoutVersion next(){
        try{
            return new PlayerLoadoutVersion(
                Math.addExact(value,1L)
            );
        }catch(ArithmeticException error){
            throw new IllegalStateException(
                "loadout version overflow",
                error
            );
        }
    }

    @Override public int compareTo(PlayerLoadoutVersion other){
        return Long.compare(value,other.value);
    }

    @Override public boolean equals(Object other){
        return other instanceof PlayerLoadoutVersion&&
            ((PlayerLoadoutVersion)other).value==value;
    }

    @Override public int hashCode(){
        return Long.hashCode(value);
    }

    @Override public String toString(){
        return "v"+Long.toUnsignedString(value);
    }
}

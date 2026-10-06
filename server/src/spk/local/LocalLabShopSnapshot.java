package spk.local;

import java.util.Objects;

/** Immutable explicit LocalLab world-economy snapshot. */
final class LocalLabShopSnapshot {
    static final int CURRENT_VERSION=1;

    final int version;
    final long rocktailStock;

    LocalLabShopSnapshot(
        int version,
        long rocktailStock
    ){
        if(version!=CURRENT_VERSION)
            throw new IllegalArgumentException(
                "unsupported LocalLab Shop snapshot version="+
                version
            );

        if(rocktailStock<0L)
            throw new IllegalArgumentException(
                "negative LocalLab Shop rocktail stock="+
                rocktailStock
            );

        this.version=version;
        this.rocktailStock=rocktailStock;
    }

    static LocalLabShopSnapshot ofRocktailStock(
        long rocktailStock
    ){
        return new LocalLabShopSnapshot(
            CURRENT_VERSION,
            rocktailStock
        );
    }

    @Override public boolean equals(Object other){
        if(this==other)return true;
        if(!(other instanceof LocalLabShopSnapshot))
            return false;

        LocalLabShopSnapshot snapshot=
            (LocalLabShopSnapshot)other;

        return version==snapshot.version&&
            rocktailStock==snapshot.rocktailStock;
    }

    @Override public int hashCode(){
        return Objects.hash(
            version,
            rocktailStock
        );
    }

    @Override public String toString(){
        return "LocalLabShopSnapshot{version="+
            version+
            ",rocktailStock="+
            rocktailStock+
            "}";
    }
}

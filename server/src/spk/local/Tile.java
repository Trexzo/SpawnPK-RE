package spk.local;

import java.util.Objects;

final class Tile {
    final int x,y,plane;
    Tile(int x,int y,int plane){ this.x=x; this.y=y; this.plane=plane; }
    int chebyshev(Tile o){ return Math.max(Math.abs(x-o.x),Math.abs(y-o.y)); }
    @Override public boolean equals(Object o){ if(!(o instanceof Tile))return false; Tile t=(Tile)o; return x==t.x&&y==t.y&&plane==t.plane; }
    @Override public int hashCode(){ return Objects.hash(x,y,plane); }
    @Override public String toString(){ return x+","+y+","+plane; }
}

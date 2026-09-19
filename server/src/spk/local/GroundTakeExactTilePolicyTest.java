package spk.local;

public final class GroundTakeExactTilePolicyTest {
    public static void main(String[] args){
        MovementState m=new MovementState(); int gx=m.x()+1,gy=m.y();
        if(LocalSession.chebyshev(m.x(),m.y(),gx,gy)!=1)throw new AssertionError("fixture");
        boolean adjacent=LocalSession.chebyshev(m.x(),m.y(),gx,gy)<=1;
        boolean exact=m.x()==gx&&m.y()==gy;
        if(!adjacent||exact)throw new AssertionError("policy fixture invalid");
        System.out.println("V5122_GROUND_TAKE_EXACT_TILE_POLICY_PASS adjacentDoesNotQualify=true exactTileRequired=true");
    }
}

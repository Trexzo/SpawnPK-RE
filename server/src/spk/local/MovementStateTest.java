package spk.local;

public final class MovementStateTest {
    public static void main(String[] args) {
        MovementState m = new MovementState();
        MovementRequest p = new MovementRequest(164, false,
            new int[]{3088,3091,3091}, new int[]{3495,3492,3494}, null);
        String r = m.accept(p);
        if (!r.startsWith("ACCEPTED")) throw new AssertionError(r);
        int[][] expected = {
            {3088,3495,4},
            {3089,3494,7},
            {3090,3493,7},
            {3091,3492,7},
            {3091,3493,1},
            {3091,3494,1}
        };
        for (int[] e : expected) {
            MovementState.Tick t=m.advance();
            if (t==null || t.tiles!=1 || t.toX!=e[0] || t.toY!=e[1] || t.dir1!=e[2])
                throw new AssertionError("step mismatch");
        }
        if (m.advance()!=null) throw new AssertionError("queue not empty");

        MovementRequest run = new MovementRequest(164, true,
            new int[]{3092,3093}, new int[]{3494,3493}, null);
        r = m.accept(run);
        if (!r.startsWith("ACCEPTED")) throw new AssertionError(r);
        MovementState.Tick rt=m.advance();
        if (rt==null || !rt.running || rt.tiles!=2 || rt.dir1!=4 || rt.dir2!=7
                || rt.toX!=3093 || rt.toY!=3493) throw new AssertionError("run mismatch");
        // Persistent run toggle must make an ordinary run=0 walk request execute
        // two tiles per server tick, independently of the client's Ctrl run bit.
        if (!m.togglePersistentRun()) throw new AssertionError("persistent run did not enable");
        MovementRequest toggled = new MovementRequest(164, false,
            new int[]{3095}, new int[]{3493}, null);
        r = m.accept(toggled);
        if (!r.startsWith("ACCEPTED") || !r.contains("effectiveRun=1")) throw new AssertionError(r);
        MovementState.Tick tr=m.advance();
        if (tr==null || !tr.running || tr.tiles!=2 || tr.toX!=3095 || tr.toY!=3493)
            throw new AssertionError("persistent toggle run mismatch");
        System.out.println("MOVEMENT_STATE_V3_PASS walkExpanded="+expected.length+" ctrlRunTiles=2 persistentRunTiles=2 final="+m.x()+","+m.y());
    }
}

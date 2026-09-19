package spk.local;

/** v5.12.2 movement regression: authoritative WALK/RUN semantics plus bank-close-on-movement
 * are covered separately by RuntimeCorrectiveIntegrationTest. */
public final class MovementRuntimeIntegrationTest {
    public static void main(String[] args){
        MovementState m=new MovementState();
        int x0=m.x(),y0=m.y();
        String w=m.accept(new MovementRequest(164,false,new int[]{x0+1},new int[]{y0},new byte[0]));
        if(!w.startsWith("ACCEPTED"))throw new AssertionError(w);
        MovementState.Tick t1=m.advance();
        if(t1==null||t1.tiles!=1||m.x()!=x0+1||m.y()!=y0)throw new AssertionError("walk tick");
        m.setPersistentRun(true);
        String r=m.accept(new MovementRequest(164,false,new int[]{x0+3},new int[]{y0},new byte[0]));
        if(!r.startsWith("ACCEPTED"))throw new AssertionError(r);
        MovementState.Tick t2=m.advance();
        if(t2==null||t2.tiles!=2||m.x()!=x0+3||m.y()!=y0)throw new AssertionError("run tick");
        m.setPersistentRun(false);
        if(m.queued()!=0)throw new AssertionError("queue not drained");
        System.out.println("V5122_MOVEMENT_RUNTIME_PASS walk1=true persistentRun2=true queueDrain=true bankCloseOnMovement=RuntimeCorrectiveIntegrationTest");
    }
}

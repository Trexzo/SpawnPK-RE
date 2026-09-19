package spk.local;

public final class SharedWorldOwnershipTest {
    public static void main(String[] args)throws Exception{
        World w=World.isolatedForTest(20L);
        WorldPlayer a=new WorldPlayer(),b=new WorldPlayer();
        if(w.players().size()!=0)throw new AssertionError("initial membership");
        long ga=w.registerPlayer(a,"alpha");
        if(w.players().size()!=1||w.players().byName("ALPHA")!=a)throw new AssertionError("alpha registration");
        long gb=w.registerPlayer(b,"beta");
        if(w.players().size()!=2||a.id().equals(b.id()))throw new AssertionError("distinct players");
        a.playerState().setCurrentLevel(PlayerState.ATTACK,77);
        if(b.playerState().currentLevel(PlayerState.ATTACK)==77)throw new AssertionError("state aliasing");
        if(!a.accepts(ga)||!b.accepts(gb))throw new AssertionError("generation");
        boolean duplicate=false;try{w.registerPlayer(new WorldPlayer(),"Alpha");}catch(IllegalStateException ok){duplicate=true;}
        if(!duplicate)throw new AssertionError("duplicate policy");
        if(!w.unregisterPlayer(a)||w.players().size()!=1)throw new AssertionError("remove alpha");
        if(w.unregisterPlayer(a))throw new AssertionError("idempotent unregister");
        if(!w.unregisterPlayer(b)||w.players().size()!=0)throw new AssertionError("remove beta");
        w.close();
        System.out.println("V512_SHARED_WORLD_OWNERSHIP_PASS worldIdentity="+System.identityHashCode(w)+" distinctPlayerIds=true membership=0_1_2_1_0 duplicatePolicy=REJECT idempotentDisconnect=true");
    }
}

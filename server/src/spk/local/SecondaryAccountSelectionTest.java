package spk.local;

public final class SecondaryAccountSelectionTest {
    public static void main(String[] args)throws Exception{
        World w=World.isolatedForTest(50L);
        try{
            String first=LocalAccountProfiles.chooseForLogin(w,"opensrc"); if(!"opensrc".equals(first))throw new AssertionError(first);
            WorldPlayer a=new WorldPlayer(); w.registerPlayer(a,first);
            String second=LocalAccountProfiles.chooseForLogin(w,"opensrc"); if(!"src".equals(second))throw new AssertionError(second);
            WorldPlayer b=new WorldPlayer(); w.registerPlayer(b,second);
            if(w.players().size()!=2||w.players().byName("opensrc")!=a||w.players().byName("src")!=b)throw new AssertionError("two-profile membership");
            boolean full=false;try{LocalAccountProfiles.chooseForLogin(w,"localtest");}catch(IllegalStateException expected){full=expected.getMessage().contains("LOCAL_PROFILE_SLOTS_FULL");}
            if(!full)throw new AssertionError("third canonical client did not fail closed");
            w.unregisterPlayer(a);w.unregisterPlayer(b);
            System.out.println("V5123_SECONDARY_ACCOUNT_SELECTION_PASS primary=opensrc secondary=src thirdCanonicalFailsClosed=true");
        }finally{w.close();}
    }
}

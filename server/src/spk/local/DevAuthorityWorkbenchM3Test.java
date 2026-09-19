package spk.local;

/** M3 adds player morph + semantic trace state to the existing session-only registry. */
public final class DevAuthorityWorkbenchM3Test {
    public static void main(String[] args){
        DevAuthorityWorkbench d=new DevAuthorityWorkbench();
        d.setPlayerNpcTransformId(8330);
        d.trace().setEnabled(true);
        d.trace().record("X","Y","Z");
        req(d.playerNpcTransformId()!=null&&d.playerNpcTransformId()==8330,"morph");
        req(d.trace().size()==1,"trace");
        d.resetAll();
        req(d.playerNpcTransformId()==null,"morph reset");
        req(d.trace().size()==0,"trace entries reset");
        boolean rejected=false;try{d.setPlayerNpcTransformId(16384);}catch(IllegalArgumentException e){rejected=true;}
        req(rejected,"range fence");
        System.out.println("V592_DEV_AUTHORITY_WORKBENCH_M3_PASS playerMorph=true trace=true resetAll=true npcIdRangeFence=true persistence=NONE_BY_DESIGN");
    }
    static void req(boolean b,String s){if(!b)throw new AssertionError(s);}
}

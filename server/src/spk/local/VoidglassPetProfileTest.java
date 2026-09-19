package spk.local;

public final class VoidglassPetProfileTest {
    public static void main(String[] args){
        if(VoidglassPetProfile.BASE_ITEM_ID!=22960)throw new AssertionError("base item");
        if(VoidglassPetProfile.BASE_NPC_ID!=3701)throw new AssertionError("base npc");
        if(VoidglassPetProfile.WORLD_MODEL_ID!=32680)throw new AssertionError("world model");
        if(VoidglassPetProfile.STAND_ANIM!=7416||VoidglassPetProfile.WALK_TURN_ANIM!=7411)throw new AssertionError("pose");
        if(!VoidglassPetProfile.allowedSelector(6)||!VoidglassPetProfile.allowedSelector(8)||VoidglassPetProfile.allowedSelector(3))throw new AssertionError("selector policy");
        VoidglassPetState s=new VoidglassPetState();
        if(s.active())throw new AssertionError("active initially");
        s.activate(Integer.valueOf(3));
        if(!s.active()||s.selectedParticle()!=6||s.previousParticleSelector().intValue()!=3)throw new AssertionError("activate");
        s.selectParticle(8);s.recordProc();s.recordProc();
        if(s.selectedParticle()!=8||s.procCount()!=2)throw new AssertionError("state update");
        Integer prior=s.clearAndRestoreSelector();
        if(s.active()||prior==null||prior.intValue()!=3||s.procCount()!=0)throw new AssertionError("clear");
        System.out.println("VOIDGLASS_PROFILE_TEST_PASS base=22960->3701 model=32680 pose=7416/7411 selectors=6,8 sessionOnly=true");
    }
}

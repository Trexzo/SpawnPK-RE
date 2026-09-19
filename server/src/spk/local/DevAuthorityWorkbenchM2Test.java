package spk.local;

/** Session override registry must never require account persistence. */
public final class DevAuthorityWorkbenchM2Test {
 public static void main(String[] args){
  DevAuthorityWorkbench d=new DevAuthorityWorkbench();
  if(d.petParticleSelector()!=null||d.petFollowFrozen()||d.petFollowDelayMs()!=null||d.hasCombatAnimationOverride(28860))throw new AssertionError(d.summary());
  d.setPetParticleSelector(255);d.setPetFollowFrozen(true);d.setPetFollowDelayMs(900L);d.setCombatAnimationOverride(28860,-1);
  if(d.petParticleSelector()!=255||!d.petFollowFrozen()||d.petFollowDelayMs()!=900L||d.combatAnimationOverride(28860)!=-1)throw new AssertionError(d.summary());
  d.resetAll();
  if(d.petParticleSelector()!=null||d.petFollowFrozen()||d.petFollowDelayMs()!=null||d.hasCombatAnimationOverride(28860))throw new AssertionError("reset "+d.summary());
  System.out.println("V591_DEV_AUTHORITY_WORKBENCH_M2_PASS particleSelector=true followFreeze=true followDelay=true combatAnimationOverride=true resetAll=true persistence=NONE_BY_DESIGN");
 }
}

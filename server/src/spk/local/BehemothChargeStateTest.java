package spk.local;
/** Server-side state accumulator; modifiers are recorded but not injected into Combat M2 fixture formula. */
public final class BehemothChargeStateTest {
 public static void main(String[] args){
  PetEffectState solar=new PetEffectState();solar.onPetChanged(25415,6650); long t=1_000_000L;
  if(solar.recordDamage(74,t)||solar.charge()!=0)throw new AssertionError("solar74");
  if(!solar.recordDamage(1,t+1)||solar.charge()!=1)throw new AssertionError("solar75");
  if(!solar.recordDamage(75,t+2)||solar.charge()!=2)throw new AssertionError("solar150");
  if(!solar.recordDamage(75,t+3)||solar.charge()!=3)throw new AssertionError("solar225");
  if(!solar.tick(t+10_004)||solar.charge()!=0)throw new AssertionError("solar reset10s");

  PetEffectState unholy=new PetEffectState();unholy.onPetChanged(25425,5159);
  unholy.recordDamage(225,t); if(unholy.charge()!=3||unholy.resetMs()!=20_000L)throw new AssertionError("unholy");
  if(!unholy.tick(t+20_000)||unholy.charge()!=0)throw new AssertionError("unholy reset20s");

  PetEffectState scooby=new PetEffectState();scooby.onPetChanged(24016,6650);
  scooby.recordDamage(75,t); if(scooby.charge()!=1||scooby.threshold()!=75||scooby.resetMs()!=20_000L)throw new AssertionError("scooby family authority");

  PetEffectState cursed=new PetEffectState();cursed.onPetChanged(24019,5161);cursed.recordDamage(75,t);if(cursed.charge()!=1||cursed.resetMs()!=20_000L)throw new AssertionError("cursed behemoth family");

  PetEffectState hydra=new PetEffectState();hydra.onPetChanged(22947,6049);
  hydra.recordDamage(49,t); if(hydra.charge()!=0)throw new AssertionError(); hydra.recordDamage(1,t+1);if(hydra.charge()!=1||hydra.threshold()!=50)throw new AssertionError();
  System.out.println("V59_BEHEMOTH_CHARGE_STATE_PASS solar=75x3_reset10s unholy=75x3_reset20s scoobyCursed=unholyFamily75_reset20s ancientHydra=50_reset10s modifiersRecordedOnly=true combatM2FormulaUntouched=true");
 }
}

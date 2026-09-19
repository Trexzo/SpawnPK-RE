package spk.local;
public final class EngineR41V913WeaponAuthorityTest{
 public static void main(String[]a){
  if(V913WeaponRuntimeAuthority.count()!=11)throw new AssertionError("rows="+V913WeaponRuntimeAuthority.count());
  row(28539,-1,16199,5090,5092,5091,4,true);
  row(25557,426,15409,4056,4057,-1,3,true);
  row(22218,426,15409,1116,1120,-1,2,true);
  row(27928,-1,440,-1,-1,-1,-1,true);
  row(25001,-1,4230,-1,27,-1,5,true);
  row(28860,426,15409,4080,4079,-1,3,true);
  row(28843,-1,10546,457,1019,-1,5,true);
  row(21099,-1,8145,-1,-1,-1,4,true);
  row(24093,-1,1658,-1,-1,-1,-1,true);
  row(25567,-1,15416,-1,-1,-1,-1,true);
  row(11791,-1,811,-1,-1,-1,5,false);
  V913WeaponRuntimeAuthority.Profile scorching=V913WeaponRuntimeAuthority.resolve(28860);
  if(scorching.attackAnimation==15624||scorching.actorGfx==4135||scorching.projectileId==4136)throw new AssertionError("obsolete scorching candidate leaked");
  CombatWeaponProfile base=CombatWeaponRepository.resolve(28860);if(base==null||base.attackSpeedTicks!=3||base.attackRange!=10)throw new AssertionError("scorching mechanics baseline unexpectedly changed");
  System.out.println("V5141_ENGINE_R41_V913_WEAPON_AUTHORITY_PASS rows=11 directBasic=10 scorching=15409+gfx4080+proj4079 speed3 shadow=16199+5090/5092/5091 webweaver=15409+4056/4057 swift=15409+1116/1120 dragonSword=8145 spellAction11791Separated=true");
 }
 static void row(int id,int pre,int anim,int gfx,int proj,int target,int speed,boolean basic){V913WeaponRuntimeAuthority.Profile p=V913WeaponRuntimeAuthority.resolve(id);if(p==null)throw new AssertionError("missing "+id);if(p.preAnimation!=pre||p.attackAnimation!=anim||p.actorGfx!=gfx||p.projectileId!=proj||p.targetGfx!=target||p.speedTicks!=speed||p.directBasicAttack!=basic)throw new AssertionError("row mismatch "+p);}
}

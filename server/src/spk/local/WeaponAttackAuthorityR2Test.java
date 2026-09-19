package spk.local;

public final class WeaponAttackAuthorityR2Test {
    public static void main(String[] args){
        if(WeaponAttackAuthorityRepository.count()!=144)throw new AssertionError("rows="+WeaponAttackAuthorityRepository.count());
        if(WeaponAttackAuthorityRepository.provenRuntimeCount()!=1)throw new AssertionError("runtime="+WeaponAttackAuthorityRepository.provenRuntimeCount());
        WeaponAttackAuthorityRepository.Row d=WeaponAttackAuthorityRepository.resolve(11235);
        if(d==null||d.attackAnimation!=15409||d.projectileId!=1120||d.gfxId!=1111||!"PROVEN_RUNTIME".equals(d.attackAuthority))throw new AssertionError("dark="+(d==null?null:d.attackAuthority));
        if(WeaponAttackAuthorityRepository.resolve(28810)!=null||WeaponAttackAuthorityRepository.resolve(28860)!=null)throw new AssertionError("Scorching live-rejected rows must not auto-import");
        WeaponAttackAuthorityRepository.Row b=WeaponAttackAuthorityRepository.resolve(21577);
        if(b==null||b.attackAnimation!=884||b.range!=10||b.speedTicks!=4)throw new AssertionError("blowpipe");
        if(WeaponPoseRepository.r2Count()<200)throw new AssertionError("R2 pose supplement too small="+WeaponPoseRepository.r2Count());
        WeaponPoseRepository.Resolution p=WeaponPoseRepository.resolve(11785);
        if(p==null||p.pose.walk!=819||p.pose.run!=824)throw new AssertionError("ACB pose="+(p==null?null:p.pose));
        System.out.println("V594_WEAPON_R2_AUTHORITY_PASS attackRows=144 provenRuntime=1 poseSupplement="+WeaponPoseRepository.r2Count()+" unsafeScorchingAutoImport=false");
    }
}

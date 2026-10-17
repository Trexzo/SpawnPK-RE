package spk.local;

/**
 * G19.2.9/30: deterministic final-admission seam inside the resolver,
 * after World/player/current canonical locks, before lifecycle HP
 * mutation. A replacement of the *selected* entry must not be hit.
 * The admission predicate here intentionally changes the registry;
 * it is a test-only injection and not recovered client policy.
 */
public final class G1929NpcLifecycleReplacedInOwnedDamageAdmissionTest {
    static void run(boolean replaceSelected)throws Exception{
        World world=World.isolatedForTest(600L);
        WorldPlayer player=new WorldPlayer();
        long generation=-1L;
        try{
            generation=world.registerPlayer(
                player,replaceSelected?
                    "g1929-selected-seam":"g1930-unrelated-seam");
            player.movement().restoreAccountState(
                false,100,3087,3495,0);

            int def=G1903DecodedOpcode72AcceptedHitWitnessTest
                .attackDefinition();
            WorldNpc selected=world.npcs().spawn(
                def,3088,3495,0);
            WorldNpc other=world.npcs().spawn(
                def,3088,3496,0);
            NpcLifecycleService lifecycle=world.npcLifecycle();
            lifecycle.register(selected,20,
                "CUSTOM_LOCALLAB_G1929_SELECTED");
            lifecycle.register(other,20,
                "CUSTOM_LOCALLAB_G1930_UNRELATED");
            NpcLifecycleService.Snapshot priorSelected=
                lifecycle.get(selected.id);
            NpcLifecycleService.Snapshot priorOther=
                lifecycle.get(other.id);
            check(priorSelected!=null&&priorSelected.alive()&&
                    priorOther!=null&&priorOther.alive(),
                "two live independent registrations");

            NpcCombatResolutionService resolver=
                new NpcCombatResolutionService(
                    player,lifecycle,
                    CombatDamageRules.localLabFallback(),
                    CombatAttackTimingRules.recoveredCompatibility(),
                    CombatSystemHooks.forPlayer(player));
            int weapon=player.equipment().weapon();
            CombatStyleRepository.Style style=
                player.combatStyles().current(
                    CombatInterfaceRepository.forWeapon(weapon));
            final WorldNpc toRestart=replaceSelected?selected:other;
            final NpcLifecycleService.Snapshot previous=
                replaceSelected?priorSelected:priorOther;
            final int[] admissionCalls={0};
            final long capturedGeneration=generation;
            NpcCombatResolutionService.Result accepted=null;
            boolean expectedRejection=false;
            try{
                accepted=resolver.resolveImmediateOwnedAdmitted(
                    world,generation,selected,weapon,style,
                    world.clock().tick(),
                    (attacker,target)->{
                        admissionCalls[0]++;
                        check(attacker==player&&target==selected&&
                                world.players().owns(player,capturedGeneration)&&
                                world.npcs().byId(selected.id)==selected,
                            "callback runs after exact World/attacker/target ownership");
                        check(lifecycle.unregisterExact(toRestart),
                            "registration removed inside last admission seam");
                        NpcLifecycleService.Snapshot replacement=
                            lifecycle.register(toRestart,20,
                                previous.sourceAuthority);
                        check(replacement.alive()&&
                                replacement.hitpoints==previous.hitpoints&&
                                replacement.maxHitpoints==previous.maxHitpoints&&
                                replacement.registrationIdentity!=
                                    previous.registrationIdentity,
                            "new equal-valued registration in last seam");
                        return true;
                    },
                    priorSelected.registrationIdentity);
            }catch(NpcLifecycleService
                    .StaleLifecycleRegistrationException expected){
                expectedRejection=true;
                check(expected.npcId.equals(selected.id),
                    "typed stale failure refers to selected lifecycle");
            }

            NpcLifecycleService.Snapshot nowSelected=
                lifecycle.get(selected.id);
            NpcLifecycleService.Snapshot nowOther=
                lifecycle.get(other.id);
            check(admissionCalls[0]==1&&
                    world.players().owns(player,generation)&&
                    world.npcs().byId(selected.id)==selected&&
                    nowSelected!=null&&nowOther!=null&&
                    world.pvmRecords().receiptCount()==0&&
                    world.groundItems().snapshot().isEmpty(),
                "admission seam completed with canonical owner intact");
            if(replaceSelected){
                check(expectedRejection&&accepted==null&&
                        nowSelected.hitpoints==20&&
                        nowSelected.registrationIdentity!=
                            priorSelected.registrationIdentity&&
                        nowOther.hitpoints==20,
                    "selected registration replacement rejected before HP");
            }else{
                check(!expectedRejection&&accepted!=null&&
                        accepted.lifecycle.appliedDamage==10&&
                        accepted.lifecycle.hitpointsAfter==10&&
                        nowSelected.hitpoints==10&&
                        nowSelected.registrationIdentity==
                            priorSelected.registrationIdentity&&
                        nowOther.hitpoints==20&&
                        nowOther.registrationIdentity!=
                            priorOther.registrationIdentity,
                    "unrelated replacement did not veto current target hit");
            }
        }finally{
            if(generation>=0L&&world.players().owns(player,generation))
                world.unregisterPlayer(player,generation);
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        run(true);
        System.out.println(
            "G1929_NPC_LIFECYCLE_REPLACED_IN_OWNED_DAMAGE_ADMISSION_PASS"+
            " mutationAfterCanonicalLocks=true"+
            " selectedEqualValuedRegistrationReplaced=true"+
            " expectedIdentityCheckedUnderDamageLock=true"+
            " freshHpPreserved=true"+
            " noUnexpectedGrant=true");
    }

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G19.2.9 "+reason);
    }

    private G1929NpcLifecycleReplacedInOwnedDamageAdmissionTest(){}
}

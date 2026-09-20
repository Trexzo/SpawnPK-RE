package spk.local;

/**
 * Attack cadence and hit-delay authority boundary.
 *
 * Damage calculation and packet presentation are deliberately outside this
 * interface. Hit delay remains a LocalLab compatibility rule until exact
 * original-server timing is recovered.
 */
interface CombatAttackTimingRules {
    final class Request {
        final int weaponId;
        final CombatWeaponProfile profile;
        final V913WeaponRuntimeAuthority.Profile runtime;

        Request(
            int weaponId,
            CombatWeaponProfile profile,
            V913WeaponRuntimeAuthority.Profile runtime
        ){
            this.weaponId=weaponId;
            this.profile=profile;
            this.runtime=runtime;
        }
    }

    final class Result {
        final int attackSpeedTicks;
        final int hitDelayTicks;
        final String cadenceAuthority;
        final String hitDelayAuthority;
        final String hitDelayRule;

        Result(
            int attackSpeedTicks,
            int hitDelayTicks,
            String cadenceAuthority,
            String hitDelayAuthority,
            String hitDelayRule
        ){
            if(attackSpeedTicks<0)
                throw new IllegalArgumentException(
                    "attackSpeedTicks="+attackSpeedTicks
                );
            if(hitDelayTicks<0)
                throw new IllegalArgumentException(
                    "hitDelayTicks="+hitDelayTicks
                );

            this.attackSpeedTicks=attackSpeedTicks;
            this.hitDelayTicks=hitDelayTicks;
            this.cadenceAuthority=cadenceAuthority;
            this.hitDelayAuthority=hitDelayAuthority;
            this.hitDelayRule=hitDelayRule;
        }

        @Override public String toString(){
            return "CombatAttackTiming{speed="+
                attackSpeedTicks+
                ",hitDelay="+hitDelayTicks+
                ",cadenceAuthority="+cadenceAuthority+
                ",hitDelayAuthority="+hitDelayAuthority+
                ",hitDelayRule="+hitDelayRule+"}";
        }
    }

    Result resolve(Request request);

    static CombatAttackTimingRules recoveredCompatibility(){
        return RecoveredCombatAttackTimingRules.INSTANCE;
    }
}

final class RecoveredCombatAttackTimingRules
    implements CombatAttackTimingRules {

    static final RecoveredCombatAttackTimingRules INSTANCE=
        new RecoveredCombatAttackTimingRules();

    static final String HIT_DELAY_AUTHORITY=
        "CUSTOM_LOCALLAB_COMPATIBILITY";
    static final String HIT_DELAY_RULE=
        "CURRENT_BEHAVIOR_IMMEDIATE_0_TICKS";

    @Override public Result resolve(Request request){
        if(request==null)
            throw new NullPointerException("request");

        V913WeaponRuntimeAuthority.Profile runtime=
            request.runtime;
        CombatWeaponProfile profile=
            request.profile;

        int speed=0;
        String cadenceAuthority=
            "UNRESOLVED_SERVER_AUTHORITY";

        if(runtime!=null&&
           runtime.directBasicAttack&&
           runtime.speedTicks>0){
            speed=runtime.speedTicks;
            cadenceAuthority=
                "V9.13_DIRECT_RUNTIME";
        }else if(profile!=null&&
                 profile.attackSpeedTicks>0){
            speed=profile.attackSpeedTicks;
            cadenceAuthority=
                "PROFILE_"+profile.certainty;
        }

        return new Result(
            speed,
            0,
            cadenceAuthority,
            HIT_DELAY_AUTHORITY,
            HIT_DELAY_RULE
        );
    }

    private RecoveredCombatAttackTimingRules(){}
}

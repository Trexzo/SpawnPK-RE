package spk.local;

/**
 * Damage calculation boundary.
 *
 * Attack timing, presentation, routing and hit publication remain outside this
 * interface. Implementations must state provenance explicitly.
 */
interface CombatDamageRules {
    final class Request {
        final CombatContext context;
        final int weaponId;
        final CombatStyleRepository.Style style;
        final long worldTick;

        Request(
            CombatContext context,
            int weaponId,
            CombatStyleRepository.Style style,
            long worldTick
        ){
            this.context=context;
            this.weaponId=weaponId;
            this.style=style;
            this.worldTick=worldTick;
        }
    }

    final class Result {
        final int damage;
        final int maxHitReference;
        final String authority;
        final String formula;

        Result(
            int damage,
            int maxHitReference,
            String authority,
            String formula
        ){
            if(damage<0)
                throw new IllegalArgumentException("damage="+damage);
            if(maxHitReference<0)
                throw new IllegalArgumentException(
                    "maxHitReference="+maxHitReference
                );

            this.damage=damage;
            this.maxHitReference=maxHitReference;
            this.authority=authority;
            this.formula=formula;
        }

        @Override public String toString(){
            return "CombatDamage{damage="+damage+
                ",maxHitReference="+maxHitReference+
                ",authority="+authority+
                ",formula="+formula+"}";
        }
    }

    Result calculate(Request request);

    String authority();
    String formula();

    static CombatDamageRules dummyFixture(){
        return DummyFixtureCombatDamageRules.INSTANCE;
    }

    static CombatDamageRules localLabFallback(){
        return LocalLabFallbackCombatDamageRules.INSTANCE;
    }
}

/**
 * Historical M2 workbench behavior. Kept only for explicit tests/dev harnesses.
 */
final class DummyFixtureCombatDamageRules implements CombatDamageRules {
    static final DummyFixtureCombatDamageRules INSTANCE=
        new DummyFixtureCombatDamageRules();

    static final String AUTHORITY="LOCAL_DEV_FIXTURE";
    static final String FORMULA="LOCAL_M2_FIXED_DUMMY_HIT";

    @Override public Result calculate(Request request){
        int damage=
            request!=null&&
            request.context==CombatContext.PLAYER_PVP
                ?100
                :200;

        return new Result(
            damage,
            damage,
            AUTHORITY,
            FORMULA
        );
    }

    @Override public String authority(){
        return AUTHORITY;
    }

    @Override public String formula(){
        return FORMULA;
    }

    private DummyFixtureCombatDamageRules(){}
}

/**
 * Minimal functional private-server fallback.
 *
 * Exact SpawnPK accuracy/max-hit formulas are unknown. This deterministic
 * fallback deliberately does not infer them from client presentation data.
 */
final class LocalLabFallbackCombatDamageRules implements CombatDamageRules {
    static final LocalLabFallbackCombatDamageRules INSTANCE=
        new LocalLabFallbackCombatDamageRules();

    static final int DAMAGE=10;
    static final String AUTHORITY="CUSTOM_LOCALLAB";
    static final String FORMULA="CUSTOM_LOCALLAB_FLAT_10_V1";

    @Override public Result calculate(Request request){
        return new Result(
            DAMAGE,
            DAMAGE,
            AUTHORITY,
            FORMULA
        );
    }

    @Override public String authority(){
        return AUTHORITY;
    }

    @Override public String formula(){
        return FORMULA;
    }

    private LocalLabFallbackCombatDamageRules(){}
}

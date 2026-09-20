package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class SemanticTimedEffectFoundationTest {
    public static void main(String[] args){
        assertCatalog();
        assertLifecycle();
        assertDomainStateHasNoProtocolIdentity();

        System.out.println(
            "SEMANTIC_TIMED_EFFECT_FOUNDATION_PASS "+
            "catalog=64 "+
            "semanticState=true "+
            "replaceAtomic=true "+
            "coexist=true "+
            "expiryDeterministic=true "+
            "snapshotImmutable=true "+
            "protocolIdentityInState=false"
        );
    }

    private static void assertCatalog(){
        if(TimedEffectCatalog.size()!=64)
            throw new AssertionError(
                "catalog size="+
                TimedEffectCatalog.size()
            );

        assertDefinition(
            0,
            "DYNAMIC"
        );
        assertDefinition(
            11,
            "TELEBLOCK"
        );
        assertDefinition(
            18,
            "FREEZE"
        );
        assertDefinition(
            43,
            "RAID_BONUS"
        );
        assertDefinition(
            62,
            "WEREWOLF_FRENZY"
        );
        assertDefinition(
            63,
            "VAMPYRE_FOCUS"
        );

        TimedEffectCatalog.Definition teleblock=
            TimedEffectCatalog.require(
                "teleblock"
            );

        if(!"Teleblock duration".equals(
                teleblock.description))
            throw new AssertionError(
                "teleblock description="+
                teleblock.description
            );

        if(!Arrays.equals(
                new int[]{9,2},
                teleblock.rawInts()))
            throw new AssertionError(
                "teleblock raw ints="+
                Arrays.toString(
                    teleblock.rawInts()
                )
            );

        if(!TimedEffectCatalog
                .PRESENTATION_AUTHORITY
                .equals(
                    teleblock.presentationAuthority))
            throw new AssertionError(
                "catalog authority="+
                teleblock.presentationAuthority
            );

        boolean immutable=false;

        try{
            TimedEffectCatalog.all()
                .clear();
        }catch(UnsupportedOperationException expected){
            immutable=true;
        }

        if(!immutable)
            throw new AssertionError(
                "catalog list mutable"
            );

        boolean unknownRejected=false;

        try{
            TimedEffectCatalog.require(
                "NOT_A_REAL_EFFECT"
            );
        }catch(IllegalArgumentException expected){
            unknownRejected=true;
        }

        if(!unknownRejected)
            throw new AssertionError(
                "unknown fixed effect accepted"
            );
    }

    private static void assertLifecycle(){
        SemanticTimedEffectService service=
            new SemanticTimedEffectService();

        SemanticTimedEffectService.ActiveEffect
            teleblock=
                service.applyFixed(
                    "TELEBLOCK",
                    10,
                    100,
                    "CUSTOM_LOCALLAB"
                );

        if(!"TELEBLOCK".equals(
                teleblock.key)||
           teleblock.appliedTick!=100||
           teleblock.expiresAtTick!=110||
           teleblock.durationTicks()!=10)
            throw new AssertionError(
                "initial teleblock="+
                teleblock
            );

        service.applyFixed(
            "STAMINA",
            20,
            100,
            "CUSTOM_LOCALLAB"
        );

        if(service.size()!=2)
            throw new AssertionError(
                "coexist size="+
                service.size()
            );

        SemanticTimedEffectService.ActiveEffect
            replaced=
                service.applyFixed(
                    "teleblock",
                    30,
                    105,
                    "CUSTOM_LOCALLAB"
                );

        if(service.size()!=2||
           replaced.appliedTick!=105||
           replaced.expiresAtTick!=135)
            throw new AssertionError(
                "replace failed="+
                replaced+
                " size="+
                service.size()
            );

        if(service.tick(119).changed())
            throw new AssertionError(
                "effect expired early"
            );

        SemanticTimedEffectService.TickResult
            tick120=
                service.tick(120);

        if(!tick120.changed()||
           !tick120.expiredKeys.equals(
               Collections.singletonList(
                   "STAMINA"
               )))
            throw new AssertionError(
                "tick120 expired="+
                tick120.expiredKeys
            );

        if(!service.active(
                "TELEBLOCK")||
           service.active(
                "STAMINA"))
            throw new AssertionError(
                "post-expiry active state wrong"
            );

        if(service.tick(134).changed())
            throw new AssertionError(
                "replacement expired early"
            );

        SemanticTimedEffectService.TickResult
            tick135=
                service.tick(135);

        if(!tick135.expiredKeys.equals(
                Collections.singletonList(
                    "TELEBLOCK"
                ))||
           service.size()!=0)
            throw new AssertionError(
                "replacement expiry wrong "+
                tick135.expiredKeys
            );

        service.applyFixed(
            "FREEZE",
            5,
            200,
            "CUSTOM_LOCALLAB"
        );

        List<SemanticTimedEffectService.ActiveEffect>
            snapshot=
                service.snapshot();

        boolean snapshotImmutable=false;

        try{
            snapshot.clear();
        }catch(UnsupportedOperationException expected){
            snapshotImmutable=true;
        }

        if(!snapshotImmutable)
            throw new AssertionError(
                "active snapshot mutable"
            );

        if(service.remove(
                "freeze")==null||
           service.size()!=0)
            throw new AssertionError(
                "remove failed"
            );

        expectInvalid(
            ()->service.applyFixed(
                "DYNAMIC",
                1,
                0,
                "CUSTOM_LOCALLAB"
            )
        );

        expectInvalid(
            ()->service.applyFixed(
                "NOT_A_REAL_EFFECT",
                1,
                0,
                "CUSTOM_LOCALLAB"
            )
        );

        expectInvalid(
            ()->service.applyFixed(
                "TELEBLOCK",
                0,
                0,
                "CUSTOM_LOCALLAB"
            )
        );

        expectInvalid(
            ()->service.applyFixed(
                "TELEBLOCK",
                1,
                0,
                " "
            )
        );

        expectInvalid(
            ()->service.applyFixed(
                "TELEBLOCK",
                Long.MAX_VALUE,
                1,
                "CUSTOM_LOCALLAB"
            )
        );
    }

    private static void assertDomainStateHasNoProtocolIdentity(){
        for(Field field:
                SemanticTimedEffectService
                    .ActiveEffect.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("opcode")||
               name.contains("target")||
               name.contains("ordinal")||
               name.contains("packet")||
               name.contains("subtype"))
                throw new AssertionError(
                    "protocol identity leaked into active state: "+
                    field.getName()
                );
        }
    }

    private static void assertDefinition(
        int ordinal,
        String key
    ){
        TimedEffectCatalog.Definition definition=
            TimedEffectCatalog.byOrdinal(
                ordinal
            );

        if(definition==null||
           !key.equals(
               definition.key)||
           TimedEffectCatalog.byKey(
               key)!=definition)
            throw new AssertionError(
                "catalog mapping ordinal="+
                ordinal+
                " key="+key+
                " got="+definition
            );
    }

    private static void expectInvalid(
        Runnable action
    ){
        boolean failed=false;

        try{
            action.run();
        }catch(IllegalArgumentException expected){
            failed=true;
        }

        if(!failed)
            throw new AssertionError(
                "invalid timed effect operation accepted"
            );
    }
}

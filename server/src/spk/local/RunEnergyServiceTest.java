package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class RunEnergyServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_RUN_ENERGY_TEST";

    public static void main(String[] args){
        WorldPlayer player=
            new WorldPlayer();
        MovementState movement=
            player.movement();
        movement.setPersistentRun(true);

        RunEnergyService service=
            new RunEnergyService(
                player,
                AUTHORITY
            );

        RunEnergyService.Snapshot initial=
            service.snapshot();

        require(
            initial.energy==100&&
            initial.maximum==100&&
            initial.persistentRunEnabled&&
            AUTHORITY.equals(initial.policyAuthority),
            "initial snapshot"
        );

        require(
            service.canAfford(100)&&
            service.canAfford(1),
            "canAfford initial"
        );

        RunEnergyService.SpendResult spent=
            service.trySpend(35);

        require(
            spent.spent()&&
            spent.requested==35&&
            spent.applied==35&&
            spent.before==100&&
            spent.after==65&&
            movement.runEnergy()==65&&
            movement.persistentRun(),
            "spend"
        );

        require(
            !service.canAfford(66)&&
            service.canAfford(65),
            "canAfford after spend"
        );

        RunEnergyService.SpendResult insufficient=
            service.trySpend(80);

        require(
            !insufficient.spent()&&
            insufficient.status==
                RunEnergyService.SpendStatus
                    .INSUFFICIENT_ENERGY&&
            insufficient.applied==0&&
            insufficient.before==65&&
            insufficient.after==65&&
            movement.runEnergy()==65&&
            movement.persistentRun(),
            "insufficient atomic"
        );

        RunEnergyService.RestoreResult restored=
            service.restore(20);

        require(
            restored.requested==20&&
            restored.applied==20&&
            restored.before==65&&
            restored.after==85&&
            movement.runEnergy()==85&&
            movement.persistentRun(),
            "restore"
        );

        RunEnergyService.RestoreResult clamped=
            service.restore(50);

        require(
            clamped.requested==50&&
            clamped.applied==15&&
            clamped.after==100&&
            movement.runEnergy()==100,
            "restore clamp"
        );

        RunEnergyService.RestoreResult full=
            service.restore(Integer.MAX_VALUE);

        require(
            full.applied==0&&
            full.before==100&&
            full.after==100,
            "full restore no-op"
        );

        movement.setRunEnergy(0);

        require(
            !service.canAfford(1),
            "zero energy affordability"
        );

        boolean runBefore=movement.persistentRun();

        RunEnergyService.SpendResult zeroInsufficient=
            service.trySpend(1);

        require(
            !zeroInsufficient.spent()&&
            movement.runEnergy()==0&&
            movement.persistentRun()==runBefore,
            "zero energy spend changed run toggle"
        );

        invalidInputsAtomic(service,movement);
        authorityGuards(player);
        boundaryGuard();

        System.out.println(
            "RUN_ENERGY_SERVICE_PASS "+
            "canonicalMovementState=true "+
            "spend=true "+
            "insufficientAtomic=true "+
            "restore=true "+
            "restoreClamped=true "+
            "canAfford=true "+
            "runToggleUntouched=true "+
            "callerDrainOwned=true "+
            "regenOwned=false "+
            "movementCadenceOwned=false "+
            "packet110Owned=false "+
            "persistenceOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void invalidInputsAtomic(
        RunEnergyService service,
        MovementState movement
    ){
        movement.setRunEnergy(40);
        boolean runBefore=movement.persistentRun();

        expect(
            IllegalArgumentException.class,
            ()->service.trySpend(0),
            "zero spend"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.trySpend(101),
            "oversized spend"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.canAfford(0),
            "zero afford"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.restore(0),
            "zero restore"
        );

        require(
            movement.runEnergy()==40&&
            movement.persistentRun()==runBefore,
            "invalid input mutated canonical movement"
        );
    }

    private static void authorityGuards(
        WorldPlayer player
    ){
        expect(
            IllegalArgumentException.class,
            ()->new RunEnergyService(
                player,
                "EXACT_CURRENT_CLIENT"
            ),
            "client authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new RunEnergyService(
                player,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority"
        );
    }

    private static void boundaryGuard(){
        for(Class<?> type:new Class<?>[]{
                RunEnergyService.class,
                RunEnergyService.Snapshot.class,
                RunEnergyService.SpendResult.class,
                RunEnergyService.RestoreResult.class
        }){
            for(Field field:type.getDeclaredFields()){
                String haystack=
                    (field.getName()+" "+
                     field.getType().getName())
                        .toLowerCase(Locale.ROOT);

                for(String forbidden:new String[]{
                        "packet",
                        "opcode",
                        "widget",
                        "agility",
                        "weight",
                        "stamina",
                        "regen",
                        "tilecost"
                }){
                    require(
                        !haystack.contains(forbidden),
                        "unowned identity leaked through "+
                        type.getSimpleName()+"."+
                        field.getName()
                    );
                }
            }
        }

        for(Method method:
                RunEnergyService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            for(String forbidden:new String[]{
                    "packet",
                    "publish",
                    "persist",
                    "regenerate",
                    "advance",
                    "movementtick",
                    "agility",
                    "weight"
            }){
                require(
                    !name.contains(forbidden),
                    "unowned behavior leaked through "+
                    method.getName()
                );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private RunEnergyServiceTest(){}
}

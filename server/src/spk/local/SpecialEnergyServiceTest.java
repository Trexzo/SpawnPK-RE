package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class SpecialEnergyServiceTest {
    private static final String AUTHORITY =
        "CUSTOM_LOCALLAB_SPECIAL_ENERGY_TEST";

    public static void main(String[] args) {
        WorldPlayer owner =
            new WorldPlayer();
        PlayerState player =
            owner.playerState();

        SpecialEnergyService service =
            new SpecialEnergyService(
                owner,
                AUTHORITY
            );

        SpecialEnergyService.Snapshot initial =
            service.snapshot();

        require(
            initial.energy == 100 &&
            initial.maximum == 100 &&
            AUTHORITY.equals(
                initial.policyAuthority
            ),
            "initial special energy"
        );

        SpecialEnergyService.SpendResult spent =
            service.trySpend(
                30
            );

        require(
            spent.spent() &&
            spent.status ==
                SpecialEnergyService
                    .SpendStatus.SPENT &&
            spent.requested == 30 &&
            spent.applied == 30 &&
            spent.before == 100 &&
            spent.after == 70 &&
            player.specialEnergy() == 70,
            "special-energy spend"
        );

        SpecialEnergyService.SpendResult insufficient =
            service.trySpend(
                80
            );

        require(
            !insufficient.spent() &&
            insufficient.status ==
                SpecialEnergyService
                    .SpendStatus.INSUFFICIENT_ENERGY &&
            insufficient.requested == 80 &&
            insufficient.applied == 0 &&
            insufficient.before == 70 &&
            insufficient.after == 70 &&
            player.specialEnergy() == 70,
            "insufficient spend atomicity"
        );

        int beforeInvalid =
            player.specialEnergy();

        expect(
            IllegalArgumentException.class,
            () -> service.trySpend(
                0
            ),
            "zero cost"
        );

        expect(
            IllegalArgumentException.class,
            () -> service.trySpend(
                101
            ),
            "cost above maximum"
        );

        require(
            player.specialEnergy() ==
                beforeInvalid,
            "invalid spend mutated energy"
        );

        SpecialEnergyService.RestoreResult restore =
            service.restore(
                20
            );

        require(
            restore.requested == 20 &&
            restore.applied == 20 &&
            restore.before == 70 &&
            restore.after == 90 &&
            player.specialEnergy() == 90,
            "special-energy restore"
        );

        SpecialEnergyService.RestoreResult clamped =
            service.restore(
                50
            );

        require(
            clamped.requested == 50 &&
            clamped.applied == 10 &&
            clamped.before == 90 &&
            clamped.after == 100 &&
            player.specialEnergy() == 100,
            "special-energy clamp"
        );

        SpecialEnergyService.RestoreResult alreadyFull =
            service.restore(
                Integer.MAX_VALUE
            );

        require(
            alreadyFull.requested ==
                Integer.MAX_VALUE &&
            alreadyFull.applied == 0 &&
            alreadyFull.before == 100 &&
            alreadyFull.after == 100 &&
            player.specialEnergy() == 100,
            "full restore no-op"
        );

        player.setSpecialEnergy(
            40
        );

        require(
            service.snapshot().energy == 40,
            "service is not reading canonical PlayerState"
        );

        SpecialEnergyService.SpendResult externalStateSpend =
            service.trySpend(
                15
            );

        require(
            externalStateSpend.before == 40 &&
            externalStateSpend.after == 25 &&
            player.specialEnergy() == 25,
            "external canonical state not respected"
        );

        int beforeInvalidRestore =
            player.specialEnergy();

        expect(
            IllegalArgumentException.class,
            () -> service.restore(
                0
            ),
            "zero restore"
        );

        require(
            player.specialEnergy() ==
                beforeInvalidRestore,
            "invalid restore mutated energy"
        );

        authorityGuards(
            owner
        );
        boundaryGuard();

        System.out.println(
            "SPECIAL_ENERGY_SERVICE_PASS " +
            "canonicalPlayerState=true " +
            "spend=true " +
            "insufficientAtomic=true " +
            "restore=true " +
            "restoreClamped=true " +
            "requestedAppliedFacts=true " +
            "energyRange0to100=true " +
            "callerCostOwned=true " +
            "mechanicOwned=false " +
            "regenOwned=false " +
            "packetOwned=false " +
            "persistenceOwned=false " +
            "protocolIndependent=true"
        );
    }

    private static void authorityGuards(
        WorldPlayer owner
    ) {
        expect(
            IllegalArgumentException.class,
            () -> new SpecialEnergyService(
                owner,
                "EXACT_CURRENT_CLIENT"
            ),
            "client authority"
        );

        expect(
            IllegalArgumentException.class,
            () -> new SpecialEnergyService(
                owner,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority"
        );
    }

    private static void boundaryGuard() {
        for (Class<?> type :
                new Class<?>[]{
                    SpecialEnergyService.class,
                    SpecialEnergyService
                        .Snapshot.class,
                    SpecialEnergyService
                        .SpendResult.class,
                    SpecialEnergyService
                        .RestoreResult.class
                }) {

            for (Field field :
                    type.getDeclaredFields()) {

                String haystack =
                    (
                        field.getName() +
                        " " +
                        field.getType().getName()
                    ).toLowerCase(
                        Locale.ROOT
                    );

                for (String forbidden :
                        new String[]{
                            "packet",
                            "opcode",
                            "widget",
                            "weapon",
                            "damage",
                            "accuracy",
                            "cooldown",
                            "regen"
                        }) {

                    require(
                        !haystack.contains(
                            forbidden
                        ),
                        "unowned identity leaked through " +
                        type.getSimpleName() +
                        "." +
                        field.getName()
                    );
                }
            }
        }

        for (Method method :
                SpecialEnergyService.class
                    .getDeclaredMethods()) {

            String name =
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            for (String forbidden :
                    new String[]{
                        "packet",
                        "publish",
                        "persist",
                        "weapon",
                        "damage",
                        "accuracy",
                        "cooldown",
                        "regenerate"
                    }) {

                require(
                    !name.contains(
                        forbidden
                    ),
                    "unowned behavior leaked through method " +
                    method.getName()
                );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ) {
        try {
            action.run();
        } catch (
            Throwable failure
        ) {
            if (type.isInstance(
                    failure)) {
                return;
            }

            throw new AssertionError(
                label +
                " wrong failure " +
                failure,
                failure
            );
        }

        throw new AssertionError(
            label +
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ) {
        if (!condition) {
            throw new AssertionError(
                label
            );
        }
    }

    private SpecialEnergyServiceTest() {}
}

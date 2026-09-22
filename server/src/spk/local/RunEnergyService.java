package spk.local;

import java.util.Objects;

/**
 * Protocol-independent spend/restore semantics over MovementState's canonical
 * 0..100 run-energy resource.
 *
 * Drain-per-tile, regeneration and movement-cadence policy remain external.
 */
final class RunEnergyService {
    static final int MAX_ENERGY=100;

    enum SpendStatus {
        SPENT,
        INSUFFICIENT_ENERGY
    }

    static final class Snapshot {
        final int energy;
        final int maximum;
        final boolean persistentRunEnabled;
        final String policyAuthority;

        private Snapshot(
            int energy,
            boolean persistentRunEnabled,
            String policyAuthority
        ){
            this.energy=energy;
            this.maximum=MAX_ENERGY;
            this.persistentRunEnabled=persistentRunEnabled;
            this.policyAuthority=policyAuthority;
        }
    }

    static final class SpendResult {
        final SpendStatus status;
        final int requested;
        final int applied;
        final int before;
        final int after;
        final boolean persistentRunEnabled;

        private SpendResult(
            SpendStatus status,
            int requested,
            int applied,
            int before,
            int after,
            boolean persistentRunEnabled
        ){
            this.status=Objects.requireNonNull(status,"status");
            this.requested=requested;
            this.applied=applied;
            this.before=before;
            this.after=after;
            this.persistentRunEnabled=persistentRunEnabled;
        }

        boolean spent(){
            return status==SpendStatus.SPENT;
        }
    }

    static final class RestoreResult {
        final int requested;
        final int applied;
        final int before;
        final int after;
        final boolean persistentRunEnabled;

        private RestoreResult(
            int requested,
            int applied,
            int before,
            int after,
            boolean persistentRunEnabled
        ){
            this.requested=requested;
            this.applied=applied;
            this.before=before;
            this.after=after;
            this.persistentRunEnabled=persistentRunEnabled;
        }
    }

    private final MovementState movement;
    private final String policyAuthority;

    RunEnergyService(
        MovementState movement,
        String policyAuthority
    ){
        this.movement=Objects.requireNonNull(movement,"movement");
        this.policyAuthority=requireGameplayAuthority(policyAuthority);
    }

    synchronized Snapshot snapshot(){
        return new Snapshot(
            currentEnergy(),
            movement.persistentRun(),
            policyAuthority
        );
    }

    synchronized boolean canAfford(int cost){
        validateCost(cost);
        return currentEnergy()>=cost;
    }

    synchronized SpendResult trySpend(int cost){
        validateCost(cost);

        int before=currentEnergy();
        boolean runEnabled=movement.persistentRun();

        if(before<cost){
            return new SpendResult(
                SpendStatus.INSUFFICIENT_ENERGY,
                cost,
                0,
                before,
                before,
                runEnabled
            );
        }

        int after=before-cost;
        movement.setRunEnergy(after);

        if(currentEnergy()!=after)
            throw new IllegalStateException(
                "run-energy spend write mismatch expected="+
                after+" actual="+currentEnergy()
            );

        if(movement.persistentRun()!=runEnabled)
            throw new IllegalStateException(
                "run-energy spend changed persistent run state"
            );

        return new SpendResult(
            SpendStatus.SPENT,
            cost,
            cost,
            before,
            after,
            runEnabled
        );
    }

    synchronized RestoreResult restore(int amount){
        if(amount<=0)
            throw new IllegalArgumentException(
                "run-energy restore must be positive amount="+amount
            );

        int before=currentEnergy();
        boolean runEnabled=movement.persistentRun();

        long candidate=(long)before+(long)amount;
        int after=candidate>=MAX_ENERGY
            ?MAX_ENERGY
            :(int)candidate;
        int applied=after-before;

        movement.setRunEnergy(after);

        if(currentEnergy()!=after)
            throw new IllegalStateException(
                "run-energy restore write mismatch expected="+
                after+" actual="+currentEnergy()
            );

        if(movement.persistentRun()!=runEnabled)
            throw new IllegalStateException(
                "run-energy restore changed persistent run state"
            );

        return new RestoreResult(
            amount,
            applied,
            before,
            after,
            runEnabled
        );
    }

    String policyAuthority(){
        return policyAuthority;
    }

    private int currentEnergy(){
        int energy=movement.runEnergy();

        if(energy<0||energy>MAX_ENERGY)
            throw new IllegalStateException(
                "MovementState run energy outside 0.."+
                MAX_ENERGY+" actual="+energy
            );

        return energy;
    }

    private static void validateCost(int cost){
        if(cost<=0||cost>MAX_ENERGY)
            throw new IllegalArgumentException(
                "run-energy cost="+cost+
                " expected=1.."+MAX_ENERGY
            );
    }

    private static String requireGameplayAuthority(String value){
        if(value==null)
            throw new NullPointerException("policyAuthority");

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "policyAuthority blank"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define run-energy gameplay policy actual="+
                clean
            );

        return clean;
    }
}

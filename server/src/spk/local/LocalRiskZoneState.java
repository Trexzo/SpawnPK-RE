package spk.local;

import java.util.Objects;

/**
 * Session-owned LocalLab PvP risk state.
 *
 * This is explicit gameplay policy, not recovered original SpawnPK wilderness
 * or death-rule authority.
 */
final class LocalRiskZoneState {
    static final String POLICY_AUTHORITY=
        "LOCAL_LAB_POLICY_PK_RISK_R1";

    enum State {
        SAFE,
        RISK
    }

    static final class Snapshot {
        final State state;
        final TeleportNavigationService.EntryKind sourceKind;
        final long revision;

        Snapshot(
            State state,
            TeleportNavigationService.EntryKind sourceKind,
            long revision
        ){
            this.state=state;
            this.sourceKind=sourceKind;
            this.revision=revision;
        }

        boolean risk(){
            return state==State.RISK;
        }
    }

    private State state=State.SAFE;
    private TeleportNavigationService.EntryKind sourceKind;
    private long revision;

    synchronized void onSuccessfulTeleport(
        TeleportNavigationService.EntryKind kind
    ){
        TeleportNavigationService.EntryKind checked=
            Objects.requireNonNull(
                kind,
                "kind"
            );

        if(checked==TeleportNavigationService.EntryKind.HOUSE)
            throw new IllegalArgumentException(
                "failed/unconfigured HOUSE cannot mutate risk"
            );

        State next=
            checked==TeleportNavigationService.EntryKind.PK||
            checked==TeleportNavigationService.EntryKind.BOUNTY
                ?State.RISK
                :State.SAFE;

        transition(
            next,
            checked
        );
    }

    synchronized void returnHome(){
        transition(
            State.SAFE,
            TeleportNavigationService.EntryKind.HOME
        );
    }

    synchronized Snapshot snapshot(){
        return new Snapshot(
            state,
            sourceKind,
            revision
        );
    }

    synchronized boolean risk(){
        return state==State.RISK;
    }

    private void transition(
        State next,
        TeleportNavigationService.EntryKind kind
    ){
        state=Objects.requireNonNull(next,"next");
        sourceKind=kind;
        revision=
            Math.addExact(
                revision,
                1L
            );
    }
}

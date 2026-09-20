package spk.local;

import java.util.*;

/**
 * Explicit integration boundary between combat and adjacent player systems.
 *
 * Exact SpawnPK prayer modifiers, spell-combat effects, special-attack costs/
 * effects and status-combat modifiers are not recovered. The production LocalLab
 * hook therefore exposes canonical state and provenance but applies no guessed
 * mechanics.
 */
interface CombatSystemHooks {
    final class Snapshot {
        final int activePrayerCount;
        final String prayerBook;
        final String magicBook;
        final int specialEnergy;
        final List<PlayerStatusState.Effect> statuses;

        final boolean prayerModifierApplied;
        final boolean magicEffectApplied;
        final boolean specialEffectApplied;
        final boolean statusModifierApplied;

        final String prayerAuthority;
        final String magicAuthority;
        final String specialAuthority;
        final String statusAuthority;

        Snapshot(
            int activePrayerCount,
            String prayerBook,
            String magicBook,
            int specialEnergy,
            List<PlayerStatusState.Effect> statuses,
            boolean prayerModifierApplied,
            boolean magicEffectApplied,
            boolean specialEffectApplied,
            boolean statusModifierApplied,
            String prayerAuthority,
            String magicAuthority,
            String specialAuthority,
            String statusAuthority
        ){
            this.activePrayerCount=activePrayerCount;
            this.prayerBook=prayerBook;
            this.magicBook=magicBook;
            this.specialEnergy=specialEnergy;
            this.statuses=Collections.unmodifiableList(
                new ArrayList<>(statuses)
            );
            this.prayerModifierApplied=prayerModifierApplied;
            this.magicEffectApplied=magicEffectApplied;
            this.specialEffectApplied=specialEffectApplied;
            this.statusModifierApplied=statusModifierApplied;
            this.prayerAuthority=prayerAuthority;
            this.magicAuthority=magicAuthority;
            this.specialAuthority=specialAuthority;
            this.statusAuthority=statusAuthority;
        }

        boolean anyModifierApplied(){
            return prayerModifierApplied||
                magicEffectApplied||
                specialEffectApplied||
                statusModifierApplied;
        }

        @Override public String toString(){
            return "CombatSystemHooks{prayers="+
                activePrayerCount+
                "@"+prayerBook+
                ",magic="+magicBook+
                ",specialEnergy="+specialEnergy+
                ",statuses="+statuses.size()+
                ",modifierApplied="+anyModifierApplied()+
                ",prayerAuthority="+prayerAuthority+
                ",magicAuthority="+magicAuthority+
                ",specialAuthority="+specialAuthority+
                ",statusAuthority="+statusAuthority+"}";
        }
    }

    Snapshot beforeDamage(
        CombatContext context,
        int weaponId,
        long worldTick
    );

    static CombatSystemHooks none(){
        return NoCombatSystemHooks.INSTANCE;
    }

    static CombatSystemHooks forPlayer(
        WorldPlayer player
    ){
        return new WorldPlayerCombatSystemHooks(
            player
        );
    }
}

final class NoCombatSystemHooks
    implements CombatSystemHooks {

    static final NoCombatSystemHooks INSTANCE=
        new NoCombatSystemHooks();

    @Override public Snapshot beforeDamage(
        CombatContext context,
        int weaponId,
        long worldTick
    ){
        return new Snapshot(
            0,
            "UNBOUND",
            "UNBOUND",
            100,
            Collections.emptyList(),
            false,
            false,
            false,
            false,
            "UNKNOWN_SERVER_AUTHORITY",
            "UNKNOWN_SERVER_AUTHORITY",
            "UNKNOWN_SERVER_AUTHORITY",
            "UNKNOWN_SERVER_AUTHORITY"
        );
    }

    private NoCombatSystemHooks(){}
}

final class WorldPlayerCombatSystemHooks
    implements CombatSystemHooks {

    static final String UNKNOWN=
        "UNKNOWN_SERVER_AUTHORITY";

    private final WorldPlayer player;

    WorldPlayerCombatSystemHooks(
        WorldPlayer player
    ){
        this.player=
            Objects.requireNonNull(
                player,
                "player"
            );
    }

    @Override public Snapshot beforeDamage(
        CombatContext context,
        int weaponId,
        long worldTick
    ){
        synchronized(player.mutationLock()){
            PrayerState prayers=
                player.prayers();
            MagicState magic=
                player.magic();
            PlayerState state=
                player.playerState();
            PlayerStatusState statuses=
                player.statusState();

            // Integration point only. The client proves these state surfaces,
            // not the original server's combat mathematics/effects.
            return new Snapshot(
                prayers.activeCount(),
                prayers.book().name(),
                magic.book().name(),
                state.specialEnergy(),
                new ArrayList<>(
                    statuses.snapshot()
                ),
                false,
                false,
                false,
                false,
                UNKNOWN,
                UNKNOWN,
                UNKNOWN,
                UNKNOWN
            );
        }
    }
}

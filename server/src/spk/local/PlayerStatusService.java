package spk.local;

import java.util.*;

/**
 * Timed status scheduler/integration point.
 *
 * Expiry timing is functional LocalLab authority. Poison/venom/sicken damage,
 * stat modifiers and other original-server effects remain UNKNOWN_SERVER_AUTHORITY.
 */
final class PlayerStatusService {
    static final String EXPIRY_AUTHORITY="CUSTOM_LOCALLAB";
    static final String EFFECT_MECHANICS_AUTHORITY="UNKNOWN_SERVER_AUTHORITY";

    static final class TickResult {
        final List<PlayerStatusState.Type> expired;

        TickResult(List<PlayerStatusState.Type> expired){
            this.expired=Collections.unmodifiableList(
                new ArrayList<>(expired)
            );
        }

        boolean changed(){
            return !expired.isEmpty();
        }

        @Override public String toString(){
            return "StatusTick{expired="+expired+
                ",expiryAuthority="+EXPIRY_AUTHORITY+
                ",effectMechanics="+EFFECT_MECHANICS_AUTHORITY+"}";
        }
    }

    private final WorldPlayer player;
    private final PlayerState playerState;
    private final PlayerStatusState statusState;

    PlayerStatusService(WorldPlayer player){
        this.player=Objects.requireNonNull(player,"player");
        this.playerState=player.playerState();
        this.statusState=player.statusState();
    }

    PlayerStatusState.Effect apply(
        PlayerStatusState.Type type,
        int magnitude,
        long durationTicks,
        long worldTick,
        String authority
    ){
        Objects.requireNonNull(type,"type");

        if(magnitude<=0)
            throw new IllegalArgumentException(
                "magnitude="+magnitude
            );
        if(durationTicks<=0)
            throw new IllegalArgumentException(
                "durationTicks="+durationTicks
            );

        String provenance=
            authority==null||authority.isEmpty()
                ?"UNSPECIFIED_CUSTOM_SOURCE"
                :authority;

        synchronized(player.mutationLock()){
            PlayerStatusState.Effect effect=
                new PlayerStatusState.Effect(
                    type,
                    magnitude,
                    worldTick,
                    worldTick+durationTicks,
                    provenance
                );

            statusState.put(effect);
            syncValues();
            return effect;
        }
    }

    TickResult tick(long worldTick){
        synchronized(player.mutationLock()){
            ArrayList<PlayerStatusState.Type> expired=
                new ArrayList<>();

            for(PlayerStatusState.Type type:
                    PlayerStatusState.Type.values()){
                PlayerStatusState.Effect effect=
                    statusState.get(type);

                if(effect!=null&&
                   worldTick>=effect.expiresAtTick){
                    statusState.remove(type);
                    expired.add(type);
                }
            }

            if(!expired.isEmpty())
                syncValues();

            return new TickResult(expired);
        }
    }

    boolean clearAll(){
        synchronized(player.mutationLock()){
            boolean changed=!statusState.empty()||
                playerState.poison()!=0||
                playerState.venom()!=0||
                playerState.sicken()!=0;

            statusState.clear();
            playerState.setNegativeEffects(0,0,0);
            return changed;
        }
    }

    private void syncValues(){
        playerState.setNegativeEffects(
            magnitude(PlayerStatusState.Type.POISON),
            magnitude(PlayerStatusState.Type.VENOM),
            magnitude(PlayerStatusState.Type.SICKEN)
        );
    }

    private int magnitude(PlayerStatusState.Type type){
        PlayerStatusState.Effect effect=
            statusState.get(type);
        return effect==null?0:effect.magnitude;
    }
}

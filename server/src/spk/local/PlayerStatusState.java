package spk.local;

import java.util.*;

/**
 * Canonical timed player-status metadata.
 *
 * Magnitudes are mirrored into PlayerState for existing client/persistence
 * surfaces. Damage/effect formulas remain outside this state.
 */
final class PlayerStatusState {
    enum Type {
        POISON,
        VENOM,
        SICKEN
    }

    static final class Effect {
        final Type type;
        final int magnitude;
        final long appliedTick;
        final long expiresAtTick;
        final String authority;

        Effect(
            Type type,
            int magnitude,
            long appliedTick,
            long expiresAtTick,
            String authority
        ){
            this.type=type;
            this.magnitude=magnitude;
            this.appliedTick=appliedTick;
            this.expiresAtTick=expiresAtTick;
            this.authority=authority;
        }

        @Override public String toString(){
            return "Effect{type="+type+
                ",magnitude="+magnitude+
                ",appliedTick="+appliedTick+
                ",expiresAtTick="+expiresAtTick+
                ",authority="+authority+"}";
        }
    }

    private final EnumMap<Type,Effect> active=
        new EnumMap<>(Type.class);

    Effect get(Type type){
        return active.get(type);
    }

    boolean active(Type type){
        return active.containsKey(type);
    }

    Collection<Effect> snapshot(){
        return Collections.unmodifiableList(
            new ArrayList<>(active.values())
        );
    }

    void put(Effect effect){
        active.put(effect.type,effect);
    }

    Effect remove(Type type){
        return active.remove(type);
    }

    void clear(){
        active.clear();
    }

    boolean empty(){
        return active.isEmpty();
    }
}

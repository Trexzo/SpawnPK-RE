package spk.local;

import java.util.*;

/**
 * Protocol-independent semantic timed-effect lifecycle.
 *
 * Active effects are identified by semantic catalog key. Client ordinals,
 * packet targets and presentation encodings are deliberately absent from
 * runtime state.
 */
final class SemanticTimedEffectService {
    static final class ActiveEffect {
        final String key;
        final long appliedTick;
        final long expiresAtTick;
        final String sourceAuthority;

        ActiveEffect(
            String key,
            long appliedTick,
            long expiresAtTick,
            String sourceAuthority
        ){
            this.key=key;
            this.appliedTick=appliedTick;
            this.expiresAtTick=expiresAtTick;
            this.sourceAuthority=
                sourceAuthority;
        }

        long durationTicks(){
            return expiresAtTick-
                appliedTick;
        }

        @Override public String toString(){
            return "TimedEffect{"+
                "key="+key+
                ",appliedTick="+
                    appliedTick+
                ",expiresAtTick="+
                    expiresAtTick+
                ",sourceAuthority="+
                    sourceAuthority+
                "}";
        }
    }

    static final class TickResult {
        final List<String> expiredKeys;

        TickResult(
            List<String> expiredKeys
        ){
            this.expiredKeys=
                Collections.unmodifiableList(
                    new ArrayList<>(
                        expiredKeys
                    )
                );
        }

        boolean changed(){
            return !expiredKeys.isEmpty();
        }
    }

    private final LinkedHashMap<String,ActiveEffect>
        active=
            new LinkedHashMap<>();

    synchronized ActiveEffect applyFixed(
        String key,
        long durationTicks,
        long worldTick,
        String sourceAuthority
    ){
        TimedEffectCatalog.Definition definition=
            TimedEffectCatalog.require(
                key
            );

        if("DYNAMIC".equals(
                definition.key))
            throw new IllegalArgumentException(
                "DYNAMIC requires a future explicit dynamic-effect contract"
            );

        if(durationTicks<=0)
            throw new IllegalArgumentException(
                "durationTicks="+
                durationTicks
            );

        if(worldTick<0)
            throw new IllegalArgumentException(
                "worldTick="+
                worldTick
            );

        String authority=
            requireAuthority(
                sourceAuthority
            );

        long expiresAtTick;

        try{
            expiresAtTick=
                Math.addExact(
                    worldTick,
                    durationTicks
                );
        }catch(ArithmeticException e){
            throw new IllegalArgumentException(
                "timed effect deadline overflow",
                e
            );
        }

        ActiveEffect effect=
            new ActiveEffect(
                definition.key,
                worldTick,
                expiresAtTick,
                authority
            );

        active.put(
            definition.key,
            effect
        );

        return effect;
    }

    synchronized ActiveEffect get(
        String key
    ){
        return active.get(
            TimedEffectCatalog
                .normalizeKey(key)
        );
    }

    synchronized boolean active(
        String key
    ){
        return get(key)!=null;
    }

    synchronized ActiveEffect remove(
        String key
    ){
        return active.remove(
            TimedEffectCatalog
                .normalizeKey(key)
        );
    }

    synchronized int size(){
        return active.size();
    }

    synchronized List<ActiveEffect> snapshot(){
        ArrayList<ActiveEffect> out=
            new ArrayList<>(
                active.values()
            );

        out.sort(
            Comparator.comparing(
                effect->
                    effect.key
            )
        );

        return Collections.unmodifiableList(
            out
        );
    }

    synchronized TickResult tick(
        long worldTick
    ){
        if(worldTick<0)
            throw new IllegalArgumentException(
                "worldTick="+
                worldTick
            );

        ArrayList<String> expired=
            new ArrayList<>();

        Iterator<Map.Entry<String,ActiveEffect>>
            iterator=
                active.entrySet()
                    .iterator();

        while(iterator.hasNext()){
            Map.Entry<String,ActiveEffect>
                entry=
                    iterator.next();

            if(worldTick>=
                    entry.getValue()
                        .expiresAtTick){
                expired.add(
                    entry.getKey()
                );
                iterator.remove();
            }
        }

        return new TickResult(
            expired
        );
    }

    synchronized void clear(){
        active.clear();
    }

    private static String requireAuthority(
        String authority
    ){
        if(authority==null||
           authority.trim().isEmpty())
            throw new IllegalArgumentException(
                "sourceAuthority"
            );

        return authority.trim();
    }
}

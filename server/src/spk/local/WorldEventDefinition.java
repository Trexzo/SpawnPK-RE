package spk.local;

import java.util.*;

/**
 * Immutable semantic global-event definition.
 *
 * World ticks and semantic phase keys are authoritative domain inputs. Client
 * controls, packet identities and presentation labels deliberately do not
 * belong here.
 */
final class WorldEventDefinition {
    static final class PhaseDefinition {
        final String key;
        final long startsAtTick;

        PhaseDefinition(String key,long startsAtTick){
            this.key=normalizePhaseKey(key);
            if(startsAtTick<0)
                throw new IllegalArgumentException(
                    "phase startsAtTick="+startsAtTick
                );
            this.startsAtTick=startsAtTick;
        }

        @Override public String toString(){
            return "PhaseDefinition{"+
                "key="+key+
                ",startsAtTick="+startsAtTick+
                "}";
        }
    }

    final WorldEventId id;
    final long startTick;
    final long endTick;
    final List<PhaseDefinition> phases;
    final String sourceAuthority;

    WorldEventDefinition(
        WorldEventId id,
        long startTick,
        long endTick,
        List<PhaseDefinition> phases,
        String sourceAuthority
    ){
        this.id=Objects.requireNonNull(id,"id");

        if(startTick<0)
            throw new IllegalArgumentException(
                "startTick="+startTick
            );

        if(endTick<=startTick)
            throw new IllegalArgumentException(
                "endTick="+endTick+
                " startTick="+startTick
            );

        this.startTick=startTick;
        this.endTick=endTick;
        this.sourceAuthority=requireAuthority(sourceAuthority);

        ArrayList<PhaseDefinition> copy=new ArrayList<>();
        HashSet<String> keys=new HashSet<>();
        long previous=Long.MIN_VALUE;

        if(phases!=null){
            for(PhaseDefinition phase:phases){
                PhaseDefinition value=
                    Objects.requireNonNull(phase,"phase");

                if(value.startsAtTick<startTick||
                   value.startsAtTick>=endTick)
                    throw new IllegalArgumentException(
                        "phase outside event window "+
                        value
                    );

                if(value.startsAtTick<=previous)
                    throw new IllegalArgumentException(
                        "phase ticks must be strictly increasing"
                    );

                if(!keys.add(value.key))
                    throw new IllegalArgumentException(
                        "duplicate phase key="+value.key
                    );

                copy.add(value);
                previous=value.startsAtTick;
            }
        }

        this.phases=Collections.unmodifiableList(copy);
    }

    int phaseIndexAt(long worldTick){
        int found=-1;

        for(int i=0;i<phases.size();i++){
            if(phases.get(i).startsAtTick>worldTick)
                break;
            found=i;
        }

        return found;
    }

    static String normalizePhaseKey(String key){
        Objects.requireNonNull(key,"key");

        String normalized=key.trim().toLowerCase(Locale.ROOT);

        if(normalized.isEmpty())
            throw new IllegalArgumentException("blank phase key");

        if(normalized.length()>128)
            throw new IllegalArgumentException("phase key too long");

        for(int i=0;i<normalized.length();i++){
            char c=normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||
                c=='_'||
                c=='-'||
                c==':';

            if(!ok)
                throw new IllegalArgumentException(
                    "invalid phase key="+key
                );
        }

        return normalized;
    }

    private static String requireAuthority(String authority){
        if(authority==null||authority.trim().isEmpty())
            throw new IllegalArgumentException("sourceAuthority");

        return authority.trim();
    }
}

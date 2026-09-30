package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Pure semantic drop resolution for one canonical NPC death.
 *
 * This service does not mutate GroundItemRegistry, inventory, rewards,
 * progression or respawn state. Caller-owned policy decides what drops resolve;
 * a later settlement adapter may materialize the immutable result.
 */
final class NpcDropResolutionService {
    static final class Drop {
        final int itemId;
        final int amount;

        Drop(
            int itemId,
            int amount
        ){
            if(itemId<0)
                throw new IllegalArgumentException(
                    "itemId="+itemId
                );
            if(amount<=0)
                throw new IllegalArgumentException(
                    "amount="+amount
                );

            this.itemId=itemId;
            this.amount=amount;
        }

        @Override public String toString(){
            return "Drop{item="+
                itemId+
                ",amount="+
                amount+
                "}";
        }
    }

    static final class DeathContext {
        final EntityId npcId;
        final int definitionId;
        final Tile deathTile;
        final long deathTick;
        final String lifecycleAuthority;
        final String recipientRef;

        private DeathContext(
            EntityId npcId,
            int definitionId,
            Tile deathTile,
            long deathTick,
            String lifecycleAuthority,
            String recipientRef
        ){
            this.npcId=npcId;
            this.definitionId=definitionId;
            this.deathTile=deathTile;
            this.deathTick=deathTick;
            this.lifecycleAuthority=lifecycleAuthority;
            this.recipientRef=recipientRef;
        }
    }

    static final class Resolution {
        final DeathContext context;
        final List<Drop> drops;
        final String dropAuthority;

        private Resolution(
            DeathContext context,
            List<Drop> drops,
            String dropAuthority
        ){
            this.context=context;
            this.drops=Collections.unmodifiableList(
                new ArrayList<>(
                    drops
                )
            );
            this.dropAuthority=dropAuthority;
        }
    }

    interface DropResolver {
        List<Drop> resolve(
            DeathContext context
        );

        String authority();
    }

    private final WorldNpcRegistry npcs;
    private final NpcLifecycleService lifecycle;
    private final DropResolver resolver;
    private final String dropAuthority;

    private final LinkedHashMap<EntityId,Resolution>
        resolved=
            new LinkedHashMap<>();

    NpcDropResolutionService(
        WorldNpcRegistry npcs,
        NpcLifecycleService lifecycle,
        DropResolver resolver
    ){
        this.npcs=
            Objects.requireNonNull(
                npcs,
                "npcs"
            );
        this.lifecycle=
            Objects.requireNonNull(
                lifecycle,
                "lifecycle"
            );
        this.resolver=
            Objects.requireNonNull(
                resolver,
                "resolver"
            );
        this.dropAuthority=
            requireGameplayAuthority(
                resolver.authority()
            );
    }

    boolean isBoundTo(
        WorldNpcRegistry expectedNpcs,
        NpcLifecycleService expectedLifecycle
    ){
        return npcs==expectedNpcs&&
            lifecycle==expectedLifecycle;
    }

    synchronized Resolution resolve(
        WorldNpc npc,
        String recipientRef
    ){
        WorldNpc checked=
            Objects.requireNonNull(
                npc,
                "npc"
            );
        String recipient=
            normalizeRecipient(
                recipientRef
            );

        Resolution existing=
            resolved.get(
                checked.id
            );

        if(existing!=null){
            if(!existing.context
                    .recipientRef
                    .equals(
                        recipient
                    ))
                throw new IllegalStateException(
                    "NPC death already resolved for different recipient npc="+
                    checked.id+
                    " existing="+
                    existing.context.recipientRef+
                    " requested="+
                    recipient
                );

            return existing;
        }

        final Resolution[] candidate=
            new Resolution[1];

        try{
            boolean current=
                lifecycle
                    .withDeadCanonicalOwnershipIfCurrent(
                        checked,
                        snapshot->{
                            DeathContext context=
                                new DeathContext(
                                    checked.id,
                                    checked.definitionId,
                                    checked.tile(),
                                    snapshot.deathTick,
                                    snapshot.sourceAuthority,
                                    recipient
                                );

                            List<Drop> raw=
                                Objects.requireNonNull(
                                    resolver.resolve(
                                        context
                                    ),
                                    "resolved drops"
                                );

                            List<Drop> canonical=
                                canonicalize(
                                    raw
                                );

                            candidate[0]=
                                new Resolution(
                                    context,
                                    canonical,
                                    dropAuthority
                                );
                        }
                    );

            if(!current)
                throw new IllegalStateException(
                    "NPC is not exact canonical registry owner id="+
                    checked.id
                );
        }catch(RuntimeException failure){
            throw failure;
        }catch(Exception failure){
            throw new IllegalStateException(
                "NPC drop resolution owned action failed id="+
                checked.id,
                failure
            );
        }

        Resolution result=
            Objects.requireNonNull(
                candidate[0],
                "drop resolution candidate"
            );

        /*
         * The owned lifecycle seam has already revalidated:
         * - exact canonical NPC registry identity;
         * - exact lifecycle Entry identity;
         * - DEAD state;
         * - exact deathTick;
         * after caller policy returns.
         *
         * Only publish/cache after that owned callback returns successfully.
         */
        resolved.put(
            checked.id,
            result
        );

        return result;
    }

    synchronized Resolution get(
        EntityId npcId
    ){
        return resolved.get(
            Objects.requireNonNull(
                npcId,
                "npcId"
            )
        );
    }

    synchronized int size(){
        return resolved.size();
    }

    synchronized List<Resolution> snapshot(){
        return Collections.unmodifiableList(
            new ArrayList<>(
                resolved.values()
            )
        );
    }

    String dropAuthority(){
        return dropAuthority;
    }

    private void requireCanonical(
        WorldNpc npc
    ){
        WorldNpc canonical=
            npcs.byId(
                npc.id
            );

        if(canonical!=npc)
            throw new IllegalStateException(
                "NPC is not exact canonical registry owner id="+
                npc.id
            );
    }

    private NpcLifecycleService.Snapshot
        requireDeadSnapshot(
            WorldNpc npc
        ){
        NpcLifecycleService.Snapshot snapshot=
            lifecycle.get(
                npc.id
            );

        if(snapshot==null)
            throw new IllegalStateException(
                "NPC lifecycle missing id="+
                npc.id
            );

        if(!snapshot.dead())
            throw new IllegalStateException(
                "NPC is not dead id="+
                npc.id+
                " state="+
                snapshot.state
            );

        if(!snapshot.hasDeathTick())
            throw new IllegalStateException(
                "dead NPC missing death tick id="+
                npc.id
            );

        return snapshot;
    }

    private static List<Drop> canonicalize(
        List<Drop> raw
    ){
        LinkedHashMap<Integer,Integer>
            amounts=
                new LinkedHashMap<>();

        for(Drop row:raw){
            Drop checked=
                Objects.requireNonNull(
                    row,
                    "drop"
                );

            Integer current=
                amounts.get(
                    checked.itemId
                );

            if(current==null){
                amounts.put(
                    checked.itemId,
                    checked.amount
                );
                continue;
            }

            long combined=
                (long)current+
                (long)checked.amount;

            if(combined>
                    Integer.MAX_VALUE)
                throw new IllegalStateException(
                    "drop amount overflow item="+
                    checked.itemId+
                    " current="+
                    current+
                    " add="+
                    checked.amount
                );

            amounts.put(
                checked.itemId,
                (int)combined
            );
        }

        ArrayList<Drop> out=
            new ArrayList<>();

        for(Map.Entry<Integer,Integer> entry:
                amounts.entrySet())
            out.add(
                new Drop(
                    entry.getKey(),
                    entry.getValue()
                )
            );

        return out;
    }

    private static String normalizeRecipient(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "recipientRef"
            );

        String clean=
            value.trim()
                .toLowerCase(
                    Locale.ROOT
                );

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "recipientRef blank"
            );

        return clean;
    }

    private static String requireGameplayAuthority(
        String value
    ){
        if(value==null)
            throw new NullPointerException(
                "dropAuthority"
            );

        String clean=
            value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                "dropAuthority blank"
            );

        if("EXACT_CURRENT_CLIENT".equals(
                clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(
                clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define NPC drops actual="+
                clean
            );

        return clean;
    }
}

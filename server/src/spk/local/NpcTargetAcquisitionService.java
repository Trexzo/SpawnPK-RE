package spk.local;

import java.util.*;

/**
 * Deterministic target acquisition for an already-canonical NPC.
 *
 * The engine owns canonical identity, exact-generation validation and stable
 * ordering only. Aggro radius, eligibility and target preference remain
 * caller-owned gameplay policy.
 */
final class NpcTargetAcquisitionService {
    enum Status {
        ACQUIRED,
        NONE,
        STALE_ATTACKER
    }

    interface AcquisitionPolicy {
        Decision evaluate(Context context) throws Exception;
    }

    static final class Decision {
        final boolean eligible;
        final long priority;

        private Decision(boolean eligible,long priority){
            if(eligible&&priority<0L)
                throw new IllegalArgumentException(
                    "priority="+priority
                );
            this.eligible=eligible;
            this.priority=priority;
        }

        static Decision ineligible(){
            return new Decision(false,0L);
        }

        static Decision eligible(long priority){
            return new Decision(true,priority);
        }
    }

    static final class Context {
        final EntityId attackerId;
        final int attackerDefinitionId;
        final Tile attackerTile;
        final EntityId candidateId;
        final long candidateGeneration;
        final Tile candidateTile;
        final boolean samePlane;
        final int chebyshevDistance;

        private Context(
            WorldNpc attacker,
            Tile attackerTile,
            CandidateFacts candidate
        ){
            this.attackerId=attacker.id;
            this.attackerDefinitionId=attacker.definitionId;
            this.attackerTile=attackerTile;
            this.candidateId=candidate.player.id();
            this.candidateGeneration=candidate.generation;
            this.candidateTile=candidate.tile;
            this.samePlane=
                attackerTile.plane==candidate.tile.plane;
            this.chebyshevDistance=
                samePlane
                    ?attackerTile.chebyshev(candidate.tile)
                    :-1;
        }
    }

    static final class Target {
        final WorldPlayer player;
        final EntityId playerId;
        final long generation;
        final Tile tile;
        final long priority;

        private Target(
            Candidate candidate,
            Tile currentTile
        ){
            this.player=candidate.player;
            this.playerId=candidate.player.id();
            this.generation=candidate.generation;
            this.tile=currentTile;
            this.priority=candidate.priority;
        }
    }

    static final class Result {
        final Status status;
        final Target target;
        final String authority;
        final String policy;

        private Result(
            Status status,
            Target target,
            String authority,
            String policy
        ){
            this.status=Objects.requireNonNull(status,"status");
            this.target=target;
            this.authority=authority;
            this.policy=policy;
        }
    }

    private static final class CandidateFacts {
        final WorldPlayer player;
        final long generation;
        final Tile tile;

        CandidateFacts(
            WorldPlayer player,
            long generation,
            Tile tile
        ){
            this.player=player;
            this.generation=generation;
            this.tile=tile;
        }
    }

    private static final class Candidate {
        final WorldPlayer player;
        final long generation;
        final Tile tile;
        final long priority;

        Candidate(
            CandidateFacts facts,
            long priority
        ){
            this.player=facts.player;
            this.generation=facts.generation;
            this.tile=facts.tile;
            this.priority=priority;
        }
    }

    private final World world;
    private final AcquisitionPolicy acquisitionPolicy;
    private final String authority;
    private final String policy;

    NpcTargetAcquisitionService(
        World world,
        AcquisitionPolicy acquisitionPolicy,
        String authority,
        String policy
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.acquisitionPolicy=
            Objects.requireNonNull(
                acquisitionPolicy,
                "acquisitionPolicy"
            );
        this.authority=
            requireGameplayAuthority(authority);
        this.policy=requireText(policy,"policy");
    }

    Result acquire(WorldNpc attacker)throws Exception{
        WorldNpc checked=
            Objects.requireNonNull(
                attacker,
                "attacker"
            );

        final Tile[] attackerTile={null};

        boolean attackerCurrent=
            world.npcs()
                .withCurrentMutationOwnershipIfCurrent(
                    checked,
                    ()->attackerTile[0]=checked.tile()
                );

        if(!attackerCurrent)
            return result(
                Status.STALE_ATTACKER,
                null
            );

        ArrayList<Candidate> eligible=
            new ArrayList<>();

        for(WorldPlayer player:
                world.players().snapshot()){
            long generation=player.generation();
            final CandidateFacts[] facts={null};

            boolean current=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    player,
                    generation,
                    ()->{
                        if(player.lifecycle().dead())
                            return;

                        facts[0]=
                            new CandidateFacts(
                                player,
                                generation,
                                new Tile(
                                    player.movement().x(),
                                    player.movement().y(),
                                    player.movement().plane()
                                )
                            );
                    }
                );

            if(!current||facts[0]==null)
                continue;

            Decision decision=
                Objects.requireNonNull(
                    acquisitionPolicy.evaluate(
                        new Context(
                            checked,
                            attackerTile[0],
                            facts[0]
                        )
                    ),
                    "acquisition decision"
                );

            if(!decision.eligible)
                continue;

            if(decision.priority<0L)
                throw new IllegalArgumentException(
                    "policy priority="+
                    decision.priority
                );

            eligible.add(
                new Candidate(
                    facts[0],
                    decision.priority
                )
            );
        }

        eligible.sort(
            Comparator
                .comparingLong(
                    (Candidate candidate)->
                        candidate.priority
                )
                .thenComparingLong(
                    candidate->
                        candidate.player.id().value
                )
        );

        for(Candidate candidate:eligible){
            final boolean[] attackerLost={false};
            final Target[] selected={null};

            boolean candidateCurrent=
                world.withOpenPlayerMutationOwnershipIfCurrent(
                    candidate.player,
                    candidate.generation,
                    ()->{
                        if(candidate.player.lifecycle().dead())
                            return;

                        Tile currentTile=
                            new Tile(
                                candidate.player.movement().x(),
                                candidate.player.movement().y(),
                                candidate.player.movement().plane()
                            );

                        if(!currentTile.equals(candidate.tile))
                            return;

                        boolean npcCurrent=
                            world.npcs()
                                .withCurrentMutationOwnershipIfCurrent(
                                    checked,
                                    ()->{
                                        if(!checked.tile().equals(
                                                attackerTile[0]))
                                            return;

                                        selected[0]=
                                            new Target(
                                                candidate,
                                                currentTile
                                            );
                                    }
                                );

                        if(!npcCurrent)
                            attackerLost[0]=true;
                    }
                );

            if(attackerLost[0])
                return result(
                    Status.STALE_ATTACKER,
                    null
                );

            if(candidateCurrent&&
               selected[0]!=null)
                return result(
                    Status.ACQUIRED,
                    selected[0]
                );
        }

        if(world.npcs().byId(checked.id)!=checked)
            return result(
                Status.STALE_ATTACKER,
                null
            );

        return result(
            Status.NONE,
            null
        );
    }

    String authority(){
        return authority;
    }

    String policy(){
        return policy;
    }

    private Result result(
        Status status,
        Target target
    ){
        return new Result(
            status,
            target,
            authority,
            policy
        );
    }

    private static String requireGameplayAuthority(
        String value
    ){
        String clean=
            requireText(
                value,
                "authority"
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean))
            throw new IllegalArgumentException(
                "client/unknown authority cannot define NPC target acquisition actual="+
                clean
            );

        return clean;
    }

    private static String requireText(
        String value,
        String name
    ){
        if(value==null)
            throw new NullPointerException(name);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(name);

        return clean;
    }
}

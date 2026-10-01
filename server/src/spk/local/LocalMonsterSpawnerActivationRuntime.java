package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Shared production composition for one explicitly configured Monster Spawner
 * graph across LocalSessions.
 *
 * This class owns no catalog, labels, activation budget, placement, combat,
 * lifecycle, drop or economy rules. Those remain caller-owned in the supplied
 * service/resolvers/runtime/executor graph.
 */
final class LocalMonsterSpawnerActivationRuntime
    implements LocalSession.MonsterSpawnerUiFactory
{
    private final World world;
    private final MonsterSpawnerService service;
    private final MonsterSpawnerPvmRuntime runtime;
    private final MonsterSpawnerPvmSpawnExecutor executor;
    private final String sessionAuthority;
    private final LocalMonsterSpawnerUiHandler.ActivationBudgetResolver
        activationBudget;
    private final LocalMonsterSpawnerUiHandler.SelectedNpcLabelResolver
        selectedLabel;

    LocalMonsterSpawnerActivationRuntime(
        World world,
        MonsterSpawnerService service,
        MonsterSpawnerPvmRuntime runtime,
        MonsterSpawnerPvmSpawnExecutor executor,
        String sessionAuthority,
        LocalMonsterSpawnerUiHandler.ActivationBudgetResolver activationBudget,
        LocalMonsterSpawnerUiHandler.SelectedNpcLabelResolver selectedLabel
    ){
        this.world=Objects.requireNonNull(
            world,
            "world"
        );
        this.service=Objects.requireNonNull(
            service,
            "service"
        );
        this.runtime=Objects.requireNonNull(
            runtime,
            "runtime"
        );
        this.executor=Objects.requireNonNull(
            executor,
            "executor"
        );
        this.sessionAuthority=
            requireServerAuthority(
                sessionAuthority,
                "sessionAuthority"
            );
        this.activationBudget=Objects.requireNonNull(
            activationBudget,
            "activationBudget"
        );
        this.selectedLabel=Objects.requireNonNull(
            selectedLabel,
            "selectedLabel"
        );

        if(!this.service.isBoundTo(
                this.world.npcs()))
            throw new IllegalArgumentException(
                "Monster Spawner activation runtime service belongs to another World"
            );

        if(!this.runtime.isBoundTo(
                this.world,
                this.service))
            throw new IllegalArgumentException(
                "Monster Spawner activation runtime PvM graph mismatch"
            );

        if(!this.executor.isBoundTo(
                this.service,
                this.runtime))
            throw new IllegalArgumentException(
                "Monster Spawner activation runtime executor graph mismatch"
            );

        this.world.installMonsterSpawnerPvmRuntime(
            this.runtime
        );
    }

    @Override public LocalMonsterSpawnerUiHandler create(
        World sessionWorld,
        WorldPlayer player,
        String canonicalUsername
    )throws Exception{
        World checkedWorld=
            Objects.requireNonNull(
                sessionWorld,
                "sessionWorld"
            );
        WorldPlayer checkedPlayer=
            Objects.requireNonNull(
                player,
                "player"
            );
        String owner=
            PartyService.requireRef(
                canonicalUsername
            );

        if(checkedWorld!=world)
            throw new IllegalArgumentException(
                "Monster Spawner activation runtime belongs to another World"
            );

        if(world.players().byName(owner)!=
                checkedPlayer||
           !world.players().owns(
                checkedPlayer,
                checkedPlayer.generation()))
            throw new IllegalStateException(
                "Monster Spawner activation runtime requires exact current account player owner="+
                owner
            );

        MonsterSpawnerService.ResumeResult resumed=
            service.resumeSessionIfPresent(
                owner,
                sessionAuthority
            );
        MonsterSpawnerService.SessionSnapshot session;
        boolean openedFresh=false;
        boolean retirementWasPending=false;

        if(resumed==null){
            session=service.openSession(
                owner,
                sessionAuthority
            );
            openedFresh=true;
        }else{
            session=resumed.session;
            retirementWasPending=
                resumed.retirementWasPending;
        }

        try{
            return new LocalMonsterSpawnerUiHandler(
                service,
                owner,
                activationBudget,
                selectedLabel
            );
        }catch(Throwable failure){
            if(openedFresh){
                try{
                    if(!service.retireSessionIfCurrentAndNoTrackedNpcs(
                            owner,
                            session
                        ))
                        failure.addSuppressed(
                            new IllegalStateException(
                                "fresh Monster Spawner session rollback refused owner="+
                                owner
                            )
                        );
                }catch(Throwable rollbackFailure){
                    failure.addSuppressed(
                        rollbackFailure
                    );
                }
            }else if(retirementWasPending){
                try{
                    service.retireSessionNowOrWhenIdle(
                        owner
                    );
                }catch(Throwable rollbackFailure){
                    failure.addSuppressed(
                        rollbackFailure
                    );
                }
            }

            rethrowCreateFailure(
                failure
            );
            return null;
        }
    }

    @Override public void onCommittedResult(
        World callbackWorld,
        WorldPlayer callbackPlayer,
        String canonicalUsername,
        LocalMonsterSpawnerUiHandler.Result result,
        ServerPacketWriter writer,
        String tag
    )throws Exception{
        Objects.requireNonNull(
            callbackPlayer,
            "callbackPlayer"
        );
        Objects.requireNonNull(
            writer,
            "writer"
        );
        Objects.requireNonNull(
            tag,
            "tag"
        );

        if(callbackWorld!=world)
            throw new IllegalArgumentException(
                "Monster Spawner activation callback World mismatch"
            );

        String owner=
            PartyService.requireRef(
                canonicalUsername
            );
        LocalMonsterSpawnerUiHandler.Result committed=
            Objects.requireNonNull(
                result,
                "result"
            );

        if(committed.status!=
                LocalMonsterSpawnerUiHandler.Status.ACTIVATED)
            return;

        MonsterSpawnerPvmSpawnExecutor.Result spawn=
            executor.execute(
                owner
            );

        if(spawn.status!=
                MonsterSpawnerPvmSpawnExecutor.Status.SPAWNED)
            throw new IllegalStateException(
                "Monster Spawner ACTIVATED callback did not produce canonical PvM spawn owner="+
                owner+
                " status="+spawn.status
            );
    }

    @Override public void onSessionClosed(
        World closeWorld,
        WorldPlayer player,
        long expectedGeneration,
        String canonicalUsername
    ){
        Objects.requireNonNull(
            player,
            "player"
        );

        if(closeWorld!=world)
            throw new IllegalArgumentException(
                "Monster Spawner session-close World mismatch"
            );

        String owner=
            PartyService.requireRef(
                canonicalUsername
            );

        if(world.players().byName(owner)!=player||
           !world.players().owns(
                player,
                expectedGeneration))
            throw new IllegalStateException(
                "Monster Spawner session-close requires exact current account generation owner="+
                owner+
                " expectedGeneration="+expectedGeneration
            );

        service.retireSessionNowOrWhenIdle(
            owner
        );
    }

    MonsterSpawnerService service(){
        return service;
    }

    MonsterSpawnerPvmRuntime runtime(){
        return runtime;
    }

    private static void rethrowCreateFailure(
        Throwable failure
    )throws Exception{
        if(failure instanceof Exception)
            throw (Exception)failure;

        if(failure instanceof Error)
            throw (Error)failure;

        throw new RuntimeException(
            failure
        );
    }

    private static String requireServerAuthority(
        String value,
        String field
    ){
        String clean=
            MatchRules.requireText(
                value,
                field
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean)||
           "UNCONFIGURED".equals(clean))
            throw new IllegalArgumentException(
                field+
                " cannot be client/unknown/unconfigured authority actual="+
                clean
            );

        return clean;
    }
}

package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Owns multiplayer/runtime service registration for an active LocalSession.
 *
 * This keeps Player81 sync, shared NPC relay and trade service lifecycle out
 * of the socket adapter while preserving their existing registration order.
 */
final class LocalSessionRuntimeBindings {
    interface SessionBridge {
        void saveAccount(String tag,String reason);

        default void publishTradeRoot(
            TradeService.RootPublication action
        )throws IOException{
            action.publish();
        }
    }

    private final World world;
    private final WorldPlayer worldPlayer;
    private final DevAuthorityWorkbench dev;
    private final NpcRegistry npcs;
    private final MovementState movement;
    private final BankState bank;
    private final SessionBridge bridge;

    private Player81WorldSync.Context player81Sync;
    private ServerPacketWriter registeredPackets;

    LocalSessionRuntimeBindings(
        World world,
        WorldPlayer worldPlayer,
        DevAuthorityWorkbench dev,
        NpcRegistry npcs,
        MovementState movement,
        BankState bank,
        SessionBridge bridge
    ){
        this.world=Objects.requireNonNull(world,"world");
        this.worldPlayer=Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.dev=Objects.requireNonNull(dev,"dev");
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.bank=Objects.requireNonNull(bank,"bank");
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    Player81WorldSync.Context context(){
        return player81Sync;
    }

    void register(
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        register(
            serverPackets,
            tag,
            worldPlayer.generation()
        );
    }

    void register(
        ServerPacketWriter serverPackets,
        String tag,
        long expectedGeneration
    )throws IOException{
        if(player81Sync!=null)return;

        boolean[] bindingStarted=
            new boolean[]{false};
        boolean[] sharedNpcInstalled=
            new boolean[]{false};
        boolean[] player81Installed=
            new boolean[]{false};
        boolean[] tradeInstalled=
            new boolean[]{false};

        try{
            world.withOpenPlayerOwnership(
                worldPlayer,
                expectedGeneration,
                ()->{
                    bindingStarted[0]=true;

                    /*
                     * Publish any required exact S2C104 option state while the
                     * old runtime bundle is still authoritative. Queue-backed
                     * rejection is retractable and therefore cannot destroy an
                     * old Trade/Player81/SharedNpc binding.
                     */
                    boolean playerOptionsPrepared=
                        Player81WorldSync
                            .preparePlayerOptionsForRegistration(
                                world,
                                worldPlayer,
                                serverPackets
                            );

                    /*
                     * SharedNpc live replacement may reject retryably while
                     * intentionally retaining the exact old relay Context.
                     * Player81 registration is destructive (it retires the old
                     * writer/owner Context immediately), so do not replace it
                     * until SharedNpc admission has committed.
                     */
                    SharedNpcWorldRelay.register(
                        serverPackets,
                        world,
                        worldPlayer,
                        npcs,
                        movement
                    );
                    sharedNpcInstalled[0]=true;

                    player81Sync=
                        Player81WorldSync.register(
                            serverPackets,
                            world,
                            worldPlayer,
                            dev,
                            playerOptionsPrepared
                        );
                    player81Installed[0]=true;

                    registeredPackets=
                        serverPackets;

                    TradeService.register(
                        world,
                        worldPlayer,
                        expectedGeneration,
                        bank,
                        serverPackets,
                        ()->bridge.saveAccount(
                            tag,
                            "TRADE_COMMIT"
                        ),
                        action->
                            bridge.publishTradeRoot(
                                action
                            )
                    );
                    tradeInstalled[0]=true;

                    /*
                     * Normally a no-op because the prepublication phase seeded
                     * all multiplayer contexts as already-sent. Retain this as
                     * a compatibility safety net for the single-player path.
                     */
                    Player81WorldSync
                        .sendPlayerOptionsIfMultiplayer(
                            world
                        );
                }
            );
        }catch(Throwable failure){
            if(bindingStarted[0])
                rollbackRegistration(
                    serverPackets,
                    tradeInstalled[0],
                    sharedNpcInstalled[0],
                    player81Installed[0],
                    failure
                );

            rethrowRegistrationFailure(
                failure
            );
        }

        System.out.println(
            tag+
            "V5131_ENGINE_R3_PLAYER_SYNC_REGISTER playerId="+
            worldPlayer.id()+
            " members="+world.players().size()+
            " "+player81Sync.summary()+
            " options="+
            (world.players().size()>1
                ?"Attack/Follow/TradeWith"
                :"DEFERRED_UNTIL_MULTIPLAYER")+
            " authority=EXACT_CLIENT_S2C104_C2S128_153_73"
        );
    }

    void unregister(){
        final ServerPacketWriter writer=
            registeredPackets;

        if(writer==null){
            player81Sync=null;
            return;
        }

        LocalSessionRuntimeBindingCleanup.run(
            "[runtime-bindings] ",
            ()->{
                registeredPackets=null;
                player81Sync=null;
            },
            ()->TradeService.unregister(
                worldPlayer,
                writer
            ),
            ()->SharedNpcWorldRelay.unregister(
                writer
            ),
            ()->Player81WorldSync.unregister(
                writer
            )
        );
    }

    private void rollbackRegistration(
        ServerPacketWriter writer,
        boolean tradeInstalled,
        boolean sharedNpcInstalled,
        boolean player81Installed,
        Throwable primary
    ){
        if(tradeInstalled)
            try{
                TradeService.unregister(
                    worldPlayer,
                    writer
                );
            }catch(Throwable cleanup){
                primary.addSuppressed(cleanup);
            }

        if(sharedNpcInstalled)
            try{
                SharedNpcWorldRelay.unregister(
                    writer
                );
            }catch(Throwable cleanup){
                primary.addSuppressed(cleanup);
            }

        if(player81Installed)
            try{
                Player81WorldSync.unregister(
                    writer
                );
            }catch(Throwable cleanup){
                primary.addSuppressed(cleanup);
            }

        registeredPackets=null;
        player81Sync=null;
    }

    private static void rethrowRegistrationFailure(
        Throwable failure
    )throws IOException{
        if(failure instanceof IOException)
            throw (IOException)failure;

        if(failure instanceof RuntimeException)
            throw (RuntimeException)failure;

        if(failure instanceof Error)
            throw (Error)failure;

        throw new IOException(
            "runtime binding registration failed",
            failure
        );
    }
}

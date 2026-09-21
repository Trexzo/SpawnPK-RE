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
        if(player81Sync!=null)return;

        long generation=
            worldPlayer.generation();

        world.withOpenPlayerOwnership(
            worldPlayer,
            generation,
            ()->{
                player81Sync=
                    Player81WorldSync.register(
                        serverPackets,
                        world,
                        worldPlayer,
                        dev
                    );

                registeredPackets=
                    serverPackets;

                SharedNpcWorldRelay.register(
                    serverPackets,
                    world,
                    worldPlayer,
                    npcs,
                    movement
                );

                TradeService.register(
                    world,
                    worldPlayer,
                    bank,
                    serverPackets,
                    ()->bridge.saveAccount(
                        tag,
                        "TRADE_COMMIT"
                    )
                );

                Player81WorldSync
                    .sendPlayerOptionsIfMultiplayer(
                        world
                    );
            }
        );

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
        if(registeredPackets==null){
            player81Sync=null;
            return;
        }

        TradeService.unregister(
            worldPlayer,
            registeredPackets
        );
        SharedNpcWorldRelay.unregister(registeredPackets);
        Player81WorldSync.unregister(registeredPackets);

        registeredPackets=null;
        player81Sync=null;
    }
}

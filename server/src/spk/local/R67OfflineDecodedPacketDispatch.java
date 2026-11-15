package spk.local;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * R67 OFFLINE typed-packet dispatch harness, not a production route.
 *
 * ClientPacketProbe performs the real pinned-client ISAAC and packet-body
 * decoding. This test-only bridge freezes selected typed request values
 * before they enter the deferred callback queue. At execution time R66
 * checks the current R64 session owner, R63 account generation and actor
 * incarnation under the real R60 cross-process OS lock before the
 * instrumented movement, NPC-attack or item-action handler is reachable.
 *
 * No real socket, attack service, movement engine or trade is invoked.
 */
final class R67OfflineDecodedPacketDispatch {
    enum Family {MOVEMENT, NPC_ATTACK, INVENTORY_ACTION}

    static final class FrozenCommand {
        final Family family;
        final int opcode;
        final int x,y;
        final boolean run;
        final int npcIndex;
        final int widgetId,slot,itemId;
        final String schema,source;

        private FrozenCommand(Family family,int opcode,int x,int y,
                              boolean run,int npcIndex,
                              int widgetId,int slot,int itemId,
                              String schema,String source){
            this.family=family;
            this.opcode=opcode;
            this.x=x;this.y=y;this.run=run;
            this.npcIndex=npcIndex;
            this.widgetId=widgetId;this.slot=slot;this.itemId=itemId;
            this.schema=schema;this.source=source;
        }
    }

    static final class CapturedRequest {
        final UUID requestId;
        final R66OfflineGameEventDispatch.Actor actor;
        final FrozenCommand command;
        private CapturedRequest(
            R66OfflineGameEventDispatch.Actor actor,FrozenCommand command
        ){
            requestId=UUID.randomUUID();
            this.actor=actor;this.command=command;
        }
    }

    @FunctionalInterface
    interface InstrumentedHandler {
        void accept(
            R66OfflineGameEventDispatch.Actor actor,FrozenCommand command
        )throws IOException;
    }

    private final R66OfflineGameEventDispatch actors;
    private final InstrumentedHandler handler;

    R67OfflineDecodedPacketDispatch(
        R66OfflineGameEventDispatch actors,InstrumentedHandler handler
    ){
        this.actors=Objects.requireNonNull(actors,"actors");
        this.handler=Objects.requireNonNull(handler,"handler");
    }

    CapturedRequest capture(
        R66OfflineGameEventDispatch.Actor actor,ClientRequest decoded
    )throws IOException{
        Objects.requireNonNull(actor,"actor");
        Objects.requireNonNull(decoded,"decoded");
        // Capture is only data freezing, NEVER session authorization:
        // the owner may be replaced before this request executes.
        ClientRequestMetadata m=decoded.metadata();
        if(m==null||m.provenance!=
                ClientRequestProvenance.EXACT_CURRENT_CLIENT)
            throw new IOException(
                "R67_CAPTURE_DENIED untrusted packet provenance"
            );
        FrozenCommand command;
        if(decoded instanceof MovementClientRequest){
            MovementRequest walk=((MovementClientRequest)decoded).movement();
            if(walk.opcode!=164&&walk.opcode!=98&&walk.opcode!=248)
                throw new IOException(
                    "R67_CAPTURE_DENIED movement opcode"
                );
            String schema=walk.opcode==248
                ?"VARBYTE_PATH_X_LE_A_SIGNED_DELTAS_Y_LE_RUN_NEG_PLUS_OPAQUE14"
                :"VARBYTE_PATH_X_LE_A_SIGNED_DELTAS_Y_LE_RUN_NEG";
            String source=walk.opcode==164
                ?"PINNED_CLIENT_MOVEMENT_OPCODE_164_WRITER"
                :walk.opcode==98
                    ?"PINNED_CLIENT_MOVEMENT_OPCODE_98_WRITER"
                    :"PINNED_CLIENT_MINIMAP_MOVEMENT_OPCODE_248_WRITER";
            requireExact(m,walk.opcode,schema,source);
            command=new FrozenCommand(
                Family.MOVEMENT,walk.opcode,
                walk.finalX(),walk.finalY(),walk.run,-1,
                -1,-1,-1,schema,source
            );
        }else if(decoded instanceof NpcActionClientRequest){
            NpcAction npc=((NpcActionClientRequest)decoded).action();
            if(npc.opcode!=72||npc.sceneIndex<0)
                throw new IOException("R67_CAPTURE_DENIED combat opcode/index");
            String schema="FIXED2_NPC_SCENE_INDEX_BE_A";
            String source="PINNED_CLIENT_NPC_OPTION_2_ATTACK_WRITER";
            requireExact(m,72,schema,source);
            command=new FrozenCommand(
                Family.NPC_ATTACK,72,-1,-1,false,
                npc.sceneIndex,-1,-1,-1,schema,source
            );
        }else if(decoded instanceof ItemContainerActionClientRequest){
            ItemContainerAction item=
                ((ItemContainerActionClientRequest)decoded).action();
            if(item.opcode!=145||item.widgetId<0||
               item.slot<0||item.itemId<0)
                throw new IOException(
                    "R67_CAPTURE_DENIED item opcode/fields"
                );
            String schema="FIXED6_WIDGET_BE_A_SLOT_BE_A_ITEM_BE_A";
            String source="PINNED_CLIENT_MENU_ACTION_632_WRITER";
            requireExact(m,145,schema,source);
            command=new FrozenCommand(
                Family.INVENTORY_ACTION,145,-1,-1,false,
                -1,item.widgetId,item.slot,item.itemId,
                schema,source
            );
        }else{
            throw new IOException(
                "R67_CAPTURE_DENIED unsupported decoded request class"
            );
        }
        return new CapturedRequest(actor,command);
    }

    void dispatch(CapturedRequest queued)throws IOException{
        Objects.requireNonNull(queued,"queued");
        actors.runVerifiedHandler(
            queued.actor,queued.requestId,
            ()->handler.accept(queued.actor,queued.command)
        );
    }

    private static void requireExact(
        ClientRequestMetadata m,int opcode,String schema,String source
    )throws IOException{
        if(m.opcode!=opcode||!schema.equals(m.schema)||
           !source.equals(m.source))
            throw new IOException(
                "R67_CAPTURE_DENIED metadata mismatch"
            );
    }
}

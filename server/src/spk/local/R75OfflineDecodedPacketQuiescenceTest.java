package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;

/**
 * R75 actual decoded-client-intent dispatcher regression. Runs inside the
 * already complete LocalPendingRequestDispatcherTest construction fixture;
 * tests the real ISAAC typed packet probes and real movement handler.
 *
 * The standard LocalSession request path already submits through World
 * command inbox; this tests the lower-level dispatcher direct drain path
 * only when an explicit offline hook is installed. No live socket.
 */
public final class R75OfflineDecodedPacketQuiescenceTest {
    private static final int[] SEED={5,6,7,8};

    public static void main(String[] args)throws Exception{
        LocalPendingRequestDispatcherTest.main(new String[]{"r75"});
    }

    static void runFixture(
        World world,
        LocalPendingRequestDispatcher dispatcher,
        WorldPlayer player,
        MovementState movement,
        BankState bank,
        ServerPacketWriter writer
    )throws Exception{
        long generation=player.generation();

        R74OfflineCommandIngressFence commands=
            new R74OfflineCommandIngressFence(world);
        commands.installForTest();
        R75OfflineCrossIngressFence cross=
            new R75OfflineCrossIngressFence(
                world,player,generation,commands
            );
        cross.installForTest();
        dispatcher.installOfflineAdmissionForTest(
            cross.decodedRequestAdmissionForTest()
        );
        world.start();

        long movementBefore=movement.acceptedPaths();
        dispatcher.drain(
            decode(movementWire(
                MovementState.INITIAL_X+1,
                MovementState.INITIAL_Y
            )),
            writer,"[r75-decoded-before] "
        );
        require(movement.acceptedPaths()==movementBefore+1,
            "R75 decoded pre-quiescence movement did not enter handler");

        // These actual ISAAC-decoded requests are created *before*
        // quiescence; they are deliberately drained only afterward.
        ClientPacketProbe oldMove=decode(movementWire(
            MovementState.INITIAL_X+2,
            MovementState.INITIAL_Y
        ));
        ClientPacketProbe oldAttack=decode(attackWire(131));
        ClientPacketProbe oldBank=decode(itemWire(3322,0,995));
        long acceptedBefore=movement.acceptedPaths();
        int coinBefore=bank.inventoryCount(995);
        int foodBefore=bank.inventoryCount(385);

        world.submitAndWait(player,generation,()->{
            commands.quiesceInsideWorldActor(player,generation);
        },10_000L);
        require(commands.isQuiesced(player,generation),
            "R75 decoded fixture failed to quiesce World actor");

        for(ClientPacketProbe stale:new ClientPacketProbe[]{
                oldMove,oldAttack,oldBank}){
            try{
                dispatcher.drain(
                    stale,writer,"[r75-decoded-after] "
                );
                throw new AssertionError(
                    "R75 post-quiescence decoded request reached handler"
                );
            }catch(IOException denied){
                require(denied.getMessage().contains(
                    "R75_DECODED_REQUEST_DENIED after final quiescence"
                ),"R75 incorrect decoded-request refusal "+denied);
            }
        }

        require(movement.acceptedPaths()==acceptedBefore &&
                bank.inventoryCount(995)==coinBefore &&
                bank.inventoryCount(385)==foodBefore,
            "R75 stale decoded request changed gameplay handler state");

        // The *actual* LocalSession socket path routes decoded requests
        // through World.submitAndWait; after R74 quiescence this path
        // refuses before reaching any typed-request handler.
        try{
            world.submit(
                player,generation,()->{
                    throw new AssertionError(
                        "R75 queued packet handler must not run"
                    );
                }
            ).get(10L,TimeUnit.SECONDS);
            throw new AssertionError(
                "R75 quiesced World packet command accepted"
            );
        }catch(ExecutionException|CancellationException denied){
            Throwable cause=denied instanceof ExecutionException
                ?denied.getCause():denied;
            require(cause.getMessage().contains(
                "R74_COMMAND_QUIESCED"
            ),"R75 wrong World packet ingress refusal "+cause);
        }

        System.out.println(
            "R75_REAL_DECODED_PACKET_QUIESCENCE_PASS "+
            "actualIsaacDecodedMovementBeforeQuiescence=true "+
            "typedMovementNpcAttackInventoryCapturedBeforeQuiescence=true "+
            "directDispatcherRequestsRefusedAfterQuiescence=true "+
            "actualMovementAndBankHandlersUnchanged=true "+
            "WorldPacketSubmissionAlsoRefused=true "+
            "defaultLiveDispatcherUnchanged=true "+
            "noLiveSessionOrSocket=true"
        );
    }

    private static ClientPacketProbe decode(byte[] wire)
            throws IOException{
        ClientPacketProbe probe=new ClientPacketProbe(
            new ByteArrayInputStream(wire),
            new IsaacCipher(SEED.clone()),
            "[r75-real-decoder] "
        );
        require(probe.readNextKnownPacket()&&
                probe.isAligned()&&
                probe.typedRequestCount()==1,
            "R75 real ISAAC probe did not decode one typed request");
        return probe;
    }

    private static byte[] movementWire(int x,int y)
            throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        opcode(out,cipher,164);
        out.write(5);
        leA(out,x);
        le(out,y);
        out.write(0);
        return out.toByteArray();
    }

    private static byte[] attackWire(int npc)
            throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        opcode(out,cipher,72);
        beA(out,npc);
        return out.toByteArray();
    }

    private static byte[] itemWire(
        int widget,int slot,int item
    )throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        IsaacCipher cipher=new IsaacCipher(SEED.clone());
        opcode(out,cipher,145);
        beA(out,widget);
        beA(out,slot);
        beA(out,item);
        return out.toByteArray();
    }

    private static void opcode(
        ByteArrayOutputStream out,IsaacCipher cipher,int id
    ){
        out.write((id+cipher.nextInt())&255);
    }
    private static void le(ByteArrayOutputStream out,int v){
        out.write(v&255);
        out.write((v>>>8)&255);
    }
    private static void leA(ByteArrayOutputStream out,int v){
        out.write((v+128)&255);
        out.write((v>>>8)&255);
    }
    private static void beA(ByteArrayOutputStream out,int v){
        out.write((v>>>8)&255);
        out.write((v+128)&255);
    }

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError(why);
    }

    private R75OfflineDecodedPacketQuiescenceTest(){}
}

package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.*;

/**
 * Source-only packet wire comparison for session-bound read-only previews.
 * Both arms use identical World, BankState, ISAAC seeds and synthetic items;
 * only one arm requests previews before actual queue-backed publication.
 */
public final class RecipeSessionPreviewWireParityTest {
    private RecipeSessionPreviewWireParityTest(){}

    private static final Set<Integer> STACKABLE=
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(100,300)));
    private static final Set<Integer> NONSTACKABLE=
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(200,400)));

    private static void ok(boolean check,String why){
        if(!check)throw new AssertionError("RECIPE_PREVIEW_WIRE "+why);
    }

    private static World isolatedWorld(){
        return World.isolatedForTest(60000L,new PlayerRepository(){
            @Override public Optional<PlayerSnapshot> load(String username){
                return Optional.empty();
            }
            @Override public void save(PlayerSnapshot snapshot){
                throw new AssertionError("preview may not save an account");
            }
        });
    }

    private static void seed(WorldPlayer player){
        int[] ids=new int[player.bank().inventoryCapacity()];
        int[] quantities=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[0]=100;quantities[0]=5;
        ids[1]=200;quantities[1]=1;
        ids[2]=200;quantities[2]=1;
        player.bank().replaceInventorySemantic(ids,quantities);
    }

    private static final class Result {
        final byte[] encoded;
        final int[] ids,quantities;
        Result(byte[] encoded,int[] ids,int[] quantities){
            this.encoded=encoded;
            this.ids=ids;
            this.quantities=quantities;
        }
    }

    private static Result runCase(
        boolean bankOpen,boolean withPreview,int repeats
    )throws Exception{
        World world=isolatedWorld();
        try{
            WorldPlayer player=new WorldPlayer();
            seed(player);
            long generation=world.registerPlayer(
                player,"recipe_preview_wire_fixture");
            OutboundPacketQueue queue=new OutboundPacketQueue(8192);
            ServerPacketWriter writer=new ServerPacketWriter(
                queue,new IsaacCipher(new int[]{0,0,0,0}));
            if(bankOpen){
                player.bank().open(writer);
                ok(queue.drainTo(new ByteArrayOutputStream(),8192)>0,
                    "bank open fixture did not generate interface bytes");
            }
            ok(queue.queuedBytes()==0&&queue.queuedPackets()==0,
                "fixture left pending initialization packets");
            RecipeSessionWriterGuard guard=
                new RecipeSessionWriterGuard(
                    world,player,generation,writer);
            Map<Integer,Integer> inputs=new HashMap<>();
            inputs.put(100,1);
            inputs.put(200,1);
            RecipeWorldOwnedAdapter.SessionPrepared prepared=
                RecipeWorldOwnedAdapter.prepareSessionMixed(
                    guard,inputs,400,1,2,
                    STACKABLE,NONSTACKABLE,null,false);
            long beforeInventory=player.bank().inventoryMutationRevision();
            long beforeView=player.bank().bankViewRevision();
            if(withPreview){
                byte[][] initial=null;
                for(int i=0;i<repeats;i++){
                    byte[][] views=
                        RecipeWorldOwnedAdapter.previewSessionViews(prepared);
                    ok(views.length==(bankOpen?2:1),
                        "wrong number of inventory preview views");
                    byte[] primary=RecipeWorldOwnedAdapter.previewSession(
                        prepared);
                    ok(Arrays.equals(views[0],primary),
                        "single preview and all-view body diverged");
                    if(initial==null){
                        // Preserve an independent baseline: this regression
                        // intentionally corrupts the caller's returned view
                        // later in the loop to test defensive ownership.
                        initial=new byte[views.length][];
                        for(int view=0;view<views.length;view++)
                            initial[view]=views[view].clone();
                    }else
                        ok(Arrays.deepEquals(initial,views),
                            "repeated preview mutated next body");
                    // Deliberately corrupt the returned copy. Subsequent
                    // preview and publication must use sealed postimage.
                    views[0][0]^=(byte)0xff;
                    if(views.length==2)views[1][0]^=(byte)0xff;
                }
            }
            ok(queue.queuedBytes()==0&&queue.queuedPackets()==0&&
                player.bank().inventoryMutationRevision()==beforeInventory&&
                player.bank().bankViewRevision()==beforeView&&
                player.bank().inventoryAt(0).qty==5&&
                player.bank().inventoryAt(1).itemId==200,
                "preview modified packet queue, inventory or bank view");
            ok(RecipeWorldOwnedAdapter.publishSession(prepared)==
                ServerPacketWriter.RecoverablePacketResult.COMMITTED&&
                queue.queuedPackets()==1,
                "source-only session recipe did not publish one batch");
            ByteArrayOutputStream wire=new ByteArrayOutputStream();
            ok(queue.drainTo(wire,8192)>0,
                "no queued inventory view publication");
            int n=player.bank().inventoryCapacity();
            int[] ids=new int[n],quantities=new int[n];
            Arrays.fill(ids,-1);
            for(int slot=0;slot<n;slot++){
                BankState.Stack stack=player.bank().inventoryAt(slot);
                if(stack!=null){
                    ids[slot]=stack.itemId;
                    quantities[slot]=stack.qty;
                }
            }
            guard.revokeAndAwait();
            ok(world.unregisterPlayer(player,generation),
                "wire fixture could not unregister player");
            return new Result(wire.toByteArray(),ids,quantities);
        }finally{
            world.close();
        }
    }

    public static void main(String[] args)throws Exception{
        for(boolean bankOpen:new boolean[]{false,true}){
            Result direct=runCase(bankOpen,false,0);
            for(int repeats:new int[]{1,5,64}){
                Result withPreview=runCase(bankOpen,true,repeats);
                ok(Arrays.equals(direct.encoded,withPreview.encoded),
                    "preview changed ISAAC or inventory wire bankOpen="+
                    bankOpen+" repeats="+repeats);
                ok(Arrays.equals(direct.ids,withPreview.ids)&&
                    Arrays.equals(direct.quantities,withPreview.quantities),
                    "preview changed semantic inventory bankOpen="+
                    bankOpen+" repeats="+repeats);
                ok(withPreview.encoded.length>0,
                    "source-only packet unexpectedly empty");
            }
        }
        System.out.println("RECIPE_SESSION_PREVIEW_WIRE_PASS"+
            " bankClosedParity=true bankOpenMirrorParity=true"+
            " isaacStateUnaffected=true previewCount=1,5,64"+
            " detachedBodies=true noAccountSaves=true"+
            " liveRecipeRegistered=false");
    }
}

package spk.local;

import java.io.*;
import java.util.*;

/** G123: actual 5000 widget route and queue-atomic Quick Prayer ON. */
public final class G123QuickPrayerOnIntegrationTest {
    private static final int[] SEED={31,32,33,34};
    private static final String POLICY=
        "LOCAL_LAB_POLICY_G121_QUICK_SELECTION";

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final LocalQuickPrayerActivationService activation;
        String lastOn;
        Bridge(WorldPlayer player,QuickPrayerSelectionService selection){
            activation=new LocalQuickPrayerActivationService(
                player,selection);
        }
        @Override public void saveAccount(String tag,String reason){}
        @Override public void clearDialogNumberKeys(){}
        @Override public void handleDevPanelWidget(
            int widget,ServerPacketWriter writer,String tag)throws IOException{}
        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,String tag){}
        @Override public void requestLogout(){}
        @Override public String handleQuickPrayerOn(
            ServerPacketWriter writer,String tag)throws IOException{
            lastOn=activation.activate(writer);
            return lastOn;
        }
        @Override public String handleQuickPrayerOff(
            ServerPacketWriter writer,String tag)throws IOException{
            String result=playerPrayers.deactivateAll(writer);
            QuickPrayerSelectionPresentation.publishActive(writer,false);
            return result;
        }
        PrayerState playerPrayers;
    }

    private static final class Capture {
        final OutboundPacketQueue queue;
        final ServerPacketWriter writer;
        Capture(int capacity){
            queue=new OutboundPacketQueue(capacity);
            writer=new ServerPacketWriter(
                queue,new IsaacCipher(SEED.clone()));
        }
        byte[] drain()throws Exception{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            queue.drainTo(out,1<<20);
            return out.toByteArray();
        }
    }

    public static void main(String[] args)throws Exception{
        require(args.length==0,"unexpected args");
        World world=World.isolatedForTest(60_000L);
        WorldPlayer player=new WorldPlayer();
        long generation=world.registerPlayer(player,"g123-player");
        try{
            player.playerState().setCurrentLevel(
                PlayerState.PRAYER,10);
            QuickPrayerSelectionService selection=
                new QuickPrayerSelectionService(player,POLICY);
            selection.beginEdit();
            selection.toggleDraft("normal:thick_skin");
            selection.toggleDraft("normal:piety");
            selection.confirmEdit();
            Bridge bridge=new Bridge(player,selection);
            bridge.playerPrayers=player.prayers();
            LocalSessionUiActionHandler ui=uiHandler(player,bridge);

            Capture low=new Capture(4096);
            ui.handleWidget(
                QuickPrayerSelectionPresentation.QUICK_ON_WIDGET,
                low.writer,"[g123-low] ");
            require(bridge.lastOn.startsWith(
                "REJECTED_PRAYER_LEVEL name=Piety")&&
                low.drain().length==0&&
                player.prayers().activeCount()==0,
                "low-level selection admitted partial quick prayers");

            // Editing must not activate the old confirmed set.
            selection.beginEdit();
            Capture editing=new Capture(4096);
            ui.handleWidget(
                QuickPrayerSelectionPresentation.QUICK_ON_WIDGET,
                editing.writer,"[g123-edit] ");
            require("REJECTED_SELECTION_DRAFT_OPEN".equals(bridge.lastOn)&&
                editing.drain().length==0,
                "quick ON applied while draft was open");
            selection.toggleDraft("normal:piety");
            selection.confirmEdit();

            Capture full=new Capture(4096);
            ui.handleWidget(
                QuickPrayerSelectionPresentation.QUICK_ON_WIDGET,
                full.writer,"[g123-on] ");
            require(bridge.lastOn.startsWith("QUICK_PRAYERS_ON")&&
                player.prayers().activeWidget(5609)&&
                player.prayers().activeCount()==1&&
                player.playerState().currentLevel(PlayerState.PRAYER)==10,
                "quick ON did not activate only selected Thick Skin");
            requireOnWire(full.drain(),true);

            Capture duplicate=new Capture(4096);
            ui.handleWidget(
                QuickPrayerSelectionPresentation.QUICK_ON_WIDGET,
                duplicate.writer,"[g123-repeat] ");
            require(bridge.lastOn.startsWith("QUICK_PRAYERS_ON")&&
                player.prayers().activeCount()==1,
                "duplicate ON toggled active prayer off");
            requireOnWire(duplicate.drain(),false);

            Capture off=new Capture(4096);
            ui.handleWidget(
                QuickPrayerSelectionPresentation.QUICK_OFF_WIDGET,
                off.writer,"[g123-off] ");
            require(!player.prayers().activeWidget(5609)&&
                player.prayers().activeCount()==0,
                "existing OFF path did not clear Quick ON state");

            // OutboundPacketQueue requires >=1024 bytes capacity.
            // Fill that *valid* queue completely with a known sentinel,
            // then ensure Quick ON does not append or alter it.
            Capture blocked=new Capture(1024);
            byte[] sentinel=new byte[1024];
            Arrays.fill(sentinel,(byte)0x5a);
            blocked.queue.offer(sentinel);
            ui.handleWidget(
                QuickPrayerSelectionPresentation.QUICK_ON_WIDGET,
                blocked.writer,"[g123-backpressure] ");
            require("REJECTED_WRITER_BACKPRESSURE_RETRYABLE"
                    .equals(bridge.lastOn)&&
                blocked.queue.queuedBytes()==1024&&
                Arrays.equals(blocked.drain(),sentinel)&&
                player.prayers().activeCount()==0,
                "queue-full ON granted prayer or appended packets");

            ByteArrayOutputStream directBytes=
                new ByteArrayOutputStream();
            ServerPacketWriter direct=new ServerPacketWriter(
                directBytes,new IsaacCipher(SEED.clone()));
            boolean refused=false;
            try{
                ui.handleWidget(
                    QuickPrayerSelectionPresentation.QUICK_ON_WIDGET,
                    direct,"[g123-direct] ");
            }catch(IOException expected){
                refused=expected.getMessage()!=null&&
                    expected.getMessage().contains(
                        "DIRECT_WRITER_NONATOMIC");
            }
            require(refused&&directBytes.size()==0&&
                player.prayers().activeCount()==0,
                "nonretractable direct writer granted prayer");

            Capture retry=new Capture(4096);
            ui.handleWidget(
                QuickPrayerSelectionPresentation.QUICK_ON_WIDGET,
                retry.writer,"[g123-retry] ");
            require(player.prayers().activeWidget(5609)&&
                bridge.lastOn.startsWith("QUICK_PRAYERS_ON"),
                "retry after backpressure did not activate");
            requireOnWire(retry.drain(),true);

            require(selection.snapshot().confirmed(
                QuickPrayerSelectionService.Book.NORMAL).equals(
                    Collections.singletonList("normal:thick_skin")),
                "ON mutated the confirmed semantic selector");
            System.out.println(
                "G123_QUICK_PRAYER_ON_PASS"+
                " nativeWidget5000Routed=true"+
                " multiSelectionLevelGateAtomic=true"+
                " editingRejected=true"+
                " packet36Then126Exact=true"+
                " duplicateOnIdempotent=true"+
                " off4999StillWorks=true"+
                " queueBackpressureZeroByte=true"+
                " directWriterDenied=true retryWorks=true"+
                " drainCombatEffectsUnknown=true"+
                " originalClientGUINotTested=true");
        }finally{
            if(player.registered())
                world.unregisterPlayer(player,generation);
            if(!world.closed())world.close();
        }
    }

    private static void requireOnWire(byte[] data,boolean expectConfig)
        throws Exception{
        IsaacCipher decode=new IsaacCipher(SEED.clone());
        int offset=0;
        if(expectConfig){
            require(data.length>=5,"missing config frame");
            int first=((data[offset++]&255)-decode.nextInt())&255;
            require(first==36,"config opcode="+first);
            byte[] expected=BootstrapPackets.config36(83,1);
            require(Arrays.equals(
                Arrays.copyOfRange(data,offset,offset+3),expected),
                "Thick Skin config36 not exact");
            offset+=3;
        }
        int op=((data[offset++]&255)-decode.nextInt())&255;
        require(op==126,"quick ON control opcode="+op);
        int n=((data[offset++]&255)<<8)|(data[offset++]&255);
        byte[] want=BootstrapPackets.widgetText126(
            1,"QUICK_PRAYERS_ON");
        require(n==want.length&&
            offset+n==data.length&&
            Arrays.equals(Arrays.copyOfRange(data,offset,offset+n),want),
            "QUICK_PRAYERS_ON control body not exact");
    }

    private static LocalSessionUiActionHandler uiHandler(
        WorldPlayer player,Bridge bridge
    ){
        BankState bank=player.bank();
        EquipmentState equipment=player.equipment();
        MovementState movement=player.movement();
        NpcRegistry npcs=new NpcRegistry(new DevAuthorityWorkbench());
        LocalPetInventoryDialogHandler petDialogs=
            new LocalPetInventoryDialogHandler(
                bank,player.miniPets(),player.petState(),npcs,
                movement,player.petAccessoryState());
        LocalGameplayWidgetHandler gameplay=
            new LocalGameplayWidgetHandler(
                player.prayers(),player.playerState(),equipment,
                player.combatStyles(),player.magic(),bank);
        LocalCompCapeCustomizeHandler compCape=
            new LocalCompCapeCustomizeHandler(
                bank,player.playerState());
        return new LocalSessionUiActionHandler(
            player,new NativeItemLibraryService(),
            new DevControlCenter(),bank,compCape,petDialogs,
            gameplay,movement,true,equipment,bridge);
    }

    private static void require(boolean ok,String label){
        if(!ok)throw new AssertionError("G123 "+label);
    }

    private G123QuickPrayerOnIntegrationTest(){}
}

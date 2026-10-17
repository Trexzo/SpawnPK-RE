package spk.local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * R5 negative safety regression: the v308-exact opcode framing accepts a
 * possible widget-33006 Save click plus the paired loadout save commands,
 * but client command data alone never identifies which semantic loadout
 * revision the server should mutate.
 *
 * The *association* of 33006 to the save pair is archived-candidate evidence
 * only; this fixture is not a certification of the exact client call order.
 */
public final class R5LoadoutEditorButtonPairNoTargetRegressionTest {
    private static final int SAVE_WIDGET=33006;
    private static final int DEFAULT_WIDGET=33009;
    private static final int[] SEED={1257,1258,1259,1260};

    public static void main(String[] args)throws Exception{
        buttonBeforePairRemainsTargetless();
        interveningWidgetExpiresHalfPair();
        System.out.println(
            "R5_LOADOUT_EDITOR_BUTTON_PAIR_NO_TARGET_PASS "+
            "widget185Decoded=true "+
            "saveButtonIndependentOfPair=true "+
            "inventoryAndEquipmentPaired=true "+
            "targetIdentityNotInWire=true "+
            "saveWithoutTargetFailClosed=true "+
            "semanticLoadoutUnchanged=true "+
            "defaultWidgetDoesNotCreateTarget=true "+
            "intervening185ExpiresHalfPair=true "+
            "normalCommandsRemainAvailable=true "+
            "originalServerTargetPolicyClaim=false"
        );
    }

    private static void buttonBeforePairRemainsTargetless()throws Exception{
        LoadoutService loadouts=new LoadoutService();
        PlayerLoadout original=seedLoadout();
        LoadoutService.Snapshot before=loadouts.create(original);
        new LoadoutEditorService(loadouts); // Existing, intentionally not invoked.

        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        IsaacCipher encode=new IsaacCipher(SEED.clone());
        writeWidget(bytes,encode,SAVE_WIDGET);
        writeCommand(bytes,encode,"cld1 "+inventoryPayload());
        writeCommand(bytes,encode,"cld2 "+equipmentPayload());
        writeWidget(bytes,encode,DEFAULT_WIDGET);
        writeCommand(bytes,encode,"ordinary_after_loadout");

        ClientPacketProbe probe=new ClientPacketProbe(
            new ByteArrayInputStream(bytes.toByteArray()),
            new IsaacCipher(SEED.clone()),"[r5-no-target] "
        );

        require(probe.readNextKnownPacket(),"decode Save widget185");
        ClientRequest click=probe.takeTypedRequest();
        require(click instanceof WidgetActionClientRequest,
            "expected typed widget185");
        require(((WidgetActionClientRequest)click).widgetId()==SAVE_WIDGET,
            "Save widget identity");
        require(click.metadata().opcode==185&&
            click.metadata().provenance==ClientRequestProvenance.EXACT_CURRENT_CLIENT,
            "exact opcode185 framing");

        require(probe.readNextKnownPacket(),"decode cld1");
        require(probe.typedRequestCount()==0,"cld1 must remain staged");

        require(probe.readNextKnownPacket(),"decode cld2");
        require(probe.typedRequestCount()==1,"one complete pair");
        ClientRequest raw=probe.takeTypedRequest();
        require(raw instanceof LoadoutEditorSaveClientRequest,
            "typed complete save pair");
        LoadoutEditorSaveClientRequest paired=(LoadoutEditorSaveClientRequest)raw;
        require(paired.inventory().size()==28&&paired.equipment().size()==14,
            "bounded v308 pair counts");
        require(paired.inventoryPacketSequence()==2L&&
                paired.equipmentPacketSequence()==3L,
            "widget action must not be silently attached to pair metadata");

        // This existing dispatcher intentionally does not select any loadout.
        String diagnostic=LocalPendingRequestDispatcher
            .loadoutEditorSaveFailClosedDiagnostic(paired);
        require(diagnostic!=null&&
            diagnostic.contains("LOADOUT_EDITOR_TARGET_ADAPTER_UNPROVEN")&&
            diagnostic.contains("stateMutation=false"),
            "pair without semantic editor context did not fail closed");
        unchanged(loadouts,before,"Save click plus pair");

        require(probe.readNextKnownPacket(),"decode default widget");
        ClientRequest defaultAction=probe.takeTypedRequest();
        require(defaultAction instanceof WidgetActionClientRequest&&
            ((WidgetActionClientRequest)defaultAction).widgetId()==DEFAULT_WIDGET,
            "default widget must remain distinct typed intent");
        // No known target/version can be inferred from the text "Set as default".
        unchanged(loadouts,before,"unproven set-default");

        require(probe.readNextKnownPacket(),"decode subsequent command");
        ClientRequest ordinary=probe.takeTypedRequest();
        require(ordinary instanceof CommandClientRequest&&
            "ordinary_after_loadout".equals(
                ((CommandClientRequest)ordinary).command()),
            "normal client commands preserved");
        require(probe.isAligned()&&probe.takeTypedRequest()==null,
            "probe alignment / queue");
        unchanged(loadouts,before,"after packet sequence");
    }

    private static void interveningWidgetExpiresHalfPair()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        IsaacCipher encoder=new IsaacCipher(SEED.clone());
        writeCommand(bytes,encoder,"cld1 "+inventoryPayload());
        writeWidget(bytes,encoder,DEFAULT_WIDGET);
        writeCommand(bytes,encoder,"cld2 "+equipmentPayload());

        ClientPacketProbe probe=new ClientPacketProbe(
            new ByteArrayInputStream(bytes.toByteArray()),
            new IsaacCipher(SEED.clone()),"[r5-half-expiry] "
        );
        require(probe.readNextKnownPacket(),"decode staged inventory half");
        require(probe.typedRequestCount()==0,"no half request");

        require(probe.readNextKnownPacket(),"decode intervening widget");
        ClientRequest intervening=probe.takeTypedRequest();
        require(intervening instanceof WidgetActionClientRequest&&
            ((WidgetActionClientRequest)intervening).widgetId()==DEFAULT_WIDGET,
            "default button independent of saved half");

        require(probe.readNextKnownPacket(),"decode orphan equipment half");
        require(probe.takeTypedRequest()==null&&probe.isAligned(),
            "intervening button must expire inventory half, not complete save");
    }

    private static PlayerLoadout seedLoadout(){
        return new PlayerLoadout(
            PlayerLoadoutId.of("loadout:r5-fixture"),
            PlayerLoadoutVersion.of(1L),
            "player:r5-fixture",
            Collections.singletonList(new PlayerLoadout.InventoryEntry(
                "item:fixture-original",3L)),
            Collections.singletonList(new PlayerLoadout.EquipmentEntry(
                "slot:weapon","item:fixture-original-weapon",1L)),
            new PlayerLoadout.SkillProfile(Collections.singletonList(
                new PlayerLoadout.SkillValue("skill:attack",50L))),
            new PlayerLoadout.PetSelection("pet:fixture"),
            "LOCAL_LAB_POLICY_R5_TEST"
        );
    }

    private static void unchanged(
        LoadoutService service,LoadoutService.Snapshot expected,String reason
    ){
        LoadoutService.Snapshot actual=service.get(
            expected.loadout.ownerRef,expected.loadout.id);
        require(actual.loadout==expected.loadout&&
            actual.lastAppliedVersion==expected.lastAppliedVersion&&
            actual.loadout.version.equals(PlayerLoadoutVersion.of(1L)),
            reason+" changed a loadout without proven editor target");
    }

    private static String inventoryPayload(){
        List<String> parts=new ArrayList<>();
        for(int i=0;i<28;i++)parts.add((100+i)+","+(i+1));
        return String.join(" ",parts);
    }

    private static String equipmentPayload(){
        List<String> parts=new ArrayList<>();
        for(int source:LoadoutEditorCompatibilityPairer.equipmentSourceIndices())
            parts.add(source<0?"0,0":(2000+source)+","+(20+source));
        return String.join(" ",parts);
    }

    private static void writeWidget(
        OutputStream output,IsaacCipher cipher,int widget
    )throws IOException{
        output.write((185+cipher.nextInt())&255);
        output.write((widget>>>8)&255);
        output.write(widget&255);
    }

    private static void writeCommand(
        OutputStream output,IsaacCipher cipher,String command
    )throws IOException{
        byte[] text=command.getBytes(StandardCharsets.ISO_8859_1);
        int length=text.length+1;
        if(length>255)throw new IllegalArgumentException("command too long");
        output.write((103+cipher.nextInt())&255);
        output.write(length);
        output.write(text);
        output.write(10);
    }

    private static void require(boolean ok,String reason){
        if(!ok)throw new AssertionError(reason);
    }

    private R5LoadoutEditorButtonPairNoTargetRegressionTest(){}
}

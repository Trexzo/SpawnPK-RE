package spk.local;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * R28: exercise the real LocalSessionUiActionHandler.handleWidget route and
 * SessionBridge.saveAccount. No-op/closed bank widgets must not request a
 * persistent save; completed inventory and settings mutations still do.
 *
 * Source-only: save callback is a probe, not a durable account receipt.
 */
public final class BankUiMutationSaveBridgeRegression {
    private BankUiMutationSaveBridgeRegression(){}

    private static final class Bridge
        implements LocalSessionUiActionHandler.SessionBridge
    {
        final List<String> saveReasons=new ArrayList<>();

        @Override public void saveAccount(String tag,String reason){
            saveReasons.add(reason);
        }
        @Override public void clearDialogNumberKeys(){}
        @Override public void handleDevPanelWidget(
            int widget,ServerPacketWriter packets,String tag){}
        @Override public void applyPetDialog(
            LocalPetInventoryDialogHandler.Result result,String tag){}
        @Override public void requestLogout(){}
    }

    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(
            "BANK_UI_MUTATION_SAVE_BRIDGE "+message);
    }

    private static void noSave(
        LocalSessionUiActionHandler ui,
        Bridge bridge,BankState bank,
        ServerPacketWriter writer,
        ByteArrayOutputStream wire,
        int widget,String scenario
    )throws Exception{
        long bankBefore=bank.bankMutationRevision();
        long inventoryBefore=bank.inventoryMutationRevision();
        int bytesBefore=wire.size();
        int savesBefore=bridge.saveReasons.size();

        ui.handleWidget(widget,writer,"[bank-ui-save] ");

        check(bridge.saveReasons.size()==savesBefore,
            scenario+" generated an account save callback");
        check(bank.bankMutationRevision()==bankBefore&&
              bank.inventoryMutationRevision()==inventoryBefore,
            scenario+" advanced semantic mutation lineage");
        check(wire.size()==bytesBefore,
            scenario+" published an unnecessary packet");
    }

    private static void expectSave(
        LocalSessionUiActionHandler ui,
        Bridge bridge,BankState bank,
        ServerPacketWriter writer,
        int widget,String reason,String scenario
    )throws Exception{
        int prior=bridge.saveReasons.size();
        ui.handleWidget(widget,writer,"[bank-ui-save] ");
        check(bridge.saveReasons.size()==prior+1&&
              reason.equals(bridge.saveReasons.get(prior)),
            scenario+" missing or duplicated save request");
    }

    public static void main(String[] args)throws Exception{
        WorldPlayer player=new WorldPlayer();
        BankState bank=player.bank();
        EquipmentState equipment=player.equipment();
        MovementState movement=player.movement();
        PetState pets=player.petState();
        PlayerState state=player.playerState();
        DevAuthorityWorkbench dev=new DevAuthorityWorkbench();
        NpcRegistry npcs=new NpcRegistry(dev);

        Bridge bridge=new Bridge();
        LocalSessionUiActionHandler ui=
            new LocalSessionUiActionHandler(
                player,
                new NativeItemLibraryService(),
                new DevControlCenter(),
                bank,
                new LocalCompCapeCustomizeHandler(bank,state),
                new LocalPetInventoryDialogHandler(
                    bank,player.miniPets(),pets,npcs,movement,
                    player.petAccessoryState()),
                new LocalGameplayWidgetHandler(
                    player.prayers(),state,equipment,
                    player.combatStyles(),player.magic(),bank),
                movement,true,equipment,bridge);

        ByteArrayOutputStream wire=new ByteArrayOutputStream();
        ServerPacketWriter writer=new ServerPacketWriter(
            wire,new IsaacCipher(new int[]{1,2,3,4}));

        noSave(ui,bridge,bank,writer,wire,
            BankState.DEPOSIT_INVENTORY_WIDGET,
            "deposit inventory with closed bank");
        noSave(ui,bridge,bank,writer,wire,
            BankState.TOGGLE_PLACEHOLDERS_WIDGET,
            "placeholder toggle with closed bank");

        bank.open(writer);
        check(bank.isOpen(),"bank open precondition");
        noSave(ui,bridge,bank,writer,wire,
            BankState.DEPOSIT_INVENTORY_WIDGET,
            "open bank empty deposit");

        long bankBefore=bank.bankMutationRevision();
        expectSave(ui,bridge,bank,writer,
            BankState.TOGGLE_PLACEHOLDERS_WIDGET,
            "BANK_PLACEHOLDERS",
            "real placeholder metadata toggle");
        check(bank.placeholdersEnabled()&&
              bank.bankMutationRevision()==bankBefore+1,
              "real placeholder widget did not change settings");

        int[] ids=new int[bank.inventoryCapacity()];
        int[] amounts=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[0]=995;amounts[0]=5;
        bank.replaceInventorySemantic(ids,amounts);

        int startingCoins=bank.bankAt(0).qty;
        long invBefore=bank.inventoryMutationRevision();
        expectSave(ui,bridge,bank,writer,
            BankState.DEPOSIT_INVENTORY_WIDGET,
            "BANK_DEPOSIT_INVENTORY",
            "real five-coin bank deposit");
        check(bank.bankAt(0).qty==startingCoins+5&&
              bank.inventoryCount(995)==0&&
              bank.inventoryMutationRevision()==invBefore+1,
              "real deposit callback did not correspond to exact coins");

        noSave(ui,bridge,bank,writer,wire,
            BankState.DEPOSIT_INVENTORY_WIDGET,
            "repeat deposit after bank inventory empty");

        bank.close(writer);
        check(!bank.isOpen(),"bank close precondition");
        noSave(ui,bridge,bank,writer,wire,
            BankState.TOGGLE_PLACEHOLDERS_WIDGET,
            "closed bank placeholder toggle after deposit");
        noSave(ui,bridge,bank,writer,wire,
            BankState.DEPOSIT_INVENTORY_WIDGET,
            "closed bank deposit after prior success");
        check(bridge.saveReasons.size()==2,
            "unexpected total bank widget account saves: "+
            bridge.saveReasons);

        System.out.println(
            "BANK_UI_MUTATION_SAVE_BRIDGE_PASS"+
            " closedWidgetsNoSave=true"+
            " emptyOpenDepositNoSave=true"+
            " validSettingsChangeSaveExactlyOnce=true"+
            " validDepositSaveExactlyOnce=true"+
            " repeatDepositNoSave=true"+
            " noOpPacketAndMutationLineageStable=true"+
            " sourceOnlyNoDurableAccountReceipt=true");
    }
}

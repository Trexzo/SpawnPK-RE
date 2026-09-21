package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Drains decoded client intent in the exact established LocalSession order.
 *
 * The caller is responsible for entering the authoritative World execution
 * context before invoking this dispatcher. This class owns request consumption
 * and domain routing, not socket reads or WorldCommandInbox handoff.
 */
final class LocalPendingRequestDispatcher {
    interface SessionBridge {
        String username();
        String loginAlias();
        boolean persistentAccount();
        long sessionWorldTick();
        SceneUpdatePublisher scenePublisher();
        Player81WorldSync.Context player81Sync();
        void refreshPlayerAppearance(
            ServerPacketWriter writer
        )throws IOException;
        void saveAccount(String tag,String reason);
        void applyPetDialogResult(
            LocalPetInventoryDialogHandler.Result result,
            String tag
        );
        void clearOpponentOverlay(
            ServerPacketWriter writer,
            String tag,
            String reason
        )throws IOException;
        void handleDevPanelAmount(
            int value,
            ServerPacketWriter writer,
            String tag
        )throws IOException;
    }

    private final WorldPlayer worldPlayer;
    private final BankState bank;
    private final EquipmentState equipment;
    private final CombatStyleState combatStyles;
    private final MovementState movement;
    private final NpcRegistry npcs;
    private final CombatEngine combat;
    private final DevControlCenter devPanel;
    private final LocalSessionUiActionHandler uiActions;
    private final LocalCommandDispatcher commandDispatcher;
    private final LocalBankObjectInteractionHandler bankObjectHandler;
    private final LocalGenericInteractionHandler genericInteractionHandler;
    private final LocalEquipmentItemActionHandler equipmentItemActions;
    private final LocalPetInventoryDialogHandler petDialogs;
    private final LocalMakeoverMageHandler makeoverMage;
    private final LocalCompCapeCustomizeHandler compCapeCustomize;
    private final LocalItemOnItemHandler itemOnItemHandler;
    private final LocalItemOnNpcHandler itemOnNpcHandler;
    private final LocalSpellTargetHandler spellTargetHandler;
    private final LocalPetDropPickupHandler petDropPickup;
    private final LocalGroundItemInteractionHandler groundItemHandler;
    private final LocalPlayerInteractionHandler playerInteractions;
    private final LocalRoutedNpcInteractionHandler routedNpcHandler;
    private final LocalBankRequestHandler bankRequests;
    private final LocalMovementRequestHandler movementRequests;
    private final LocalPetRealtimeScheduler petRealtime;
    private final SessionBridge bridge;

    LocalPendingRequestDispatcher(
        WorldPlayer worldPlayer,
        BankState bank,
        EquipmentState equipment,
        CombatStyleState combatStyles,
        MovementState movement,
        NpcRegistry npcs,
        CombatEngine combat,
        DevControlCenter devPanel,
        LocalSessionUiActionHandler uiActions,
        LocalCommandDispatcher commandDispatcher,
        LocalBankObjectInteractionHandler bankObjectHandler,
        LocalGenericInteractionHandler genericInteractionHandler,
        LocalEquipmentItemActionHandler equipmentItemActions,
        LocalPetInventoryDialogHandler petDialogs,
        LocalCompCapeCustomizeHandler compCapeCustomize,
        LocalItemOnItemHandler itemOnItemHandler,
        LocalItemOnNpcHandler itemOnNpcHandler,
        LocalSpellTargetHandler spellTargetHandler,
        LocalPetDropPickupHandler petDropPickup,
        LocalGroundItemInteractionHandler groundItemHandler,
        LocalPlayerInteractionHandler playerInteractions,
        LocalRoutedNpcInteractionHandler routedNpcHandler,
        LocalBankRequestHandler bankRequests,
        LocalMovementRequestHandler movementRequests,
        LocalPetRealtimeScheduler petRealtime,
        SessionBridge bridge
    ){
        this.worldPlayer=Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.bank=Objects.requireNonNull(bank,"bank");
        this.equipment=Objects.requireNonNull(equipment,"equipment");
        this.combatStyles=Objects.requireNonNull(combatStyles,"combatStyles");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.npcs=Objects.requireNonNull(npcs,"npcs");
        this.combat=Objects.requireNonNull(combat,"combat");
        this.devPanel=Objects.requireNonNull(devPanel,"devPanel");
        this.uiActions=Objects.requireNonNull(uiActions,"uiActions");
        this.commandDispatcher=Objects.requireNonNull(
            commandDispatcher,"commandDispatcher");
        this.bankObjectHandler=Objects.requireNonNull(
            bankObjectHandler,"bankObjectHandler");
        this.genericInteractionHandler=Objects.requireNonNull(
            genericInteractionHandler,"genericInteractionHandler");
        this.equipmentItemActions=Objects.requireNonNull(
            equipmentItemActions,"equipmentItemActions");
        this.petDialogs=Objects.requireNonNull(petDialogs,"petDialogs");
        this.makeoverMage=
            new LocalMakeoverMageHandler(
                worldPlayer,
                equipment
            );
        this.compCapeCustomize=Objects.requireNonNull(
            compCapeCustomize,"compCapeCustomize");
        this.itemOnItemHandler=Objects.requireNonNull(
            itemOnItemHandler,"itemOnItemHandler");
        this.itemOnNpcHandler=Objects.requireNonNull(
            itemOnNpcHandler,"itemOnNpcHandler");
        this.spellTargetHandler=Objects.requireNonNull(
            spellTargetHandler,"spellTargetHandler");
        this.petDropPickup=Objects.requireNonNull(
            petDropPickup,"petDropPickup");
        this.groundItemHandler=Objects.requireNonNull(
            groundItemHandler,"groundItemHandler");
        this.playerInteractions=Objects.requireNonNull(
            playerInteractions,"playerInteractions");
        this.routedNpcHandler=Objects.requireNonNull(
            routedNpcHandler,"routedNpcHandler");
        this.bankRequests=Objects.requireNonNull(
            bankRequests,"bankRequests");
        this.movementRequests=Objects.requireNonNull(
            movementRequests,"movementRequests");
        this.petRealtime=Objects.requireNonNull(
            petRealtime,"petRealtime");
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    void drain(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        Objects.requireNonNull(clientPackets,"clientPackets");
        Objects.requireNonNull(serverPackets,"serverPackets");

        acceptInterfaceClose(
            clientPackets,
            serverPackets,
            tag
        );
        acceptDialogueContinue(
            clientPackets,
            serverPackets,
            tag
        );
        acceptWidgetAction(
            clientPackets,
            serverPackets,
            tag
        );
        acceptCharacterDesign(
            clientPackets,
            serverPackets,
            tag
        );
        acceptCommand(
            clientPackets,
            serverPackets,
            tag
        );
        acceptObjectInteraction(
            clientPackets,
            serverPackets,
            tag
        );
        acceptGenericInteraction(
            clientPackets,
            tag
        );
        acceptItemAction(
            clientPackets,
            serverPackets,
            tag
        );
        acceptItemOnItem(
            clientPackets,
            serverPackets,
            tag
        );
        acceptItemOnNpc(
            clientPackets,
            serverPackets,
            tag
        );
        acceptSpellTarget(
            clientPackets,
            serverPackets,
            tag
        );
        acceptDropItem(
            clientPackets,
            serverPackets,
            tag
        );
        acceptGroundItemInteraction(
            clientPackets,
            serverPackets,
            tag
        );
        acceptPlayerAction(
            clientPackets,
            serverPackets,
            tag
        );
        acceptNpcAction(
            clientPackets,
            serverPackets,
            tag
        );
        acceptAmount(
            clientPackets,
            serverPackets,
            tag
        );
        acceptContainerDrag(
            clientPackets,
            serverPackets,
            tag
        );
        acceptMovement(
            clientPackets,
            serverPackets,
            tag
        );

        long now=System.currentTimeMillis();
        petRealtime.ensureFollowScheduled(now);
        petRealtime.ensureTestSequenceScheduled(now);
    }

    private void acceptInterfaceClose(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        if(!clientPackets.takeInterfaceClose())return;

        boolean makeoverCancelled=
            makeoverMage.cancel();

        if(makeoverCancelled)
            System.out.println(
                tag+
                "MAKEOVER_MAGE_DIALOG_CANCEL reason=CLIENT_INTERFACE_CLOSE"
            );

        uiActions.handleInterfaceClose(
            clientPackets.isAligned(),
            serverPackets,
            tag
        );
    }

    private void acceptDialogueContinue(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        Integer widget=
            clientPackets.takeDialogueContinue();
        if(widget==null)return;

        if(makeoverMage.handleContinue(
                widget.intValue(),
                serverPackets,
                tag))
            return;

        System.out.println(
            tag+
            "DIALOGUE_CONTINUE_UNHANDLED widget="+
            widget+
            " framingPreserved=true"
        );
    }

    private void acceptWidgetAction(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        Integer widget=clientPackets.takeWidgetAction();
        if(widget==null)return;

        if(makeoverMage.handleWidget(
                widget.intValue(),
                serverPackets,
                tag))
            return;

        uiActions.handleWidget(
            widget.intValue(),
            serverPackets,
            tag
        );
    }

    private void acceptCharacterDesign(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        CharacterDesignRequest request=
            clientPackets.takeCharacterDesign();
        if(request==null)return;

        LocalMakeoverMageHandler.Result result=
            makeoverMage.handleDesign(
                request,
                serverPackets,
                tag
            );

        if(!result.handled){
            System.out.println(
                tag+
                "CHARACTER_DESIGN_UNHANDLED request="+
                request
            );
            return;
        }

        if(result.saveReason!=null){
            bridge.refreshPlayerAppearance(
                serverPackets
            );
            bridge.saveAccount(
                tag,
                result.saveReason
            );
        }

        if(result.logText!=null)
            System.out.println(
                tag+
                result.logText
            );
    }

    private void acceptCommand(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        String command=clientPackets.takeCommand();
        if(command==null)return;

        commandDispatcher.handle(
            command,
            clientPackets.isAligned(),
            bridge.username(),
            bridge.loginAlias(),
            bridge.persistentAccount(),
            bridge.sessionWorldTick(),
            serverPackets,
            tag
        );
    }

    private void acceptObjectInteraction(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        ObjectInteraction request=
            clientPackets.takeObjectInteraction();

        if(request==null)return;

        String result=
            bankObjectHandler.handle(
                request,
                serverPackets
            );

        if(result!=null)
            System.out.println(tag+result);
    }

    private void acceptGenericInteraction(
        ClientPacketProbe clientPackets,
        String tag
    ){
        for(
            GenericInteractionEvent event;
            (event=R85GenericC2SBridge.take(
                clientPackets
            ))!=null;
        ){
            String result=
                genericInteractionHandler.handle(event);

            if(result!=null)
                System.out.println(tag+result);
        }
    }

    private void acceptItemAction(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        ItemContainerAction action=
            clientPackets.takeItemAction();

        if(action==null)return;

        String tradeItem=
            TradeService.handleItemAction(
                worldPlayer,
                action
            );

        if(tradeItem!=null){
            System.out.println(
                tag+"V5140_TRADE_ITEM "+
                action+
                " result="+tradeItem
            );
            return;
        }

        LocalEquipmentItemActionHandler.Result equipmentAction=
            equipmentItemActions.handle(
                action,
                bridge.username(),
                serverPackets
            );

        if(equipmentAction!=null){
            for(String line:
                equipmentAction.beforeSaveLogs){
                System.out.println(tag+line);
            }

            if(equipmentAction.saveReason!=null){
                bridge.saveAccount(
                    tag,
                    equipmentAction.saveReason
                );
            }

            for(String line:
                equipmentAction.afterSaveLogs){
                System.out.println(tag+line);
            }
            return;
        }

        LocalPetInventoryDialogHandler.Result petDialogItem=
            petDialogs.handleItemAction(
                action,
                serverPackets
            );

        if(petDialogItem!=null){
            bridge.applyPetDialogResult(
                petDialogItem,
                tag
            );
            return;
        }

        String compCapeItem=
            compCapeCustomize.handleItemAction(
                action,
                serverPackets
            );

        if(compCapeItem!=null){
            System.out.println(tag+compCapeItem);
            return;
        }

        String result=
            bank.apply(
                action,
                serverPackets
            );

        bridge.saveAccount(
            tag,
            "BANK_ITEM_ACTION"
        );

        System.out.println(
            tag+"V522_BANK_ITEM_ACTION "+
            action+
            " result="+result+
            " decoderAligned=true"
        );
    }

    private void acceptItemOnItem(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        ItemOnItemAction action=
            clientPackets.takeItemOnItem();

        if(action==null)return;

        LocalItemOnItemHandler.Result result=
            itemOnItemHandler.handle(
                action,
                serverPackets
            );

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+result.logText
        );
    }

    private void acceptItemOnNpc(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        ItemOnNpcAction action=
            clientPackets.takeItemOnNpc();

        if(action==null)return;

        LocalItemOnNpcHandler.Result result=
            itemOnNpcHandler.handle(
                action,
                serverPackets
            );

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+result.logText
        );
    }

    private void acceptSpellTarget(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        SpellTargetRequest request=
            clientPackets.takeSpellTarget();

        if(request==null)return;

        System.out.println(
            tag+
            spellTargetHandler.handle(
                request,
                serverPackets
            )
        );
    }

    private void acceptDropItem(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        DropItemAction action=
            clientPackets.takeDropItem();

        if(action==null)return;

        petDropPickup.handleDrop(
            action,
            serverPackets,
            tag
        );
    }

    private void acceptGroundItemInteraction(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        GroundItemInteraction action=
            clientPackets.takeGroundItemInteraction();

        if(action==null)return;

        applyGroundItemResult(
            groundItemHandler.handle(
                action,
                bridge.username(),
                bridge.scenePublisher(),
                serverPackets
            ),
            tag
        );
    }

    private void applyGroundItemResult(
        LocalGroundItemInteractionHandler.Result result,
        String tag
    ){
        if(result==null)return;

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+result.logText
        );
    }

    private void acceptPlayerAction(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        PlayerAction action=
            clientPackets.takePlayerAction();

        if(action==null)return;

        Player81WorldSync.Context playerSync=
            bridge.player81Sync();

        if(playerSync==null){
            System.out.println(
                tag+
                "V5131_PLAYER_ACTION "+
                action+
                " result=REJECTED_SYNC_NOT_READY"
            );
            return;
        }

        WorldPlayer target=
            playerSync.resolveVisible(
                action.playerIndex
            );

        if(target==null||
           !target.registered()){
            System.out.println(
                tag+
                "V5131_PLAYER_ACTION "+
                action+
                " result=REJECTED_STALE_OR_NOT_VISIBLE"
            );
            return;
        }

        if(combat.active()){
            boolean cancelled=
                combat.cancelForManualMovement();

            if(cancelled){
                bridge.clearOpponentOverlay(
                    serverPackets,
                    tag,
                    "PLAYER_INTERACTION_REPLACES_NPC_COMBAT"
                );
            }
        }

        String result=
            playerInteractions.handleResolved(
                action,
                target,
                playerSync
            );

        if(result!=null)
            System.out.println(tag+result);
    }

    private void acceptNpcAction(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        NpcAction action=
            clientPackets.takeNpcAction();

        if(action==null)return;

        NpcEntity clicked=
            npcs.scene(action.sceneIndex);

        NpcEntity pet=npcs.pet();

        if(petDropPickup.handlePickupNpcAction(
            action,
            serverPackets,
            tag
        )){
            return;
        }

        petDropPickup.cancelDeferredForNewNpcAction(
            action,
            tag
        );

        if(clicked!=null&&
           clicked==pet&&
           clicked.definitionId==1334&&
           action.opcode==17){
            String reset=
                LocalDevVisualOverrideStore.set(
                    "intrinsicfx",
                    null
                );

            System.out.println(
                tag+
                "V5128_YOSHIGANGER_SWITCH_EFFECT scene="+
                clicked.sceneIndex+
                " result=PENDING_FUNCTIONAL_MODE_RECONSTRUCTION visualAccessoryInvented=false bodyGreenPreserved=true overrideReset="+
                reset
            );
            return;
        }

        if(makeoverMage.beginIfSupported(
                action,
                clicked,
                serverPackets,
                tag))
            return;

        if(isCombatAttackAction(
            action,
            clicked
        )){
            int combatRoot=
                CombatInterfaceRepository.forWeapon(
                    equipment.weapon()
                );

            long now=System.currentTimeMillis();

            String result=
                combat.request(
                    clicked,
                    movement,
                    equipment.weapon(),
                    now,
                    combatStyles.current(combatRoot),
                    serverPackets
                );

            String approach="NONE";

            if(result.contains(
                "TARGET_DEFERRED_RANGE"
            )){
                approach=
                    combat.beginServerOwnedApproach(
                        clicked,
                        movement,
                        equipment.weapon(),
                        now
                    );
            }

            System.out.println(
                tag+
                "V5123_COMBAT_REQUEST "+
                action+
                " semantic=NPC_ATTACK clicked="+
                clicked+
                " result="+result+
                " approach="+approach
            );
            return;
        }

        String routed=
            routedNpcHandler.handle(
                action,
                clicked,
                serverPackets
            );

        if(routed!=null)
            System.out.println(tag+routed);
    }

    private void acceptAmount(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        Integer amount=clientPackets.takeAmount();
        if(amount==null)return;

        if(devPanel.hasPending()){
            bridge.handleDevPanelAmount(
                amount.intValue(),
                serverPackets,
                tag
            );
            return;
        }

        LocalBankRequestHandler.Result result=
            bankRequests.handleAmount(
                amount.intValue(),
                serverPackets
            );

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+
            result.logText+
            " decoderAligned="+
            clientPackets.isAligned()
        );
    }

    private void acceptContainerDrag(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        ContainerDrag drag=
            clientPackets.takeContainerDrag();

        if(drag==null)return;

        LocalBankRequestHandler.Result result=
            bankRequests.handleDrag(
                drag,
                serverPackets
            );

        if(result.saveReason!=null)
            bridge.saveAccount(
                tag,
                result.saveReason
            );

        System.out.println(
            tag+
            result.logText+
            " decoderAligned="+
            clientPackets.isAligned()
        );
    }

    private void acceptMovement(
        ClientPacketProbe clientPackets,
        ServerPacketWriter serverPackets,
        String tag
    )throws IOException{
        MovementRequest request=
            clientPackets.takeMovement();

        if(request==null)return;

        movementRequests.handle(
            request,
            serverPackets,
            tag
        );
    }

    static boolean isCombatAttackAction(
        NpcAction action,
        NpcEntity clicked
    ){
        return action!=null&&
            action.opcode==72&&
            clicked!=null&&
            CombatTargetRepository.isCombatDummy(
                clicked.definitionId
            );
    }
}

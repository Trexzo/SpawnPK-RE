package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Owns LocalLab's one-stop developer panel presentation lifecycle.
 *
 * The panel remains session-local. This coordinator owns open/render/prompt
 * transitions and delegates individual page actions to the already-extracted
 * renderer/widget/amount handlers.
 */
final class LocalDevPanelCoordinator {
    @FunctionalInterface
    interface RootReplacingAmountAction {
        LocalDevPanelAmountHandler.Outcome handle() throws IOException;
    }

    interface SessionBridge {
        String username();
        SceneUpdatePublisher scenePublisher();
        void replaceScenePublisher(SceneUpdatePublisher replacement);
        void saveAccount(String tag,String reason);
        default LocalDevPanelAmountHandler.Outcome
            handleRootReplacingAmount(
                RootReplacingAmountAction action
            )throws IOException{
            return Objects.requireNonNull(
                action,
                "action"
            ).handle();
        }
    }

    private final DevControlCenter devPanel;
    private final WorldPlayer worldPlayer;
    private final BankState bank;
    private final NativeItemLibraryService itemLibrary;
    private final LocalPetInventoryDialogHandler petDialogs;
    private final LocalDevPanelRenderer renderer;
    private final LocalDevPanelAmountHandler amounts;
    private final LocalDevPanelWidgetHandler widgets;
    private final LocalDialogNumberKeyState dialogKeys;
    private final SessionBridge bridge;

    LocalDevPanelCoordinator(
        DevControlCenter devPanel,
        WorldPlayer worldPlayer,
        BankState bank,
        NativeItemLibraryService itemLibrary,
        LocalPetInventoryDialogHandler petDialogs,
        LocalDevPanelRenderer renderer,
        LocalDevPanelAmountHandler amounts,
        LocalDevPanelWidgetHandler widgets,
        LocalDialogNumberKeyState dialogKeys,
        SessionBridge bridge
    ){
        this.devPanel=Objects.requireNonNull(devPanel,"devPanel");
        this.worldPlayer=Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.bank=Objects.requireNonNull(bank,"bank");
        this.itemLibrary=Objects.requireNonNull(itemLibrary,"itemLibrary");
        this.petDialogs=Objects.requireNonNull(petDialogs,"petDialogs");
        this.renderer=Objects.requireNonNull(renderer,"renderer");
        this.amounts=Objects.requireNonNull(amounts,"amounts");
        this.widgets=Objects.requireNonNull(widgets,"widgets");
        this.dialogKeys=Objects.requireNonNull(dialogKeys,"dialogKeys");
        this.bridge=Objects.requireNonNull(bridge,"bridge");
    }

    void open(
        DevControlCenter.Page page,
        ServerPacketWriter writer
    )throws IOException{
        DevControlCenter.StateSnapshot prior=
            devPanel.snapshot();

        writer.beginBatch();
        boolean ended=false;

        try{
            writer.fixed(
                219,
                new byte[0]
            );
            devPanel.open(page);

            if(!renderer.render(writer))
                throw new IllegalStateException(
                    "staged Dev Panel did not render"
                );

            writer.endBatch();
            ended=true;
        }catch(IOException failure){
            devPanel.restore(prior);
            if(!ended)
                try{
                    writer.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }catch(RuntimeException failure){
            devPanel.restore(prior);
            if(!ended)
                try{
                    writer.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }catch(Error failure){
            devPanel.restore(prior);
            if(!ended)
                try{
                    writer.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }

        dialogKeys.publish(
            2482,
            2483,
            2484,
            2485
        );
    }

    void render(
        ServerPacketWriter writer
    )throws IOException{
        if(renderer.render(writer)){
            dialogKeys.publish(
                2482,
                2483,
                2484,
                2485
            );
        }
    }

    void handleWidget(
        int widget,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        LocalDevPanelWidgetHandler.Outcome outcome=
            widgets.handle(
                widget,
                bridge.username(),
                bridge.scenePublisher(),
                writer
            );

        if(outcome==null)return;

        if(outcome.scenePublisher!=null)
            bridge.replaceScenePublisher(
                outcome.scenePublisher
            );

        if(outcome.saveReason!=null)
            bridge.saveAccount(
                tag,
                outcome.saveReason
            );

        if(outcome.directLogText!=null){
            System.out.println(
                tag+outcome.directLogText
            );
            return;
        }

        if(!outcome.renderAfter)return;

        if(outcome.resultText!=null&&
           !outcome.resultText.isEmpty()){
            System.out.println(
                tag+
                "V5171_DEV_PANEL page="+
                devPanel.page()+
                " choice="+
                (outcome.choice+1)+
                " result={"+
                outcome.resultText+
                "}"
            );
        }

        render(writer);
    }

    void promptAmount(
        DevControlCenter.PendingAmount pending,
        ServerPacketWriter writer
    )throws IOException{
        writer.beginBatch();
        boolean ended=false;

        try{
            writer.fixed(
                219,
                new byte[0]
            );
            writer.fixed(
                27,
                new byte[0]
            );
            writer.endBatch();
            ended=true;
        }catch(IOException failure){
            if(!ended)
                try{
                    writer.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }catch(RuntimeException failure){
            if(!ended)
                try{
                    writer.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }catch(Error failure){
            if(!ended)
                try{
                    writer.endBatch();
                }catch(Throwable ignored){}
            throw failure;
        }

        devPanel.prompt(pending);
        dialogKeys.clear();
    }

    void handleAmount(
        int value,
        ServerPacketWriter writer,
        String tag
    )throws IOException{
        LocalDevPanelAmountHandler.Outcome outcome;

        if(devPanel.pending()==
                DevControlCenter.PendingAmount.ITEM_LIBRARY_ID&&
           ItemAuthorityRepository.get(value)!=null){
            outcome=
                bridge.handleRootReplacingAmount(
                    ()->
                        amounts.handle(
                            value,
                            bridge.username(),
                            bridge.scenePublisher(),
                            writer
                        )
                );

            if(outcome==null)
                return;
        }else{
            outcome=
                amounts.handle(
                    value,
                    bridge.username(),
                    bridge.scenePublisher(),
                    writer
                );
        }

        if(outcome.scenePublisher!=null)
            bridge.replaceScenePublisher(
                outcome.scenePublisher
            );

        if(outcome.saveReason!=null)
            bridge.saveAccount(
                tag,
                outcome.saveReason
            );

        System.out.println(
            tag+
            "V5171_DEV_PANEL_AMOUNT kind="+
            outcome.pending+
            " value="+value+
            " result={"+
            outcome.resultText+
            "}"
        );

        if(outcome.reopen){
            devPanel.finishPrompt();
            render(writer);
        }else{
            devPanel.cancelPending();
        }
    }

    void closeSession(){
        if(petDialogs.hasAnyOpen()||
           devPanel.isOpen()){
            dialogKeys.clear();
        }

        devPanel.close();
    }
}

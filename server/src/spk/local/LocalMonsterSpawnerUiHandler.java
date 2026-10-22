package spk.local;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * Exact-v308 Monster Spawner widget adapter over caller-owned server policy.
 *
 * This class owns no catalog mapping, activation budget, spawn location,
 * reward/drop or combat behavior. It only translates the proven 41000-family
 * widget contract into the already-existing MonsterSpawnerService primitives.
 */
final class LocalMonsterSpawnerUiHandler {
    enum Status {
        ROW_SELECTED,
        ACTIVATED,
        DEACTIVATED
    }

    static final class Context {
        final String ownerRef;
        final MonsterSpawnerService.SessionSnapshot session;

        private Context(
            String ownerRef,
            MonsterSpawnerService.SessionSnapshot session
        ){
            this.ownerRef=ownerRef;
            this.session=session;
        }
    }

    @FunctionalInterface
    interface ActivationBudgetResolver {
        int spawnBudget(Context context);

        default String authority(){
            return "UNCONFIGURED";
        }
    }

    @FunctionalInterface
    interface SelectedNpcLabelResolver {
        String label(
            MonsterSpawnerService.CatalogEntry entry
        );

        default String authority(){
            return "UNCONFIGURED";
        }
    }

    static final class Result {
        final Status status;
        final int rowIndex;
        final int activationBudget;
        final MonsterSpawnerService.SessionSnapshot session;

        private Result(
            Status status,
            int rowIndex,
            int activationBudget,
            MonsterSpawnerService.SessionSnapshot session
        ){
            this.status=Objects.requireNonNull(
                status,
                "status"
            );
            this.rowIndex=rowIndex;
            this.activationBudget=activationBudget;
            this.session=Objects.requireNonNull(
                session,
                "session"
            );
        }
    }

    private final MonsterSpawnerService service;
    private final String ownerRef;
    private final ActivationBudgetResolver activationBudget;
    private final SelectedNpcLabelResolver selectedLabel;

    LocalMonsterSpawnerUiHandler(
        MonsterSpawnerService service,
        String ownerRef,
        ActivationBudgetResolver activationBudget,
        SelectedNpcLabelResolver selectedLabel
    ){
        this.service=Objects.requireNonNull(
            service,
            "service"
        );
        this.ownerRef=
            PartyService.requireRef(
                ownerRef
            );
        this.activationBudget=
            Objects.requireNonNull(
                activationBudget,
                "activationBudget"
            );
        this.selectedLabel=
            Objects.requireNonNull(
                selectedLabel,
                "selectedLabel"
            );

        MonsterSpawnerService.SessionSnapshot session=
            this.service.getSession(
                this.ownerRef
            );

        if(session==null)
            throw new IllegalArgumentException(
                "Monster Spawner UI requires pre-existing service session "+
                this.ownerRef
            );

        requireServerAuthority(
            this.activationBudget.authority(),
            "activation budget authority"
        );
        requireServerAuthority(
            this.selectedLabel.authority(),
            "selected label authority"
        );
    }

    boolean isOwnedBy(
        String expectedOwnerRef
    ){
        return ownerRef.equals(
            PartyService.requireRef(
                expectedOwnerRef
            )
        );
    }

    boolean isBoundToOwner(
        String expectedOwnerRef
    ){
        return isOwnedBy(
            expectedOwnerRef
        );
    }

    boolean isBoundTo(
        World expectedWorld
    ){
        World checked=
            Objects.requireNonNull(
                expectedWorld,
                "expectedWorld"
            );

        return service.isBoundTo(
            checked.npcs()
        );
    }

    boolean ownsWidget(
        int widgetId
    ){
        return MonsterSpawnerPresentation
            .resolveWidget(
                widgetId
            )!=null;
    }

    void open(
        ServerPacketWriter packets
    )throws IOException{
        ServerPacketWriter checkedPackets=
            Objects.requireNonNull(
                packets,
                "packets"
            );
        MonsterSpawnerService.SessionSnapshot before=
            service.getSession(
                ownerRef
            );

        if(before==null)
            throw new IllegalStateException(
                "Monster Spawner session disappeared "+
                ownerRef
            );

        MonsterSpawnerService.CatalogSnapshot catalog=
            service.catalog();
        String labelAuthority=
            requireServerAuthority(
                selectedLabel.authority(),
                "selected label authority"
            );
        LinkedHashMap<Integer,String> rowLabels=
            new LinkedHashMap<>();

        for(MonsterSpawnerService.CatalogEntry entry:
                catalog.entries)
            rowLabels.put(
                entry.rowIndex,
                resolveSelectedLabel(
                    entry,
                    labelAuthority
                )
            );

        String retainedLabel=null;

        if(before.selectedRowIndex!=null){
            MonsterSpawnerService.CatalogEntry entry=
                catalog.row(
                    before.selectedRowIndex
                );

            if(entry==null||
               before.selectedDefinitionId==null||
               before.selectedDefinitionId.intValue()!=
                    entry.definitionId||
               before.selectedSemanticKey==null||
               !before.selectedSemanticKey.equals(
                    entry.semanticKey
               ))
                throw new IllegalStateException(
                    "retained Monster Spawner selection identity changed owner="+
                    ownerRef
                );

            retainedLabel=
                rowLabels.get(
                    entry.rowIndex
                );
        }

        final String presentationLabel=
            retainedLabel;

        service.presentSessionCatalogIfCurrent(
            ownerRef,
            before,
            catalog,
            current->{
                MonsterSpawnerPresentation.open(
                    checkedPackets
                );

                for(MonsterSpawnerService.CatalogEntry entry:
                        catalog.entries)
                    MonsterSpawnerPresentation
                        .publishRowText(
                            checkedPackets,
                            entry.rowIndex,
                            rowLabels.get(
                                entry.rowIndex
                            )
                        );

                if(presentationLabel!=null)
                    MonsterSpawnerPresentation
                        .publishSelectedNpcText(
                            checkedPackets,
                            presentationLabel
                        );
            }
        );
    }

    Result handle(
        int widgetId,
        ServerPacketWriter packets
    )throws IOException{
        MonsterSpawnerPresentation.Input input=
            MonsterSpawnerPresentation
                .resolveWidget(
                    widgetId
                );

        if(input==null)
            return null;

        if(input.kind==
                MonsterSpawnerPresentation
                    .InputKind.SELECT_ROW)
            return selectRow(
                input.rowIndex,
                packets
            );

        if(input.kind==
                MonsterSpawnerPresentation
                    .InputKind.TOGGLE)
            return toggle();

        throw new IllegalStateException(
            "unhandled Monster Spawner input "+
            input.kind
        );
    }

    private Result selectRow(
        int rowIndex,
        ServerPacketWriter packets
    )throws IOException{
        MonsterSpawnerService.SessionSnapshot before=
            service.getSession(
                ownerRef
            );

        if(before==null)
            throw new IllegalStateException(
                "Monster Spawner session disappeared "+
                ownerRef
            );

        MonsterSpawnerService.CatalogSnapshot catalog=
            service.catalog();

        MonsterSpawnerService.CatalogEntry entry=
            catalog.row(
                rowIndex
            );

        if(entry==null)
            return null;

        if(before.active)
            throw new IllegalStateException(
                "cannot change Monster Spawner selection while active"
            );

        String label=
            resolveSelectedLabel(
                entry
            );

        MonsterSpawnerService.SessionSnapshot session=
            service.selectRowIfCurrent(
                ownerRef,
                before,
                catalog,
                entry,
                currentEntry->
                    MonsterSpawnerPresentation
                        .publishSelectedNpcText(
                            Objects.requireNonNull(
                                packets,
                                "packets"
                            ),
                            label
                        )
            );

        if(session.selectedRowIndex==null||
           session.selectedRowIndex.intValue()!=rowIndex||
           session.selectedDefinitionId==null||
           session.selectedDefinitionId.intValue()!=
                entry.definitionId||
           session.selectedSemanticKey==null||
           !session.selectedSemanticKey.equals(
                entry.semanticKey
           ))
            throw new IllegalStateException(
                "Monster Spawner selected row identity changed row="+
                rowIndex
            );

        return new Result(
            Status.ROW_SELECTED,
            rowIndex,
            0,
            session
        );
    }

    private Result toggle(){
        MonsterSpawnerService.SessionSnapshot before=
            service.getSession(
                ownerRef
            );

        if(before==null)
            throw new IllegalStateException(
                "Monster Spawner session disappeared "+
                ownerRef
            );

        String authority=
            requireServerAuthority(
                activationBudget.authority(),
                "activation budget authority"
            );

        if(!authority.equals(
                before.policyAuthority
            ))
            throw new IllegalStateException(
                "Monster Spawner activation authority mismatch owner="+
                ownerRef+
                " session="+before.policyAuthority+
                " adapter="+authority
            );

        if(before.active){
            MonsterSpawnerService.SessionSnapshot after=
                service.deactivateIfCurrent(
                    ownerRef,
                    before
                );

            return new Result(
                Status.DEACTIVATED,
                after.selectedRowIndex==null
                    ?-1
                    :after.selectedRowIndex.intValue(),
                0,
                after
            );
        }

        Context context=
            new Context(
                ownerRef,
                before
            );

        int budget=
            activationBudget.spawnBudget(
                context
            );
        String afterAuthority=
            requireServerAuthority(
                activationBudget.authority(),
                "activation budget authority"
            );

        if(!authority.equals(
                afterAuthority))
            throw new IllegalStateException(
                "Monster Spawner activation authority changed during budget resolution owner="+
                ownerRef+
                " before="+authority+
                " after="+afterAuthority
            );

        if(budget<=0)
            throw new IllegalArgumentException(
                "Monster Spawner activation budget="+
                budget
            );

        MonsterSpawnerService.SessionSnapshot after=
            service.activateIfCurrent(
                ownerRef,
                before,
                budget
            );

        return new Result(
            Status.ACTIVATED,
            after.selectedRowIndex==null
                ?-1
                :after.selectedRowIndex.intValue(),
            budget,
            after
        );
    }

    private String resolveSelectedLabel(
        MonsterSpawnerService.CatalogEntry entry
    ){
        String authority=
            requireServerAuthority(
                selectedLabel.authority(),
                "selected label authority"
            );

        return resolveSelectedLabel(
            entry,
            authority
        );
    }

    private String resolveSelectedLabel(
        MonsterSpawnerService.CatalogEntry entry,
        String expectedAuthority
    ){
        MonsterSpawnerService.CatalogEntry checkedEntry=
            Objects.requireNonNull(
                entry,
                "entry"
            );
        String expected=
            requireServerAuthority(
                expectedAuthority,
                "expected selected label authority"
            );
        String beforeAuthority=
            requireServerAuthority(
                selectedLabel.authority(),
                "selected label authority"
            );

        if(!expected.equals(
                beforeAuthority))
            throw new IllegalStateException(
                "Monster Spawner selected label authority changed before resolution owner="+
                ownerRef+
                " expected="+expected+
                " actual="+beforeAuthority
            );

        String rawLabel=
            selectedLabel.label(
                checkedEntry
            );
        String afterAuthority=
            requireServerAuthority(
                selectedLabel.authority(),
                "selected label authority"
            );

        if(!expected.equals(
                afterAuthority))
            throw new IllegalStateException(
                "Monster Spawner selected label authority changed during resolution owner="+
                ownerRef+
                " expected="+expected+
                " actual="+afterAuthority
            );

        return MonsterSpawnerPresentation
            .prepareSelectedNpcName(
                rawLabel
            );
    }

    private static String requireServerAuthority(
        String value,
        String field
    ){
        String clean=
            MatchRules.requireText(
                value,
                field
            );

        if("EXACT_CURRENT_CLIENT".equals(clean)||
           "UNKNOWN_SERVER_AUTHORITY".equals(clean)||
           "UNCONFIGURED".equals(clean))
            throw new IllegalArgumentException(
                field+" cannot own server policy actual="+
                clean
            );

        return clean;
    }
}

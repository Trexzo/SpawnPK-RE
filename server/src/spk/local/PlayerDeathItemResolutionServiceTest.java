package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PlayerDeathItemResolutionServiceTest {
    private static final String AUTHORITY=
        "CUSTOM_LOCALLAB_DEATH_ITEMS_TEST";

    public static void main(String[] args){
        exactDispositionAndReplay();
        sameTickDeathsUseDistinctSequence();
        deadRequired();
        fullCoverageAndKeepValidation();
        stateDriftRejected();
        crossPlayerPreviewRejected();
        emptyCarriedAllowed();
        authorityGuards();
        boundaryGuard();

        System.out.println(
            "PLAYER_DEATH_ITEM_RESOLUTION_PASS "+
            "deadRequired=true "+
            "exactDeathTick=true "+
            "deathSequenceIdentity=true "+
            "previewThenResolve=true "+
            "callbackUnderPlayerLock=false "+
            "inventoryAndEquipment=true "+
            "exactSourceLines=true "+
            "fullCoverageRequired=true "+
            "partialStackKeep=true "+
            "lostDerived=true "+
            "replayIdempotent=true "+
            "stateDriftRejected=true "+
            "crossPlayerPreviewRejected=true "+
            "itemValueOwned=false "+
            "protectItemOwned=false "+
            "lootRecipientOwned=false "+
            "itemMutation=false "+
            "groundMutation=false "+
            "protocolIndependent=true"
        );
    }

    private static void exactDispositionAndReplay(){
        WorldPlayer player=
            configuredPlayer();

        kill(
            player,
            77L,
            "pvp-test",
            5L
        );

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                AUTHORITY
            );

        PlayerDeathItemResolutionService.DeathPreview
            preview=
                service.previewCurrentDeath();

        require(
            preview.playerId.equals(
                player.id()
            )&&
            preview.deathTick==77L&&
            preview.deathSequence==1L&&
            "pvp-test".equals(
                preview.deathCause
            )&&
            preview.carried.size()==4,
            "death preview identity"
        );

        expect(
            UnsupportedOperationException.class,
            ()->preview.carried.clear(),
            "carried preview mutable"
        );

        PlayerDeathItemResolutionService.CarriedLine
            coins=findItem(
                preview.carried,
                995
            );
        PlayerDeathItemResolutionService.CarriedLine
            food=findItem(
                preview.carried,
                15272
            );
        PlayerDeathItemResolutionService.CarriedLine
            weapon=findItem(
                preview.carried,
                4151
            );
        PlayerDeathItemResolutionService.CarriedLine
            ammo=findItem(
                preview.carried,
                892
            );

        require(
            coins.source==
                PlayerDeathItemResolutionService
                    .Source.INVENTORY&&
            coins.sourceIndex==0&&
            coins.equipmentSlot==null&&
            weapon.source==
                PlayerDeathItemResolutionService
                    .Source.EQUIPMENT&&
            weapon.equipmentSlot==
                EquipmentSlot.WEAPON,
            "carried source identity"
        );

        List<PlayerDeathItemResolutionService.Decision>
            decisions=
                Arrays.asList(
                    new PlayerDeathItemResolutionService
                        .Decision(
                            coins.lineId,
                            40
                        ),
                    new PlayerDeathItemResolutionService
                        .Decision(
                            food.lineId,
                            food.quantity
                        ),
                    new PlayerDeathItemResolutionService
                        .Decision(
                            weapon.lineId,
                            weapon.quantity
                        ),
                    new PlayerDeathItemResolutionService
                        .Decision(
                            ammo.lineId,
                            0
                        )
                );

        PlayerDeathItemResolutionService.Resolution
            resolution=
                service.resolveCurrentDeath(
                    preview,
                    decisions
                );

        require(
            resolution.deathSequence==
                preview.deathSequence&&
            resolution.keptTotalQuantity()==43&&
            resolution.lostTotalQuantity()==110&&
            AUTHORITY.equals(
                resolution.policyAuthority
            ),
            "death disposition totals"
        );

        PlayerDeathItemResolutionService.Disposition
            coinDisposition=
                dispositionFor(
                    resolution,
                    995
                );
        PlayerDeathItemResolutionService.Disposition
            ammoDisposition=
                dispositionFor(
                    resolution,
                    892
                );

        require(
            coinDisposition.keptAmount==40&&
            coinDisposition.lostAmount==60&&
            ammoDisposition.keptAmount==0&&
            ammoDisposition.lostAmount==50,
            "partial/full loss derivation"
        );

        PlayerDeathItemResolutionService.Resolution
            replay=
                service.resolveCurrentDeath(
                    preview,
                    decisions
                );

        require(
            replay==resolution&&
            service.size()==1&&
            player.bank()
                .inventoryAt(0).qty==100&&
            player.equipment()
                .quantityAt(
                    EquipmentSlot.AMMO
                )==50,
            "resolution replay/item mutation boundary"
        );
    }

    private static void sameTickDeathsUseDistinctSequence(){
        WorldPlayer player=
            configuredPlayer();

        PlayerLifecycleService lifecycle=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            );
        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                AUTHORITY
            );

        require(
            lifecycle.applyDamage(
                500,
                90L,
                "first",
                0L
            ).died,
            "first same-tick death"
        );

        PlayerDeathItemResolutionService.DeathPreview first=
            service.previewCurrentDeath();

        service.resolveCurrentDeath(
            first,
            keepAll(first.carried)
        );

        require(
            lifecycle.tick(
                90L,
                99
            )==
                PlayerLifecycleService.TickResult
                    .RESPAWNED,
            "zero-delay respawn"
        );

        require(
            lifecycle.applyDamage(
                500,
                90L,
                "second",
                0L
            ).died,
            "second same-tick death"
        );

        PlayerDeathItemResolutionService.DeathPreview second=
            service.previewCurrentDeath();

        require(
            first.deathTick==
                second.deathTick&&
            first.deathSequence==1L&&
            second.deathSequence==2L,
            "same-tick death identity collision"
        );

        service.resolveCurrentDeath(
            second,
            keepAll(second.carried)
        );

        require(
            service.size()==2,
            "distinct death resolutions not retained"
        );
    }

    private static void deadRequired(){
        WorldPlayer player=
            configuredPlayer();

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                AUTHORITY
            );

        expect(
            IllegalStateException.class,
            service::previewCurrentDeath,
            "alive preview"
        );

        require(
            service.size()==0,
            "alive preview cached resolution"
        );
    }

    private static void fullCoverageAndKeepValidation(){
        WorldPlayer player=
            configuredPlayer();

        kill(
            player,
            81L,
            "validation",
            5L
        );

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                AUTHORITY
            );

        PlayerDeathItemResolutionService.DeathPreview preview=
            service.previewCurrentDeath();

        expect(
            IllegalArgumentException.class,
            ()->service.resolveCurrentDeath(
                preview,
                Collections.singletonList(
                    new PlayerDeathItemResolutionService
                        .Decision(
                            preview.carried
                                .get(0)
                                .lineId,
                            0
                        )
                )
            ),
            "partial coverage"
        );

        ArrayList<PlayerDeathItemResolutionService.Decision>
            duplicate=
                new ArrayList<>(
                    keepNone(
                        preview.carried
                    )
                );

        duplicate.set(
            duplicate.size()-1,
            new PlayerDeathItemResolutionService
                .Decision(
                    preview.carried
                        .get(0)
                        .lineId,
                    0
                )
        );

        expect(
            IllegalArgumentException.class,
            ()->service.resolveCurrentDeath(
                preview,
                duplicate
            ),
            "duplicate coverage"
        );

        ArrayList<PlayerDeathItemResolutionService.Decision>
            overflow=
                new ArrayList<>(
                    keepNone(
                        preview.carried
                    )
                );

        PlayerDeathItemResolutionService.CarriedLine first=
            preview.carried.get(0);

        overflow.set(
            0,
            new PlayerDeathItemResolutionService
                .Decision(
                    first.lineId,
                    first.quantity+1
                )
        );

        expect(
            IllegalArgumentException.class,
            ()->service.resolveCurrentDeath(
                preview,
                overflow
            ),
            "kept amount overflow"
        );

        require(
            service.size()==0,
            "invalid decisions cached"
        );
    }

    private static void stateDriftRejected(){
        WorldPlayer player=
            configuredPlayer();

        kill(
            player,
            82L,
            "drift",
            5L
        );

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                AUTHORITY
            );

        PlayerDeathItemResolutionService.DeathPreview preview=
            service.previewCurrentDeath();

        synchronized(player.mutationLock()){
            player.equipment()
                .setStack(
                    EquipmentSlot.WEAPON,
                    4151,
                    2
                );
        }

        expect(
            IllegalStateException.class,
            ()->service.resolveCurrentDeath(
                preview,
                keepNone(
                    preview.carried
                )
            ),
            "carried state drift"
        );

        require(
            service.size()==0,
            "drift cached resolution"
        );
    }

    private static void crossPlayerPreviewRejected(){
        WorldPlayer first=
            configuredPlayer();
        WorldPlayer second=
            configuredPlayer();

        kill(
            first,
            83L,
            "first-player",
            5L
        );
        kill(
            second,
            83L,
            "second-player",
            5L
        );

        PlayerDeathItemResolutionService firstService=
            new PlayerDeathItemResolutionService(
                first,
                AUTHORITY
            );
        PlayerDeathItemResolutionService secondService=
            new PlayerDeathItemResolutionService(
                second,
                AUTHORITY
            );

        PlayerDeathItemResolutionService.DeathPreview preview=
            firstService.previewCurrentDeath();

        expect(
            IllegalArgumentException.class,
            ()->secondService.resolveCurrentDeath(
                preview,
                keepNone(
                    preview.carried
                )
            ),
            "cross-player preview"
        );
    }

    private static void emptyCarriedAllowed(){
        WorldPlayer player=
            new WorldPlayer();

        synchronized(player.mutationLock()){
            player.bank().restoreAccountState(
                new BankState.Stack[
                    BankState.BANK_CAPACITY
                ],
                new BankState.Stack[
                    BankState.INVENTORY_CAPACITY
                ],
                false
            );

            int[] items=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            int[] quantities=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            Arrays.fill(
                items,
                -1
            );

            player.equipment()
                .restoreAccountState(
                    items,
                    quantities
                );
        }

        kill(
            player,
            84L,
            "empty",
            5L
        );

        PlayerDeathItemResolutionService service=
            new PlayerDeathItemResolutionService(
                player,
                AUTHORITY
            );

        PlayerDeathItemResolutionService.DeathPreview preview=
            service.previewCurrentDeath();

        require(
            preview.carried.isEmpty(),
            "empty carried preview"
        );

        PlayerDeathItemResolutionService.Resolution result=
            service.resolveCurrentDeath(
                preview,
                Collections.emptyList()
            );

        require(
            result.dispositions.isEmpty()&&
            result.keptTotalQuantity()==0&&
            result.lostTotalQuantity()==0,
            "empty carried resolution"
        );
    }

    private static void authorityGuards(){
        WorldPlayer player=
            new WorldPlayer();

        expect(
            IllegalArgumentException.class,
            ()->new PlayerDeathItemResolutionService(
                player,
                "EXACT_CURRENT_CLIENT"
            ),
            "client death policy authority"
        );

        expect(
            IllegalArgumentException.class,
            ()->new PlayerDeathItemResolutionService(
                player,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown death policy authority"
        );
    }

    private static WorldPlayer configuredPlayer(){
        WorldPlayer player=
            new WorldPlayer();

        synchronized(player.mutationLock()){
            BankState.Stack[] bank=
                new BankState.Stack[
                    BankState.BANK_CAPACITY
                ];
            BankState.Stack[] inventory=
                new BankState.Stack[
                    BankState.INVENTORY_CAPACITY
                ];

            inventory[0]=
                new BankState.Stack(
                    995,
                    100
                );
            inventory[1]=
                new BankState.Stack(
                    15272,
                    2
                );

            player.bank().restoreAccountState(
                bank,
                inventory,
                false
            );

            int[] items=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];
            int[] quantities=
                new int[
                    EquipmentState.EQUIPMENT_SLOTS
                ];

            Arrays.fill(
                items,
                -1
            );

            items[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=4151;
            quantities[
                EquipmentSlot.WEAPON
                    .equipmentIndex
            ]=1;
            items[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ]=892;
            quantities[
                EquipmentSlot.AMMO
                    .equipmentIndex
            ]=50;

            player.equipment()
                .restoreAccountState(
                    items,
                    quantities
                );
        }

        return player;
    }

    private static void kill(
        WorldPlayer player,
        long tick,
        String cause,
        long respawnDelay
    ){
        PlayerLifecycleService.DamageResult result=
            new PlayerLifecycleService(
                player,
                AUTHORITY
            ).applyDamage(
                500,
                tick,
                cause,
                respawnDelay
            );

        require(
            result.died&&
            player.lifecycle().dead(),
            "death fixture"
        );
    }

    private static List<PlayerDeathItemResolutionService.Decision>
        keepAll(
            List<PlayerDeathItemResolutionService.CarriedLine>
                lines
        ){
        ArrayList<PlayerDeathItemResolutionService.Decision>
            out=
                new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                lines)
            out.add(
                new PlayerDeathItemResolutionService
                    .Decision(
                        line.lineId,
                        line.quantity
                    )
            );

        return out;
    }

    private static List<PlayerDeathItemResolutionService.Decision>
        keepNone(
            List<PlayerDeathItemResolutionService.CarriedLine>
                lines
        ){
        ArrayList<PlayerDeathItemResolutionService.Decision>
            out=
                new ArrayList<>();

        for(PlayerDeathItemResolutionService.CarriedLine line:
                lines)
            out.add(
                new PlayerDeathItemResolutionService
                    .Decision(
                        line.lineId,
                        0
                    )
            );

        return out;
    }

    private static PlayerDeathItemResolutionService.CarriedLine
        findItem(
            List<PlayerDeathItemResolutionService.CarriedLine>
                lines,
            int itemId
        ){
        PlayerDeathItemResolutionService.CarriedLine found=null;

        for(PlayerDeathItemResolutionService.CarriedLine line:
                lines)
            if(line.itemId==itemId){
                if(found!=null)
                    throw new AssertionError(
                        "duplicate fixture item "+
                        itemId
                    );
                found=line;
            }

        if(found==null)
            throw new AssertionError(
                "missing fixture item "+
                itemId
            );

        return found;
    }

    private static PlayerDeathItemResolutionService.Disposition
        dispositionFor(
            PlayerDeathItemResolutionService.Resolution resolution,
            int itemId
        ){
        for(PlayerDeathItemResolutionService.Disposition disposition:
                resolution.dispositions)
            if(disposition.line.itemId==
                    itemId)
                return disposition;

        throw new AssertionError(
            "missing disposition item "+
            itemId
        );
    }

    private static void boundaryGuard(){
        for(Class<?> type:new Class<?>[]{
                PlayerDeathItemResolutionService.class,
                PlayerDeathItemResolutionService
                    .CarriedLine.class,
                PlayerDeathItemResolutionService
                    .DeathPreview.class,
                PlayerDeathItemResolutionService
                    .Resolution.class,
                PlayerDeathItemResolutionService
                    .Disposition.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                for(String forbidden:
                        new String[]{
                            "widget",
                            "opcode",
                            "packet",
                            "sceneindex",
                            "itemvalue",
                            "protectitem",
                            "killer",
                            "recipient",
                            "grounditem",
                            "policycallback",
                            "resolver"
                        })
                    require(
                        !name.contains(
                            forbidden
                        ),
                        "unowned identity leaked through "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }

        for(Method method:
                PlayerDeathItemResolutionService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            for(String forbidden:
                    new String[]{
                        "packet",
                        "publish",
                        "removeitem",
                        "spawn",
                        "ground",
                        "killer",
                        "recipient",
                        "persist",
                        "callback",
                        "resolver"
                    })
                require(
                    !name.contains(
                        forbidden
                    ),
                    "unowned behavior leaked through "+
                    method.getName()
                );
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(
                    failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private PlayerDeathItemResolutionServiceTest(){}
}

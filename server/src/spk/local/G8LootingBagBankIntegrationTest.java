package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;

public final class G8LootingBagBankIntegrationTest {
    private static final String OWNER="g8-looting-bag";
    private static final String TEST_INTAKE_AUTHORITY=
        "LOCAL_LAB_G8_TEST_CONFIRMED_EXTERNAL_INTAKE";

    public static void main(String[] args)throws Exception{
        boolean exactRoot26700=false;
        boolean exactContainer26706=false;
        boolean itemOpcodes=false;
        boolean rootLifecycle=false;
        boolean oneFiveTenAll=false;
        boolean staleIdentityRejected=false;
        boolean reservationFirst=false;
        boolean bankCredit=false;
        boolean predictedPostimage=false;
        boolean transportFailureAtomic=false;
        boolean bankFullCancel=false;
        boolean overflowCancel=false;
        boolean closedUiNoop=false;

        World world=
            World.isolatedForTest(
                60_000L
            );
        WorldPlayer player=
            new WorldPlayer();

        world.registerPlayer(
            player,
            OWNER
        );

        try{
            LootingBagService bags=
                world.lootingBags();
            BankState bank=
                player.bank();
            LocalLootingBagBankHandler handler=
                new LocalLootingBagBankHandler(
                    ()->OWNER,
                    bags,
                    bank
                );

            ByteArrayOutputStream wire=
                new ByteArrayOutputStream();
            ServerPacketWriter writer=
                writer(wire,1);

            LocalLootingBagBankHandler.Result
                emptyOpen=
                    handler.open(
                        writer
                    );

            exactRoot26700=
                LootingBagPresentation.ROOT==
                    26700&&
                emptyOpen.status==
                    LocalLootingBagBankHandler
                        .Status.OPENED;
            exactContainer26706=
                LootingBagPresentation
                    .CONTAINER_WIDGET==
                        26706;

            require(
                exactRoot26700&&
                exactContainer26706&&
                handler.isOpen()&&
                wire.size()>0,
                "empty exact Looting Bag root did not open"
            );

            require(
                handler.close()&&
                !handler.isOpen(),
                "empty Looting Bag root did not close"
            );

            bags.confirmExternalDeposit(
                OWNER,
                "g8:seed:main",
                Collections.singletonList(
                    new LootingBagService.StackSpec(
                        200,
                        30L
                    )
                ),
                TEST_INTAKE_AUTHORITY
            );

            handler.open(
                writer
            );

            int wireBefore=
                wire.size();

            LocalLootingBagBankHandler.Result one=
                handler.handle(
                    action(
                        145,
                        0,
                        200
                    ),
                    writer
                );

            LootingBagService.Snapshot afterOne=
                bags.get(OWNER);

            predictedPostimage=
                wire.size()>wireBefore&&
                afterOne.slots.size()==1&&
                afterOne.slots.get(0)
                    .amount==29L&&
                afterOne.slots.get(0)
                    .reserved==0L;

            LocalLootingBagBankHandler.Result five=
                handler.handle(
                    action(
                        117,
                        0,
                        200
                    ),
                    writer
                );

            LocalLootingBagBankHandler.Result ten=
                handler.handle(
                    action(
                        43,
                        0,
                        200
                    ),
                    writer
                );

            LocalLootingBagBankHandler.Result all=
                handler.handle(
                    action(
                        129,
                        0,
                        200
                    ),
                    writer
                );

            oneFiveTenAll=
                one.status==
                    LocalLootingBagBankHandler
                        .Status.DEPOSITED&&
                one.amount==1&&
                five.status==
                    LocalLootingBagBankHandler
                        .Status.DEPOSITED&&
                five.amount==5&&
                ten.status==
                    LocalLootingBagBankHandler
                        .Status.DEPOSITED&&
                ten.amount==10&&
                all.status==
                    LocalLootingBagBankHandler
                        .Status.DEPOSITED&&
                all.amount==14;

            itemOpcodes=oneFiveTenAll;

            bankCredit=
                bankCount(
                    bank,
                    200
                )==30&&
                bags.get(OWNER)
                    .slots
                    .isEmpty();

            require(
                oneFiveTenAll&&
                bankCredit&&
                predictedPostimage,
                "Looting Bag 1/5/10/All bank path failed"
            );

            bags.confirmExternalDeposit(
                OWNER,
                "g8:seed:identity",
                Collections.singletonList(
                    new LootingBagService.StackSpec(
                        201,
                        2L
                    )
                ),
                TEST_INTAKE_AUTHORITY
            );

            require(
                handler.close(),
                "open Looting Bag did not close"
            );

            long closedBefore=
                bags.get(OWNER)
                    .slots.get(0)
                    .amount;
            int closedBankBefore=
                bankCount(
                    bank,
                    201
                );

            LocalLootingBagBankHandler.Result
                closed=
                    handler.handle(
                        action(
                            145,
                            0,
                            201
                        ),
                        writer
                    );

            closedUiNoop=
                closed.status==
                    LocalLootingBagBankHandler
                        .Status.CLOSED_UI_NOOP&&
                bags.get(OWNER)
                    .slots.get(0)
                    .amount==
                        closedBefore&&
                bankCount(
                    bank,
                    201
                )==
                    closedBankBefore;

            handler.open(
                writer
            );

            LocalLootingBagBankHandler.Result
                stale=
                    handler.handle(
                        action(
                            145,
                            0,
                            999
                        ),
                        writer
                    );

            staleIdentityRejected=
                stale.status==
                    LocalLootingBagBankHandler
                        .Status.BANK_REJECTED&&
                bags.get(OWNER)
                    .slots.get(0)
                    .amount==2L&&
                bankCount(
                    bank,
                    201
                )==0;

            rootLifecycle=
                closedUiNoop&&
                handler.isOpen();

            require(
                closedUiNoop&&
                staleIdentityRejected&&
                rootLifecycle,
                "Looting Bag root/stale-input fence failed"
            );

            transportFailureAtomic=
                transportFailureAtomic();
            BankFailureProof bankFailures=
                bankFailureProof();

            reservationFirst=
                bankFailures.reservationFirst;
            bankFullCancel=
                bankFailures.bankFullCancel;
            overflowCancel=
                bankFailures.overflowCancel;

            require(
                transportFailureAtomic&&
                reservationFirst&&
                bankFullCancel&&
                overflowCancel,
                "Looting Bag failure-atomic bank proof failed"
            );

            System.out.println(
                "G8_LOOTING_BAG_BANK_PASS"+
                " exactRoot26700="+exactRoot26700+
                " exactContainer26706="+exactContainer26706+
                " itemOpcodes="+itemOpcodes+
                " rootLifecycle="+rootLifecycle+
                " oneFiveTenAll="+oneFiveTenAll+
                " staleIdentityRejected="+staleIdentityRejected+
                " reservationFirst="+reservationFirst+
                " bankCredit="+bankCredit+
                " predictedPostimage="+predictedPostimage+
                " transportFailureAtomic="+
                    transportFailureAtomic+
                " bankFullCancel="+bankFullCancel+
                " overflowCancel="+overflowCancel+
                " closedUiNoop="+closedUiNoop+
                " intakeClaim=false"+
                " wildernessClaim=false"+
                " deathClaim=false"+
                " bulkWidgetClaim=false"+
                " originalSpawnpkPolicyClaim=false"
            );
        }finally{
            world.close();
        }
    }

    private static boolean transportFailureAtomic()
        throws Exception
    {
        LootingBagService bags=
            new LootingBagService();
        BankState bank=
            new BankState();
        String owner=
            "g8-transport";

        bags.confirmExternalDeposit(
            owner,
            "g8:transport:seed",
            Collections.singletonList(
                new LootingBagService.StackSpec(
                    202,
                    4L
                )
            ),
            TEST_INTAKE_AUTHORITY
        );

        LocalLootingBagBankHandler handler=
            new LocalLootingBagBankHandler(
                ()->owner,
                bags,
                bank
            );

        handler.open(
            writer(
                new ByteArrayOutputStream(),
                2
            )
        );

        int bankBefore=
            bankCount(
                bank,
                202
            );
        long bagBefore=
            bags.get(owner)
                .slots.get(0)
                .amount;

        boolean failed=false;

        try{
            handler.handle(
                action(
                    145,
                    0,
                    202
                ),
                writer(
                    new FailingOutputStream(),
                    3
                )
            );
        }catch(IOException expected){
            failed=true;
        }

        LootingBagService.Snapshot after=
            bags.get(owner);

        return failed&&
            bankCount(bank,202)==
                bankBefore&&
            after.slots.size()==1&&
            after.slots.get(0)
                .amount==bagBefore&&
            after.slots.get(0)
                .reserved==0L&&
            latestSettlement(after)==
                LootingBagService
                    .SettlementState.CANCELLED;
    }

    private static BankFailureProof bankFailureProof()
        throws Exception
    {
        String fullOwner=
            "g8-full-bank";
        LootingBagService fullBags=
            new LootingBagService();
        BankState fullBank=
            new BankState();

        BankState.Stack[] full=
            new BankState.Stack[
                BankState.BANK_CAPACITY
            ];

        for(int i=0;i<full.length;i++)
            full[i]=
                new BankState.Stack(
                    10_000+i,
                    1
                );

        fullBank.restoreAccountState(
            full,
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ],
            false
        );

        fullBags.confirmExternalDeposit(
            fullOwner,
            "g8:full:seed",
            Collections.singletonList(
                new LootingBagService.StackSpec(
                    9_999,
                    1L
                )
            ),
            TEST_INTAKE_AUTHORITY
        );

        LocalLootingBagBankHandler fullHandler=
            new LocalLootingBagBankHandler(
                ()->fullOwner,
                fullBags,
                fullBank
            );

        fullHandler.open(
            writer(
                new ByteArrayOutputStream(),
                4
            )
        );

        LocalLootingBagBankHandler.Result fullResult=
            fullHandler.handle(
                action(
                    129,
                    0,
                    9_999
                ),
                writer(
                    new ByteArrayOutputStream(),
                    5
                )
            );

        LootingBagService.Snapshot fullAfter=
            fullBags.get(fullOwner);

        boolean bankFullCancel=
            fullResult.status==
                LocalLootingBagBankHandler
                    .Status.BANK_REJECTED&&
            fullAfter.slots.size()==1&&
            fullAfter.slots.get(0)
                .amount==1L&&
            fullAfter.slots.get(0)
                .reserved==0L&&
            bankCount(
                fullBank,
                9_999
            )==0&&
            latestSettlement(fullAfter)==
                LootingBagService
                    .SettlementState.CANCELLED;

        boolean reservationFirst=
            !fullAfter.settlements.isEmpty()&&
            latestSettlement(fullAfter)==
                LootingBagService
                    .SettlementState.CANCELLED;

        String overflowOwner=
            "g8-overflow-bank";
        LootingBagService overflowBags=
            new LootingBagService();
        BankState overflowBank=
            new BankState();
        BankState.Stack[] overflow=
            new BankState.Stack[
                BankState.BANK_CAPACITY
            ];

        overflow[0]=
            new BankState.Stack(
                300,
                Integer.MAX_VALUE
            );

        overflowBank.restoreAccountState(
            overflow,
            new BankState.Stack[
                BankState.INVENTORY_CAPACITY
            ],
            false
        );

        overflowBags.confirmExternalDeposit(
            overflowOwner,
            "g8:overflow:seed",
            Collections.singletonList(
                new LootingBagService.StackSpec(
                    300,
                    1L
                )
            ),
            TEST_INTAKE_AUTHORITY
        );

        LocalLootingBagBankHandler overflowHandler=
            new LocalLootingBagBankHandler(
                ()->overflowOwner,
                overflowBags,
                overflowBank
            );

        overflowHandler.open(
            writer(
                new ByteArrayOutputStream(),
                6
            )
        );

        LocalLootingBagBankHandler.Result
            overflowResult=
                overflowHandler.handle(
                    action(
                        145,
                        0,
                        300
                    ),
                    writer(
                        new ByteArrayOutputStream(),
                        7
                    )
                );

        LootingBagService.Snapshot overflowAfter=
            overflowBags.get(
                overflowOwner
            );

        boolean overflowCancel=
            overflowResult.status==
                LocalLootingBagBankHandler
                    .Status.BANK_REJECTED&&
            overflowAfter.slots.size()==1&&
            overflowAfter.slots.get(0)
                .amount==1L&&
            overflowAfter.slots.get(0)
                .reserved==0L&&
            bankCount(
                overflowBank,
                300
            )==
                Integer.MAX_VALUE&&
            latestSettlement(overflowAfter)==
                LootingBagService
                    .SettlementState.CANCELLED;

        return new BankFailureProof(
            reservationFirst,
            bankFullCancel,
            overflowCancel
        );
    }

    private static LootingBagService.SettlementState
        latestSettlement(
            LootingBagService.Snapshot snapshot
        ){
        if(snapshot.settlements.isEmpty())
            return null;

        return snapshot.settlements
            .get(
                snapshot.settlements.size()-1
            ).state;
    }

    private static ItemContainerAction action(
        int opcode,
        int clientSlot,
        int itemId
    ){
        return new ItemContainerAction(
            opcode,
            LootingBagPresentation
                .CONTAINER_WIDGET,
            clientSlot,
            itemId,
            0,
            "G8_LOOTING_BAG"
        );
    }

    private static ServerPacketWriter writer(
        OutputStream out,
        int seed
    ){
        return new ServerPacketWriter(
            out,
            new IsaacCipher(
                new int[]{
                    seed,
                    seed+1,
                    seed+2,
                    seed+3
                }
            )
        );
    }

    private static int bankCount(
        BankState bank,
        int itemId
    ){
        long total=0L;

        for(int i=0;
            i<bank.bankCapacity();
            i++){
            BankState.Stack stack=
                bank.bankAt(i);

            if(stack!=null&&
               stack.itemId==itemId)
                total+=stack.qty;
        }

        return total>Integer.MAX_VALUE
            ?Integer.MAX_VALUE
            :(int)total;
    }

    private static final class BankFailureProof {
        final boolean reservationFirst;
        final boolean bankFullCancel;
        final boolean overflowCancel;

        BankFailureProof(
            boolean reservationFirst,
            boolean bankFullCancel,
            boolean overflowCancel
        ){
            this.reservationFirst=
                reservationFirst;
            this.bankFullCancel=
                bankFullCancel;
            this.overflowCancel=
                overflowCancel;
        }
    }

    private static final class FailingOutputStream
        extends OutputStream {

        @Override public void write(int value)
            throws IOException
        {
            throw new IOException(
                "EXPECTED_G8_TRANSPORT_FAILURE"
            );
        }

        @Override public void write(
            byte[] bytes,
            int offset,
            int length
        )throws IOException{
            throw new IOException(
                "EXPECTED_G8_TRANSPORT_FAILURE"
            );
        }
    }

    private static void require(
        boolean condition,
        String message
    ){
        if(!condition)
            throw new AssertionError(
                message
            );
    }

    private G8LootingBagBankIntegrationTest(){}
}

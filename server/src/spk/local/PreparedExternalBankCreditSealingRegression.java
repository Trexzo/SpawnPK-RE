package spk.local;

import java.io.*;
import java.util.*;

/**
 * R23: prepared external bank credit mirrors cannot be tampered with between
 * preparation and commit. The real BankState owner and lineage still govern
 * authorization, and the private prepared snapshots govern the exact delta.
 *
 * These are source-local integrity checks, not authorization for an external
 * source of coins/rewards or evidence of a durable mailbox transaction.
 */
public final class PreparedExternalBankCreditSealingRegression {
    private static final int COINS=995;
    private PreparedExternalBankCreditSealingRegression(){}

    private static void check(boolean value,String label){
        if(!value)
            throw new AssertionError(
                "EXTERNAL_BANK_CREDIT_SEAL "+label);
    }

    private static void rejection(
        BankState bank,BankState.PreparedExternalBankCredit candidate,
        String label
    ){
        int before=bank.bankAt(0).qty;
        long generation=bank.bankMutationRevision();
        boolean denied=false;
        try{
            bank.commitPreparedExternalBankCredit(candidate);
        }catch(IllegalStateException expected){
            denied=true;
        }
        check(denied&&bank.bankAt(0).qty==before&&
            bank.bankMutationRevision()==generation,
            "tampered prepared bank credit accepted: "+label);
    }

    private static void expectedSnapshotTamper(){
        BankState bank=new BankState();
        BankState.PreparedExternalBankCredit proposed=
            bank.prepareExternalBankCredit(COINS,2);
        int before=bank.bankAt(0).qty;
        proposed.expectedBank[0].qty++;
        rejection(bank,proposed,"expected bank quantity");
        check(bank.bankAt(0).qty==before,
            "expected copy aliased into canonical bank");
    }

    private static void postimageQuantityTamper(){
        BankState bank=new BankState();
        BankState.PreparedExternalBankCredit proposed=
            bank.prepareExternalBankCredit(COINS,3);
        proposed.postimage[0].qty=Integer.MAX_VALUE;
        rejection(bank,proposed,"arbitrary extra coins");
    }

    private static void postimageIdentityTamper(){
        BankState bank=new BankState();
        BankState.PreparedExternalBankCredit proposed=
            bank.prepareExternalBankCredit(COINS,3);
        proposed.postimage[0]=
            new BankState.Stack(28807,1);
        rejection(bank,proposed,"substituted grant item");
    }

    private static void postimageSideSlotTamper(){
        BankState bank=new BankState();
        BankState.PreparedExternalBankCredit proposed=
            bank.prepareExternalBankCredit(COINS,1);
        proposed.postimage[10]=
            new BankState.Stack(28824,500);
        rejection(bank,proposed,"unauthorized secondary item slot");
    }

    private static void postimageTabTamper(){
        BankState bank=new BankState();
        BankState.PreparedExternalBankCredit proposed=
            bank.prepareExternalBankCredit(COINS,1);
        proposed.postimage[0].tab=6;
        rejection(bank,proposed,"hidden bank tab mutation");
    }

    private static void sameContentsPositiveCommit(){
        BankState bank=new BankState();
        BankState.PreparedExternalBankCredit proposed=
            bank.prepareExternalBankCredit(COINS,17);
        long before=bank.bankMutationRevision();
        check(proposed.accepted()&&
            proposed.expectedBank[0]!=bank.bankAt(0)&&
            proposed.postimage[0].qty==100017,
            "accepted prepared credit snapshot malformed");
        int committed=bank.commitPreparedExternalBankCredit(proposed);
        check(committed==0&&bank.bankAt(0).qty==100017&&
            bank.bankMutationRevision()==before+1,
            "sealed correct postimage failed normal commit");
        rejection(bank,proposed,"same-instance prepared token replay");
    }

    private static void alreadyRejectedCredit(){
        BankState bank=new BankState();
        BankState.PreparedExternalBankCredit proposed=
            bank.prepareExternalBankCredit(COINS,Integer.MAX_VALUE);
        check(!proposed.accepted()&&
            proposed.rejection.contains("REJECTED_QUANTITY_OVERFLOW"),
            "overflow prepared credit unexpectedly accepted");
        long before=bank.bankMutationRevision();
        boolean denied=false;
        try{
            bank.commitPreparedExternalBankCredit(proposed);
        }catch(IllegalArgumentException expected){
            denied=true;
        }
        check(denied&&before==bank.bankMutationRevision(),
            "preflight rejection became a bank credit");
    }

    private static void bankGenerationTransitions()throws Exception{
        BankState bank=new BankState();
        ServerPacketWriter writer=new ServerPacketWriter(
            new OutboundPacketQueue(8192),
            new IsaacCipher(new int[]{1,2,3,4}));
        bank.open(writer);
        long start=bank.bankMutationRevision();
        BankState.PreparedExternalBankCredit proposed=
            bank.prepareExternalBankCredit(COINS,1);
        check(bank.togglePlaceholders(writer)
                .startsWith("PLACEHOLDERS_ENABLED")&&
            bank.bankMutationRevision()==start+1,
            "placeholder mutation lost bank lineage");
        rejection(bank,proposed,"credit after placeholder postimage");
        int[] ids=new int[bank.inventoryCapacity()];
        int[] amounts=new int[ids.length];
        Arrays.fill(ids,-1);
        ids[0]=COINS;amounts[0]=4;
        bank.replaceInventorySemantic(ids,amounts);
        long second=bank.bankMutationRevision();
        BankState.PreparedExternalBankCredit prior=
            bank.prepareExternalBankCredit(COINS,1);
        check(bank.depositInventory(writer)
                .startsWith("DEPOSIT_INVENTORY")||
            bank.bankAt(0).qty==100004,
            "bank deposit inventory fixture failed");
        check(bank.bankMutationRevision()>second&&
            bank.bankAt(0).qty==100004,
            "deposit-all failed to advance bank lineage");
        rejection(bank,prior,"credit after deposit all bank mutation");
    }

    public static void main(String[] args)throws Exception{
        expectedSnapshotTamper();
        postimageQuantityTamper();
        postimageIdentityTamper();
        postimageSideSlotTamper();
        postimageTabTamper();
        sameContentsPositiveCommit();
        alreadyRejectedCredit();
        bankGenerationTransitions();
        System.out.println(
            "PREPARED_EXTERNAL_BANK_CREDIT_SEAL_PASS"+
            " privateConstruction=true independentSnapshots=true"+
            " expectedImageMutationRejected=true"+
            " postimageQuantityItemTabAndSideSlotRejected=true"+
            " originalGrantCommitOnce=true overflowPreflightDenied=true"+
            " bankPlaceholderAndDepositAllGeneration=true"+
            " noNewDonorGrantAuthority=true");
    }
}

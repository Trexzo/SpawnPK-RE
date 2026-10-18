package spk.local;

import java.util.*;

/**
 * Explicit source-only recipe adapter for BankState. No packets, XP or
 * client action registration. Caller must supply verified stackability.
 * Call only on the owning World thread; no concurrent inventory mutation.
 */
final class RecipeBankSemanticAdapter {
    private RecipeBankSemanticAdapter(){}
    static final class Prepared {
        private final BankState owner;
        private final int[] expectedIds,expectedQuantities;
        private final boolean expectedBankOpen;
        private final long expectedBankViewRevision;
        private final long expectedInventoryMutationRevision;
        // Tests may deliberately mutate this view; semantic/publication paths
        // use only privately sealed arrays and reject any changed proposal.
        final RecipeStackPostimagePlanner.Postimage proposed;
        private final int[] sealedIds,sealedQuantities;
        private boolean committed;
        // Pin retries to the same outbound authority, including backpressure
        // or publication failures. This is NOT a cross-World session lease.
        private ServerPacketWriter publicationWriter;
        private Prepared(BankState owner,int[] ids,int[] quantities,RecipeStackPostimagePlanner.Postimage image){
            this.owner=Objects.requireNonNull(owner,"owner");
            expectedIds=ids.clone();expectedQuantities=quantities.clone();
            expectedBankOpen=owner.isOpen();
            expectedBankViewRevision=owner.bankViewRevision();
            expectedInventoryMutationRevision=owner.inventoryMutationRevision();
            proposed=image;
            sealedIds=image.ids.clone();sealedQuantities=image.quantities.clone();
        }
    }
    static Prepared prepare(
        BankState bank,Map<Integer,Integer> ingredients,
        int product,int perBatch,int batches,Set<Integer> stackable
    ){
        return prepareWithTool(bank,ingredients,product,perBatch,batches,
            stackable,null,false);
    }

    /** Pure recipe snapshot preparation. The World still owns real state;
     * this monitor only serializes competing source-only recipe operations.
     */
    static Prepared prepareWithTool(
        BankState bank,Map<Integer,Integer> ingredients,
        int product,int perBatch,int batches,Set<Integer> stackable,
        Integer toolId,boolean consumeTool
    ){
        Objects.requireNonNull(bank,"bank");
        synchronized(bank){
            int n=bank.inventoryCapacity();
            int[] ids=new int[n],qty=new int[n];
            for(int i=0;i<n;i++){
                BankState.Stack stack=bank.inventoryAt(i);
                ids[i]=stack==null?-1:stack.itemId;
                qty[i]=stack==null?0:stack.qty;
            }
            return new Prepared(bank,ids,qty,
                RecipeStackPostimagePlanner.prepareWithTool(
                    ids,qty,ingredients,product,perBatch,batches,
                    stackable,toolId,consumeTool));
        }
    }
    /**
     * Separate source-only path for verified mixed stackability. Nonstackable
     * outputs require one inventory slot per unit; the returned postimage
     * still uses the same sealed BankState publication boundary.
     */
    static Prepared prepareMixedWithTool(
        BankState bank,Map<Integer,Integer> ingredients,
        int product,int perBatch,int batches,
        Set<Integer> stackable,Set<Integer> nonstackable,
        Integer toolId,boolean consumeTool
    ){
        Objects.requireNonNull(bank,"bank");
        synchronized(bank){
            int n=bank.inventoryCapacity();
            int[] ids=new int[n],qty=new int[n];
            for(int slot=0;slot<n;slot++){
                BankState.Stack stack=bank.inventoryAt(slot);
                ids[slot]=stack==null?-1:stack.itemId;
                qty[slot]=stack==null?0:stack.qty;
            }
            RecipeMixedPostimagePlanner.Postimage image=
                RecipeMixedPostimagePlanner.prepare(
                    ids,qty,ingredients,product,perBatch,batches,
                    stackable,nonstackable,toolId,consumeTool);
            return new Prepared(bank,ids,qty,
                new RecipeStackPostimagePlanner.Postimage(
                    image.ids,image.quantities));
        }
    }

    /** Encode the sealed prospective client inventory before semantic commit.
     * The returned bytes are evidence, not a publication or ACK.
     */
    static byte[] encodePreparedInventory(BankState bank,Prepared prepared)
        throws java.io.IOException{
        Objects.requireNonNull(bank,"bank");
        Objects.requireNonNull(prepared,"prepared");
        if(bank!=prepared.owner)
            throw new IllegalStateException("RECIPE_DIFFERENT_INVENTORY_OWNER");
        if(prepared.committed)
            throw new IllegalStateException("RECIPE_PREPARED_ALREADY_COMMITTED");
        if(!Arrays.equals(prepared.sealedIds,prepared.proposed.ids)||
           !Arrays.equals(prepared.sealedQuantities,prepared.proposed.quantities))
            throw new IllegalStateException("RECIPE_PREPARED_POSTIMAGE_CHANGED");
        // Do not even generate a preview of an obsolete snapshot. This is
        // read-only and does not claim a client ACK or reserve a transaction.
        requireCurrentPreimage(bank,prepared);
        return bank.previewRecipeInventoryPacket(
            prepared.sealedIds,prepared.sealedQuantities);
    }
    /**
     * Source-only queue-backed publication and semantic commit. Caller must
     * hold owning World authority; do not cross-session share a BankState.
     * A queue admission is NOT a remote client acknowledgement.
     */
    static ServerPacketWriter.RecoverablePacketResult
        publishAndCommitIfIdle(BankState bank,Prepared prepared,
                              ServerPacketWriter writer)
        throws java.io.IOException{
        Objects.requireNonNull(writer,"writer");
        synchronized(bank){
            // Both inventory views are encoded before queue admission.
            // The recoverable publication call enqueues both or neither.
            encodePreparedInventory(bank,prepared);
            byte[][] views=bank.previewRecipeInventoryViews(
                prepared.sealedIds,prepared.sealedQuantities);
            requireCurrentPreimage(bank,prepared);
            if(prepared.publicationWriter!=null&&
               prepared.publicationWriter!=writer)
                throw new IllegalStateException(
                    "RECIPE_PUBLICATION_WRITER_CHANGED");
            // Bind only after the preimage and both client views validate.
            // Even a queue-full or terminal attempt cannot move authority
            // to an unrelated writer: reprepare for a new session.
            if(prepared.publicationWriter==null)
                prepared.publicationWriter=writer;
            ServerPacketWriter.RecoverablePacketResult result=
                writer.publishQueueOnlyRecoverablePacketIfIdle(
                    ()->{for(byte[] view:views)writer.varShort(53,view);});
            if(result==ServerPacketWriter.RecoverablePacketResult.COMMITTED)
                commit(bank,prepared);
            return result;
        }
    }

    private static void requireCurrentPreimage(BankState bank,Prepared prepared){
        if(prepared.committed)
            throw new IllegalStateException("RECIPE_PREPARED_ALREADY_COMMITTED");
        if(bank!=prepared.owner)
            throw new IllegalStateException("RECIPE_DIFFERENT_INVENTORY_OWNER");
        if(bank.isOpen()!=prepared.expectedBankOpen||
           bank.bankViewRevision()!=prepared.expectedBankViewRevision)
            throw new IllegalStateException("RECIPE_STALE_BANK_VIEW");
        if(bank.inventoryCapacity()!=prepared.expectedIds.length)
            throw new IllegalStateException("RECIPE_INVENTORY_CAPACITY_CHANGED");
        if(bank.inventoryMutationRevision()!=
           prepared.expectedInventoryMutationRevision)
            throw new IllegalStateException(
                "RECIPE_STALE_INVENTORY_GENERATION");
        for(int i=0;i<prepared.expectedIds.length;i++){
            BankState.Stack current=bank.inventoryAt(i);
            int id=current==null?-1:current.itemId;
            int qty=current==null?0:current.qty;
            if(id!=prepared.expectedIds[i]||qty!=prepared.expectedQuantities[i])
                throw new IllegalStateException("RECIPE_STALE_INVENTORY_POSTIMAGE");
        }
    }
    static void commit(BankState bank,
        Prepared prepared
    ){
        Objects.requireNonNull(bank,"bank");
        Objects.requireNonNull(prepared,"prepared");
        synchronized(bank){
            // The direct semantic path is source-only, not public gameplay.
            // Serialize it with recipe preparation and queue publication.
            requireCurrentPreimage(bank,prepared);
            if(!Arrays.equals(prepared.sealedIds,prepared.proposed.ids)||
               !Arrays.equals(prepared.sealedQuantities,
                   prepared.proposed.quantities))
                throw new IllegalStateException(
                    "RECIPE_PREPARED_POSTIMAGE_CHANGED");
            bank.replaceInventorySemantic(prepared.sealedIds.clone(),
                prepared.sealedQuantities.clone());
            prepared.committed=true;
        }
    }
}

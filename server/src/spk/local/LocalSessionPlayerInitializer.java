package spk.local;

import java.util.Objects;

/**
 * Owns post-login player/account initialization before packet publication.
 *
 * This is a behavior-preserving extraction from LocalSession: account
 * selection/load, persisted-state reconciliation and WorldPlayer registration.
 */
final class LocalSessionPlayerInitializer {
    static final class Result {
        final String username;
        final boolean persistentAccount;
        final boolean newAccount;
        final long worldPlayerGeneration;
        final LocalJvmAccountSessionLease.Token sessionLease;

        Result(
            String username,
            boolean persistentAccount,
            boolean newAccount,
            long worldPlayerGeneration,
            LocalJvmAccountSessionLease.Token sessionLease
        ){
            this.username=username;
            this.persistentAccount=persistentAccount;
            this.newAccount=newAccount;
            this.worldPlayerGeneration=worldPlayerGeneration;
            this.sessionLease=sessionLease;
        }
    }

    private final World world;
    private final WorldPlayer worldPlayer;
    private final WorldPlayerPersistence persistence;
    private final BankState bank;
    private final EquipmentState equipment;
    private final MovementState movement;
    private final PetState petState;
    private final PlayerState playerState;
    private final PetEffectState petEffects;
    private final PetAccessoryState petAccessoryState;
    // Test-only deterministic injection; production default is a no-op.
    // The actual admission policy is always enforced after this boundary.
    private final Runnable beforeRegistration;

    LocalSessionPlayerInitializer(
        World world,
        WorldPlayer worldPlayer,
        BankState bank,
        EquipmentState equipment,
        MovementState movement,
        PetState petState,
        PlayerState playerState,
        PetEffectState petEffects,
        PetAccessoryState petAccessoryState
    ){
        this(world,worldPlayer,bank,equipment,movement,petState,
            playerState,petEffects,petAccessoryState,()->{});
    }

    /** Allows deterministic tests to arm a review fence after load. */
    LocalSessionPlayerInitializer(
        World world,
        WorldPlayer worldPlayer,
        BankState bank,
        EquipmentState equipment,
        MovementState movement,
        PetState petState,
        PlayerState playerState,
        PetEffectState petEffects,
        PetAccessoryState petAccessoryState,
        Runnable beforeRegistration
    ){
        this.beforeRegistration=Objects.requireNonNull(
            beforeRegistration,"beforeRegistration"
        );
        this.world=Objects.requireNonNull(world,"world");
        this.worldPlayer=Objects.requireNonNull(worldPlayer,"worldPlayer");
        this.persistence=world.persistence();
        this.bank=Objects.requireNonNull(bank,"bank");
        this.equipment=Objects.requireNonNull(equipment,"equipment");
        this.movement=Objects.requireNonNull(movement,"movement");
        this.petState=Objects.requireNonNull(petState,"petState");
        this.playerState=Objects.requireNonNull(playerState,"playerState");
        this.petEffects=Objects.requireNonNull(petEffects,"petEffects");
        this.petAccessoryState=Objects.requireNonNull(
            petAccessoryState,"petAccessoryState");
    }

    Result initialize(
        String loginAlias,
        String tag
    ){
        synchronized(world.loginInitializationLock()){
        LocalAccountLifecycle.Selection account=
            LocalAccountLifecycle.select(
                world,
                loginAlias,
                tag
            );

        String username=account.username;
        boolean persistentAccount=account.persistent;
        // G21.189: capture opt-in once before a session is admitted.
        // Dynamic flag changes after login are explicitly unsupported.
        final boolean reviewVeto=Boolean.getBoolean(
            LocalReviewIntentAdmissionVeto.PROPERTY);
        final boolean oneShotReview=Boolean.getBoolean(
            LocalOneShotActiveReviewOnAdmission.PROPERTY);
        LocalReviewIntentAdmissionVeto.requireConfiguration(
            reviewVeto,persistentAccount);
        LocalOneShotActiveReviewOnAdmission.requireConfiguration(
            oneShotReview,persistentAccount,reviewVeto);
        // G21.298: reject inconsistent opt-in durable NEGATIVE
        // final-review evidence before any account hydration/admission.
        LocalFinalSaveNegativeWitness.requireAdmissionConfiguration(
            persistentAccount);

        // G21.181 opt-in: acquire before loading or creating any account
        // state. A competing World with the same concrete account path
        // cannot concurrently become an admitted owner in this JVM.
        LocalJvmAccountSessionLease.Token sessionLease=
            persistentAccount
                ?LocalJvmAccountSessionLease.acquire(persistence,username)
                :null;
        // If registration succeeds but a subsequent post-register step
        // throws, do NOT release an opt-in ownership token while a World
        // member may still exist. Preserve fail-closed manual review.
        boolean worldMembershipCommitted=false;
        try{
        // Reject a preexisting ACTIVE/review leaf before any account
        // snapshot hydration, defaults or World registration.
        LocalReviewIntentAdmissionVeto.requireAdmissible(
            reviewVeto,persistentAccount,sessionLease);

        LocalAccountLifecycle.LoadResult accountLoad=
            LocalAccountLifecycle.load(
                account,
                worldPlayer,
                persistence,
                PetAccessoryAuthority::isAccessory,
                tag
            );

        if(accountLoad.failed)
            throw new IllegalStateException(
                "ACCOUNT_LOAD_FAILED profile="+
                username+
                " repository="+
                persistence.repositoryName()
            );

        if(accountLoad.missing&&
           PlayabilityStarterAccountPolicy.enabled()){
            PlayabilityStarterAccountPolicy.Result starter=
                PlayabilityStarterAccountPolicy.apply(
                    worldPlayer
                );

            System.out.println(
                tag+
                "PLAYABILITY_STARTER_ACCOUNT_APPLIED "+
                "profile="+username+
                " "+starter
            );
        }

        petAccessoryState.setActiveItem(
            accountLoad.accessoryItem
        );

        // One-time migration from the superseded LocalLab bug that stored native
        // icons in AMMO.
        if(!playerState.cosmetic().active()&&
           ItemCatalog.isNativePlayerIcon(
               equipment.itemAt(EquipmentSlot.AMMO)
           )){
            int legacyIcon=
                equipment.unequip(EquipmentSlot.AMMO);
            playerState.cosmetic().set(legacyIcon);

            System.out.println(
                tag+
                "V511_COSMETIC_MIGRATION legacyAmmoIcon="+
                legacyIcon+
                " -> dedicatedBs cosmetic=true ammoCleared=true"
            );
        }

        playerState.syncEquipmentPresentation(equipment);

        /*
         * Keep login privilege (Client.cT) separate from per-player appearance
         * role (rs.a.k.aC). Exact v308 proves these are distinct channels, and
         * the original SpawnPK named-rank -> aC table is still unknown server
         * authority. Do not infer aC from LOCAL_DEV_RANK here.
         */
        if(petState.active()){
            PetDefinitionRepository.Def persistedDef=
                PetDefinitionRepository.get(
                    petState.itemId()
                );

            if(persistedDef!=null&&
               persistedDef.npcId!=petState.npcId()){
                int oldNpc=petState.npcId();
                petState.activate(persistedDef);

                System.out.println(
                    tag+
                    "V59_PET_MAPPING_RECONCILE item="+
                    petState.itemId()+
                    " npc="+oldNpc+
                    "->"+petState.npcId()+
                    " provenance="+persistedDef.provenance
                );
            }

            petEffects.onPetChanged(
                petState.itemId(),
                petState.npcId()
            );
        }

        // Apply maintained Scopesight fixture values before the initial packet-134
        // skill publication.
        playerState.syncScopesightMaintenance(
            scopesightActive()
        );

        DefaultLoadoutService.Snapshot g1Default=
            G1DefaultLoadoutRegearService
                .ensureStarterDefault(
                    world,
                    username
                );

        System.out.println(
            tag+
            "G1_DEFAULT_LOADOUT_READY owner="+
            username+
            " id="+
            g1Default.defaultLoadoutId+
            " revision="+
            g1Default.selectionRevision+
            " authority="+
            g1Default.policyAuthority
        );

        // G21.35: a review fence or account mutation may have appeared
        // while starter/pet/default-loadout initialization was running.
        // Recheck on the SAME normal persistence FIFO immediately before
        // publishing World membership; never hydrate from forensic reads.
        beforeRegistration.run();
        MailboxLateSessionAdmission.requireStillAdmissible(
            account,accountLoad,persistence
        );
        // Fail closed if the review sidecar appeared while setup ran.
        // This remains an observation, NOT an atomic RAW-writer fence.
        LocalReviewIntentAdmissionVeto.requireAdmissible(
            reviewVeto,persistentAccount,sessionLease);
        // G21.190 ONE-SHOT crash-negative: force ACTIVE intent before
        // membership, but NEVER retire it on healthy final save.
        // A later login must be manually reviewed and denied by G21.189.
        LocalOneShotActiveReviewOnAdmission.forceBeforeWorldRegistration(
            oneShotReview,persistentAccount,sessionLease);

        long generation=
            world.registerPlayerAndStart(
                worldPlayer,
                username
            );
        worldMembershipCommitted=true;

        // G21.200: record the exact in-memory World owner identity and
        // generation in the already-held optional account lease. This
        // is not an on-disk epoch, a save fence, or ACTIVE retirement.
        if(sessionLease!=null)
            sessionLease.bindAdmittedWorldOwner(
                worldPlayer,generation);
        else if(persistentAccount){
            // G21.201 late-enable compatibility: record that THIS
            // actual World membership predates any optional account
            // lease. A later opt-in owner is not its retroactive token.
            // This deliberately retains the G21.184 unsafe legacy
            // overlapping-save behavior rather than disguising it.
            java.nio.file.Path file=
                persistence.fileBackedAccountPathForJvmSessionLease(
                    username);
            if(file!=null)
                LocalJvmAccountSessionLease.noteUnleasedWorldAdmission(
                    file,username,worldPlayer,generation);
        }

        System.out.println(
            tag+"V512_WORLD_REGISTER playerId="+
            worldPlayer.id()+
            " generation="+generation+
            " username="+username+
            " members="+world.players().size()+
            " worldIdentity="+System.identityHashCode(world)
        );

        return new Result(
            username,
            persistentAccount,
            accountLoad.missing,
            generation,
            sessionLease
        );
        }catch(RuntimeException|Error failure){
            // Before World registration, no session state was admitted:
            // release the unused lease. AFTER registration, even if this
            // initializer did not return a Result, retention is safer
            // than enabling overlapping same-account admission.
            if(sessionLease!=null&&!worldMembershipCommitted)
                sessionLease.releaseAfterInitializationFailure();
            throw failure;
        }
        }
    }

    private boolean scopesightActive(){
        return petState.active()&&
            petState.itemId()==ScopesightPetProfile.ITEM_ID&&
            petState.npcId()==ScopesightPetProfile.NPC_ID;
    }
}

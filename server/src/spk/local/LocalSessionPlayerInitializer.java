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

        Result(
            String username,
            boolean persistentAccount,
            boolean newAccount,
            long worldPlayerGeneration
        ){
            this.username=username;
            this.persistentAccount=persistentAccount;
            this.newAccount=newAccount;
            this.worldPlayerGeneration=worldPlayerGeneration;
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

        long generation=
            world.registerPlayerAndStart(
                worldPlayer,
                username
            );

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
            generation
        );
        }
    }

    private boolean scopesightActive(){
        return petState.active()&&
            petState.itemId()==ScopesightPetProfile.ITEM_ID&&
            petState.npcId()==ScopesightPetProfile.NPC_ID;
    }
}

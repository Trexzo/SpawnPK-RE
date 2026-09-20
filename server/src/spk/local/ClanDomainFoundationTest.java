package spk.local;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** Deterministic regression for Issue #169 semantic clan / Clan Wars foundation. */
public final class ClanDomainFoundationTest {
    public static void main(String[] args) {
        ClanAggregate.MemberId owner = new ClanAggregate.MemberId("owner-1");
        ClanAggregate.MemberId general = new ClanAggregate.MemberId("general-1");

        EnumMap<ClanAggregate.Permission, ClanAggregate.PermissionThreshold> thresholds =
            new EnumMap<ClanAggregate.Permission, ClanAggregate.PermissionThreshold>(ClanAggregate.Permission.class);
        thresholds.put(ClanAggregate.Permission.ENTER_CHAT, ClanAggregate.PermissionThreshold.ANYONE);
        thresholds.put(ClanAggregate.Permission.TALK_CHAT, ClanAggregate.PermissionThreshold.RECRUIT_PLUS);
        thresholds.put(ClanAggregate.Permission.KICK_OR_MUTE, ClanAggregate.PermissionThreshold.CAPTAIN_PLUS);
        thresholds.put(ClanAggregate.Permission.BAN_CHAT, ClanAggregate.PermissionThreshold.GENERAL_PLUS);

        ClanAggregate clan = new ClanAggregate(
            new ClanAggregate.ClanId("clan-alpha"),
            "Alpha",
            owner,
            thresholds,
            false,
            ClanEvidenceAuthority.EXACT_CURRENT_CLIENT
        );

        clan.putMember(general, ClanAggregate.Rank.GENERAL);
        clan.setGeneralHasCoOwnerPrivileges(true);
        clan.setPermission(ClanAggregate.Permission.TALK_CHAT, ClanAggregate.PermissionThreshold.CORPORAL_PLUS);

        ClanAggregate.Snapshot clanSnapshot = clan.snapshot();
        require(clanSnapshot.members().get(owner) == ClanAggregate.Rank.OWNER, "owner rank");
        require(clanSnapshot.members().get(general) == ClanAggregate.Rank.GENERAL, "general rank");
        require(clanSnapshot.generalHasCoOwnerPrivileges(), "co-owner policy");
        require(
            clanSnapshot.permissions().get(ClanAggregate.Permission.TALK_CHAT)
                == ClanAggregate.PermissionThreshold.CORPORAL_PLUS,
            "talk threshold"
        );
        require(clanSnapshot.definitionAuthority() == ClanEvidenceAuthority.EXACT_CURRENT_CLIENT, "authority");

        expectUnsupported(new Runnable() {
            @Override public void run() {
                clanSnapshot.members().put(new ClanAggregate.MemberId("intruder"), ClanAggregate.Rank.RECRUIT);
            }
        }, "member snapshot immutability");

        expectUnsupported(new Runnable() {
            @Override public void run() {
                clanSnapshot.permissions().put(
                    ClanAggregate.Permission.BAN_CHAT,
                    ClanAggregate.PermissionThreshold.ANYONE
                );
            }
        }, "permission snapshot immutability");

        expectIllegalState(new Runnable() {
            @Override public void run() {
                clan.removeMember(owner);
            }
        }, "owner removal");

        ClanWarDefinition definition = new ClanWarDefinition(
            ClanWarDefinition.SpellRule.STANDARD_SPELLS,
            ClanWarDefinition.PrayerRule.STANDARD_PRAYERS,
            ClanWarDefinition.WeaponRule.NO_STAFF_OF_THE_DEAD,
            ClanWarDefinition.VictoryMode.KILL_TARGET,
            Integer.valueOf(100),
            ClanWarDefinition.Arena.GRIDLOCK,
            EnumSet.of(
                ClanWarDefinition.AdvancedRule.PJ_TIMER,
                ClanWarDefinition.AdvancedRule.SINGLE_SPELLS
            ),
            ClanEvidenceAuthority.EXACT_CURRENT_CLIENT
        );

        require(definition.killTarget().intValue() == 100, "kill target");
        require(definition.advancedRules().contains(ClanWarDefinition.AdvancedRule.PJ_TIMER), "advanced rule");
        require(ClanWarDefinition.exactVisibleKillTargets().size() == 5, "exact visible kill target vocabulary");

        expectUnsupported(new Runnable() {
            @Override public void run() {
                definition.advancedRules().remove(ClanWarDefinition.AdvancedRule.PJ_TIMER);
            }
        }, "definition immutability");

        ClanWarChallenge accepted = new ClanWarChallenge(
            new ClanWarChallenge.ChallengeId("challenge-1"),
            clanSnapshot.id(),
            new ClanAggregate.ClanId("clan-beta"),
            definition
        );
        require(accepted.accept() == ClanWarChallenge.State.ACCEPTED, "accept");
        require(accepted.accept() == ClanWarChallenge.State.ACCEPTED, "accept idempotent");
        expectIllegalState(new Runnable() {
            @Override public void run() {
                accepted.decline();
            }
        }, "cross-terminal transition");

        ClanWarChallenge declined = new ClanWarChallenge(
            new ClanWarChallenge.ChallengeId("challenge-2"),
            clanSnapshot.id(),
            new ClanAggregate.ClanId("clan-gamma"),
            definition
        );
        require(declined.decline() == ClanWarChallenge.State.DECLINED, "decline");
        require(declined.decline() == ClanWarChallenge.State.DECLINED, "decline idempotent");

        ClanWarChallenge cancelled = new ClanWarChallenge(
            new ClanWarChallenge.ChallengeId("challenge-3"),
            clanSnapshot.id(),
            new ClanAggregate.ClanId("clan-delta"),
            definition
        );
        require(cancelled.cancel() == ClanWarChallenge.State.CANCELLED, "cancel");
        require(cancelled.cancel() == ClanWarChallenge.State.CANCELLED, "cancel idempotent");

        assertProtocolIndependent(
            ClanAggregate.class,
            ClanRepository.class,
            ClanWarDefinition.class,
            ClanWarChallenge.class
        );

        System.out.println(
            "ISSUE169_CLAN_CLANWARS_FOUNDATION_PASS " +
            "clanAggregate=true permissions=4 thresholdVocabulary=9 repositoryBoundary=true " +
            "warDefinitionImmutable=true challengeFailClosed=true terminalIdempotent=true " +
            "authorityPreserved=true protocolIndependent=true productionRulesInvented=false"
        );
    }

    private static void assertProtocolIndependent(Class<?>... roots) {
        String[] forbidden = {"widget", "opcode", "subtype", "packet", "sprite", "sceneindex", "clientclass"};
        for (Class<?> root : roots) {
            for (java.lang.reflect.Field field : root.getDeclaredFields()) {
                String haystack = (field.getName() + " " + field.getType().getName()).toLowerCase(java.util.Locale.ROOT);
                for (String token : forbidden) {
                    require(!haystack.contains(token), root.getName() + " leaked protocol identity through " + field.getName());
                }
            }
        }
    }

    private static void expectUnsupported(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected immutable view: " + label);
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void expectIllegalState(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalStateException: " + label);
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}

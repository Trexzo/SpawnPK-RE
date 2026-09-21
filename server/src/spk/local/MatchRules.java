package spk.local;

import java.util.Objects;

/**
 * Immutable semantic match capability/rule metadata.
 *
 * These values describe caller-selected policy. Enforcement remains outside
 * this object and no client widgets/config ids are encoded here.
 */
final class MatchRules {
    enum TeamMode {
        FREE_FOR_ALL,
        TEAMS
    }

    enum SpellPolicy {
        UNRESTRICTED,
        STANDARD_ONLY,
        BINDING_ONLY,
        DISABLED
    }

    enum PrayerPolicy {
        UNRESTRICTED,
        STANDARD_ONLY,
        DISABLED
    }

    enum RestrictionPolicy {
        ALLOWED,
        DISABLED
    }

    enum WinConditionKind {
        SCORE_TARGET,
        LAST_TEAM_STANDING,
        CALLER_RESOLVED
    }

    static final long NO_SCORE_TARGET=-1L;

    final TeamMode teamMode;
    final SpellPolicy spellPolicy;
    final PrayerPolicy prayerPolicy;
    final RestrictionPolicy freezePolicy;
    final RestrictionPolicy interferencePolicy;
    final WinConditionKind winConditionKind;
    final long scoreTarget;
    final String modeKey;
    final String sourceAuthority;

    MatchRules(
        TeamMode teamMode,
        SpellPolicy spellPolicy,
        PrayerPolicy prayerPolicy,
        RestrictionPolicy freezePolicy,
        RestrictionPolicy interferencePolicy,
        WinConditionKind winConditionKind,
        long scoreTarget,
        String modeKey,
        String sourceAuthority
    ){
        this.teamMode=
            Objects.requireNonNull(
                teamMode,
                "teamMode"
            );
        this.spellPolicy=
            Objects.requireNonNull(
                spellPolicy,
                "spellPolicy"
            );
        this.prayerPolicy=
            Objects.requireNonNull(
                prayerPolicy,
                "prayerPolicy"
            );
        this.freezePolicy=
            Objects.requireNonNull(
                freezePolicy,
                "freezePolicy"
            );
        this.interferencePolicy=
            Objects.requireNonNull(
                interferencePolicy,
                "interferencePolicy"
            );
        this.winConditionKind=
            Objects.requireNonNull(
                winConditionKind,
                "winConditionKind"
            );

        if(winConditionKind==WinConditionKind.SCORE_TARGET){
            if(scoreTarget<=0)
                throw new IllegalArgumentException(
                    "scoreTarget="+scoreTarget+
                    " required for SCORE_TARGET"
                );
        }else if(scoreTarget!=NO_SCORE_TARGET)
            throw new IllegalArgumentException(
                "scoreTarget="+scoreTarget+
                " invalid for "+winConditionKind
            );

        this.scoreTarget=scoreTarget;
        this.modeKey=normalizeKey(modeKey,"modeKey");
        this.sourceAuthority=
            requireText(
                sourceAuthority,
                "sourceAuthority"
            );
    }

    boolean hasScoreTarget(){
        return scoreTarget!=NO_SCORE_TARGET;
    }

    static String normalizeKey(String value,String field){
        if(value==null)
            throw new NullPointerException(field);

        String normalized=
            value.trim().toLowerCase(
                java.util.Locale.ROOT
            );

        if(normalized.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        if(normalized.length()>160)
            throw new IllegalArgumentException(
                field+" too long"
            );

        for(int i=0;i<normalized.length();i++){
            char c=normalized.charAt(i);
            boolean ok=
                c>='a'&&c<='z'||
                c>='0'&&c<='9'||
                c=='.'||c=='_'||c=='-'||c==':';

            if(!ok)
                throw new IllegalArgumentException(
                    "invalid "+field+"="+value
                );
        }

        return normalized;
    }

    static String requireText(String value,String field){
        if(value==null)
            throw new NullPointerException(field);

        String clean=value.trim();

        if(clean.isEmpty())
            throw new IllegalArgumentException(
                field+" blank"
            );

        return clean;
    }
}

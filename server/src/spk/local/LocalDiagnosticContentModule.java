package spk.local;

import java.util.Objects;
import spk.content.api.*;

/**
 * Internal LocalLab diagnostics exposed through the content-command lifecycle.
 *
 * This module intentionally stays in spk.local: it may read internal diagnostic
 * state, but no such capability is added to the public plugin/content API.
 */
final class LocalDiagnosticContentModule
    implements ContentModule {

    static final String MODULE_ID=
        "locallab-diagnostics";

    private final ContentRegistry registry;

    LocalDiagnosticContentModule(
        ContentRegistry registry
    ){
        this.registry=
            Objects.requireNonNull(
                registry,
                "registry"
            );
    }

    @Override public String id(){
        return MODULE_ID;
    }

    @Override public void register(
        ContentRegistrar registrar
    ){
        registrar.command(
            "contentregistry",
            100,
            context->
                ContentResult.handled(
                    "CONTENT_REGISTRY_DIAGNOSTIC "+
                        registry.summary(),
                    null
                )
        );

        registrar.command(
            "authority",
            100,
            context->
                ContentResult.handled(
                    authoritySummary(),
                    null
                )
        );
    }

    static String authoritySummary(){
        return "V5124_AUTHORITY "+
            AuthorityR16R25Publisher.status()+
            " bankWrapperExact="+
            BankState.BANK_WRAPPER_ROOT+
            " bankRuntimeRoot="+
            BankState.BANK_ROOT+
            " combatProfiles="+
            CombatStyleRepository.rootCount()+
            " combatStyles="+
            CombatStyleRepository.countStyles()+
            " note=R25_core_17_59_plus_independent_exact_staff328_3; unproven_server_mechanics_remain_fail_closed";
    }
}

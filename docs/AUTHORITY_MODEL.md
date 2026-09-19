# Authority model

- EXACT_CURRENT_CLIENT — proven from pinned current client bytecode.
- EXACT_CURRENT_CACHE — proven from exact current cache/config data.
- LOCAL_RUNTIME_PROVEN — reproduced in LocalLab/runtime.
- HISTORICAL_CORROBORATION — supported by historical client/build evidence.
- INFERENCE — reasoned but not directly proven.
- UNKNOWN_SERVER_AUTHORITY — client path known, production server outcome unknown.
- CUSTOM_LOCALLAB — deliberately LocalLab-created behavior/content.

Do not silently promote inference, unknown server authority, or custom LocalLab
content into recovered production behavior.
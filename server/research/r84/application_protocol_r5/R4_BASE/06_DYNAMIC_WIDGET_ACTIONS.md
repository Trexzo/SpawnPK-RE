# Dynamic widget action/text/geometry protocol — subtype 37

The server can mutate widget actions at runtime without replacing the whole interface:
- op0 clear action strings
- op1 set one action slot
- op2 fill all action slots with one string
- op3 allocate/reset or remove the action-string array
- op4 update two widget scalar/geometry fields (`ac`, `ap`)

This explains why static interface builders alone are insufficient to recover every production right-click/action label. LocalLab should expose this as a generic widget-action publisher rather than hardcoding per-interface patches.

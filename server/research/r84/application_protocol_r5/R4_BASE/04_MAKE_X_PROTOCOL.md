# Make-X quantity interface

Subtype 35 is now operation-level closed. It controls item previews, the 1/5/10/X/All selector, dynamic titles/subtitles, per-row action/tooltips and row imagery/resources.

Notable exact widgets:
- amount controls 55303/55305/55307/55309/55311, labels immediately following
- upper preview family 55314/55316/55318/55320
- lower/action family 55322/55324/55326/55328/55330

Operation 4 is not just text: it timestamps the row then calls the exact resource resolver. Strings beginning `npc_<id>` trigger the NPC-definition sprite path (with asynchronous loading if not cached); other strings are loaded as named image resources.

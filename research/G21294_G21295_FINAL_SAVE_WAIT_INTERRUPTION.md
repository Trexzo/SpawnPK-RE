# G21.294/G21.295 — interrupted final-save wait remains a failure

Parent [#2848](https://github.com/Trexzo/SpawnPK-RE/pull/2848) exact `35f29f2d4f813cb844a39b11882feec3cd252ed8`, 703/703 hosted certified (Java11 major55/full Gradle).

**Real production issue:** `LocalSession.saveAccountFinal` calls `SaveTicket.completion.get(5,SECONDS)` under a `catch(Throwable)`. A thrown `InterruptedException` clears the session thread interrupt bit; the catch logged but never restored it. This can mask shutdown/cancellation intent even though an active opt-in in-JVM lease correctly remains stranded on uncertain final save.

**Fix:** exactly the same 5,000ms bounded worker-completion condition now calls `LocalFinalSaveCompletionWait.awaitConfirmed`, which restores `Thread.currentThread().interrupt()` **only** if `CompletableFuture.get` throws `InterruptedException`, then rethrows. Existing `reservation.abort(e)`, failure logs, and no lease release on uncertain outcome are unchanged. A timeout does not cancel the still-owned worker future; a worker exception/cancellation cannot pass as success.

**Real G21294/95 regressions:** timeout does not cancel or complete worker; eventual independent completion does not retroactively validate the earlier outcome; underlying IOException exception, cancellation and zero deadline rejected. A real second Java thread is interrupted during the wait and exits with its own interrupted bit restored while worker future remains pending.

Focused count 703→705, explicit individual JavaExec tasks. Critical 23-class G21.293 retention remains in the same build. No World persistence mutation, server packet, gameplay, recovery, raw writer, durable final-save receipt or owner epoch added. Not an account durability fix; #2540/#2559 stay open.

Gate: fresh exact-head hosted 705/705, G21294/95, Java11 major55, full Gradle; draft/unmerged, main untouched.

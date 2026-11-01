# Feature: compute results carried across submits on the GPU (wb-carry)

Locator: `odd/tasks/wb-carry.md` (Engram mirror: `odd/wb-carry/tasks`, project "nierauto port android").

## Objective
Stop the replay from waiting for the whole previous GPU frame when NieR reads, in frame N+1, what a compute shader
wrote in frame N (a 1920-byte storage binding and 12-byte indirect dispatch arguments).

## Problem / why
Compute-written regions live one submit in the upload ring; their results reach guest memory only through deferred
write-backs. Frame N+1's `written_bind` (and the indirect arguments read) needed those bytes, so it applied N's
write-back and waited for N's fence: ~18 ms a frame on the PC (Android mode), 33-57 ms on the Adreno 610. Not waiting
on the binding alone only moved the wait to the indirect arguments (PROGRESO 22).

## Design
- End of submit N (`written_carry`, nvn_vk.cpp before `frame_submit`, pipelined + deferred only): each region is
  copied on the GPU into a persistent device-local arena; a carry {range, arena offset, producer serial, expected guest
  bytes, link} is registered. Arena entries are freed FIFO once the consumer submit (producer + 1) retires.
- Submit N+1 (`written_adopt`): a compute binding, compute read or indirect-argument read of bytes covered by a carry
  from submit N whose write-back is still pending, and whose guest bytes still equal the carry's expected bytes (no CPU
  write since), becomes a region of N+1 initialised by a GPU copy from the arena: no wait.
- Its `before` (initial content) is N's GPU result: filled when N's write-back is applied (always before N+1's), via
  `WriteBack::next`; the same write-back mirrors its changed words into the successor's expected bytes.
- Any doubt (CPU wrote, partial overlap with other regions, arena full, carry not from the previous submit): the old
  path (apply the write-back, wait).
- `NX_WB_CARRY=0`: off.

## Constraints
- Same guest-visible results as before (write-back diffs, order oldest first).
- Shared runtime (P5R/P4G use it): off when write-backs are not deferred; no effect without carries.
- Test-first exception: no unit-test harness for the Vulkan replay; functional checks are PC runs (bench.sh, Android
  mode) comparing fps, the "reads that applied deferred write-backs" log line and screenshots against the baseline.

## Tasks
- [x] T1 Carry arena + registry + `written_carry` + `WriteBack::next` filling in `apply_write_back` (route: inline,
      one coherent change in nvn_written.cpp / nvn_frames.cpp / nvn_vk.cpp / nvn_vk.h)
- [x] T2 `written_adopt` hooked into `written_bind`, `written_read` and indirect dispatch arguments
- [x] T3 PC verification: fps vs baseline (26.6 fps `runs/wbsite1`), log line, frame comparison, no crash; commit
- [ ] T4 Android build; tablet test when the tablet is free (pending user)

## Acceptance criteria
- Android-mode PC run: the "written bind" / "indirect" waits mostly gone, fps above 26.6, frames visually identical.
- No new crash in a 285 s `bench.sh` run.

## Checks
`games/nier/tools/build_pc.cmd`, `bench.sh <name> "NX_PIPELINED=1;NX_HANDHELD=1"`, log grep, frame diff.

## Progress
- T1-T3 done (route: inline; one coherent change across nvn_written.cpp, nvn_frames.cpp, nvn_vk.cpp/.h, nvn_draw.cpp,
  nvn_draw_state.h). Risk tier: high (concurrency/ordering of GPU data across submits): writer self-check by A/B runs;
  independent verifier not available in this session (no subagents) -> pending, compensated by the 0-refusal log and
  frame review.
- Evidence (PC, Android mode, same build, `bench.sh` 285 s): `NX_WB_CARRY=0` 26.6 fps with "written bind 60
  (1208 ms)" waits every 60 frames; carry on **53.0 fps**, no deferred write-back waits at all, 4474/4837 and 2100/2100
  ranges adopted, 0 refused for CPU writes, 0 arena-full, 0 crashes; frames (`runs/carry_cmp.png`) render clouds, sea,
  explosions, lasers and GPU particles normally. Commit: `5a644f2`.

## Delivery
Local branch `nier`, work-unit commits; no push. Strategy: single local branch (no PR).

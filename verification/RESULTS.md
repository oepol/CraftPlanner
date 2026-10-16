# Verification – Craft Planner 1.1.0+mc26.3

Date: 2026-10-16. Minecraft 26.3, Java 25, Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, optional Mod Menu 21.0.0.

## Passed

- Gradle 9.6.0 portable build and JUnit suite: **50 tests, 0 failures, 0 errors**. The normal Loom path remains in the project; the host requires the documented portable path because of its Unix-socket probe restriction.
- 20 original resolver regressions and 4 original config tests.
- 12 workflow tests: repeater chain uses normal stone; raw material/intermediate progression; target completion; shared alternative stock; shulker arithmetic; transfer totals; cached-stock exclusion; double-chest identity; source deduplication/removal; pin cap and substeps.
- 12 lifecycle tests: world profiles and restart; legacy import; draft-settings rollback/persistence; async cancellation/errors; archived completion/restart; partial deposit; loss/other-player negative cases; shared deposit limits; incomplete goals; withdrawal/redeposit; partially delivered stock is not allocated twice.
- 2 language tests: DE/EN key and placeholder parity, required literal translation keys, removal of the old visible slogan.
- Real Minecraft registry bootstrap plus default component binding: named white and blue shulker boxes, multiple contents, updated actual ItemContainerContents and disabled inclusion. The first standalone attempt exposed a missing default-component binding in the test harness; the final harness initializes it using Minecraft’s own initializers and passes. See component-check.log.
- With Mod Menu: 41 mod classes linked, the Mod Menu config-screen factory obtained, and the Fabric client entrypoint constructed.
- Without Mod Menu on the classpath: 40 non-optional mod classes linked and the Fabric client entrypoint constructed. The optional compatibility entrypoint is deliberately not loaded.
- JAR contains only Craft Planner classes/resources and license, no Minecraft or dependency classes.

## Not run / limits of evidence

These are headless compilation, data-structure and lifecycle checks, not an end-to-end game test. Not run: graphical Fabric client startup/event registration, visual layout at GUI scales, actual Mod Menu click/navigation, real-world chest transfers and network packet timing, live recipe-manager reload, world/dimension/death/respawn gameplay. The portable build does not claim a successful Loom build in this restricted host.

Read README.md for the exact supported material sources, conservative component-dependent recipe handling, cached-chest policy and archived-goal semantics. No auto-crafting or inventory mutation is implemented.

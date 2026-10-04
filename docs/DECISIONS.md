# Design decisions log

Rules: [`DESIGN.md` → DESIGN DECISION PROTOCOL](DESIGN.md#design-decision-protocol). One entry per decision, newest last.

- **Tuning keys** name entries in the planned tuning data file (not created yet).
- **Reversible:** easy = change a tuning value or a small piece of code; hard = saved data or a big visible change.
- **Status:** DRAFT means decided in principle, not built yet.

| # | Date | Question | Choice | Why | Alternatives | Tuning key | Reversible | Status |
|---|---|---|---|---|---|---|---|---|
| D1 | 2026-10-03 | Do kingdoms keep running when no player is near? | Keep kingdom areas loaded first (they keep simulating), up to a cap. | A living kingdom shouldn't freeze when the observer walks away. | Simulate unloaded kingdoms abstractly; freeze them when unloaded. | `kingdom.max_loaded` | Easy (cap); switching to abstract simulation is hard | DRAFT |
| D2 | 2026-10-03 | What happens when the king dies? | The kingdom becomes scenic ruins, and its soldiers migrate to other kingdoms. | No punishing wipe-out; leaves something to look at; soldiers live on. | Kingdom vanishes; a new king is crowned; the soldiers turn hostile. | `kingdom.ruin.*`, `kingdom.migration.*` | Hard (visible, saved state) | DRAFT |
| D3 | 2026-10-03 | How is a kingdom laid out? | Organic: soft ring zones around the keep, a master plan, scored building slots, radial roads, and a wall that follows the terrain. | Looks natural and readable to an observer; scoring reuses the `BuildSitePlanner` checks. | Fixed grid; a fully random layout. | `kingdom.layout.*` (ring radii, slot scoring weights, road and wall settings) | Medium (the master plan is saved) | DRAFT |
| D4 | 2026-10-03 | How does the keep grow with tiers? | A tier upgrade may replace the keep or extend it. | Visible progress without rebuilding everything. | Always replace; always add new buildings. | `kingdom.tiers.*` | Easy | DRAFT |
| D5 | 2026-10-03 | What do gifts from the player do? | They boost the economy or the soldiers. | The player helps instead of controlling; no penalties. | Gifts as tribute or diplomacy only. | `gifts.*` | Easy | DRAFT |
| D6 | 2026-10-03 | How does mining work? | In stages (e.g. surface, then shallow, then deeper), unlocked over time. | Gradual and readable; limits terrain damage early on. | Unlimited mining from the start. | `mining.stages.*` | Easy | DRAFT |
| D7 | 2026-10-03 | Where does the population come from? | It starts with visitors who join the kingdom, later slow reproduction. | Sims-like growth; no sudden swarms. | Spawns from buildings; created only by the player. | `population.visitor_rate`, `population.birth_rate` | Easy | DRAFT |
| D8 | 2026-10-03 | Where does the tuning file live and what format? | One flat JSON object with dotted, namespaced keys at `data/csr/tuning/tuning.json`, read through the data-pack system (mod resources, overridable by a world data pack). Missing key → default; bad value → reported + default; unknown key → reported. | It's in the mod's data resources (editable without compiling, reloadable), and worlds can override it the vanilla way. Flat keys are easy to search and document. | A config file in `config/`; nested JSON objects; one file per namespace. | — (format) | Easy before stage 1 uses it | built (stage 0) |
| D9 | 2026-10-03 | Is the number of kingdom tiers tunable? | No: `Tuning.TIERS` = 10 is a design constant; per-tier keys are lists of exactly 10. | The tier count shapes content and the roadmap (tiers 1–10), not balance. | A `kingdom.tier_count` key. | — | Easy (one constant) | built (stage 0) |

Migration notes (saved-data changes): none yet.

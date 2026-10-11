# AI safety

Aranya gives agricultural advice. Advice that is confidently wrong can cost a farmer a
season, so this document defines the limits of automated reasoning in the product.

## 1. Position for Stage 01

**No AI or machine-learning component exists in Aranya Stage 01.** No model is bundled, no
inference runs on device, no remote AI provider is called. The app makes no inferences of
any kind about crops, soil, weather or markets.

This is intentional, and it should stay the default until the underlying data is
trustworthy. A language model over poor data produces confident prose, and confident prose
is exactly what an agricultural product must not produce.

## 2. Non-negotiable rules

1. **No proprietary AI provider may be a hard dependency.** Core functionality works with
   no account, no key and no subscription.
2. **No model may be bundled in the APK.** It breaks the size budget and the offline-first
   guarantee simultaneously.
3. **No model output may be presented as data.** Model output is labelled as model output.
4. **A model may never invent a measurement.** Fabricated values are the single worst
   failure this product can have. See `docs/AI_SAFETY.md` section 5.
5. **Agricultural advice must be traceable to a source or a stated rule.** Anything else is
   an opinion and must be labelled one.

## 3. Where judgement may legitimately be used

Not all synthesis is forbidden. These uses are acceptable, with the constraints given:

| Use | Constraint |
| --- | --- |
| Explaining a retrieved value in plain language | Must not introduce a number not in the record |
| Summarising several retrieved values | Every component must retain its own provenance and freshness |
| Ranking options the user explicitly narrowed to | Must state the criteria used |
| Translating or rephrasing UI and retrieved text | Must not alter units, dates or quantities |
| Extracting structured fields from retrieved text | Must be validated against the record schema; rejection is a normal outcome |

In every case: **the data is retrieved, the judgement is presentation.** The system fetches
from sources of record and renders them; it does not invent them.

## 4. Prohibited uses

| Prohibited | Why |
| --- | --- |
| Predicting a specific yield or price | Unverifiable, and a farmer may act on it financially |
| Diagnosing a specific pest or disease from a photo | Requires validated reference material; a wrong diagnosis leads to wrong pesticide and wasted money |
| Recommending a specific pesticide, dose or fertiliser rate | Can damage soil, crop and health; needs local extension-officer validation |
| Claiming a soil value for a specific plot from a gridded model | ISRIC explicitly advises against farm-level use of SoilGrids |
| Any advice presented without its basis and date | Untraceable advice is worse than no advice |

## 5. The measurement rule, and what AI must never do

The existing rule in `data-pipeline/normalizers/normalize.py`:

```
may_say_measured()  ->  true only for kind in ("measured", "lab_reported")
```

A language model **must not** be able to override this. Concretely:

- A model must not write "your soil pH is 6.8" from a SoilGrids estimate. The correct
  rendering is a regional estimate with its limitation attached, and
  `describe_value_for_user()` returns `regional_estimate_not_a_field_test` at field scale.
- A model must not convert `kind: modelled` into prose that implies observation. Words
  like "measured", "found", "tested" and "your field shows" are banned for non-measured
  values, via `prohibited_claims`.
- If a model cannot render a value honestly, the correct behaviour is to render the value
  with its qualifier, or to not render it.

**Implementation requirement for any future AI feature:** the model's output passes
through the same `describe_value_for_user()` contract as any other renderer. The model
must not have a path to the screen that bypasses it. This should be enforced by a test.

## 6. Uncertainty communication

Users need to know how much weight a number carries, without being overwhelmed.

- Uncertainty is expressed as a **plain-language qualifier** chosen by rule, not by a
  model: estimate, measurement, regional estimate, plus an outdated prefix when stale.
- Freshness is always visible: updated time, or "Age unknown" when no timestamp exists.
  The app shows the latter rather than hiding the gap.
- Confidence labels are derived from measured evidence, never asserted by a provider's
  marketing copy.

## 7. If on-device AI is ever evaluated

Optional, separately assessed, off by default. Assessment must cover:

| Question | Requirement |
| --- | --- |
| Size | Model download must be on-demand, never in the APK. Report installed size increase. |
| Memory | Must run without OOM on an 8 GB device alongside normal app use. |
| Battery | Inference must not run in a continuous background service. |
| Offline | Must work with no network after first use, or say clearly that it does not. |
| Determinism | Same input, same output. Non-determinism is unacceptable for a numeric claim. |
| Licence | Model weights licence must be compatible with MIT distribution. |
| Honest labelling | Users must know when and how inference affected a result. |

A model that fails any of these does not ship.

## 8. Review checklist for any AI feature

Before merge, every item must be answerable with evidence:

- [ ] What data was retrieved, from which source, and when?
- [ ] Does any sentence assert a measurement that is not `lab_reported`?
- [ ] Does every rendered number trace to a record field?
- [ ] Are uncertainty and freshness visible in the UI?
- [ ] Does the output pass through `describe_value_for_user()`?
- [ ] Does it work with no account, key, subscription or network?
- [ ] Is the model licence compatible with MIT distribution?
- [ ] What is the measured APK and memory cost?

If any answer is unknown, the feature is not ready.

## 9. Reporting

A user who believes Aranya gave a wrong recommendation should be able to report it, and
the report should carry the source ids, timestamps and retrieval records for that moment.
That is a direct benefit of the provenance architecture in Stage 01: the evidence needed
to investigate already exists on the device. Stage 02 should expose it.

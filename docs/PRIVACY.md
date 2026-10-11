# Privacy

Aranya is used by farmers, often on shared or low-cost devices, sometimes on a family
connection. Privacy is a product requirement, not a compliance exercise.

## 1. Stage 01 collects nothing

Stage 01 declares **no Android permissions** and contains **no network code**. The
`AndroidManifest.xml` requests nothing, so the app cannot access the network, location,
contacts, storage or the camera.

The app stores one thing: a generated registry snapshot in its own assets. It is
build-time content, not user data.

There is **no analytics, no advertising, no crash reporting and no tracking**. This is
enforced by `SourcePolicyTest` and `verify_source_policy.py`, which fail the build if any
of those SDKs appear in the Gradle configuration.

## 2. What will change, and why it is justified

Later stages must access the network. That requires `INTERNET` and `ACCESS_NETWORK_STATE`,
added **with the feature that needs them**, never pre-emptively.

Data sent off the device will be limited to what the request cannot avoid:

| Sent | Why | Is it personal? |
| --- | --- | --- |
| District coordinates | Required to request local weather | Only if precise GPS is used, which is not planned |
| API keys | Required by some sources | A credential, not user data; stored locally, never committed |
| User-Agent | Provider policy requires identification | No |

Aranya will not send a device identifier, advertising ID, account, name or location history
to any server, because none of them are needed for agricultural advice.

## 3. Location

**No GPS by default, and none planned.** District selection is manual entry or a coarse
administrative area. Weather and soil advice for a district does not require knowing a
farmer's exact position, and continuous precise location would be a serious privacy cost
with no corresponding benefit for this product.

If a future feature genuinely needs precise location (for example mapping a field boundary
the user draws), it must be requested at the moment of use, explained before the system
dialog appears, and refuse gracefully if denied.

## 4. Soil Health Card data

`soil_health_card` holds **farm-level personal data**: it identifies a farm and its
fertility results. Consequences:

- Aranya will not scrape or redistribute government soil records.
- The recommended path is **the farmer entering their own results**. That requires no
  redistribution, creates no third-party copy, and gives a genuine field-scale measurement.
- Values entered this way are marked `kind: lab_reported` with
  `basis: laboratory_analysis`, and `manual_entry_at` instead of `retrieved_at`, so they
  are never confused with a retrieved or modelled value.
- Anything already entered stays on the device unless the user explicitly exports it.

## 5. Storage

- Cached retrieved data is **disposable** and excluded from cloud backup
  (`backup_rules.xml`), so the user does not ship an agronomic history to a backup service
  they did not choose.
- Small preferences (district, language, theme) are backed up, because restoring them is
  convenient and reveals nothing sensitive.
- Cache growth is bounded by design. Nothing accumulates without limit.
- Deleting app data removes everything.

## 6. Offline by default

Aranya works from on-device data. This is partly a privacy property: a farmer is not
silent-broadcasting their location and interests to a server each time they open the app.
It also means the product remains useful with no connectivity, which is where a meaningful
share of users are.

## 7. No account, ever, for core use

No registration, no subscription, no cloud account. Credentials for sources that require
them are supplied by the user, stored locally, and never committed to version control.
`SourcePolicyTest` fails the build if an API key or secret appears in source.

## 8. Children and vulnerable users

Aranya is not directed at children. Agricultural advice affects household income, so the
product should not be marketed to or designed for minors.

## 9. Transparency

The app's Source Status screen shows every source, its real licence, its verification date
and its limitations. A farmer can check what Aranya used and when. `docs/LICENCES.md`
records every obligation in full.

This is the practical privacy guarantee: **Aranya does not require trust, it provides
evidence.**

## 10. Commitments for later stages

- [ ] Request a permission only at the moment the user invokes a feature needing it.
- [ ] Explain before the system dialog, in the user's language.
- [ ] Refuse gracefully when denied; no feature may hard-require a permission.
- [ ] Send nothing beyond what the request requires.
- [ ] Log nothing that identifies a person. Technical logs use source ids and timestamps.
- [ ] Never record a person's location history.
- [ ] Publish a plain-language data-handling notice before the first external release.
- [ ] Re-review every added permission in a pull request.

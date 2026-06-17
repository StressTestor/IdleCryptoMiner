# launch checklist

everything between "the code is done" and "live on Google Play production".
the code/CI items (M0-M4) are done; the rest need you (account, money, real
humans, hosted pages, Play Console forms). grouped by who/what.

## what the code already does

- [x] permanent `applicationId` `io.github.stresstestor.idlecryptominer`
- [x] signed, minified (R8 + resource shrink) `bundleRelease` wired to a
      tag-triggered CI job that uploads the `.aab` + `mapping.txt`
- [x] no INTERNET permission; `allowBackup=false` (save stays on device)
- [x] adaptive launcher icon + 512 store icon (`store-assets/ic_launcher-512.png`)
- [x] in-app "simulation, not real mining/money" disclaimer (settings)
- [x] lightweight crash logging + mapping upload for Play Vitals deobfuscation
- [x] reset-progress (full data wipe) for the data-deletion answer

## account + the testing gate (do this FIRST - it's the long pole)

- [ ] create a Google Play developer account, pay the $25 one-time fee
- [ ] complete identity verification (can take several days - start now)
- [ ] **closed test: 12+ testers opted in for 14 *continuous* days** before you
      can promote to production (hard gate for new personal accounts). recruit
      the testers now; this is the binding constraint on the launch date.
- [ ] review the Play automated pre-launch report; fold device/layout findings in

## signing (one time, irreversible - read docs/RELEASE.md)

- [ ] generate the upload keystore with `keytool`; back it up off-machine
- [ ] enroll in Play App Signing on first upload
- [ ] add CI secrets so the release workflow can sign:
      `KEYSTORE_BASE64` (`base64 -i upload.jks`), `KEYSTORE_PASSWORD`,
      `KEY_ALIAS`, `KEY_PASSWORD`
- [ ] cut a release: `git tag v1.0.0 && git push --tags`, then download the
      signed `.aab` artifact

## smoke test before submitting

- [ ] install the **minified release** build on real devices (incl. an API 24 /
      OEM-skin device) and play the core loop - R8 issues only show at runtime

## Play Console paperwork

- [ ] host a privacy policy and link it (state: all data on-device, nothing
      collected or sent)
- [ ] Data Safety form: no data collected/shared; data deletion = uninstall +
      in-app reset-progress (consistent with `allowBackup=false`)
- [ ] content rating questionnaire - answer deliberately for the crypto theme
      (it's a clicker: no real money, no real mining, no gambling)
- [ ] target audience / families declaration (general / 13+, not child-directed)
- [ ] store listing: title, short + full description, the 512 icon,
      1024x500 feature graphic, 2-3 phone screenshots
- [ ] promote off the closed track to production via a **staged % rollout**
      (e.g. 10-20%) while watching crashes/Vitals, then ramp to 100%

## deliberately deferred to v1.1 (not launch blockers)

- ads (AdMob/UMP), in-app purchases - the `AdManager` seam is kept but unused
- a crash-reporting backend (Crashlytics / Sentry) - v1 relies on Play Vitals +
  the logcat handler + uploaded mapping
- full UI-string externalization to `strings.xml` for i18n - the app is
  English-only for v1, so inline strings are fine; externalize when adding a
  second language
- achievements, events, the themed-crypto-sim (market/heat/pools)

# Stage97 client rollout

Stage97 keeps the Stage93 protocol-3 Core and changes only the managed client UI generation.

## Candidate identity

- Build ID: `runtime-stage97`
- Core SHA256: `c3c580b456ad5bd17144188a557d6d50ce2d3c23eee5685f7fdf28b632c1f2a1`
- UI SHA256: `fed175cb342b9bc45e612ea724ace16ca325d7b02ebcaa76b13157f2f54bab1d`
- Resource pack SHA256: `ec3c1e83d59195ba5a8fb2a90a0a41b7439f3b98f10970bfc9c359d0f7a22dae`

## Transition allowlist

Keep Stage96 accepted during the first Stage97 smoke. `GgoClientBuildPolicy` parses comma-separated values.

```text
GGO_ALLOWED_CLIENT_BUILDS=runtime-stage96,runtime-stage97
GGO_ALLOWED_CORE_SHA256=c3c580b456ad5bd17144188a557d6d50ce2d3c23eee5685f7fdf28b632c1f2a1
GGO_ALLOWED_UI_SHA256=783b0a6c572de0f98cd2e882eb3f98b2014e760062f50818110cea5300ee2852,fed175cb342b9bc45e612ea724ace16ca325d7b02ebcaa76b13157f2f54bab1d
```

Do not change or print `GGO_SERVER_KEY` as part of this rollout.

## Order

1. Publish the Stage97 Core/UI/resource-pack files and candidate manifest to the official CDN.
2. Add Stage97 to the server allowlist while retaining Stage96.
3. Restart only as required for environment changes and verify the Stage93 server reaches `Done` normally.
4. Switch the launcher candidate bootstrap from Stage96 to Stage97.
5. Smoke `INSTALL/UPDATE -> PLAY -> GGO frontend -> PLAY ONLINE` against `play.kvicloud.ru:24842`.
6. Keep Stage96 allowed until the Stage97 smoke is confirmed. Remove Stage96 from the allowlist only in a later cleanup.

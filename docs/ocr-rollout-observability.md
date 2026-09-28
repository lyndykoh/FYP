# OCR rollout and observability

This runbook covers the answer-only OCR rollout. It is deliberately scoped to
metadata. Uploaded images, OCR answer text, prompts, provider bodies, API keys,
and exception messages that may contain provider content must not enter logs,
metrics labels, traces, analytics events, or database columns.

## Controls

Normal OCR remains the default path. The rollout/telemetry switch must default
to `false` and be set only in the grading-service environment. Keep the
diagnostic switch separate:

| Control | Purpose | Default / scope |
| --- | --- | --- |
| `ai.ocr.diagnostic.enabled` | Allows the controlled exact-image diagnostic harness to call the provider and retain a bounded raw response in process memory. | `false`; temporary test environment only |
| `ocr.diagnostic.run-live=true` | Enables the opt-in JUnit live reproduction. | Absent; never set in production |
| `ocr.diagnostic.image-path` | Local path read by that JUnit reproduction. | Absent; never set in production |
| `ai.ocr.observability.enabled` | Enables a safe, correlated outcome event. Aggregate counters remain on in both states, tagged `cohort=baseline` or `cohort=enabled`. | `false` until canary approval |

The rollout flag is a server-side configuration value, not a `NEXT_PUBLIC_*`
value and not a student- or Tutor-editable request field. In environment-backed
Spring configuration, bind it as `AI_OCR_OBSERVABILITY_ENABLED` to
`ai.ocr.observability.enabled`. A flag change is a deployment/configuration
change and must be audited with its timestamp, operator, and environment.

This flag releases correlation events, not a different OCR model, prompt, or
parser. It therefore cannot by itself prove that recognition quality improved.
Use the cohort labels to compare instrumentation windows around a separately
reviewed OCR release.

## Safe telemetry contract

Emit one aggregate counter per completed OCR extraction in both cohorts. When
the flag is enabled, emit one structured event per extraction containing only:

- a generated `correlation_id`
- one bounded outcome reason, one of `answers`, `no_answers`, `uncertain`,
  `invalid_provider_response`, or `provider_unavailable`
- whether the final confidence is zero

Do not use image hashes, filenames, worksheet codes, student IDs, class IDs,
email addresses, raw URLs containing query data, provider request/response
bodies, prompts, OCR text, exception messages, JWTs, API keys, or image pixels
as fields or labels. In particular, never put an answer or correlation ID into
a high-cardinality metric label; keep the ID only in restricted trace/log
metadata so an operator can join one incident without making a searchable
content store.

`provider_unavailable` includes transport failures. Provider HTTP rejections
and malformed provider output are recorded as `invalid_provider_response`.
The five dashboard outcome series remain the stable contract above.

## Baseline and candidate comparison

Capture a baseline before enabling the flag. Use the same traffic source,
worksheet mix, image/PDF mix, provider/model, and time-of-day profile for both
periods. The primary measure is the zero-confidence rate:

```text
zero_confidence_rate = zero_confidence_attempts / eligible_ocr_attempts
rate_change = candidate_zero_confidence_rate - baseline_zero_confidence_rate
relative_change = rate_change / baseline_zero_confidence_rate
```

Exclude rejected uploads that never reached OCR, health checks, test fixtures,
and duplicate retries from both numerator and denominator. Report counts as
well as rates. Do not call a rollout better from a rate based on fewer than
100 eligible attempts per period; prefer at least 1,000 per period and a
minimum comparable observation window of 24 hours. Keep a seven-day comparison
when volume permits to cover weekday/weekend differences.

Break the result down by the five outcome reasons and rollout cohort. A
candidate is not healthy merely because zero-confidence falls if `uncertain` or
`provider_unavailable` rises. Establish the go/no-go threshold before looking
at the candidate data; the release owner should record the threshold and
decision with the dashboard snapshot. Media-type, preflight, latency, and
provider-status breakdowns require a separately approved private telemetry
extension and are not emitted by this implementation.

## Enable, monitor, and roll back

Use the deployment system’s reviewed configuration change to enable the flag
for a small Tutor/student cohort or canary instance. The conceptual sequence
is:

```text
1. Confirm the baseline dashboard and alert routes are green.
2. Set `AI_OCR_OBSERVABILITY_ENABLED=true` for the canary.
3. Restart or reload the grading service using the normal deployment command.
4. Verify the flag value from restricted operator configuration, never from a student API.
5. Watch all five outcome series and the zero-confidence rate.
6. Expand only after the pre-agreed minimum window and sample size pass.
```

Rollback is the same reviewed configuration operation with the flag set to
`false`, followed by a grading-service restart/reload and a smoke test of the
existing Tutor Upload and Student Upload flows. Rollback stops correlated events
and returns aggregate counters to the `baseline` cohort. It does not change OCR
behaviour, delete submissions, or delete OCR records.

The private collector/dashboard must be reachable only by authorized
operators. This repository currently has no configured metrics exporter or
private collector, and exposes only the health endpoint. Provision and validate
that private path before enabling the flag or claiming that monitoring is live.
Do not expose metrics endpoints, raw diagnostic responses, or rollout state
through student pages, Tutor View, public frontend bundles, or unauthenticated
actuator endpoints.

## Exact-image diagnostic

Run the controlled harness from the grading-service directory with the
existing provider settings supplied through the local environment. Do not put
the key on the command line or in a report:

```bash
set -a
source ../../.env
set +a

./mvnw -q test -DforkCount=0 \
  -Dtest=AiOcrLiveDiagnosticTest \
  -Docr.diagnostic.run-live=true \
  -Dai.ocr.diagnostic.enabled=true \
  -Docr.diagnostic.image-path="/Users/yingchun/Downloads/WhatsApp Image 2026-09-09 at 15.48.50.jpeg"
```

The harness must print only its sanitized one-line report: correlation ID,
outcome, raw-response captured/truncated flags and length, provider HTTP status,
answer-detected boolean, and numeric confidence. It may retain the raw provider
body only in bounded process memory for the duration of the run; it must never
write that body, the uploaded image, or extracted answer text to logs, test
reports, or the database. Remove the diagnostic flags after the run.

For this image, the success criterion is `answers`,
`student_answer_detected=true`, and `confidence > 0`, with the extracted value
containing only the handwritten response. A `0` result must be triaged by its
captured outcome reason. `invalid_provider_response` points to the provider
contract/parser layer; `provider_unavailable` or an HTTP rejection points to
transport/provider configuration; `uncertain` points to attribution or
recognition ambiguity. Image-quality warnings are evidence to review, not a
reason to rewrite confidence after the provider has responded.

## Limitations

The offline AI/OCR mock proves request plumbing and deterministic parsing, not
handwriting recognition. A live exact-image run needs a configured,
image-capable provider, network access, and an approved test environment.
Dashboard aggregates can show whether the rollout changes `0` confidence, but
they cannot prove semantic transcription accuracy without a separately
controlled, consented evaluation set.

# Backend service boundaries

The application uses the auth-service JWT as its cross-service identity contract. A valid token contains a stable positive `userId`, the account email as its subject, and exactly one supported `role` (`TUTOR` or `STUDENT`). Every backend independently verifies the signature and expiry with `JWT_SECRET`; no service trusts identity headers supplied by the browser.

## Ownership

- **auth-service** owns credentials, password hashing, account identity, role assignment, login, and token creation. Other services store only the auth-service `userId` required for ownership or profile linking.
- **learning-service** owns tutor classes, class memberships, canonical student profiles, and future syllabus, worksheet, mastery, mistake, alert, and report records. Tutor and Student views must use these same records.
- **grading-service** owns uploaded-submission processing, OCR extraction, and advisory marking results. It must not update learning mastery until a tutor approves results through a future learning-service workflow.
- **frontend** owns presentation and route protection only. Authorisation and ownership checks remain mandatory in each backend.

There are intentionally no database foreign keys across service-owned schemas. Cross-service references use stable IDs and are validated through authenticated service APIs. Class and student HTTP APIs, grading-to-learning approval orchestration, and their UI consumers are deferred to their dedicated post-Issue-07 feature issues.

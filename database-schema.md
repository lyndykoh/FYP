# Database schema

## Ownership

PostgreSQL is shared only as an operational server.  The application uses
three separate schemas—`auth`, `learning`, and `grading`—and each Spring Boot
service runs its own Flyway migrations.  The schema names and connection
configuration are defined in [compose.yaml](../compose.yaml).  The migration
files are the executable source of truth; this document is a navigational map,
not a second schema definition.

```mermaid
erDiagram
  AUTH_USERS {
    bigint id PK
    varchar email UK
    varchar password
    varchar fullname
    varchar role
  }
  TUTOR_CLASSES {
    bigint id PK
    bigint tutor_id
    varchar class_name
    varchar subject
    varchar class_level
  }
  STUDENT_PROFILES {
    bigint id PK
    bigint tutor_id
    bigint login_user_id UK
    varchar full_name
  }
  CLASS_MEMBERSHIPS {
    bigint id PK
    bigint class_id FK
    bigint student_profile_id FK
    bigint tutor_id
  }
  SYLLABUS_TOPICS {
    bigint id PK
    bigint parent_id FK
    varchar code UK
    varchar node_type
  }
  QUESTIONS {
    bigint id PK
    bigint syllabus_topic_id FK
    varchar code UK
    varchar question_type
    varchar prompt
  }
  WORKSHEETS {
    bigint id PK
    bigint tutor_id
    varchar title
    varchar audience_type
    varchar status
  }
  WORKSHEET_QUESTIONS {
    bigint id PK
    bigint worksheet_id FK
    bigint question_id FK
    int position
  }
  WORKSHEET_ASSIGNMENTS {
    bigint id PK
    bigint worksheet_id FK
    bigint class_id
    bigint student_profile_id
  }
  SUBMISSION_DOCUMENTS {
    bigint id PK
    bigint owner_user_id
    bigint worksheet_id
    bigint student_id
    varchar status
  }
  SUBMISSION_PAGES {
    bigint id PK
    bigint submission_document_id FK
    int page_number
    varchar storage_key UK
  }
  OCR_EXTRACTIONS {
    bigint id PK
    bigint submission_page_id FK
    bigint worksheet_question_id
    varchar status
  }
  SUBMISSIONS {
    bigint id PK
    bigint submission_document_id FK
    bigint worksheet_id
    bigint student_id
    bigint worksheet_question_id
    varchar review_status
  }
  ANSWER_REVIEWS {
    bigint id PK
    bigint submission_id FK
    bigint reviewer_user_id
    varchar action
  }

  TUTOR_CLASSES ||--o{ CLASS_MEMBERSHIPS : contains
  STUDENT_PROFILES ||--o{ CLASS_MEMBERSHIPS : joins
  SYLLABUS_TOPICS ||--o{ SYLLABUS_TOPICS : parent
  SYLLABUS_TOPICS ||--o{ QUESTIONS : classifies
  WORKSHEETS ||--o{ WORKSHEET_QUESTIONS : contains
  QUESTIONS ||--o{ WORKSHEET_QUESTIONS : snapshots
  WORKSHEETS ||--o{ WORKSHEET_ASSIGNMENTS : assigns
  SUBMISSION_DOCUMENTS ||--o{ SUBMISSION_PAGES : stores
  SUBMISSION_PAGES ||--o| OCR_EXTRACTIONS : extracts
  SUBMISSION_DOCUMENTS ||--o{ SUBMISSIONS : answers
  SUBMISSIONS ||--o{ ANSWER_REVIEWS : audits
```

`AUTH_USERS` is intentionally shown separately: `learning` and `grading`
retain stable auth IDs but do not create cross-schema foreign keys.  This keeps
service ownership explicit while authentication remains centralised.

## Migration source of truth

| Schema | Initial and representative migrations | Verification |
| --- | --- | --- |
| `auth` | [V1 users](../backend/auth-service/src/main/resources/db/migration/V1__create_users.sql) | [migration integration test](../backend/auth-service/src/test/java/com/fttranscendence/authservice/database/MigrationIntegrationTest.java) |
| `learning` | [classes](../backend/learning-service/src/main/resources/db/migration/V2__create_classes.sql), [students](../backend/learning-service/src/main/resources/db/migration/V3__create_students.sql), [taxonomy](../backend/learning-service/src/main/resources/db/migration/V4__create_syllabus.sql), [questions](../backend/learning-service/src/main/resources/db/migration/V6__create_questions.sql), [worksheets](../backend/learning-service/src/main/resources/db/migration/V7__create_worksheets.sql) | [migration integration test](../backend/learning-service/src/test/java/com/fttranscendence/learning/database/MigrationIntegrationTest.java) |
| `grading` | [submission documents](../backend/grading-service/src/main/resources/db/migration/V2__create_submission_documents.sql), [answers/reviews](../backend/grading-service/src/main/resources/db/migration/V3__create_answers_and_reviews.sql), [OCR](../backend/grading-service/src/main/resources/db/migration/V5__create_ocr_extractions.sql) | [migration integration test](../backend/grading-service/src/test/java/com/fttranscendence/grading/database/MigrationIntegrationTest.java) |

## Integrity rules worth preserving

- A class membership is unique per student and class and is constrained to the
  same Tutor owner.
- Question records reference an existing syllabus topic or subtopic; worksheet
  questions preserve a deterministic position.
- An assignment is either class-targeted or student-targeted, never both.
- Each persisted page has a bounded, accepted media type and a SHA-256 checksum
  unique within its document.
- Student-answer reviews are append-only audit records associated with a
  persisted answer row.

For the full service-level ownership explanation, see
[service boundaries](architecture/service-boundaries.md).

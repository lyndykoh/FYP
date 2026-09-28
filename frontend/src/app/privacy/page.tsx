import type { Metadata } from "next";
import LegalDocument, {
  type LegalSection,
} from "@/components/legal/LegalDocument";

export const metadata: Metadata = { title: "Privacy Policy | Lumina Academy" };

const sections: LegalSection[] = [
  {
    heading: "Information in this application",
    paragraphs: [
      "Lumina Academy stores account details such as an email address, a password hash, and a tutor or student role. It also stores learning records needed to operate the workspace, including classes, worksheets, marks, feedback, mastery and diagnostic findings.",
      "When a student submits work, uploaded PDF or image pages and the resulting OCR text may be stored with the submission. Do not upload information that the operator has not authorised you to submit.",
    ],
  },
  {
    heading: "How information is used",
    paragraphs: [
      "The application uses this information to authenticate users, assign and review work, extract text from submitted pages, generate marking assistance, record tutor-approved results, and show learning progress and subject insights.",
      "The browser stores the current authentication token in local storage so the application can call its APIs. Sign out clears that token from the browser.",
    ],
  },
  {
    heading: "OCR and AI processing",
    paragraphs: [
      "The deployment may be configured to send submission content or marking context to an external OCR or AI provider. The provider, regions, retention, and contractual terms depend on the operator configuration; this application does not claim that every deployment uses the same provider.",
      "AI output is advisory. A tutor approval is the final authority for a marked result and the diagnostic evidence shown to students.",
    ],
  },
  {
    heading: "Storage, access and sharing",
    paragraphs: [
      "Records and uploaded documents are stored in the deployment database and upload storage. Tutors can access records for their classes, and students can access their own assigned work and results according to the application permissions.",
      "The repository contains no marketing or analytics tracker. The operator remains responsible for infrastructure access controls, backups, provider agreements, and any other sharing required by its deployment.",
    ],
  },
  {
    heading: "Your choices and data requests",
    paragraphs: [
      "Do not use the service if you do not agree to the operator processing information needed for the workflows above. To request access, correction, export, deletion, or answers about retention, contact the organisation operating your deployment through its published support channel.",
      "This build does not configure a universal contact address, jurisdiction, retention duration, or automated deletion schedule. The operator must publish those details and handle requests under the laws that apply to its service.",
    ],
  },
];

export default function PrivacyPage() {
  return (
    <LegalDocument
      title="Privacy Policy"
      summary="How the current Lumina Academy build handles accounts, learning records, submitted work, OCR, and marking assistance."
      sections={sections}
    />
  );
}

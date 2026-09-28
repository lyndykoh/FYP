import type { Metadata } from "next";
import LegalDocument, {
  type LegalSection,
} from "@/components/legal/LegalDocument";

export const metadata: Metadata = { title: "Terms of Use | Lumina Academy" };

const sections: LegalSection[] = [
  {
    heading: "Using the workspace",
    paragraphs: [
      "Lumina Academy is a tutor and student workspace for assigning learning activities, submitting written work, recording tutor-approved marks, and reviewing progress. Use the account issued or approved by the deployment operator and keep its credentials private.",
      "You are responsible for the accuracy and appropriateness of material you submit and for using the service only for legitimate learning and administration.",
    ],
  },
  {
    heading: "Uploads and learning records",
    paragraphs: [
      "Uploaded PDFs and images may be retained in the deployment and may become part of a student submission, OCR extraction, marking review, or learning record. Check with the operator before uploading personal or confidential material.",
      "Do not attempt to access another student’s records, bypass role restrictions, interfere with the service, or upload malicious, unlawful, or infringing content.",
    ],
  },
  {
    heading: "OCR and AI assistance",
    paragraphs: [
      "OCR and AI features can be incomplete or incorrect. They are assistance for a tutor workflow, not a guarantee of accuracy or a substitute for professional judgement.",
      "A tutor must review and approve a result before it is treated as final. Students should raise questions about a result with their tutor or the deployment operator.",
    ],
  },
  {
    heading: "Availability and changes",
    paragraphs: [
      "The operator may maintain, change, suspend, or retire a deployment and its configured providers. Features can be unavailable during maintenance or when a dependent service is unavailable.",
      "The operator is responsible for publishing any service-specific fees, support arrangements, retention rules, governing terms, and contact details. This document does not invent those deployment-specific terms.",
    ],
  },
  {
    heading: "Questions and acceptable use",
    paragraphs: [
      "Questions, account issues, deletion requests, and reports of misuse should be sent through the support channel published by the organisation operating the deployment.",
      "By continuing to use the service, you agree to follow these rules and any additional policies communicated by that operator. If you do not agree, stop using the deployment and contact its operator.",
    ],
  },
];

export default function TermsPage() {
  return (
    <LegalDocument
      title="Terms of Use"
      summary="Practical rules for using the current Lumina Academy tutor and student workspace."
      sections={sections}
    />
  );
}

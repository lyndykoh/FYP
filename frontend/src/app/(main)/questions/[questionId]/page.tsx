"use client";

import { useParams } from "next/navigation";

import QuestionDetail from "@/components/questions/QuestionDetail";

export default function QuestionDetailPage() {
  const params = useParams<{ questionId: string }>();
  return <QuestionDetail questionId={Number(params.questionId)} />;
}

"use client";

import { useParams } from "next/navigation";

import ClassDetail from "@/components/classes/ClassDetail";

export default function ClassDetailPage() {
  const params = useParams<{ classId: string }>();
  return <ClassDetail classId={Number(params.classId)} />;
}

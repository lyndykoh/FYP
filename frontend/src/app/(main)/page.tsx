"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import Skeleton from "@mui/material/Skeleton";
import Typography from "@mui/material/Typography";
import { useRouter } from "next/navigation";

import { getBrowserSession, getRoleHome } from "@/lib/auth";

/** Root is a role-aware hand-off; each role loads its own dashboard data. */
export default function MainHomePage() {
  const router = useRouter();

  React.useEffect(() => {
    const timer = window.setTimeout(() => {
      const session = getBrowserSession();
      router.replace(session ? getRoleHome(session.role) : "/login");
    }, 0);
    return () => window.clearTimeout(timer);
  }, [router]);

  return (
    <Box
      role="status"
      aria-live="polite"
      sx={{ display: "grid", gap: 1.25, maxWidth: 620 }}
    >
      <Typography
        sx={{
          color: "#A09488",
          fontSize: 10.5,
          fontWeight: 700,
          letterSpacing: ".13em",
        }}
      >
        OPENING YOUR WORKSPACE
      </Typography>
      <Skeleton height={54} width="56%" sx={{ bgcolor: "#F0EAE0" }} />
      <Skeleton height={130} sx={{ bgcolor: "#F0EAE0" }} />
    </Box>
  );
}

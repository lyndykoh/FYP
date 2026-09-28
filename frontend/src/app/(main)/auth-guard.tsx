"use client";

import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { usePathname, useRouter } from "next/navigation";

import { getBrowserSession, getRoleHome, isPathAllowed } from "@/lib/auth";

type GuardState = "loading" | "authorized" | "unauthenticated" | "unauthorized";

interface GuardResult {
  pathname: string | null;
  state: GuardState;
  roleHome: string;
}

function loadingResult(): GuardResult {
  return {
    pathname: null,
    state: "loading",
    roleHome: "/",
  };
}

function stateForPath(
  result: GuardResult,
  pathname: string | null,
): GuardState {
  return result.pathname === pathname ? result.state : "loading";
}

export default function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const [result, setResult] = useState<GuardResult>(loadingResult);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const session = getBrowserSession();
      if (!session) {
        setResult({ pathname, state: "unauthenticated", roleHome: "/" });
        router.replace("/login");
        return;
      }

      const home = getRoleHome(session.role);
      if (!isPathAllowed(session.role, pathname)) {
        setResult({ pathname, state: "unauthorized", roleHome: home });
        router.replace(home);
        return;
      }

      setResult({ pathname, state: "authorized", roleHome: home });
    }, 0);

    return () => window.clearTimeout(timer);
  }, [pathname, router]);

  const state = stateForPath(result, pathname);

  if (state === "authorized") return children;

  if (state === "loading") {
    return (
      <Stack
        role="status"
        aria-live="polite"
        spacing={2}
        sx={{
          minHeight: "50vh",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <CircularProgress size={28} />
        <Typography>Checking your access…</Typography>
      </Stack>
    );
  }

  if (state === "unauthenticated") {
    return (
      <Box sx={{ p: 3 }}>
        <Alert
          severity="warning"
          action={
            <Button onClick={() => router.replace("/login")}>Sign in</Button>
          }
        >
          Your session is missing or has expired. Please sign in to continue.
        </Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      <Alert
        severity="error"
        action={
          <Button onClick={() => router.replace(result.roleHome)}>
            Return home
          </Button>
        }
      >
        You do not have permission to view this page.
      </Alert>
    </Box>
  );
}

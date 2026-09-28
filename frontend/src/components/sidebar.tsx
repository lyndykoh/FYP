"use client";

import * as React from "react";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import List from "@mui/material/List";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { getBrowserSession, type AuthRole } from "@/lib/auth";
import strings from "@/locales/en.json";

import {
  getNavigationItems,
  getWorkspaceLabel,
  isNavigationItemSelected,
} from "./navigation-config";
import SidebarPageItem from "./sidebar-page-item";

export interface SidebarProps {
  disableCollapsibleSidebar?: boolean;
  container?: Element;
  showDesktop?: boolean;
  showRail?: boolean;
}

function getInitials(email?: string): string {
  return email ? email.slice(0, 2).toUpperCase() : "LA";
}

export default function Sidebar({
  disableCollapsibleSidebar: _disableCollapsibleSidebar,
  container: _container,
  showDesktop = true,
  showRail = true,
}: SidebarProps) {
  void _disableCollapsibleSidebar;
  void _container;
  const pathname = usePathname();
  const [role, setRole] = React.useState<AuthRole | null>(null);
  const [email, setEmail] = React.useState<string>();

  React.useEffect(() => {
    const timer = window.setTimeout(() => {
      const session = getBrowserSession();
      setRole(session?.role ?? null);
      setEmail(session?.email);
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  const items = role ? getNavigationItems(role) : [];

  return (
    <>
      {showDesktop ? (
        <Box
          component="aside"
          aria-label="Workspace navigation"
          sx={{
            display: "none",
            "@media (min-width:880px)": { display: "flex" },
            position: "sticky",
            top: 0,
            flex: "0 0 246px",
            width: 246,
            height: "100vh",
            flexDirection: "column",
            bgcolor: "#FBF9F5",
            borderRight: "1px solid #EDE6DB",
            zIndex: 20,
          }}
        >
          <Box sx={{ px: 2.75, pt: 2.75, pb: 2.25 }}>
            <Typography
              variant="h6"
              sx={{ color: "#2A2622", fontSize: "1.1875rem", fontWeight: 600 }}
            >
              {strings.common.sidebarTitle}
            </Typography>
            {role ? (
              <Typography
                variant="caption"
                sx={{ display: "block", mt: 0.5, color: "#A09488" }}
              >
                {getWorkspaceLabel(role)}
              </Typography>
            ) : null}
          </Box>
          <Box
            component="nav"
            aria-label="Desktop navigation"
            sx={{ flex: 1, overflowY: "auto", px: 1.5 }}
          >
            <List>
              {items.map((item) => {
                const Icon = item.icon;
                const selected = isNavigationItemSelected(item, pathname);

                return (
                  <SidebarPageItem
                    key={item.id}
                    {...item}
                    icon={<Icon fontSize="small" />}
                    selected={selected}
                  />
                );
              })}
            </List>
          </Box>
          <Divider />
          <Stack
            direction="row"
            spacing={1.25}
            sx={{ alignItems: "center", px: 2.75, py: 2.25 }}
          >
            <Avatar
              sx={{
                width: 32,
                height: 32,
                bgcolor: "#D8B384",
                color: "#3A332C",
                fontSize: "0.75rem",
                fontWeight: 700,
              }}
            >
              {getInitials(email)}
            </Avatar>
            <Box sx={{ minWidth: 0 }}>
              <Typography
                variant="body2"
                noWrap
                sx={{ color: "#4A443D", fontWeight: 500 }}
              >
                {email ?? "Lumina account"}
              </Typography>
              {role ? (
                <Typography variant="caption" sx={{ color: "#8B837A" }}>
                  {getWorkspaceLabel(role)}
                </Typography>
              ) : null}
            </Box>
          </Stack>
        </Box>
      ) : null}

      {showRail ? (
        <Box
          component="nav"
          aria-label="Navigation"
          sx={{
            display: "block",
            "@media (min-width:880px)": { display: "none" },
            bgcolor: "#FBF9F5",
            borderBottom: "1px solid #EDE6DB",
            overflowX: "auto",
            scrollbarWidth: "thin",
            px: { xs: 2, sm: 3 },
            py: 1.25,
          }}
        >
          <Stack direction="row" spacing={0.75} sx={{ width: "max-content" }}>
            {items.map((item) => {
              const selected = isNavigationItemSelected(item, pathname);

              return (
                <Button
                  key={item.id}
                  component={Link}
                  href={item.href}
                  size="small"
                  aria-current={selected ? "page" : undefined}
                  sx={{
                    minHeight: 34,
                    borderRadius: 20,
                    px: 1.5,
                    whiteSpace: "nowrap",
                    color: selected ? "#9E3A24" : "#5A544C",
                    bgcolor: selected ? "#F4E4DE" : "transparent",
                    border: selected
                      ? "1px solid #E0B9AC"
                      : "1px solid transparent",
                    boxShadow: "none",
                    "&:hover": {
                      bgcolor: selected ? "#F4E4DE" : "#F4EFE6",
                    },
                  }}
                >
                  {item.title}
                </Button>
              );
            })}
          </Stack>
        </Box>
      ) : null}
    </>
  );
}

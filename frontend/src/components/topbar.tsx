"use client";

import * as React from "react";
import LogoutOutlinedIcon from "@mui/icons-material/LogoutOutlined";
import PersonOutlineIcon from "@mui/icons-material/PersonOutlineOutlined";
import MuiAppBar from "@mui/material/AppBar";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import IconButton from "@mui/material/IconButton";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import { styled } from "@mui/material/styles";
import Link from "next/link";
import { useRouter } from "next/navigation";

import {
  clearAuthSession,
  getBrowserSession,
  getRoleHome,
  type AuthSession,
} from "@/lib/auth";
import strings from "@/locales/en.json";

const AppBar = styled(MuiAppBar)({
  position: "sticky",
  top: 0,
  zIndex: 30,
  border: 0,
  borderBottom: "1px solid #EDE6DB",
  backgroundColor: "rgba(250,247,242,.92)",
  backdropFilter: "blur(10px)",
  boxShadow: "none",
});

function getInitials(email?: string): string {
  return email ? email.slice(0, 2).toUpperCase() : "LA";
}

export default function Topbar() {
  const router = useRouter();
  const [session, setSession] = React.useState<AuthSession | null>(null);
  const [accountAnchor, setAccountAnchor] = React.useState<HTMLElement | null>(
    null,
  );

  React.useEffect(() => {
    const timer = window.setTimeout(() => setSession(getBrowserSession()), 0);
    return () => window.clearTimeout(timer);
  }, []);

  const closeAccountMenu = () => {
    setAccountAnchor(null);
  };

  const openProfile = () => {
    closeAccountMenu();
    router.push("/profile");
  };

  const handleLogout = () => {
    closeAccountMenu();
    clearAuthSession();
    router.replace("/login");
  };

  return (
    <AppBar data-testid="topbar">
      <Toolbar sx={{ minHeight: 58, px: { xs: 2, sm: 3.75 }, gap: 2 }}>
        <Box
          component="a"
          href="#main-content"
          sx={{
            position: "absolute",
            left: 12,
            top: -48,
            px: 1.5,
            py: 1,
            borderRadius: 1,
            bgcolor: "#FFFDFA",
            color: "#2A2622",
            "&:focus": { top: 8, zIndex: 1 },
          }}
        >
          Skip to content
        </Box>
        <Link
          href={session ? getRoleHome(session.role) : "/"}
          style={{ color: "inherit", textDecoration: "none" }}
        >
          <Typography
            variant="h6"
            sx={{
              color: "#2A2622",
              fontSize: "1rem",
              fontWeight: 500,
              whiteSpace: "nowrap",
            }}
          >
            {strings.common.topbarTitle}
          </Typography>
        </Link>
        <Box sx={{ flex: 1 }} />
        <Box
          component="nav"
          aria-label="Legal links"
          sx={{ display: { xs: "none", sm: "flex" }, gap: 1.5 }}
        >
          <Link href="/privacy" style={{ color: "#5F574E", fontSize: 12 }}>
            Privacy
          </Link>
          <Link href="/terms" style={{ color: "#5F574E", fontSize: 12 }}>
            Terms
          </Link>
        </Box>
        <IconButton
          aria-label="Open account menu"
          aria-controls={accountAnchor ? "account-menu" : undefined}
          aria-expanded={accountAnchor ? "true" : undefined}
          aria-haspopup="menu"
          onClick={(event) => setAccountAnchor(event.currentTarget)}
          sx={{
            width: 34,
            height: 34,
            p: 0,
            overflow: "hidden",
            borderRadius: "50%",
            border: "none",
            bgcolor: "transparent",
            "&:hover": { bgcolor: "transparent" },
          }}
        >
          <Avatar
            sx={{
              width: 34,
              height: 34,
              bgcolor: "#C6D0C4",
              color: "#3A332C",
              fontSize: "0.75rem",
              fontWeight: 700,
            }}
          >
            {getInitials(session?.email)}
          </Avatar>
        </IconButton>
        <Menu
          id="account-menu"
          anchorEl={accountAnchor}
          open={Boolean(accountAnchor)}
          onClose={closeAccountMenu}
          slotProps={{ list: { "aria-label": "Account options" } }}
          anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
          transformOrigin={{ horizontal: "right", vertical: "top" }}
        >
          {session ? (
            <Box sx={{ px: 2, py: 1, maxWidth: 280 }}>
              <Typography variant="body2" noWrap>
                {session.email}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {session.role === "TUTOR" ? "Tutor" : "Student"}
              </Typography>
            </Box>
          ) : null}
          <Divider />
          {session?.role !== "TUTOR" ? (
            <MenuItem onClick={openProfile}>
              <PersonOutlineIcon fontSize="small" sx={{ mr: 1.25 }} />
              Profile
            </MenuItem>
          ) : null}
          <MenuItem onClick={handleLogout}>
            <LogoutOutlinedIcon fontSize="small" sx={{ mr: 1.25 }} />
            Logout
          </MenuItem>
        </Menu>
      </Toolbar>
    </AppBar>
  );
}

"use client";

import * as React from "react";
import Box from "@mui/material/Box";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import Link from "next/link";

export interface SidebarPageItemProps {
  id: string;
  title: string;
  icon?: React.ReactNode;
  href: string;
  action?: React.ReactNode;
  selected?: boolean;
  disabled?: boolean;
}

function isExternalHref(href: string): boolean {
  return href.startsWith("http://") || href.startsWith("https://");
}

export default function SidebarPageItem({
  id,
  title,
  icon,
  href,
  action,
  selected = false,
  disabled = false,
}: SidebarPageItemProps) {
  const hasExternalHref = isExternalHref(href);

  return (
    <ListItem disablePadding data-navigation-id={id}>
      <ListItemButton
        {...(hasExternalHref
          ? {
              component: "a",
              href,
              target: "_blank",
              rel: "noopener noreferrer",
            }
          : { component: Link, href })}
        selected={selected}
        disabled={disabled}
        aria-current={selected ? "page" : undefined}
        sx={{
          minHeight: 40,
          px: 1.5,
          py: 0.75,
          gap: 1.25,
          borderRadius: 2,
          color: "#5A544C",
          "&:hover": { bgcolor: "#FBF7F1" },
          "&.Mui-selected, &.Mui-selected:hover": {
            bgcolor: "#F4E4DE",
            color: "#9E3A24",
          },
          "&.Mui-selected .MuiListItemIcon-root": { color: "#9E3A24" },
        }}
      >
        {icon ? (
          <ListItemIcon sx={{ minWidth: 0, color: "inherit" }}>
            {icon}
          </ListItemIcon>
        ) : null}
        <ListItemText primary={title} sx={{ my: 0, minWidth: 0 }} />
        {action ? <Box sx={{ ml: "auto" }}>{action}</Box> : null}
      </ListItemButton>
    </ListItem>
  );
}

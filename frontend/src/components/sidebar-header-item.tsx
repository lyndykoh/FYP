"use client";

import * as React from "react";
import ListSubheader from "@mui/material/ListSubheader";
import type {} from "@mui/material/themeCssVarsAugmentation";
import SidebarContext from "@/context/sidebar-context";
import { DRAWER_WIDTH } from "@/utils/constants";
import getDrawerSxTransitionMixin from "@/utils/mixins";

export interface SidebarHeaderItemProps {
  children?: React.ReactNode;
}

export default function SidebarHeaderItem({
  children,
}: SidebarHeaderItemProps) {
  const sidebarContext = React.useContext(SidebarContext);
  if (!sidebarContext) {
    throw new Error("Sidebar context was used without a provider.");
  }
  const {
    mini = false,
    isFullyExpanded = true,
    hasDrawerTransitions,
  } = sidebarContext;

  return (
    <ListSubheader
      sx={[
        {
          fontSize: 12,
          fontWeight: "600",
          height: mini ? 0 : 36,
          px: 1.5,
          py: 0,
          minWidth: DRAWER_WIDTH,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
          zIndex: 2,
        },
        hasDrawerTransitions
          ? getDrawerSxTransitionMixin(isFullyExpanded, "height")
          : null,
      ]}
    >
      {children}
    </ListSubheader>
  );
}

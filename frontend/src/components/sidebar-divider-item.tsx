"use client";

import * as React from "react";
import Divider from "@mui/material/Divider";
import type {} from "@mui/material/themeCssVarsAugmentation";
import SidebarContext from "@/context/sidebar-context";
import getDrawerSxTransitionMixin from "@/utils/mixins";

export default function SidebarDividerItem() {
  const sidebarContext = React.useContext(SidebarContext);
  if (!sidebarContext) {
    throw new Error("Sidebar context was used without a provider.");
  }
  const { isFullyExpanded = true, hasDrawerTransitions } = sidebarContext;

  return (
    <li>
      <Divider
        sx={[
          {
            borderBottomWidth: 1,
            my: 1,
            mx: -0.5,
          },
          hasDrawerTransitions
            ? getDrawerSxTransitionMixin(isFullyExpanded, "margin")
            : null,
        ]}
      />
    </li>
  );
}

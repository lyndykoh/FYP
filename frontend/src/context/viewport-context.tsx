import * as React from "react";
import type { Theme } from "@mui/material/styles";

export interface ViewportContextValue {
  theme: Theme;
  isOverSmViewport: boolean;
  isOverMdViewport: boolean;
  isNavigationExpanded: boolean;
  setIsNavigationExpanded: (newExpanded: boolean) => void;
  handleToggleHeaderMenu: (isExpanded: boolean) => void;
}

const ViewportContext = React.createContext<ViewportContextValue | null>(null);

export default ViewportContext;

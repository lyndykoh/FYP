import * as React from "react";

export interface SidebarContextValue {
  expandedItemIds: string[];
  handleSetSidebarExpanded: (newExpanded: boolean) => () => void;
  handlePageItemClick: (id: string, hasNestedNavigation: boolean) => void;
  mini: boolean;
  isFullyExpanded: boolean;
  setIsFullyExpanded: React.Dispatch<React.SetStateAction<boolean>>;
  isFullyCollapsed: boolean;
  setIsFullyCollapsed: React.Dispatch<React.SetStateAction<boolean>>;
  hasDrawerTransitions: boolean;
}

const SidebarContext = React.createContext<SidebarContextValue | null>(null);

export default SidebarContext;

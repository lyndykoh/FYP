"use client";
import React from "react";
import useMediaQuery from "@mui/material/useMediaQuery";
import ViewportContext from "@/context/viewport-context";
import SidebarContext, {
  type SidebarContextValue,
} from "@/context/sidebar-context";

interface SidebarProviderProps {
  children?: React.ReactNode;
}

const SidebarProvider = ({ children }: SidebarProviderProps) => {
  const viewportContext = React.useContext(ViewportContext);

  if (!viewportContext) {
    throw new Error("Viewport context was used without a provider.");
  }

  const {
    theme,
    isOverSmViewport,
    isOverMdViewport,
    isNavigationExpanded,
    setIsNavigationExpanded,
  } = viewportContext;
  const disableCollapsibleSidebar = false;
  const [expandedItemIds, setExpandedItemIds] = React.useState<string[]>([]);
  const prefersReducedMotion = useMediaQuery(
    "(prefers-reduced-motion: reduce)",
  );
  const shouldReduceDrawerMotion =
    theme.motion.reducedMotion === "always" ||
    (theme.motion.reducedMotion === "system" && prefersReducedMotion);
  const drawerEnteringDuration = shouldReduceDrawerMotion
    ? 0
    : theme.transitions.duration.enteringScreen;
  const drawerLeavingDuration = shouldReduceDrawerMotion
    ? 0
    : theme.transitions.duration.leavingScreen;
  const [isFullyExpanded, setIsFullyExpanded] =
    React.useState(isNavigationExpanded);
  const [isFullyCollapsed, setIsFullyCollapsed] =
    React.useState(!isNavigationExpanded);

  React.useEffect(() => {
    const drawerWidthTransitionTimeout = setTimeout(
      () => setIsFullyExpanded(isNavigationExpanded),
      isNavigationExpanded ? drawerEnteringDuration : 0,
    );
    return () => clearTimeout(drawerWidthTransitionTimeout);
  }, [drawerEnteringDuration, isNavigationExpanded]);

  React.useEffect(() => {
    const drawerWidthTransitionTimeout = setTimeout(
      () => setIsFullyCollapsed(!isNavigationExpanded),
      isNavigationExpanded ? 0 : drawerLeavingDuration,
    );
    return () => clearTimeout(drawerWidthTransitionTimeout);
  }, [drawerLeavingDuration, isNavigationExpanded]);

  const mini = !disableCollapsibleSidebar && !isNavigationExpanded;

  const handleSetSidebarExpanded = React.useCallback(
    (newExpanded: boolean) => () => {
      setIsNavigationExpanded(newExpanded);
    },
    [setIsNavigationExpanded],
  );

  const handlePageItemClick = React.useCallback(
    (itemId: string, hasNestedNavigation: boolean) => {
      if (hasNestedNavigation && !mini) {
        setExpandedItemIds((previousValue) =>
          previousValue.includes(itemId)
            ? previousValue.filter(
                (previousValueItemId) => previousValueItemId !== itemId,
              )
            : [...previousValue, itemId],
        );
      } else if (!isOverSmViewport && !hasNestedNavigation) {
        setIsNavigationExpanded(false);
      }
    },
    [mini, setIsNavigationExpanded, isOverSmViewport],
  );

  const hasDrawerTransitions =
    isOverSmViewport && (!disableCollapsibleSidebar || isOverMdViewport);

  const sidebarContextValue = React.useMemo<SidebarContextValue>(
    () => ({
      expandedItemIds,
      handleSetSidebarExpanded,
      handlePageItemClick,
      mini,
      isFullyExpanded,
      setIsFullyExpanded,
      isFullyCollapsed,
      setIsFullyCollapsed,
      hasDrawerTransitions,
    }),
    [
      expandedItemIds,
      handleSetSidebarExpanded,
      handlePageItemClick,
      mini,
      isFullyExpanded,
      isFullyCollapsed,
      hasDrawerTransitions,
    ],
  );

  return (
    <SidebarContext.Provider value={sidebarContextValue}>
      {children}
    </SidebarContext.Provider>
  );
};

export default SidebarProvider;

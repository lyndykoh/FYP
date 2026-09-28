"use client";
import React from "react";
import { useTheme } from "@mui/material/styles";
import useMediaQuery from "@mui/material/useMediaQuery";
import ViewportContext, {
  type ViewportContextValue,
} from "@/context/viewport-context";

interface ViewportProviderProps {
  children?: React.ReactNode;
}

const ViewportProvider = ({ children }: ViewportProviderProps) => {
  const theme = useTheme();

  const [isDesktopNavigationExpanded, setIsDesktopNavigationExpanded] =
    React.useState(true);
  const [isMobileNavigationExpanded, setIsMobileNavigationExpanded] =
    React.useState(false);

  const isOverSmViewport = useMediaQuery(theme.breakpoints.up("sm"));
  const isOverMdViewport = useMediaQuery(theme.breakpoints.up("md"));

  const isNavigationExpanded = isOverMdViewport
    ? isDesktopNavigationExpanded
    : isMobileNavigationExpanded;

  const setIsNavigationExpanded = React.useCallback(
    (newExpanded: boolean) => {
      if (isOverMdViewport) {
        setIsDesktopNavigationExpanded(newExpanded);
      } else {
        setIsMobileNavigationExpanded(newExpanded);
      }
    },
    [
      isOverMdViewport,
      setIsDesktopNavigationExpanded,
      setIsMobileNavigationExpanded,
    ],
  );

  const handleToggleHeaderMenu = React.useCallback(
    (isExpanded: boolean) => {
      setIsNavigationExpanded(isExpanded);
    },
    [setIsNavigationExpanded],
  );

  const viewportContextValue = React.useMemo<ViewportContextValue>(
    () => ({
      theme,
      isOverSmViewport,
      isOverMdViewport,
      isNavigationExpanded,
      setIsNavigationExpanded,
      handleToggleHeaderMenu,
    }),
    [
      theme,
      isOverSmViewport,
      isOverMdViewport,
      isNavigationExpanded,
      setIsNavigationExpanded,
      handleToggleHeaderMenu,
    ],
  );

  return (
    <ViewportContext.Provider value={viewportContextValue}>
      {children}
    </ViewportContext.Provider>
  );
};

export default ViewportProvider;

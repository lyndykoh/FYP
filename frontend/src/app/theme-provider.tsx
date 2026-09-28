"use client";

import * as React from "react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { inputsCustomizations } from "../customizations/inputs";
import { dataDisplayCustomizations } from "../customizations/data-display";
import { feedbackCustomizations } from "../customizations/feedback";
import { navigationCustomizations } from "../customizations/navigation";
import { surfacesCustomizations } from "../customizations/surfaces";
import {
  colorSchemes,
  shape,
  shadows,
  typography,
} from "../theme/theme-primitives";

const theme = createTheme({
  cssVariables: {
    colorSchemeSelector: "data-mui-color-scheme",
    cssVarPrefix: "lumina",
  },
  colorSchemes,
  typography,
  shadows,
  shape,
  components: {
    ...inputsCustomizations,
    ...dataDisplayCustomizations,
    ...feedbackCustomizations,
    ...navigationCustomizations,
    ...surfacesCustomizations,
  },
});

export default function AppTheme({ children }: { children: React.ReactNode }) {
  return <ThemeProvider theme={theme}>{children}</ThemeProvider>;
}

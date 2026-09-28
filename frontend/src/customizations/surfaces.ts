import { Components, Theme } from "@mui/material/styles";
import { gray } from "../theme/theme-primitives";

export const surfacesCustomizations: Components<Theme> = {
  MuiAccordion: {
    defaultProps: { elevation: 0, disableGutters: true },
    styleOverrides: {
      root: {
        overflow: "hidden",
        backgroundColor: gray[50],
        border: "1px solid #EBE4D9",
        "&::before": { display: "none" },
        "&:not(:last-of-type)": { borderBottom: "none" },
        "&:first-of-type": {
          borderTopLeftRadius: 14,
          borderTopRightRadius: 14,
        },
        "&:last-of-type": {
          borderBottomLeftRadius: 14,
          borderBottomRightRadius: 14,
        },
      },
    },
  },
  MuiAccordionSummary: {
    styleOverrides: {
      root: {
        minHeight: 48,
        padding: "0 16px",
        "&:hover": { backgroundColor: "#FBF7F1" },
      },
    },
  },
  MuiAccordionDetails: { styleOverrides: { root: { padding: "0 16px 18px" } } },
  MuiPaper: {
    defaultProps: { elevation: 0 },
    styleOverrides: { root: { backgroundImage: "none" } },
  },
  MuiCard: {
    styleOverrides: {
      root: {
        padding: 20,
        gap: 16,
        backgroundColor: gray[50],
        border: "1px solid #EBE4D9",
        borderRadius: 14,
        boxShadow: "none",
        transition: "border-color .18s ease, transform .18s ease",
        "&:hover": { borderColor: gray[400] },
      },
    },
  },
  MuiCardContent: {
    styleOverrides: {
      root: { padding: 0, "&:last-child": { paddingBottom: 0 } },
    },
  },
  MuiCardHeader: { styleOverrides: { root: { padding: 0 } } },
  MuiCardActions: { styleOverrides: { root: { padding: 0 } } },
};

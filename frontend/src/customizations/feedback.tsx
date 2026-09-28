import { Components, Theme } from "@mui/material/styles";
import { orange, gray } from "../theme/theme-primitives";

export const feedbackCustomizations: Components<Theme> = {
  MuiAlert: {
    styleOverrides: {
      root: {
        borderRadius: 12,
        backgroundColor: orange[100],
        color: gray[800],
        border: `1px solid ${orange[200]}`,
      },
      icon: { color: orange[500] },
    },
  },
  MuiDialog: {
    styleOverrides: {
      paper: {
        borderRadius: 14,
        border: "1px solid #EBE4D9",
        boxShadow: "0 12px 34px rgba(42,38,34,.28)",
      },
    },
  },
  MuiLinearProgress: {
    styleOverrides: {
      root: { height: 8, borderRadius: 8, backgroundColor: "#F0EAE0" },
      bar: {
        borderRadius: 8,
        transition: "transform .65s cubic-bezier(.2,.8,.3,1)",
      },
    },
  },
};

import { Components, Theme } from "@mui/material/styles";
import { chipClasses } from "@mui/material/Chip";
import { iconButtonClasses } from "@mui/material/IconButton";
import { svgIconClasses } from "@mui/material/SvgIcon";
import { green, gray, red } from "../theme/theme-primitives";

export const dataDisplayCustomizations: Components<Theme> = {
  MuiList: {
    styleOverrides: {
      root: { padding: 0, display: "flex", flexDirection: "column", gap: 2 },
    },
  },
  MuiListItem: { styleOverrides: { root: { padding: 0 } } },
  MuiListItemText: {
    styleOverrides: {
      primary: { fontSize: "0.84375rem", fontWeight: 500, lineHeight: 1.45 },
      secondary: { fontSize: "0.75rem", lineHeight: 1.45 },
    },
  },
  MuiListSubheader: {
    styleOverrides: {
      root: {
        padding: "4px 8px",
        backgroundColor: "transparent",
        color: gray[500],
        fontSize: "0.65625rem",
        fontWeight: 600,
        letterSpacing: ".13em",
      },
    },
  },
  MuiListItemIcon: { styleOverrides: { root: { minWidth: 0 } } },
  MuiChip: {
    defaultProps: { size: "small" },
    styleOverrides: {
      root: {
        minHeight: 24,
        border: "1px solid #E4DCD0",
        borderRadius: 20,
        backgroundColor: gray[100],
        color: gray[600],
        [`& .${chipClasses.label}`]: {
          paddingLeft: 9,
          paddingRight: 9,
          fontSize: "0.75rem",
          fontWeight: 600,
        },
        [`& .${chipClasses.icon}`]: { color: "inherit", marginLeft: 7 },
        [`& .${svgIconClasses.root}`]: { fontSize: "0.875rem" },
      },
      colorDefault: {
        borderColor: "#E4DCD0",
        backgroundColor: gray[100],
        color: gray[600],
      },
      colorSuccess: {
        borderColor: "#BBD0BD",
        backgroundColor: green[50],
        color: green[600],
      },
      colorError: {
        borderColor: red[200],
        backgroundColor: red[50],
        color: red[600],
      },
    },
  },
  MuiTableCell: {
    styleOverrides: {
      root: { borderColor: "#F0EAE0", color: gray[700] },
      head: {
        color: gray[500],
        fontSize: "0.65625rem",
        fontWeight: 600,
        letterSpacing: ".09em",
        textTransform: "uppercase",
      },
    },
  },
  MuiTablePagination: {
    styleOverrides: {
      actions: {
        display: "flex",
        gap: 8,
        marginRight: 6,
        [`& .${iconButtonClasses.root}`]: {
          minWidth: 34,
          width: 34,
          height: 34,
        },
      },
    },
  },
};

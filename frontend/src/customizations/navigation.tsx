import * as React from "react";
import { alpha, Components, Theme } from "@mui/material/styles";
import { SvgIconProps } from "@mui/material/SvgIcon";
import { menuItemClasses } from "@mui/material/MenuItem";
import { selectClasses } from "@mui/material/Select";
import { tabClasses } from "@mui/material/Tab";
import UnfoldMoreRoundedIcon from "@mui/icons-material/UnfoldMoreRounded";
import { brand, gray } from "../theme/theme-primitives";

const SelectExpandIcon = React.forwardRef<SVGSVGElement, SvgIconProps>(
  (props, ref) => (
    <UnfoldMoreRoundedIcon fontSize="small" {...props} ref={ref} />
  ),
);
SelectExpandIcon.displayName = "SelectExpandIcon";

export const navigationCustomizations: Components<Theme> = {
  MuiMenuItem: {
    styleOverrides: {
      root: {
        minHeight: 40,
        borderRadius: 8,
        padding: "8px 10px",
        color: gray[700],
        [`&.${menuItemClasses.focusVisible}`]: { backgroundColor: brand[100] },
        [`&.${menuItemClasses.selected}`]: {
          backgroundColor: brand[100],
          color: brand[600],
        },
        [`&.${menuItemClasses.selected}:hover`]: {
          backgroundColor: brand[100],
        },
      },
    },
  },
  MuiMenu: {
    styleOverrides: {
      list: { padding: 6 },
      paper: {
        marginTop: 4,
        borderRadius: 12,
        border: "1px solid #EBE4D9",
        backgroundImage: "none",
        backgroundColor: gray[50],
        boxShadow: "0 4px 16px rgba(42,38,34,.13)",
      },
    },
  },
  MuiSelect: {
    defaultProps: { IconComponent: SelectExpandIcon },
    styleOverrides: {
      root: {
        minHeight: 40,
        borderRadius: 9,
        border: "1px solid #E4DCD0",
        backgroundColor: gray[50],
        "&:hover": { borderColor: gray[400], backgroundColor: gray[50] },
        [`&.${selectClasses.focused}`]: {
          borderColor: brand[500],
          boxShadow: `0 0 0 3px ${alpha(brand[500], 0.16)}`,
        },
        "&:before, &:after": { display: "none" },
      },
      select: { display: "flex", alignItems: "center" },
    },
  },
  MuiLink: {
    defaultProps: { underline: "none" },
    styleOverrides: {
      root: {
        color: brand[500],
        fontWeight: 500,
        textDecoration: "underline",
        textUnderlineOffset: 3,
        "&:hover": { color: brand[700] },
      },
    },
  },
  MuiDrawer: { styleOverrides: { paper: { backgroundColor: gray[100] } } },
  MuiPaginationItem: {
    styleOverrides: {
      root: {
        minWidth: 34,
        height: 34,
        "&.Mui-selected": {
          color: gray[50],
          backgroundColor: brand[600],
          "&:hover": { backgroundColor: brand[700] },
        },
      },
    },
  },
  MuiTabs: {
    styleOverrides: {
      root: { minHeight: 40 },
      indicator: { height: 2, borderRadius: 2, backgroundColor: brand[600] },
    },
  },
  MuiTab: {
    styleOverrides: {
      root: {
        minHeight: 40,
        minWidth: "fit-content",
        padding: "8px 10px",
        color: gray[600],
        textTransform: "none",
        borderRadius: 8,
        "&:hover": { backgroundColor: gray[200], color: gray[800] },
        [`&.${tabClasses.selected}`]: { color: brand[600], fontWeight: 600 },
      },
    },
  },
  MuiStepConnector: {
    styleOverrides: {
      line: { borderColor: "#EDE6DB", borderTopWidth: 1, borderRadius: 99 },
    },
  },
  MuiStepIcon: {
    styleOverrides: {
      root: {
        color: "transparent",
        border: `1px solid ${gray[400]}`,
        width: 14,
        height: 14,
        borderRadius: "50%",
        "& text": { display: "none" },
        "&.Mui-active": { color: brand[600], border: "none" },
        "&.Mui-completed": { color: "#5C7A63", border: "none" },
      },
    },
  },
};

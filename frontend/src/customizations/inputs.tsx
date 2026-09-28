import { alpha, Components, Theme } from "@mui/material/styles";
import { outlinedInputClasses } from "@mui/material/OutlinedInput";
import { svgIconClasses } from "@mui/material/SvgIcon";
import { toggleButtonClasses } from "@mui/material/ToggleButton";
import CheckBoxOutlineBlankRoundedIcon from "@mui/icons-material/CheckBoxOutlineBlankRounded";
import CheckRoundedIcon from "@mui/icons-material/CheckRounded";
import RemoveRoundedIcon from "@mui/icons-material/RemoveRounded";
import { brand, gray } from "../theme/theme-primitives";

export const inputsCustomizations: Components<Theme> = {
  MuiButtonBase: {
    defaultProps: { disableTouchRipple: true, disableRipple: true },
    styleOverrides: {
      root: {
        boxSizing: "border-box",
        transition:
          "background-color .18s ease, border-color .18s ease, color .18s ease, transform .18s ease",
        "&:focus-visible": {
          outline: `3px solid ${alpha(brand[500], 0.52)}`,
          outlineOffset: 3,
        },
      },
    },
  },
  MuiButton: {
    styleOverrides: {
      root: {
        minHeight: 40,
        borderRadius: 10,
        boxShadow: "0 1px 2px rgba(42,38,34,.12)",
        textTransform: "none",
        letterSpacing: 0,
        "&.Mui-disabled": {
          backgroundColor: gray[300],
          color: "#B5AA9C",
          boxShadow: "none",
        },
        variants: [
          {
            props: { color: "primary", variant: "contained" },
            style: {
              backgroundColor: brand[600],
              color: gray[100],
              "&:hover": {
                backgroundColor: brand[700],
                boxShadow: "0 1px 2px rgba(42,38,34,.12)",
              },
            },
          },
          {
            props: { color: "secondary", variant: "contained" },
            style: {
              backgroundColor: brand[400],
              color: gray[50],
              "&:hover": {
                backgroundColor: "#D2795F",
                boxShadow: "0 1px 2px rgba(42,38,34,.12)",
              },
            },
          },
          {
            props: { variant: "outlined" },
            style: {
              color: gray[800],
              borderColor: "#E4DCD0",
              backgroundColor: gray[50],
              "&:hover": { backgroundColor: gray[200], borderColor: gray[400] },
            },
          },
          {
            props: { variant: "text" },
            style: {
              color: gray[600],
              boxShadow: "none",
              "&:hover": { backgroundColor: gray[200], color: brand[500] },
            },
          },
          {
            props: { size: "small" },
            style: { minHeight: 34, padding: "7px 12px" },
          },
          {
            props: { size: "medium" },
            style: { minHeight: 40, padding: "10px 18px" },
          },
        ],
      },
    },
  },
  MuiIconButton: {
    styleOverrides: {
      root: () => ({
        minWidth: 34,
        minHeight: 34,
        borderRadius: 9,
        color: gray[600],
        border: "1px solid #EBE4D9",
        backgroundColor: gray[100],
        "&:hover": { backgroundColor: gray[200], borderColor: gray[400] },
        variants: [
          {
            props: { size: "small" },
            style: {
              width: 34,
              height: 34,
              padding: 7,
              [`& .${svgIconClasses.root}`]: { fontSize: "1rem" },
            },
          },
          {
            props: { size: "medium" },
            style: { width: 40, height: 40, padding: 9 },
          },
        ],
      }),
    },
  },
  MuiToggleButtonGroup: {
    styleOverrides: {
      root: { borderRadius: 9, boxShadow: "0 1px 2px rgba(42,38,34,.08)" },
    },
  },
  MuiToggleButton: {
    styleOverrides: {
      root: {
        minHeight: 36,
        padding: "8px 12px",
        color: gray[600],
        borderColor: "#E4DCD0",
        textTransform: "none",
        "&:hover": { backgroundColor: gray[200] },
        [`&.${toggleButtonClasses.selected}`]: {
          backgroundColor: brand[100],
          color: brand[600],
          borderColor: brand[300],
        },
        [`&.${toggleButtonClasses.selected}:hover`]: {
          backgroundColor: brand[100],
        },
      },
    },
  },
  MuiCheckbox: {
    defaultProps: {
      disableRipple: true,
      icon: <CheckBoxOutlineBlankRoundedIcon />,
      checkedIcon: <CheckRoundedIcon sx={{ height: 15, width: 15 }} />,
      indeterminateIcon: <RemoveRoundedIcon sx={{ height: 15, width: 15 }} />,
    },
    styleOverrides: {
      root: {
        margin: 9,
        padding: 0,
        width: 18,
        height: 18,
        borderRadius: 5,
        color: gray[500],
        "&.Mui-checked, &.MuiCheckbox-indeterminate": { color: brand[600] },
      },
    },
  },
  MuiInputBase: {
    styleOverrides: {
      input: { "&::placeholder": { opacity: 1, color: gray[500] } },
    },
  },
  MuiOutlinedInput: {
    styleOverrides: {
      root: {
        minHeight: 40,
        padding: "8px 12px",
        borderRadius: 9,
        border: "1px solid #E4DCD0",
        backgroundColor: gray[50],
        color: gray[800],
        "&:hover": { borderColor: gray[400] },
        [`&.${outlinedInputClasses.focused}`]: {
          borderColor: brand[500],
          boxShadow: `0 0 0 3px ${alpha(brand[500], 0.16)}`,
        },
      },
      input: { padding: 0 },
      notchedOutline: { border: "none" },
    },
  },
  MuiInputAdornment: { styleOverrides: { root: { color: gray[500] } } },
  MuiFormLabel: {
    styleOverrides: {
      root: {
        marginBottom: 8,
        color: gray[600],
        fontSize: "0.75rem",
        fontWeight: 500,
      },
    },
  },
};

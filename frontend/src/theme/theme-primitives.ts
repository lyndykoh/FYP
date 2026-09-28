import { createTheme, Shadows } from "@mui/material/styles";

declare module "@mui/material/Paper" {
  interface PaperPropsVariantOverrides {
    highlighted: true;
  }
}

declare module "@mui/material/styles" {
  interface ColorRange {
    50: string;
    100: string;
    200: string;
    300: string;
    400: string;
    500: string;
    600: string;
    700: string;
    800: string;
    900: string;
  }

  // eslint-disable-next-line @typescript-eslint/no-empty-object-type -- required MUI module augmentation
  interface PaletteColor extends ColorRange {}

  interface Palette {
    baseShadow: string;
  }
}

const defaultTheme = createTheme();

export const brand = {
  50: "#FDF6F3",
  100: "#F4E4DE",
  200: "#F1D9D1",
  300: "#E0B9AC",
  400: "#E08A72",
  500: "#B4573F",
  600: "#9E3A24",
  700: "#8A3120",
  800: "#6D261A",
  900: "#4E1A12",
};

export const gray = {
  50: "#FFFDFA",
  100: "#FBF9F5",
  200: "#F4EFE6",
  300: "#EDE6DB",
  400: "#DCCFBE",
  500: "#8B837A",
  600: "#6F675E",
  700: "#5A544C",
  800: "#2A2622",
  900: "#1B1917",
};

export const green = {
  50: "#E4EDE4",
  100: "#D5E2D5",
  200: "#BBD0BD",
  300: "#93A896",
  400: "#78927C",
  500: "#5C7A63",
  600: "#4A6B50",
  700: "#39563F",
  800: "#29422F",
  900: "#1C3022",
};

export const orange = {
  50: "#FDF4E8",
  100: "#F3EBDD",
  200: "#E8D2AE",
  300: "#D8B384",
  400: "#B78851",
  500: "#7A6238",
  600: "#654F2D",
  700: "#503E22",
  800: "#3D2E19",
  900: "#2B2011",
};

export const red = {
  50: "#FDF1ED",
  100: "#F7E3DC",
  200: "#F0DCD4",
  300: "#E0B9AC",
  400: "#D07B62",
  500: "#B4573F",
  600: "#9E3A24",
  700: "#8A3120",
  800: "#6D261A",
  900: "#4E1A12",
};

export const colorSchemes = {
  light: {
    palette: {
      primary: {
        light: brand[400],
        main: brand[600],
        dark: brand[700],
        contrastText: gray[100],
      },
      secondary: {
        light: brand[100],
        main: brand[400],
        dark: brand[500],
        contrastText: gray[50],
      },
      info: {
        light: brand[100],
        main: brand[500],
        dark: brand[700],
        contrastText: gray[50],
      },
      warning: {
        light: orange[200],
        main: orange[500],
        dark: orange[700],
      },
      error: {
        light: red[200],
        main: red[600],
        dark: red[800],
      },
      success: {
        light: green[300],
        main: green[500],
        dark: green[600],
      },
      grey: gray,
      divider: "#EBE4D9",
      background: { default: "#F7F4EF", paper: "#FFFDFA" },
      text: { primary: gray[800], secondary: gray[600] },
      action: { hover: "#FBF7F1", selected: brand[100] },
      baseShadow: "0 1px 3px rgba(42,38,34,.12)",
    },
  },
};

export const typography = {
  fontFamily: 'var(--font-dm-sans), "DM Sans", sans-serif',
  h1: {
    fontFamily: 'var(--font-playfair), "Playfair Display", serif',
    fontSize: "2.375rem",
    fontWeight: 500,
    lineHeight: 1.1,
    letterSpacing: "-0.02em",
  },
  h2: {
    fontFamily: 'var(--font-playfair), "Playfair Display", serif',
    fontSize: "2.125rem",
    fontWeight: 500,
    lineHeight: 1.15,
    letterSpacing: "-0.02em",
  },
  h3: {
    fontFamily: 'var(--font-playfair), "Playfair Display", serif',
    fontSize: "1.9375rem",
    fontWeight: 500,
    lineHeight: 1.2,
    letterSpacing: "-0.02em",
  },
  h4: {
    fontFamily: 'var(--font-playfair), "Playfair Display", serif',
    fontSize: "1.5rem",
    fontWeight: 500,
    lineHeight: 1.3,
  },
  h5: {
    fontFamily: 'var(--font-playfair), "Playfair Display", serif',
    fontSize: "1.3125rem",
    fontWeight: 500,
    lineHeight: 1.35,
  },
  h6: {
    fontFamily: 'var(--font-playfair), "Playfair Display", serif',
    fontSize: "1.125rem",
    fontWeight: 500,
    lineHeight: 1.4,
  },
  subtitle1: { fontSize: "1.125rem", fontWeight: 500, lineHeight: 1.45 },
  subtitle2: { fontSize: "0.875rem", fontWeight: 500, lineHeight: 1.45 },
  body1: { fontSize: "0.90625rem", fontWeight: 400, lineHeight: 1.65 },
  body2: { fontSize: "0.84375rem", fontWeight: 400, lineHeight: 1.6 },
  caption: { fontSize: "0.75rem", fontWeight: 400, lineHeight: 1.45 },
  overline: {
    fontSize: "0.65625rem",
    fontWeight: 600,
    lineHeight: 1.45,
    letterSpacing: ".13em",
  },
  button: { fontSize: "0.84375rem", fontWeight: 500, lineHeight: 1.3 },
};

export const shape = { borderRadius: 10 };

// @ts-expect-error MUI's fixed-length Shadows tuple is assembled from the default theme.
export const shadows: Shadows = [
  "none",
  "0 1px 2px rgba(42,38,34,.12)",
  "0 1px 3px rgba(42,38,34,.12)",
  "0 4px 16px rgba(42,38,34,.13)",
  ...defaultTheme.shadows.slice(4),
];

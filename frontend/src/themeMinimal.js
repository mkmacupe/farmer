import { alpha, createTheme } from "@mui/material/styles";

const palette = {
  primary: {
    main: "#2E5B4E",
    dark: "#1e3d34",
    light: "#4f7b6d",
    contrastText: "#FFFFFF",
  },
  secondary: {
    main: "#6B7280",
    dark: "#4B5563",
    light: "#9CA3AF",
    contrastText: "#FFFFFF",
  },
  success: {
    main: "#4f8a6d",
    light: "#e6f1eb",
    dark: "#3f7059",
  },
  warning: {
    main: "#b18a52",
    light: "#f7efe2",
    dark: "#6f522e",
  },
  error: {
    main: "#b97777",
    light: "#f6eaea",
    dark: "#975f5f",
  },
  info: {
    main: "#5a7fa8",
    light: "#e8eef5",
    dark: "#48688b",
  },
  background: {
    default: "#fafafa",
    paper: "#ffffff",
  },
  text: {
    primary: "#18181b",
    secondary: "#63636c",
  },
  divider: "#e5e7eb",
};

const focusOutline = `3px solid ${alpha(palette.primary.main, 0.42)}`;
const focusRing = `0 0 0 3px ${alpha(palette.primary.main, 0.12)}`;

const themeMinimal = createTheme({
  palette,
  shadows: Array(25).fill("none"),
  shape: {
    borderRadius: 10,
  },
  typography: {
    fontFamily: 'system-ui, -apple-system, "Segoe UI", "Noto Sans", sans-serif',
    h1: { fontWeight: 600, fontSize: "2rem", letterSpacing: "-0.025em", lineHeight: 1.2 },
    h2: { fontWeight: 600, fontSize: "1.5rem", letterSpacing: "-0.02em", lineHeight: 1.25 },
    h3: { fontWeight: 600, fontSize: "1.25rem", letterSpacing: "-0.01em" },
    h4: { fontWeight: 600, fontSize: "1.125rem" },
    h5: { fontWeight: 600, fontSize: "1rem" },
    h6: { fontWeight: 600, fontSize: "0.95rem" },
    subtitle1: { fontSize: "0.9375rem", fontWeight: 500 },
    subtitle2: { fontSize: "0.875rem", fontWeight: 500 },
    body1: { fontSize: "0.9375rem", lineHeight: 1.55 },
    body2: { fontSize: "0.875rem", lineHeight: 1.5 },
    button: { textTransform: "none", fontWeight: 600, fontSize: "0.875rem" },
    caption: { fontSize: "0.75rem", color: palette.text.secondary },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundColor: palette.background.default,
          color: palette.text.primary,
        },
        'button:focus-visible, [role="button"]:focus-visible, a:focus-visible, input:focus-visible, select:focus-visible, textarea:focus-visible, [tabindex]:not([tabindex="-1"]):focus-visible': {
          outline: focusOutline,
          outlineOffset: 2,
        },
      },
    },
    MuiButton: {
      defaultProps: {
        disableElevation: true,
        disableRipple: true,
      },
      styleOverrides: {
        root: {
          borderRadius: 10,
          padding: "8px 16px",
        },
        containedPrimary: {
          backgroundColor: palette.primary.main,
          "&:hover": {
            backgroundColor: palette.primary.dark,
          },
        },
        outlined: {
          borderColor: "#d4d4d8",
          color: palette.text.primary,
          backgroundColor: palette.background.paper,
        },
      },
    },
    MuiPaper: {
      defaultProps: {
        elevation: 0,
      },
      styleOverrides: {
        root: {
          backgroundImage: "none",
        },
        outlined: {
          border: `1px solid ${palette.divider}`,
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 12,
          border: `1px solid ${palette.divider}`,
        },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundColor: palette.background.paper,
          borderBottom: `1px solid ${palette.divider}`,
          color: palette.text.primary,
        },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundColor: palette.background.paper,
          borderRight: `1px solid ${palette.divider}`,
        },
      },
    },
    MuiTextField: {
      defaultProps: {
        variant: "outlined",
        size: "small",
        fullWidth: true,
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: 10,
          backgroundColor: palette.background.paper,
          "& .MuiOutlinedInput-notchedOutline": {
            borderColor: "#d4d4d8",
          },
          "&:hover .MuiOutlinedInput-notchedOutline": {
            borderColor: "#a1a1aa",
          },
          "&.Mui-focused .MuiOutlinedInput-notchedOutline": {
            borderColor: palette.primary.main,
            boxShadow: focusRing,
          },
        },
      },
    },
    MuiBottomNavigation: {
      styleOverrides: {
        root: {
          backgroundColor: palette.background.paper,
          borderTop: `1px solid ${palette.divider}`,
        },
      },
    },
    MuiBottomNavigationAction: {
      styleOverrides: {
        root: {
          color: palette.text.secondary,
          "&.Mui-selected": {
            color: palette.primary.main,
            fontWeight: 600,
          },
        },
      },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: 10,
        },
      },
    },
  },
});

export default themeMinimal;

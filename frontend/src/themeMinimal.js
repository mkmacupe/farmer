import { createTheme, alpha } from '@mui/material/styles';

const palette = {
  primary: {
    main: '#2E5B4E',
    dark: '#1e3d34',
    light: '#4f7b6d',
    contrastText: '#FFFFFF'
  },
  secondary: {
    main: '#6B7280',
    dark: '#4B5563',
    light: '#9CA3AF',
    contrastText: '#FFFFFF'
  },
  success: {
    main: '#4f8a6d',
    light: '#e6f1eb',
    dark: '#3f7059'
  },
  warning: {
    main: '#b18a52',
    light: '#f7efe2',
    dark: '#6f522e'
  },
  error: {
    main: '#b97777',
    light: '#f6eaea',
    dark: '#975f5f'
  },
  info: {
    main: '#5a7fa8',
    light: '#e8eef5',
    dark: '#48688b'
  },
  background: {
    default: '#fafafa',
    paper: '#ffffff'
  },
  text: {
    primary: '#18181b',
    secondary: '#63636c'
  },
  divider: '#f0f0f0',
  action: {
    hover: 'rgba(0, 0, 0, 0.03)',
    selected: 'rgba(46, 91, 78, 0.06)',
    focus: 'rgba(46, 91, 78, 0.12)'
  }
};

const themeMinimal = createTheme({
  palette,
  shadows: Array(25).fill('none'),
  shape: {
    borderRadius: 10
  },
  typography: {
    fontFamily: 'system-ui, -apple-system, "Segoe UI", "Noto Sans", sans-serif',
    h1: { fontWeight: 600, fontSize: '2rem', letterSpacing: '-0.025em', lineHeight: 1.2 },
    h2: { fontWeight: 600, fontSize: '1.5rem', letterSpacing: '-0.02em', lineHeight: 1.3 },
    h3: { fontWeight: 600, fontSize: '1.25rem', letterSpacing: '-0.01em' },
    h4: { fontWeight: 600, fontSize: '1.125rem' },
    h5: { fontWeight: 600, fontSize: '1.05rem' },
    h6: { fontWeight: 600, fontSize: '1rem' },
    subtitle1: { fontSize: '0.9375rem', fontWeight: 500 },
    subtitle2: { fontSize: '0.875rem', fontWeight: 500 },
    body1: { fontSize: '0.9375rem', lineHeight: 1.6 },
    body2: { fontSize: '0.875rem', lineHeight: 1.5, color: palette.text.secondary },
    button: { textTransform: 'none', fontWeight: 600, fontSize: '0.875rem' },
    caption: { fontSize: '0.75rem', color: palette.text.secondary },
    overline: {
      fontWeight: 600,
      fontSize: '0.6875rem',
      letterSpacing: '0.06em',
      textTransform: 'uppercase',
      color: palette.text.secondary
    }
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundColor: palette.background.default,
          backgroundImage: 'none'
        },
        'button:focus-visible, [role="button"]:focus-visible, a:focus-visible, input:focus-visible, select:focus-visible, textarea:focus-visible, [tabindex]:not([tabindex="-1"]):focus-visible': {
          outline: `3px solid ${alpha(palette.primary.main, 0.55)}`,
          outlineOffset: 2
        }
      }
    },
    MuiTypography: {
      defaultProps: {
        variantMapping: {
          h1: 'h1',
          h2: 'h2',
          h3: 'h3',
          h4: 'h2',
          h5: 'h2',
          h6: 'h3',
          subtitle1: 'p',
          subtitle2: 'p',
          body1: 'p',
          body2: 'p',
          inherit: 'p'
        }
      }
    },
    MuiButton: {
      defaultProps: {
        disableElevation: true,
        disableRipple: true
      },
      styleOverrides: {
        root: {
          borderRadius: 8,
          padding: '8px 18px',
          transition: 'background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease',
          fontWeight: 600,
          '&:focus-visible': {
            outline: `3px solid ${alpha(palette.primary.main, 0.5)}`,
            outlineOffset: 2
          }
        },
        containedPrimary: {
          backgroundColor: palette.primary.main,
          '&:hover': {
            backgroundColor: palette.primary.dark
          }
        },
        outlined: {
          borderColor: '#e4e4e7',
          color: palette.text.primary,
          '&:hover': {
            borderColor: '#a1a1aa',
            backgroundColor: 'rgba(0,0,0,0.02)'
          }
        },
        text: {
          '&:hover': {
            backgroundColor: 'rgba(0,0,0,0.03)'
          }
        },
        sizeSmall: {
          padding: '5px 12px',
          fontSize: '0.8125rem'
        },
        sizeLarge: {
          padding: '10px 24px',
          fontSize: '0.9375rem'
        }
      }
    },
    MuiIconButton: {
      styleOverrides: {
        root: {
          transition: 'background-color 0.15s ease, color 0.15s ease',
          '&:hover': {
            backgroundColor: 'rgba(0,0,0,0.04)'
          },
          '&:focus-visible': {
            outline: `3px solid ${alpha(palette.primary.main, 0.5)}`,
            outlineOffset: 2
          }
        }
      }
    },
    MuiPaper: {
      defaultProps: {
        elevation: 0
      },
      styleOverrides: {
        root: {
          backgroundImage: 'none',
          border: 'none',
          transition: 'border-color 0.15s ease'
        },
        outlined: {
          border: `1px solid ${palette.divider}`
        }
      }
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 12,
          border: `1px solid ${palette.divider}`,
          backgroundColor: palette.background.paper,
          transition: 'border-color 0.15s ease',
          '&:hover': {
            borderColor: '#d4d4d8'
          }
        }
      }
    },
    MuiAppBar: {
      styleOverrides: {
        root: {
          backgroundColor: 'rgba(255,255,255,0.85)',
          backdropFilter: 'blur(12px)',
          borderBottom: `1px solid ${palette.divider}`,
          color: palette.text.primary
        }
      }
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundColor: palette.background.paper,
          borderRight: `1px solid ${palette.divider}`
        }
      }
    },
    MuiTextField: {
      defaultProps: {
        variant: 'outlined',
        size: 'small',
        fullWidth: true
      }
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          backgroundColor: palette.background.paper,
          transition: 'background-color 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease',
          '& .MuiOutlinedInput-notchedOutline': {
            borderColor: '#e4e4e7',
            transition: 'border-color 0.15s ease'
          },
          '&:hover .MuiOutlinedInput-notchedOutline': {
            borderColor: '#a1a1aa'
          },
          '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
            borderColor: palette.primary.main,
            borderWidth: '1.5px',
            boxShadow: `0 0 0 3px ${alpha(palette.primary.main, 0.08)}`
          }
        }
      }
    },
    MuiTableHead: {
      styleOverrides: {
        root: {
          '& .MuiTableCell-head': {
            fontWeight: 600,
            color: palette.text.secondary,
            fontSize: '0.75rem',
            textTransform: 'uppercase',
            letterSpacing: '0.04em',
            backgroundColor: palette.background.paper,
            borderBottom: `2px solid ${palette.divider}`
          }
        }
      }
    },
    MuiTableCell: {
      styleOverrides: {
        root: {
          borderBottom: `1px solid ${palette.divider}`,
          padding: '12px 16px',
          fontSize: '0.875rem'
        }
      }
    },
    MuiTableRow: {
      styleOverrides: {
        root: {
          transition: 'background-color 0.1s ease',
          '&:hover': {
            backgroundColor: 'rgba(0,0,0,0.015)'
          },
          '&:focus-within': {
            backgroundColor: alpha(palette.primary.main, 0.06)
          }
        }
      }
    },
    MuiChip: {
      styleOverrides: {
        root: {
          borderRadius: 6,
          fontWeight: 500,
          height: 26,
          fontSize: '0.75rem',
          '&:focus-visible': {
            outline: `3px solid ${alpha(palette.primary.main, 0.45)}`,
            outlineOffset: 2
          }
        },
        sizeSmall: {
          height: 24,
          fontSize: '0.7rem'
        }
      },
      variants: [
        {
          props: { variant: 'filled', color: 'secondary' },
          style: {
            backgroundColor: '#e6e9ee',
            color: '#4b5563'
          }
        },
        {
          props: { variant: 'filled', color: 'info' },
          style: {
            backgroundColor: '#e8eef5',
            color: '#48688b'
          }
        },
        {
          props: { variant: 'filled', color: 'success' },
          style: {
            backgroundColor: '#e6f1eb',
            color: '#3f7059'
          }
        },
        {
          props: { variant: 'filled', color: 'warning' },
          style: {
            backgroundColor: '#f7efe2',
            color: '#6f522e'
          }
        },
        {
          props: { variant: 'filled', color: 'error' },
          style: {
            backgroundColor: '#f6eaea',
            color: '#975f5f'
          }
        },
        {
          props: { variant: 'outlined', color: 'secondary' },
          style: {
            borderColor: '#cfd6df',
            color: '#5b6471',
            backgroundColor: '#f8fafc'
          }
        },
        {
          props: { variant: 'outlined', color: 'info' },
          style: {
            borderColor: '#bfd0e2',
            color: '#48688b',
            backgroundColor: '#f4f7fb'
          }
        },
        {
          props: { variant: 'outlined', color: 'success' },
          style: {
            borderColor: '#bfd8ca',
            color: '#3f7059',
            backgroundColor: '#f3f8f5'
          }
        },
        {
          props: { variant: 'outlined', color: 'warning' },
          style: {
            borderColor: '#dfcfb6',
            color: '#6f522e',
            backgroundColor: '#fbf8f2'
          }
        },
        {
          props: { variant: 'outlined', color: 'error' },
          style: {
            borderColor: '#e3c8c8',
            color: '#975f5f',
            backgroundColor: '#fbf6f6'
          }
        }
      ]
    },
    MuiDivider: {
      styleOverrides: {
        root: {
          borderColor: palette.divider
        }
      }
    },
    MuiFormHelperText: {
      styleOverrides: {
        root: {
          color: palette.text.secondary,
          '&.Mui-disabled': {
            color: '#5f6368'
          }
        }
      }
    },
    MuiAlert: {
      styleOverrides: {
        root: {
          borderRadius: 10,
          fontSize: '0.875rem'
        },
        standardSuccess: {
          backgroundColor: '#f3f8f5',
          color: '#3f7059',
          '& .MuiAlert-icon': { color: '#4f8a6d' }
        },
        standardError: {
          backgroundColor: '#fbf6f6',
          color: '#975f5f',
          '& .MuiAlert-icon': { color: '#b97777' }
        },
        standardInfo: {
          backgroundColor: '#f4f7fb',
          color: '#48688b',
          '& .MuiAlert-icon': { color: '#5a7fa8' }
        },
        standardWarning: {
          backgroundColor: '#fbf8f2',
          color: '#6f522e',
          '& .MuiAlert-icon': { color: '#6f522e' }
        }
      }
    },
    MuiSvgIcon: {
      styleOverrides: {
        root: {
          transition: 'color 0.15s ease'
        },
        colorInfo: {
          color: palette.info.dark
        },
        colorSuccess: {
          color: palette.success.dark
        },
        colorWarning: {
          color: palette.warning.dark
        },
        colorError: {
          color: palette.error.dark
        }
      }
    },
    MuiDialog: {
      styleOverrides: {
        paper: {
          borderRadius: 14,
          border: `1px solid ${palette.divider}`
        }
      }
    },
    MuiDialogTitle: {
      styleOverrides: {
        root: {
          fontWeight: 600,
          fontSize: '1.1rem'
        }
      }
    },
    MuiBottomNavigation: {
      styleOverrides: {
        root: {
          backgroundColor: 'rgba(255,255,255,0.9)',
          backdropFilter: 'blur(12px)',
          borderTop: `1px solid ${palette.divider}`
        }
      }
    },
    MuiBottomNavigationAction: {
      styleOverrides: {
        root: {
          color: palette.text.secondary,
          transition: 'color 0.15s ease',
          '&.Mui-selected': {
            color: palette.primary.main
          },
          '&:focus-visible': {
            outline: `3px solid ${alpha(palette.primary.main, 0.45)}`,
            outlineOffset: -2
          }
        },
        label: {
          fontSize: '0.7rem',
          fontWeight: 500
        }
      }
    },
    MuiFab: {
      styleOverrides: {
        root: {
          borderRadius: 14,
          textTransform: 'none',
          fontWeight: 600
        }
      }
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          borderRadius: 8,
          transition: 'background-color 0.15s ease, color 0.15s ease'
        }
      }
    },
    MuiSnackbar: {
      styleOverrides: {
        root: {
          '& .MuiAlert-root': {
            boxShadow: '0 4px 12px rgba(0,0,0,0.08)',
            border: `1px solid ${palette.divider}`
          }
        }
      }
    },
    MuiSkeleton: {
      styleOverrides: {
        root: {
          backgroundColor: 'rgba(0,0,0,0.04)'
        }
      }
    },
    MuiAvatar: {
      styleOverrides: {
        root: {
          fontSize: '0.875rem',
          fontWeight: 600
        }
      }
    },
    MuiTooltip: {
      styleOverrides: {
        tooltip: {
          backgroundColor: '#18181b',
          fontSize: '0.75rem',
          borderRadius: 6,
          padding: '6px 12px'
        },
        arrow: {
          color: '#18181b'
        }
      }
    }
  }
});

export default themeMinimal;

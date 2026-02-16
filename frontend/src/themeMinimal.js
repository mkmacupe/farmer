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
    main: '#16a34a',
    light: '#bbf7d0',
    dark: '#15803d'
  },
  warning: {
    main: '#d97706',
    light: '#fef3c7',
    dark: '#b45309'
  },
  error: {
    main: '#dc2626',
    light: '#fecaca',
    dark: '#b91c1c'
  },
  info: {
    main: '#2563eb',
    light: '#dbeafe',
    dark: '#1d4ed8'
  },
  background: {
    default: '#fafafa',
    paper: '#ffffff'
  },
  text: {
    primary: '#18181b',
    secondary: '#71717a'
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
    fontFamily: '"IBM Plex Sans", "Noto Sans", "system-ui", sans-serif',
    h1: { fontWeight: 700, fontSize: '2rem', letterSpacing: '-0.025em', lineHeight: 1.2 },
    h2: { fontWeight: 700, fontSize: '1.5rem', letterSpacing: '-0.02em', lineHeight: 1.3 },
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
          transition: 'all 0.15s ease',
          fontWeight: 600
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
          transition: 'all 0.15s ease',
          '&:hover': {
            backgroundColor: 'rgba(0,0,0,0.04)'
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
          transition: 'all 0.15s ease',
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
          fontSize: '0.75rem'
        },
        sizeSmall: {
          height: 24,
          fontSize: '0.7rem'
        }
      }
    },
    MuiDivider: {
      styleOverrides: {
        root: {
          borderColor: palette.divider
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
          backgroundColor: '#f0fdf4',
          color: '#15803d',
          '& .MuiAlert-icon': { color: '#16a34a' }
        },
        standardError: {
          backgroundColor: '#fef2f2',
          color: '#b91c1c',
          '& .MuiAlert-icon': { color: '#dc2626' }
        },
        standardInfo: {
          backgroundColor: '#eff6ff',
          color: '#1d4ed8',
          '& .MuiAlert-icon': { color: '#2563eb' }
        },
        standardWarning: {
          backgroundColor: '#fffbeb',
          color: '#b45309',
          '& .MuiAlert-icon': { color: '#d97706' }
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
          fontWeight: 700,
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
          transition: 'all 0.15s ease'
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
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundColor: palette.background.paper,
          borderRight: `1px solid ${palette.divider}`
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

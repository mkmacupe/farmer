import React, { useState } from 'react';
import { Box, Paper, Tabs, Tab, Typography, ThemeProvider, CssBaseline } from '@mui/material';
import themeMinimal from '../../../src/themeMinimal';

export default function LabShell({ children, variants }) {
  const [activeTab, setActiveTab] = useState(0);

  const handleChange = (event, newValue) => {
    setActiveTab(newValue);
  };

  const ActiveVariant = variants[activeTab].component;

  return (
    <ThemeProvider theme={themeMinimal}>
      <CssBaseline />
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', bgcolor: '#f5f5f5' }}>
        <Paper square elevation={1} sx={{ zIndex: 10, px: 2, pt: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
            <Typography variant="h6" sx={{ fontWeight: 700, mr: 2, color: 'primary.main' }}>
              Лаборатория дизайна
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Исследование интерфейса панели
            </Typography>
          </Box>
          <Tabs 
            value={activeTab} 
            onChange={handleChange} 
            variant="scrollable" 
            scrollButtons="auto"
            sx={{ borderBottom: 1, borderColor: 'divider' }}
          >
            {variants.map((v, index) => (
              <Tab key={index} label={`${String.fromCharCode(65 + index)}: ${v.name}`} />
            ))}
          </Tabs>
        </Paper>
        <Box sx={{ flexGrow: 1, overflow: 'auto', p: 3 }}>
          <Box sx={{ maxWidth: 1200, mx: 'auto', height: '100%' }}>
            <ActiveVariant />
          </Box>
        </Box>
      </Box>
    </ThemeProvider>
  );
}

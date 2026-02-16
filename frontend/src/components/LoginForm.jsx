import React, { useState } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Container from '@mui/material/Container';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';
import Fade from '@mui/material/Fade';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Divider from '@mui/material/Divider';
import VisibilityIcon from '@mui/icons-material/Visibility';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';

export default function LoginForm({ onLogin, loading, error }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);

  const demoAccounts = [
    { label: 'mogilevkhim', username: 'mogilevkhim' },
    { label: 'mogilevlift', username: 'mogilevlift' },
    { label: 'babushkina', username: 'babushkina' },
    { label: 'manager', username: 'manager' },
    { label: 'logistician', username: 'logistician' },
    { label: 'driver1', username: 'driver1' },
    { label: 'driver2', username: 'driver2' },
    { label: 'driver3', username: 'driver3' }
  ];

  const canSubmit = username.trim().length > 0 && password.length > 0;

  const applyDemo = (usernameValue) => {
    setUsername(usernameValue);
    setPassword('1');
  };

  const submit = (event) => {
    event.preventDefault();
    onLogin(username.trim(), password);
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        px: 2,
        py: 6,
        bgcolor: '#fafafa'
      }}
    >
      <Container maxWidth="xs">
        <Fade in timeout={350}>
          <Box>
            {/* Login Card */}
            <Box
              sx={{
                bgcolor: 'white',
                borderRadius: 3,
                border: '1px solid #f0f0f0',
                p: { xs: 3, sm: 4 }
              }}
            >
              <Stack spacing={2.5}>
                <Box>
                  <Typography variant="h6" fontWeight={600} sx={{ mb: 0.25 }}>
                    Вход
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    Введите логин и пароль
                  </Typography>
                </Box>

                {error && (
                  <Alert severity="error" sx={{ borderRadius: 2 }}>
                    {error}
                  </Alert>
                )}

                <Box component="form" onSubmit={submit}>
                  <Stack spacing={2}>
                    <TextField
                      label="Логин"
                      autoComplete="username"
                      required
                      value={username}
                      onChange={(event) => setUsername(event.target.value)}
                      autoFocus
                    />
                    <TextField
                      label="Пароль"
                      type={showPassword ? 'text' : 'password'}
                      autoComplete="current-password"
                      required
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      InputProps={{
                        endAdornment: (
                          <InputAdornment position="end">
                            <IconButton
                              edge="end"
                              aria-label={showPassword ? 'Скрыть символы' : 'Показать символы'}
                              onClick={() => setShowPassword((prev) => !prev)}
                              size="small"
                            >
                              {showPassword ? <VisibilityOffIcon fontSize="small" /> : <VisibilityIcon fontSize="small" />}
                            </IconButton>
                          </InputAdornment>
                        )
                      }}
                    />
                    <Button
                      type="submit"
                      fullWidth
                      variant="contained"
                      size="large"
                      disabled={loading || !canSubmit}
                      sx={{ mt: 0.5 }}
                    >
                      {loading ? 'Вход...' : 'Войти'}
                    </Button>
                  </Stack>
                </Box>

                <Divider>
                  <span />
                </Divider>

                <Stack direction="row" spacing={0.75} useFlexGap flexWrap="wrap" justifyContent="center">
                  {demoAccounts.map((account) => (
                    <Chip
                      key={account.username}
                      label={account.label}
                      size="small"
                      variant={username === account.username ? 'filled' : 'outlined'}
                      color={username === account.username ? 'primary' : 'default'}
                      onClick={() => applyDemo(account.username)}
                      disabled={loading}
                      sx={{
                        fontWeight: 500,
                        cursor: 'pointer',
                        transition: 'all 0.15s ease'
                      }}
                    />
                  ))}
                </Stack>
              </Stack>
            </Box>
          </Box>
        </Fade>
      </Container>
    </Box>
  );
}

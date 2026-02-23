import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Skeleton from '@mui/material/Skeleton';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import { alpha } from '@mui/material/styles';

export function ProductCardSkeleton() {
  return (
    <Card
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden'
      }}
    >
      <Skeleton
        variant="rectangular"
        height={160}
        animation="wave"
        sx={{ bgcolor: (theme) => alpha(theme.palette.primary.main, 0.06) }}
      />
      <CardContent sx={{ flexGrow: 1 }}>
        <Skeleton variant="text" width="85%" height={28} animation="wave" />
        <Skeleton variant="text" width="65%" height={18} sx={{ mb: 1.5 }} animation="wave" />
        <Stack direction="row" spacing={1} alignItems="center" mt={1.5}>
          <Skeleton variant="rounded" width={75} height={26} animation="wave" />
          <Skeleton variant="text" width={90} height={28} animation="wave" />
        </Stack>
        <Skeleton variant="text" width="45%" height={16} sx={{ mt: 1.5 }} animation="wave" />
      </CardContent>
      <Box sx={{ px: 2.5, pb: 2.5 }}>
        <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
          <Stack direction="row" spacing={0.5}>
            <Skeleton variant="circular" width={32} height={32} animation="wave" />
            <Skeleton variant="rounded" width={40} height={32} animation="wave" />
            <Skeleton variant="circular" width={32} height={32} animation="wave" />
          </Stack>
          <Skeleton variant="rounded" width={100} height={36} animation="wave" />
        </Stack>
      </Box>
    </Card>
  );
}

export function ProductGridSkeleton({ count = 6 }) {
  return (
    <Grid container spacing={3}>
      {Array.from({ length: count }).map((_, index) => (
        <Grid size={{ xs: 12, sm: 6, lg: 4 }} key={index}>
          <ProductCardSkeleton />
        </Grid>
      ))}
    </Grid>
  );
}

export function OrderTableSkeleton({ rows = 5 }) {
  return (
    <TableContainer
      component={Paper}
      elevation={0}
      sx={{
        borderRadius: 3,
        border: (theme) => `1px solid ${alpha(theme.palette.primary.main, 0.1)}`
      }}
    >
      <Table size="small">
        <TableHead>
          <TableRow>
            {['№', 'Директор', 'Адрес', 'Статус', 'Дата', 'Сумма', 'Действия'].map((header, idx) => (
              <TableCell key={header}>
                <Skeleton variant="text" width={idx === 0 ? 30 : 70} height={20} animation="wave" />
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {Array.from({ length: rows }).map((_, index) => (
            <TableRow key={index}>
              <TableCell>
                <Skeleton variant="text" width={45} height={24} animation="wave" />
              </TableCell>
              <TableCell>
                <Skeleton variant="text" width={130} height={22} animation="wave" />
              </TableCell>
              <TableCell>
                <Skeleton variant="text" width={180} height={22} animation="wave" />
              </TableCell>
              <TableCell>
                <Skeleton variant="rounded" width={90} height={26} animation="wave" />
              </TableCell>
              <TableCell>
                <Skeleton variant="text" width={100} height={18} animation="wave" />
              </TableCell>
              <TableCell>
                <Skeleton variant="text" width={75} height={24} animation="wave" />
              </TableCell>
              <TableCell>
                <Skeleton variant="rounded" width={110} height={34} animation="wave" />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

export function DashboardSkeleton() {
  return (
    <Stack spacing={3}>
      {/* Hero section */}
      <Paper
        sx={{
          p: 4,
          borderRadius: 4,
          background: (theme) => `linear-gradient(145deg, ${alpha(theme.palette.primary.main, 0.08)} 0%, ${alpha(theme.palette.background.paper, 1)} 100%)`
        }}
      >
        <Grid container spacing={3} alignItems="center">
          <Grid size={{ xs: 12, md: 6 }}>
            <Skeleton variant="text" width={140} height={18} animation="wave" />
            <Skeleton variant="text" width="90%" height={48} sx={{ mt: 1.5 }} animation="wave" />
            <Skeleton variant="text" width="70%" height={22} sx={{ mt: 1 }} animation="wave" />
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <Skeleton
                variant="rounded"
                height={110}
                sx={{ flex: 1, borderRadius: 3 }}
                animation="wave"
              />
              <Skeleton
                variant="rounded"
                height={110}
                sx={{ flex: 1, borderRadius: 3 }}
                animation="wave"
              />
            </Stack>
          </Grid>
        </Grid>
      </Paper>

      {/* Stats cards */}
      <Grid container spacing={2}>
        {[1, 2, 3, 4].map((i) => (
          <Grid size={{ xs: 12, sm: 6, md: 3 }} key={i}>
            <Paper sx={{ p: 3, borderRadius: 3 }}>
              <Stack direction="row" spacing={2} alignItems="center">
                <Skeleton variant="circular" width={52} height={52} animation="wave" />
                <Box sx={{ flex: 1 }}>
                  <Skeleton variant="text" width="70%" height={18} animation="wave" />
                  <Skeleton variant="text" width="50%" height={36} animation="wave" />
                </Box>
              </Stack>
            </Paper>
          </Grid>
        ))}
      </Grid>
    </Stack>
  );
}

export function ProfileSkeleton() {
  return (
    <Paper sx={{ p: 4, borderRadius: 3 }}>
      <Stack direction="row" alignItems="center" spacing={2} mb={3}>
        <Skeleton variant="circular" width={56} height={56} animation="wave" />
        <Box>
          <Skeleton variant="text" width={180} height={28} animation="wave" />
          <Skeleton variant="text" width={120} height={18} animation="wave" />
        </Box>
      </Stack>
      <Grid container spacing={2.5} sx={{ mb: 3 }}>
        <Grid size={{ xs: 12, sm: 4 }}>
          <Skeleton variant="rounded" height={56} animation="wave" sx={{ borderRadius: 2 }} />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <Skeleton variant="rounded" height={56} animation="wave" sx={{ borderRadius: 2 }} />
        </Grid>
        <Grid size={{ xs: 12, sm: 4 }}>
          <Skeleton variant="rounded" height={56} animation="wave" sx={{ borderRadius: 2 }} />
        </Grid>
      </Grid>
      <Skeleton variant="rounded" width={180} height={44} animation="wave" sx={{ borderRadius: 2 }} />
    </Paper>
  );
}

export function AddressCardSkeleton() {
  return (
    <Card variant="outlined" sx={{ borderRadius: 3 }}>
      <CardContent sx={{ p: 2.5 }}>
        <Stack direction="row" spacing={2} alignItems="flex-start">
          <Skeleton variant="circular" width={44} height={44} animation="wave" />
          <Box sx={{ flex: 1 }}>
            <Skeleton variant="text" width="65%" height={26} animation="wave" />
            <Skeleton variant="text" width="90%" height={20} sx={{ mt: 0.5 }} animation="wave" />
            <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
              <Skeleton variant="rounded" width={85} height={32} animation="wave" />
              <Skeleton variant="rounded" width={65} height={32} animation="wave" />
              <Skeleton variant="rounded" width={65} height={32} animation="wave" />
            </Stack>
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}

export function NotificationSkeleton({ count = 3 }) {
  return (
    <Stack spacing={1.5}>
      {Array.from({ length: count }).map((_, index) => (
        <Paper
          key={index}
          variant="outlined"
          sx={{ p: 2, borderRadius: 2 }}
        >
          <Stack direction="row" spacing={2} alignItems="center">
            <Skeleton variant="circular" width={36} height={36} animation="wave" />
            <Box sx={{ flex: 1 }}>
              <Skeleton variant="text" width="60%" height={20} animation="wave" />
              <Skeleton variant="text" width="85%" height={16} animation="wave" />
            </Box>
            <Skeleton variant="text" width={60} height={14} animation="wave" />
          </Stack>
        </Paper>
      ))}
    </Stack>
  );
}

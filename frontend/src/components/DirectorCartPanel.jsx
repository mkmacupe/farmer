import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Divider from "@mui/material/Divider";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import ShoppingCartIcon from "@mui/icons-material/ShoppingCart";
import { formatMoney } from "../utils/formatters.js";

export default function DirectorCartPanel({
  selectedItems,
  selectedProductsCount,
  selectedUnitsCount,
  selectedTotal,
  addresses,
  selectedAddressId,
  onSelectAddress,
  addressHelperText,
  onCreateOrder,
  onClearCart,
  loading,
  canCreateOrder,
  isCompactLayout,
}) {
  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2.5,
        borderRadius: 3,
        position: { md: "sticky" },
        top: { md: 96 },
      }}
    >
      <Typography variant="subtitle1" fontWeight={600} gutterBottom>
        Корзина
      </Typography>
      <Stack spacing={1.5}>
        {selectedItems.length > 0 ? (
          <Stack spacing={1}>
            {selectedItems.map((item) => (
              <Box
                key={item.product.id}
                sx={{
                  display: "flex",
                  justifyContent: "space-between",
                  gap: 1,
                  alignItems: "baseline",
                }}
              >
                <Typography
                  variant="body2"
                  noWrap={!isCompactLayout}
                  sx={{ maxWidth: isCompactLayout ? "100%" : 180 }}
                >
                  {item.product.name}
                </Typography>
                <Typography
                  variant="body2"
                  fontWeight={600}
                  whiteSpace="nowrap"
                >
                  {item.quantity} × {formatMoney(item.product.price)}
                </Typography>
              </Box>
            ))}
          </Stack>
        ) : (
          <Typography variant="body2" color="text.secondary">
            Корзина пуста
          </Typography>
        )}
        <Divider />
        <Box sx={{ display: "flex", justifyContent: "space-between" }}>
          <Typography variant="body2" color="text.secondary">
            Позиций
          </Typography>
          <Typography fontWeight={600}>{selectedProductsCount}</Typography>
        </Box>
        <Box sx={{ display: "flex", justifyContent: "space-between" }}>
          <Typography variant="body2" color="text.secondary">
            Единиц
          </Typography>
          <Typography fontWeight={600}>{selectedUnitsCount}</Typography>
        </Box>
        <Divider />
        <Box sx={{ display: "flex", justifyContent: "space-between" }}>
          <Typography variant="subtitle2" fontWeight={600}>
            Итого
          </Typography>
          <Typography variant="subtitle1" fontWeight={800} color="primary">
            {formatMoney(selectedTotal)} BYN
          </Typography>
        </Box>
        <TextField
          select
          label="Адрес доставки"
          fullWidth
          size="small"
          value={selectedAddressId}
          onChange={(event) => onSelectAddress(event.target.value)}
          helperText={addressHelperText}
        >
          <MenuItem value="">Выберите адрес</MenuItem>
          {addresses.map((address) => (
            <MenuItem key={address.id} value={address.id}>
              {address.label}
            </MenuItem>
          ))}
        </TextField>
        <Button
          variant="contained"
          size="large"
          startIcon={<ShoppingCartIcon />}
          onClick={onCreateOrder}
          disabled={loading || !canCreateOrder}
        >
          Отправить заявку
        </Button>
        <Button
          variant="outlined"
          onClick={onClearCart}
          disabled={loading}
        >
          Очистить
        </Button>
      </Stack>
    </Paper>
  );
}

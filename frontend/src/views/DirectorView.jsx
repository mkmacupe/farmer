import { Suspense, lazy, useEffect, useMemo, useState } from "react";
import {
  createDirectorAddress,
  createOrder,
  deleteDirectorAddress,
  getDirectorAddresses,
  getDirectorProfile,
  getMyOrders,
  getProductCategories,
  getProductsPage,
  reverseGeo,
  repeatOrder,
  subscribeNotifications,
  updateDirectorAddress,
  updateDirectorProfile,
} from "../api.js";
import OrdersTable from "../components/OrdersTable.jsx";

import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import Paper from "@mui/material/Paper";
import Typography from "@mui/material/Typography";
import TextField from "@mui/material/TextField";
import Button from "@mui/material/Button";
import MenuItem from "@mui/material/MenuItem";
import Alert from "@mui/material/Alert";
import Snackbar from "@mui/material/Snackbar";
import Slide from "@mui/material/Slide";
import Grid from "@mui/material/Grid";
import InputAdornment from "@mui/material/InputAdornment";
import Card from "@mui/material/Card";
import CardContent from "@mui/material/CardContent";
import CardActions from "@mui/material/CardActions";
import IconButton from "@mui/material/IconButton";
import Avatar from "@mui/material/Avatar";
import Chip from "@mui/material/Chip";
import Divider from "@mui/material/Divider";
import Fab from "@mui/material/Fab";
import useMediaQuery from "@mui/material/useMediaQuery";
import { useTheme } from "@mui/material/styles";

import SearchIcon from "@mui/icons-material/Search";
import AddLocationIcon from "@mui/icons-material/AddLocation";
import MapIcon from "@mui/icons-material/Map";
import ShoppingCartIcon from "@mui/icons-material/ShoppingCart";
import DeleteIcon from "@mui/icons-material/Delete";
import SaveIcon from "@mui/icons-material/Save";
import RefreshIcon from "@mui/icons-material/Refresh";
import AddIcon from "@mui/icons-material/Add";
import RemoveIcon from "@mui/icons-material/Remove";
import LocationOnOutlinedIcon from "@mui/icons-material/LocationOnOutlined";
import ProductImage from "../components/ProductImage.jsx";
import { formatMoney } from "../utils/formatters.js";
import {
  filterLocalizedCategories,
  filterLocalizedProducts,
} from "../utils/productFilters.js";
import {
  ProductGridSkeleton,
  ProfileSkeleton,
} from "../components/LoadingSkeletons.jsx";
import InboxOutlinedIcon from "@mui/icons-material/InboxOutlined";

const AddressMapPicker = lazy(
  () => import("../components/AddressMapPicker.jsx"),
);

function formatShortDate(value) {
  if (!value) return "";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "";
  return parsed.toLocaleString("ru-RU", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function mapUrl(latitude, longitude) {
  if (latitude == null || longitude == null) {
    return "";
  }
  return `https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=17/${latitude}/${longitude}`;
}

export default function DirectorView({ token, activeSection }) {
  const [profile, setProfile] = useState(null);
  const [profileDraft, setProfileDraft] = useState({ fullName: "", phone: "" });
  const [addresses, setAddresses] = useState([]);
  const [editingAddressId, setEditingAddressId] = useState(null);
  const [addressDraft, setAddressDraft] = useState({
    label: "",
    addressLine: "",
    latitude: "",
    longitude: "",
  });

  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([]);
  const [catalogCategory, setCatalogCategory] = useState("");
  const [catalogSearchInput, setCatalogSearchInput] = useState("");
  const [catalogSearch, setCatalogSearch] = useState("");
  const catalogPageSize = 12;
  const [catalogPage, setCatalogPage] = useState(0);
  const [catalogHasNext, setCatalogHasNext] = useState(false);
  const [catalogTotalItems, setCatalogTotalItems] = useState(0);
  const [catalogLoadingMore, setCatalogLoadingMore] = useState(false);
  const [quantities, setQuantities] = useState({});
  const [selectedAddressId, setSelectedAddressId] = useState("");
  const theme = useTheme();
  const isCompactLayout = useMediaQuery(theme.breakpoints.down("md"));

  const [orders, setOrders] = useState([]);
  const [notifications, setNotifications] = useState([]);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [reverseGeoLoading, setReverseGeoLoading] = useState(false);

  const showSection = (sectionId) =>
    !activeSection || activeSection === sectionId;
  const isAddressSectionVisible = showSection("director-addresses");
  const isCatalogSectionVisible = showSection("director-catalog");
  const shouldLoadAddresses =
    isAddressSectionVisible || isCatalogSectionVisible;

  const selectedItems = useMemo(
    () =>
      products
        .map((product) => ({
          product,
          quantity: Number.parseInt(quantities[product.id] || "0", 10),
        }))
        .filter((item) => item.quantity > 0),
    [products, quantities],
  );

  const selectedProductsCount = selectedItems.length;
  const selectedUnitsCount = selectedItems.reduce(
    (sum, item) => sum + item.quantity,
    0,
  );
  const selectedTotal = selectedItems.reduce(
    (sum, item) => sum + Number(item.product.price || 0) * item.quantity,
    0,
  );
  const orderStats = useMemo(
    () => ({
      CREATED: orders.filter((order) => order.status === "CREATED").length,
      APPROVED: orders.filter((order) => order.status === "APPROVED").length,
      ASSIGNED: orders.filter((order) => order.status === "ASSIGNED").length,
      DELIVERED: orders.filter((order) => order.status === "DELIVERED").length,
    }),
    [orders],
  );
  const latestNotifications = useMemo(
    () => notifications.slice(0, 4),
    [notifications],
  );
  const recentOrders = useMemo(
    () =>
      [...orders]
        .sort((a, b) => new Date(b.createdAt || 0) - new Date(a.createdAt || 0))
        .slice(0, 3),
    [orders],
  );
  const activeOrdersCount =
    orderStats.CREATED + orderStats.APPROVED + orderStats.ASSIGNED;
  const canCreateOrder = selectedItems.length > 0 && Boolean(selectedAddressId);
  const addressHelperText = !addresses.length
    ? "Сначала добавьте адрес в разделе «Адреса»."
    : !selectedAddressId
      ? "Выберите адрес для оформления заказа."
      : "";

  const loadProfile = async (signal) => {
    const data = await getDirectorProfile(token);
    if (signal?.aborted) {
      return;
    }
    setProfile(data);
    setProfileDraft({
      fullName: data.fullName || "",
      phone: data.phone || "",
    });
  };

  const loadAddresses = async (signal) => {
    const data = await getDirectorAddresses(token);
    if (signal?.aborted) {
      return;
    }
    setAddresses(data);
    if (!selectedAddressId && data.length > 0) {
      setSelectedAddressId(String(data[0].id));
    }
  };

  const loadCatalog = async (signal) => {
    const [productsPage, categoriesData] = await Promise.all([
      getProductsPage(token, {
        category: catalogCategory || null,
        q: catalogSearch || null,
        page: 0,
        size: catalogPageSize,
      }),
      getProductCategories(token),
    ]);
    if (signal?.aborted) {
      return;
    }
    setProducts(filterLocalizedProducts(productsPage.items));
    setCatalogPage(productsPage.page);
    setCatalogHasNext(productsPage.hasNext);
    setCatalogTotalItems(productsPage.totalItems);
    setCategories(filterLocalizedCategories(categoriesData));
  };

  const loadOrders = async (signal) => {
    const data = await getMyOrders(token);
    if (signal?.aborted) {
      return;
    }
    setOrders(data);
  };

  const load = async (signal) => {
    if (signal?.aborted) {
      return;
    }
    setLoading(true);
    setError("");
    try {
      await Promise.all([loadProfile(signal), loadOrders(signal)]);
    } catch (err) {
      if (signal?.aborted) {
        return;
      }
      setError(err.message || "Не удалось загрузить данные");
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal);
    const unsubscribe = subscribeNotifications(token, {
      onNotification: (payload) => {
        setNotifications((prev) => [payload, ...prev].slice(0, 12));
      },
    });
    return () => {
      controller.abort();
      unsubscribe();
    };
  }, [token]);

  useEffect(() => {
    if (!shouldLoadAddresses) {
      return;
    }
    const controller = new AbortController();
    loadAddresses(controller.signal).catch((err) => {
      if (!controller.signal.aborted) {
        setError(err.message || "Не удалось загрузить адреса");
      }
    });
    return () => controller.abort();
  }, [token, shouldLoadAddresses]);

  useEffect(() => {
    if (!isCatalogSectionVisible) {
      return;
    }
    const controller = new AbortController();
    loadCatalog(controller.signal).catch((err) => {
      if (!controller.signal.aborted) {
        setError(err.message || "Не удалось загрузить каталог");
      }
    });
    return () => controller.abort();
  }, [token, isCatalogSectionVisible, catalogCategory, catalogSearch]);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      setCatalogSearch(catalogSearchInput.trim());
    }, 400);

    return () => window.clearTimeout(handle);
  }, [catalogSearchInput]);

  const resetAddressForm = () => {
    setEditingAddressId(null);
    setAddressDraft({
      label: "",
      addressLine: "",
      latitude: "",
      longitude: "",
    });
  };

  const handleProfileSave = async () => {
    setError("");
    setSuccess("");
    try {
      setLoading(true);
      const updated = await updateDirectorProfile(token, {
        fullName: profileDraft.fullName,
        phone: profileDraft.phone,
      });
      setProfile(updated);
      setSuccess("Профиль обновлён");
    } catch (err) {
      setError(err.message || "Не удалось обновить профиль");
    } finally {
      setLoading(false);
    }
  };

  const handleAddressSave = async () => {
    setError("");
    setSuccess("");
    if (
      !String(addressDraft.latitude || "").trim() ||
      !String(addressDraft.longitude || "").trim()
    ) {
      setError("Укажите точку доставки на карте");
      return;
    }
    const latitude = addressDraft.latitude
      ? Number(addressDraft.latitude)
      : null;
    const longitude = addressDraft.longitude
      ? Number(addressDraft.longitude)
      : null;
    const normalizedLabel = addressDraft.label.trim() || "Точка доставки";
    const normalizedAddress =
      addressDraft.addressLine.trim() ||
      `Координаты: ${addressDraft.latitude}, ${addressDraft.longitude}`;

    const payload = {
      label: normalizedLabel,
      addressLine: normalizedAddress,
      latitude,
      longitude,
    };

    try {
      setLoading(true);
      if (editingAddressId) {
        await updateDirectorAddress(token, editingAddressId, payload);
        setSuccess("Адрес обновлён");
      } else {
        await createDirectorAddress(token, payload);
        setSuccess("Адрес добавлен");
      }
      resetAddressForm();
      await loadAddresses();
    } catch (err) {
      setError(err.message || "Не удалось сохранить адрес");
    } finally {
      setLoading(false);
    }
  };

  const handleAddressEdit = (address) => {
    setEditingAddressId(address.id);
    setAddressDraft({
      label: address.label || "",
      addressLine: address.addressLine || "",
      latitude: address.latitude ?? "",
      longitude: address.longitude ?? "",
    });
  };

  const handleAddressDelete = async (id) => {
    const confirmed = window.confirm("Удалить адрес доставки?");
    if (!confirmed) {
      return;
    }
    setError("");
    try {
      setLoading(true);
      await deleteDirectorAddress(token, id);
      if (String(id) === selectedAddressId) {
        setSelectedAddressId("");
      }
      await loadAddresses();
    } catch (err) {
      setError(err.message || "Не удалось удалить адрес");
    } finally {
      setLoading(false);
    }
  };

  const handleAddressMapSelect = async (latitude, longitude) => {
    setError("");
    setAddressDraft((prev) => ({
      ...prev,
      label: prev.label || "Точка доставки",
      latitude,
      longitude,
    }));

    setReverseGeoLoading(true);
    try {
      const resolved = await reverseGeo(token, latitude, longitude);
      setAddressDraft((prev) => ({
        ...prev,
        label: prev.label || "Точка доставки",
        addressLine: resolved?.displayName || prev.addressLine,
        latitude:
          resolved?.latitude != null ? String(resolved.latitude) : latitude,
        longitude:
          resolved?.longitude != null ? String(resolved.longitude) : longitude,
      }));
    } catch (err) {
      setAddressDraft((prev) => ({
        ...prev,
        label: prev.label || "Точка доставки",
        addressLine:
          prev.addressLine || `Координаты: ${latitude}, ${longitude}`,
      }));
      setError(
        err.message || "Координаты выбраны, но адрес определить не удалось",
      );
    } finally {
      setReverseGeoLoading(false);
    }
  };

  const handleCreateOrder = async () => {
    setError("");
    setSuccess("");
    const deliveryAddressId = Number(selectedAddressId);
    if (!deliveryAddressId) {
      setError("Выберите адрес доставки");
      return;
    }

    const items = selectedItems.map((item) => ({
      productId: item.product.id,
      quantity: item.quantity,
    }));
    if (items.length === 0) {
      setError("Добавьте хотя бы одну позицию в корзину");
      return;
    }

    try {
      setLoading(true);
      await createOrder(token, { deliveryAddressId, items });
      setSuccess("Заявка на доставку создана");
      setQuantities({});
      await Promise.all([loadCatalog(), loadOrders()]);
    } catch (err) {
      setError(err.message || "Не удалось создать заказ");
    } finally {
      setLoading(false);
    }
  };

  const handleRepeatOrder = async (orderId) => {
    const confirmed = window.confirm(
      `Повторить заказ #${orderId}? Товары будут добавлены в корзину.`,
    );
    if (!confirmed) {
      return;
    }
    setError("");
    setSuccess("");
    try {
      setLoading(true);
      await repeatOrder(token, orderId);
      setSuccess(`Заказ #${orderId} повторён`);
      await Promise.all([loadCatalog(), loadOrders()]);
    } catch (err) {
      setError(err.message || "Не удалось повторить заказ");
    } finally {
      setLoading(false);
    }
  };

  const updateQuantity = (product, nextValue) => {
    const clamped = Math.max(0, Math.min(nextValue, product.stockQuantity));
    setQuantities((prev) => ({
      ...prev,
      [product.id]: clamped ? String(clamped) : "",
    }));
  };

  const handleLoadMoreProducts = async () => {
    if (!catalogHasNext || catalogLoadingMore) {
      return;
    }
    setCatalogLoadingMore(true);
    setError("");
    try {
      const nextPage = catalogPage + 1;
      const nextPageData = await getProductsPage(token, {
        category: catalogCategory || null,
        q: catalogSearch || null,
        page: nextPage,
        size: catalogPageSize,
      });
      setProducts((prev) =>
        filterLocalizedProducts([...prev, ...nextPageData.items]),
      );
      setCatalogPage(nextPageData.page);
      setCatalogHasNext(nextPageData.hasNext);
      setCatalogTotalItems(nextPageData.totalItems);
    } catch (err) {
      setError(err.message || "Не удалось загрузить ещё товары");
    } finally {
      setCatalogLoadingMore(false);
    }
  };

  const cartPanel = (
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
          onChange={(event) => setSelectedAddressId(event.target.value)}
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
          onClick={handleCreateOrder}
          disabled={loading || !canCreateOrder}
        >
          Отправить заявку
        </Button>
        <Button
          variant="outlined"
          onClick={() => setQuantities({})}
          disabled={loading}
        >
          Очистить
        </Button>
      </Stack>
    </Paper>
  );

  const HeroStat = null; // removed

  return (
    <Stack spacing={3} sx={{ pb: 4 }}>
      <Snackbar
        open={Boolean(error)}
        autoHideDuration={6000}
        onClose={() => setError("")}
        anchorOrigin={{ vertical: "top", horizontal: "left" }}
        TransitionComponent={Slide}
        TransitionProps={{ direction: "right" }}
      >
        <Alert severity="error" onClose={() => setError("")}>
          {error}
        </Alert>
      </Snackbar>
      <Snackbar
        open={Boolean(success)}
        autoHideDuration={6000}
        onClose={() => setSuccess("")}
        anchorOrigin={{ vertical: "top", horizontal: "left" }}
        TransitionComponent={Slide}
        TransitionProps={{ direction: "right" }}
      >
        <Alert severity="success" onClose={() => setSuccess("")}>
          {success}
        </Alert>
      </Snackbar>

      {showSection("director-profile") && (
        <Box
          sx={{
            p: { xs: 2.5, md: 3 },
            borderRadius: 3,
            border: "1px solid",
            borderColor: "divider",
            bgcolor: "background.paper",
          }}
        >
          <Grid container spacing={3} alignItems="center">
            <Grid size={{ xs: 12, md: 6 }}>
              <Typography variant="overline" sx={{ color: "text.secondary" }}>
                Панель директора
              </Typography>
              <Typography
                variant="h5"
                fontWeight={600}
                sx={{ mt: 0.5, mb: 0.5, letterSpacing: "-0.02em" }}
              >
                Закупки без лишних шагов
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Сводка по заказам, текущая корзина и уведомления.
              </Typography>
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                <Box
                  sx={{
                    flex: 1,
                    p: 2,
                    borderRadius: 2.5,
                    bgcolor: "#f0fdf4",
                    border: "1px solid #dcfce7",
                  }}
                >
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    fontWeight={500}
                  >
                    Корзина
                  </Typography>
                  <Typography
                    variant="h5"
                    fontWeight={600}
                    sx={{ mt: 0.5, color: "primary.main" }}
                  >
                    {formatMoney(selectedTotal)}{" "}
                    <Typography
                      component="span"
                      variant="body2"
                      fontWeight={500}
                    >
                      BYN
                    </Typography>
                  </Typography>
                </Box>
                <Box
                  sx={{
                    flex: 1,
                    p: 2,
                    borderRadius: 2.5,
                    bgcolor: "#f4f4f5",
                    border: "1px solid #e4e4e7",
                  }}
                >
                  <Typography
                    variant="caption"
                    color="text.secondary"
                    fontWeight={500}
                  >
                    Активные заказы
                  </Typography>
                  <Typography variant="h5" fontWeight={600} sx={{ mt: 0.5 }}>
                    {activeOrdersCount}
                  </Typography>
                </Box>
              </Stack>
            </Grid>
          </Grid>

          <Box
            sx={{
              mt: 3,
              pt: 2.5,
              borderTop: "1px solid",
              borderColor: "divider",
            }}
          >
            <Typography
              variant="overline"
              sx={{ color: "text.secondary", mb: 1.5, display: "block" }}
            >
              Последние заказы
            </Typography>
            {recentOrders.length > 0 ? (
              <Stack spacing={1.5}>
                {recentOrders.map((order) => (
                  <Box
                    key={order.id}
                    sx={{
                      p: 1.5,
                      borderRadius: 2,
                      border: "1px solid",
                      borderColor: "divider",
                      bgcolor: "background.default",
                    }}
                  >
                    <Stack
                      direction="row"
                      alignItems="center"
                      justifyContent="space-between"
                    >
                      <Typography variant="subtitle2" fontWeight={600}>
                        Заказ #{order.id}
                      </Typography>
                      <Chip
                        label={
                          order.status === "DELIVERED"
                            ? "Доставлен"
                            : order.status === "ASSIGNED"
                              ? "На маршруте"
                              : order.status === "APPROVED"
                                ? "Одобрен"
                                : "Создан"
                        }
                        size="small"
                        variant="outlined"
                      />
                    </Stack>
                    <Typography variant="body2" color="text.secondary" noWrap>
                      {order.deliveryAddressText || "Адрес не указан"}
                    </Typography>
                    <Stack
                      direction="row"
                      justifyContent="space-between"
                      sx={{ mt: 0.5 }}
                    >
                      <Typography variant="caption" color="text.secondary">
                        {formatShortDate(order.createdAt) || "—"}
                      </Typography>
                      <Typography variant="subtitle2" fontWeight={600}>
                        {formatMoney(order.totalAmount)} BYN
                      </Typography>
                    </Stack>
                  </Box>
                ))}
              </Stack>
            ) : (
              <Typography variant="body2" color="text.secondary">
                Пока нет заказов. Вы можете сформировать заказ из каталога.
              </Typography>
            )}
          </Box>
        </Box>
      )}

      {!!latestNotifications.length && (
        <Box
          sx={{
            p: 2.5,
            borderRadius: 2.5,
            bgcolor: "#f4f4f5",
            border: "1px solid #e4e4e7",
          }}
        >
          <Typography
            variant="overline"
            sx={{ color: "text.secondary", mb: 1.5, display: "block" }}
          >
            Последние уведомления
          </Typography>
          <Stack spacing={1}>
            {latestNotifications.map((notification, index) => (
              <Box
                key={`${notification.createdAt || "n/a"}-${index}`}
                sx={{
                  p: 1.5,
                  borderRadius: 2,
                  bgcolor: "background.paper",
                  border: "1px solid",
                  borderColor: "divider",
                  transition: "border-color 0.15s ease",
                  "&:hover": { borderColor: "#d4d4d8" },
                }}
              >
                <Typography
                  variant="body2"
                  fontWeight={600}
                  sx={{ fontSize: "0.8125rem" }}
                >
                  {notification.title || "Событие"}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {notification.message || "Без описания"}
                </Typography>
              </Box>
            ))}
          </Stack>
        </Box>
      )}
      {showSection("director-profile") && (
        <Box
          sx={{
            p: 3,
            borderRadius: 2.5,
            bgcolor: "background.paper",
            border: "1px solid",
            borderColor: "divider",
          }}
          id="director-profile"
        >
          <Typography variant="h6" fontWeight={600} sx={{ mb: 2.5 }}>
            Профиль директора
          </Typography>

          {loading && !profile && <ProfileSkeleton />}

          {profile && (
            <Box>
              <Grid container spacing={2} mb={3}>
                <Grid size={{ xs: 12, sm: 4 }}>
                  <TextField
                    label="ФИО"
                    fullWidth
                    value={profileDraft.fullName}
                    onChange={(event) =>
                      setProfileDraft((prev) => ({
                        ...prev,
                        fullName: event.target.value,
                      }))
                    }
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 4 }}>
                  <TextField
                    label="Телефон"
                    fullWidth
                    value={profileDraft.phone}
                    onChange={(event) =>
                      setProfileDraft((prev) => ({
                        ...prev,
                        phone: event.target.value,
                      }))
                    }
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 4 }}>
                  <TextField
                    label="Юр. лицо"
                    fullWidth
                    disabled
                    value={profile.legalEntityName || ""}
                    helperText="Нельзя изменить"
                  />
                </Grid>
              </Grid>
              <Button
                variant="contained"
                onClick={handleProfileSave}
                disabled={loading}
                startIcon={<SaveIcon />}
              >
                Сохранить профиль
              </Button>
            </Box>
          )}
        </Box>
      )}

      {showSection("director-addresses") && (
        <Box
          sx={{
            p: 3,
            borderRadius: 2.5,
            bgcolor: "background.paper",
            border: "1px solid",
            borderColor: "divider",
          }}
          id="director-addresses"
        >
          <Stack
            direction={{ xs: "column", sm: "row" }}
            alignItems={{ xs: "flex-start", sm: "center" }}
            justifyContent="space-between"
            mb={2.5}
            spacing={1}
          >
            <Typography variant="h6" fontWeight={600}>
              Адреса доставки
            </Typography>
            {editingAddressId && (
              <Typography variant="caption" color="primary">
                Редактирование #{editingAddressId}
              </Typography>
            )}
          </Stack>

          <Box sx={{ bgcolor: "action.hover", p: 2, borderRadius: 3, mb: 3 }}>
            <Grid container spacing={2} alignItems="center">
              <Grid size={{ xs: 12, sm: 4 }}>
                <TextField
                  label="Название точки"
                  fullWidth
                  size="small"
                  value={addressDraft.label}
                  onChange={(event) =>
                    setAddressDraft((prev) => ({
                      ...prev,
                      label: event.target.value,
                    }))
                  }
                  placeholder="Например: Центральный магазин"
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Suspense
                  fallback={
                    <Box
                      sx={{ p: 2, bgcolor: "action.hover", borderRadius: 2 }}
                    >
                      Загрузка карты...
                    </Box>
                  }
                >
                  <AddressMapPicker
                    latitude={addressDraft.latitude}
                    longitude={addressDraft.longitude}
                    onSelect={handleAddressMapSelect}
                  />
                </Suspense>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Stack spacing={0.5}>
                  <Typography variant="caption" color="text.secondary">
                    Адрес:{" "}
                    {addressDraft.addressLine || "Выберите точку на карте"}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Координаты:{" "}
                    {addressDraft.latitude && addressDraft.longitude
                      ? `${addressDraft.latitude}, ${addressDraft.longitude}`
                      : "—"}
                  </Typography>
                  {reverseGeoLoading && (
                    <Typography variant="caption" color="text.secondary">
                      Определяем адрес по координатам...
                    </Typography>
                  )}
                </Stack>
              </Grid>
              <Grid size={{ xs: 12 }}>
                <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                  <Button
                    variant="contained"
                    onClick={handleAddressSave}
                    disabled={loading}
                    startIcon={<AddLocationIcon />}
                  >
                    {editingAddressId ? "Сохранить" : "Добавить"}
                  </Button>
                  {editingAddressId && (
                    <Button onClick={resetAddressForm} disabled={loading}>
                      Отмена
                    </Button>
                  )}
                </Stack>
              </Grid>
            </Grid>
          </Box>

          {!addresses.length ? (
            <Stack alignItems="center" spacing={2} sx={{ py: 4 }}>
              <LocationOnOutlinedIcon
                sx={{ fontSize: 48, color: "text.disabled" }}
              />
              <Typography color="text.secondary">
                Пока нет адресов доставки
              </Typography>
              <Typography variant="caption" color="text.disabled">
                Добавьте адрес с помощью формы выше
              </Typography>
            </Stack>
          ) : (
            <Grid container spacing={2}>
              {addresses.map((address) => (
                <Grid size={{ xs: 12, md: 6 }} key={address.id}>
                  <Card variant="outlined" sx={{ height: "100%" }}>
                    <CardContent>
                      <Stack
                        direction="row"
                        spacing={2}
                        alignItems="flex-start"
                      >
                        <Avatar
                          sx={{
                            bgcolor: "primary.light",
                            color: "primary.main",
                          }}
                        >
                          <LocationOnOutlinedIcon />
                        </Avatar>
                        <Box sx={{ flexGrow: 1 }}>
                          <Typography variant="subtitle1" fontWeight={600}>
                            {address.label}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {address.addressLine}
                          </Typography>
                          <Stack
                            direction="row"
                            spacing={1}
                            sx={{ mt: 2, flexWrap: "wrap" }}
                          >
                            {mapUrl(address.latitude, address.longitude) ? (
                              <Button
                                href={mapUrl(
                                  address.latitude,
                                  address.longitude,
                                )}
                                target="_blank"
                                rel="noopener noreferrer"
                                startIcon={<MapIcon />}
                                size="small"
                                variant="outlined"
                              >
                                Карта
                              </Button>
                            ) : (
                              <Chip label="Нет координат" size="small" />
                            )}
                            <Button
                              size="small"
                              color="error"
                              variant="outlined"
                              startIcon={<DeleteIcon />}
                              onClick={() => handleAddressDelete(address.id)}
                            >
                              Удалить
                            </Button>
                          </Stack>
                        </Box>
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>
          )}
        </Box>
      )}
      {showSection("director-catalog") && (
        <Box
          sx={{
            p: 3,
            borderRadius: 2.5,
            bgcolor: "background.paper",
            border: "1px solid",
            borderColor: "divider",
          }}
          id="director-catalog"
        >
          <Stack
            direction={{ xs: "column", sm: "row" }}
            alignItems={{ xs: "flex-start", sm: "center" }}
            justifyContent="space-between"
            mb={2.5}
            spacing={1}
          >
            <Typography variant="h6" fontWeight={600}>
              Каталог и корзина
            </Typography>
          </Stack>

          <Grid container spacing={2} mb={3} alignItems="center">
            <Grid size={{ xs: 12, sm: 4 }}>
              <TextField
                select
                label="Категория"
                fullWidth
                size="small"
                value={catalogCategory}
                onChange={(event) => setCatalogCategory(event.target.value)}
              >
                <MenuItem value="">Все категории</MenuItem>
                {categories.map((cat) => (
                  <MenuItem key={cat} value={cat}>
                    {cat}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 8 }}>
              <TextField
                label="Поиск товаров"
                fullWidth
                size="small"
                value={catalogSearchInput}
                onChange={(event) => setCatalogSearchInput(event.target.value)}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon color="action" />
                    </InputAdornment>
                  ),
                }}
              />
            </Grid>
          </Grid>

          <Grid container spacing={3}>
            {isCompactLayout && <Grid size={{ xs: 12 }}>{cartPanel}</Grid>}
            <Grid size={{ xs: 12, md: 8 }}>
              <Grid container spacing={2}>
                {products.map((product) => {
                  const quantity =
                    Number.parseInt(quantities[product.id] || "0", 10) || 0;
                  return (
                    <Grid size={{ xs: 12, sm: 6, lg: 4 }} key={product.id}>
                      <Card
                        sx={{
                          height: "100%",
                          display: "flex",
                          flexDirection: "column",
                        }}
                      >
                        <ProductImage
                          src={product.photoUrl}
                          alt={product.name}
                          height={160}
                        />
                        <CardContent sx={{ flexGrow: 1 }}>
                          <Typography
                            variant="subtitle1"
                            fontWeight={600}
                            gutterBottom
                          >
                            {product.name}
                          </Typography>
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            display="block"
                            gutterBottom
                          >
                            {product.description || "Описание отсутствует"}
                          </Typography>
                          <Stack
                            direction="row"
                            spacing={1}
                            alignItems="center"
                            mt={1.5}
                          >
                            <Chip label={product.category} size="small" />
                            <Typography
                              variant="subtitle1"
                              fontWeight={800}
                              color="primary"
                            >
                              {formatMoney(product.price)} BYN
                            </Typography>
                          </Stack>
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            display="block"
                            sx={{ mt: 1 }}
                          >
                            Остаток: {product.stockQuantity}
                          </Typography>
                        </CardContent>
                        <CardActions
                          sx={{
                            px: 2,
                            pb: 2,
                            pt: 0,
                            flexDirection: "column",
                            alignItems: "stretch",
                            gap: 1,
                          }}
                        >
                          <Stack
                            direction="row"
                            spacing={1}
                            alignItems="center"
                            justifyContent="space-between"
                          >
                            <IconButton
                              size="small"
                              onClick={() =>
                                updateQuantity(product, quantity - 1)
                              }
                              disabled={quantity <= 0}
                              aria-label="Уменьшить количество"
                            >
                              <RemoveIcon fontSize="small" />
                            </IconButton>
                            <Typography fontWeight={600}>{quantity}</Typography>
                            <IconButton
                              size="small"
                              onClick={() =>
                                updateQuantity(product, quantity + 1)
                              }
                              disabled={quantity >= product.stockQuantity}
                              aria-label="Увеличить количество"
                            >
                              <AddIcon fontSize="small" />
                            </IconButton>
                            <Button
                              variant={quantity > 0 ? "contained" : "outlined"}
                              size="small"
                              onClick={() =>
                                updateQuantity(
                                  product,
                                  quantity > 0 ? quantity : 1,
                                )
                              }
                            >
                              {quantity > 0 ? "В корзине" : "В корзину"}
                            </Button>
                          </Stack>
                        </CardActions>
                      </Card>
                    </Grid>
                  );
                })}
                {catalogHasNext && (
                  <Grid size={{ xs: 12 }}>
                    <Stack alignItems="center" sx={{ mt: 1 }} spacing={1}>
                      <Button
                        variant="outlined"
                        onClick={handleLoadMoreProducts}
                        disabled={catalogLoadingMore}
                      >
                        {catalogLoadingMore ? "Загрузка..." : "Показать ещё"}
                      </Button>
                      <Typography variant="caption" color="text.secondary">
                        Загружено {products.length} из {catalogTotalItems}
                      </Typography>
                    </Stack>
                  </Grid>
                )}
                {loading && !products.length && (
                  <ProductGridSkeleton count={6} />
                )}
                {!loading && !products.length && (
                  <Grid size={{ xs: 12 }}>
                    <Stack alignItems="center" spacing={2} sx={{ py: 6 }}>
                      <InboxOutlinedIcon
                        sx={{ fontSize: 64, color: "text.disabled" }}
                      />
                      <Typography color="text.secondary">
                        Каталог пуст
                      </Typography>
                      <Typography variant="caption" color="text.disabled">
                        Товары появятся после добавления менеджером
                      </Typography>
                    </Stack>
                  </Grid>
                )}
              </Grid>
            </Grid>

            {!isCompactLayout && (
              <Grid size={{ xs: 12, md: 4 }}>{cartPanel}</Grid>
            )}
          </Grid>
        </Box>
      )}

      {showSection("director-orders") && (
        <Box
          sx={{
            p: 3,
            borderRadius: 2.5,
            bgcolor: "background.paper",
            border: "1px solid",
            borderColor: "divider",
          }}
          id="director-orders"
        >
          <Typography variant="h6" fontWeight={600} gutterBottom>
            История заказов
          </Typography>
          <OrdersTable
            orders={orders}
            loading={loading && !orders.length}
            showCustomer={false}
            emptyText="История заказов пуста."
            actionRenderer={(order) => (
              <Button
                variant="outlined"
                size="small"
                startIcon={<RefreshIcon />}
                onClick={() => handleRepeatOrder(order.id)}
                disabled={loading}
              >
                Повторить
              </Button>
            )}
          />
        </Box>
      )}

      <Fab
        variant="extended"
        color="secondary"
        onClick={handleCreateOrder}
        disabled={loading || !canCreateOrder}
        sx={{
          position: "fixed",
          right: 16,
          bottom: { xs: 92, md: 24 },
          display: { xs: "flex", md: "none" },
        }}
      >
        <ShoppingCartIcon sx={{ mr: 1 }} />
        {formatMoney(selectedTotal)} BYN
      </Fab>
    </Stack>
  );
}

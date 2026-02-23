import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import LocationOnOutlinedIcon from '@mui/icons-material/LocationOnOutlined';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import GridViewOutlinedIcon from '@mui/icons-material/GridViewOutlined';
import AssignmentOutlinedIcon from '@mui/icons-material/AssignmentOutlined';
import Inventory2OutlinedIcon from '@mui/icons-material/Inventory2Outlined';
import GroupOutlinedIcon from '@mui/icons-material/GroupOutlined';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import RouteOutlinedIcon from '@mui/icons-material/RouteOutlined';
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined';

const ICON_MAP = {
  'director-profile': PersonOutlineIcon,
  'director-addresses': LocationOnOutlinedIcon,
  'director-catalog': ShoppingCartOutlinedIcon,
  'director-orders': HistoryOutlinedIcon,
  'manager-dashboard': GridViewOutlinedIcon,
  'manager-orders': AssignmentOutlinedIcon,
  'manager-products': Inventory2OutlinedIcon,
  'manager-users': GroupOutlinedIcon,
  'manager-reports': DescriptionOutlinedIcon,
  'logistic-orders': RouteOutlinedIcon,
  'driver-orders': LocalShippingOutlinedIcon
};

export function itemIcon(id, props = {}) {
  const Icon = ICON_MAP[id] || AssignmentOutlinedIcon;
  return <Icon fontSize="small" {...props} />;
}

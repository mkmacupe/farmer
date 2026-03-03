import { NAV_ITEMS, roleLabel, sectionLabel } from './navigationData.js';
import { itemIcon as resolveItemIcon } from './navigationIcons.jsx';

export { NAV_ITEMS, roleLabel, sectionLabel };

export function itemIcon(id, props) {
  return resolveItemIcon(id, props);
}

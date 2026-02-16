import React from 'react';
import LabShell from './components/LabShell';
import VariantA from './variants/VariantA';
import VariantB from './variants/VariantB';
import VariantC from './variants/VariantC';
import VariantD from './variants/VariantD';
import VariantE from './variants/VariantE';
import VariantF from './variants/VariantF';
import VariantG from './variants/VariantG';
import VariantH from './variants/VariantH';

const variants = [
  { name: 'Hierarchy Focus', component: VariantA },
  { name: 'Split Layout', component: VariantB },
  { name: 'High Density', component: VariantC },
  { name: 'Task Flow', component: VariantD },
  { name: 'Brand Expressive', component: VariantE },
  { name: 'Pro SaaS (Dark)', component: VariantF },
  { name: 'Kanban Board', component: VariantG },
  { name: 'Ultra Minimal', component: VariantH },
];

export default function DesignLabPage() {
  return <LabShell variants={variants} />;
}

import React from 'react';
import LabShell from './components/LabShell';
import VariantA from './variants/VariantA';
import VariantB from './variants/VariantB';
import VariantC from './variants/VariantC';
import VariantD from './variants/VariantD';
import VariantE from './variants/VariantE';
import VariantF from './variants/VariantF';

const variants = [
  { name: 'Спокойный обзор', component: VariantA },
  { name: 'Стол решений', component: VariantB },
  { name: 'Плотный реестр', component: VariantC },
  { name: 'Поток задач', component: VariantD },
  { name: 'Пульс клиентов', component: VariantE },
  { name: 'График доставок', component: VariantF },
];

export default function DesignLabPage() {
  return <LabShell variants={variants} />;
}

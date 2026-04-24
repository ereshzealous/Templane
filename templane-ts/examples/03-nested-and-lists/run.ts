// 03 — Nested objects, enums, lists: an order receipt validated before render.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { compile } from '../../dist/handlebars-templane';

const source = readFileSync(join(__dirname, 'order.templane'), 'utf8');
const tmpl = compile(source, 'order');

const output = tmpl.render({
  customer: { name: 'Jordan Shah', tier: 'pro' },
  items: [
    { sku: 'BOOK-042', qty: 2 },
    { sku: 'PEN-003',  qty: 5 },
    { sku: 'MUG-099',  qty: 1 },
  ],
  total_cents: 5993,
});

process.stdout.write(output);

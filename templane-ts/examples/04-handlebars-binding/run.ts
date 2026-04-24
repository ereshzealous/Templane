// 04 — Handlebars binding: real Handlebars features on validated data.
//
// Registers an "eq" helper so templates can dispatch on enum values,
// then renders a notification email. The type-check still fires before
// any Handlebars rendering — bad data never hits the engine.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import Handlebars from 'handlebars';
import { compile } from '../../dist/handlebars-templane';

Handlebars.registerHelper('eq', (a: unknown, b: unknown) => a === b);

const source = readFileSync(join(__dirname, 'email.templane'), 'utf8');
const tmpl = compile(source, 'email');

const output = tmpl.render({
  user: { name: 'Lin', is_new: true },
  unread_count: 4,
  notifications: [
    { kind: 'mention', source: 'alex' },
    { kind: 'reply',   source: 'Priya' },
    { kind: 'follow',  source: 'design-team' },
    { kind: 'mention', source: 'jamie' },
  ],
});

process.stdout.write(output);

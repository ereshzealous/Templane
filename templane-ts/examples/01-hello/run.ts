// 01 — Hello: compile a .templane file and render with handlebars-templane.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { compile } from '../../dist/handlebars-templane';

const source = readFileSync(join(__dirname, 'greeting.templane'), 'utf8');
const tmpl = compile(source, 'greeting');

const output = tmpl.render({
  name: 'Arya',
  temperature_c: 22,
  is_morning: true,
});

process.stdout.write(output);

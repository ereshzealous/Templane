// 02 — Validation errors: surface every error code in one pass.
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  compile,
  TemplaneHandlebarsError,
} from '../../dist/handlebars-templane';

const source = readFileSync(join(__dirname, 'profile.templane'), 'utf8');
const tmpl = compile(source, 'profile');

// Intentionally bad data — trips every error code at once.
const badData = {
  // "name" missing          → missing_required_field
  age: 'thirty',              // type_mismatch (expected number)
  role: 'superuser',          // invalid_enum_value
  rol: 'admin',               // unknown_field → did_you_mean "role"
};

try {
  tmpl.render(badData);
} catch (err) {
  if (!(err instanceof TemplaneHandlebarsError)) throw err;
  console.log(`render refused: ${err.errors.length} error(s)\n`);
  for (const e of err.errors) {
    console.log(`  [${e.code}] ${e.field}: ${e.message}`);
  }
}

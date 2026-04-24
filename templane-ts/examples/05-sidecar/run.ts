// 05 — Sidecar mode: keep your .hbs files, add a schema beside them.
//
// release-notes.hbs is plain Handlebars — no Templane syntax. The schema
// next to it (release-notes.schema.yaml) declares the data contract
// and points back to the .hbs via `body: ./release-notes.hbs`.
import { join } from 'node:path';
import { compileFromPath, TemplaneHandlebarsError } from '../../dist/handlebars-templane';

async function main() {
  const tmpl = await compileFromPath(
    join(__dirname, 'release-notes.schema.yaml'),
  );

  // --- Good data: renders cleanly ---
  console.log('--- Good data: renders cleanly ---');
  console.log(tmpl.render({
    product: 'Templane',
    version: 'v0.1.0',
    release_date: '2026-04-24',
    commit_sha: '9183711',
    repo_url: 'https://github.com/ereshzealous/Templane',
    breaking_changes: false,
    highlights: [
      'Sidecar mode (SPEC 1.1): schemas can reference external bodies',
      'Cross-impl conformance: 5 × 40/40 fixtures green',
      'Engine bindings for Jinja, Handlebars, FreeMarker',
    ],
    changes: [
      { category: 'Added', items: ['`compileFromPath()` for sidecar schemas', 'Per-language examples (20+)'] },
      { category: 'Fixed', items: ['Go module path now matches repo URL', 'FreeMarker shadowJar task deps'] },
    ],
    contributors: ['ereshzealous'],
  }));

  // --- Bad data: type-check refuses ---
  console.log('--- Bad data: type-check refuses ---');
  try {
    await tmpl.render({
      product: 'Templane',
      version: 2,                           // type_mismatch
      release_date: '2026-04-24',
      commit_sha: '9183711',
      repo_url: 'https://...',
      // breaking_changes missing           → missing_required_field
      highlights: ['...'],
      changes: [
        { category: 'Added', items: 'just one thing' }, // items not a list
      ],
      contributors: ['erish'],
    });
  } catch (err) {
    if (!(err instanceof TemplaneHandlebarsError)) throw err;
    console.log(`render refused: ${err.errors.length} error(s)\n`);
    for (const e of err.errors) {
      console.log(`  [${e.code}] ${e.field}: ${e.message}`);
    }
  }
}

main().catch((err) => { console.error(err); process.exit(1); });

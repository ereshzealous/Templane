import { test } from 'node:test';
import * as assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { compile, compileFromPath, TemplaneHandlebarsError } from '../src/handlebars-templane';

test('compile + render basic template', () => {
  const source = [
    'name:',
    '  type: string',
    '  required: true',
    '---',
    'Hello {{name}}!',
  ].join('\n');
  const tmpl = compile(source, 'greeting');
  assert.equal(tmpl.render({ name: 'Alice' }), 'Hello Alice!');
});

test('render throws on type mismatch', () => {
  const source = [
    'age:',
    '  type: number',
    '  required: true',
    '---',
    '{{age}}',
  ].join('\n');
  const tmpl = compile(source, 'age');
  assert.throws(
    () => tmpl.render({ age: 'old' }),
    (err: Error) => err instanceof TemplaneHandlebarsError,
  );
});

test('render throws on missing required field', () => {
  const source = [
    'name:',
    '  type: string',
    '  required: true',
    '---',
    'Hi {{name}}',
  ].join('\n');
  const tmpl = compile(source, 'greet');
  assert.throws(() => tmpl.render({}), TemplaneHandlebarsError);
});

test('check returns errors without throwing', () => {
  const source = [
    'count:',
    '  type: number',
    '  required: true',
    '---',
    '{{count}}',
  ].join('\n');
  const tmpl = compile(source, 'counter');
  const errors = tmpl.check({ count: 'five' });
  assert.equal(errors.length, 1);
  assert.equal(errors[0].code, 'type_mismatch');
});

test('check returns empty array on valid data', () => {
  const source = 'name:\n  type: string\n  required: true\n---\n{{name}}';
  const tmpl = compile(source, 't');
  assert.deepEqual(tmpl.check({ name: 'Alice' }), []);
});

test('compile throws on invalid schema', () => {
  assert.throws(
    () => compile('- a list\n---\nbody', 'bad'),
    /Schema parse error/,
  );
});

test('compile throws on missing body separator', () => {
  assert.throws(
    () => compile('name:\n  type: string\n  required: true', 'noBody'),
    /body/,
  );
});

test('handlebars each helper renders list', () => {
  const source = [
    'items:',
    '  type: list',
    '  items:',
    '    type: string',
    '  required: true',
    '---',
    '{{#each items}}- {{this}}\n{{/each}}',
  ].join('\n');
  const tmpl = compile(source, 'list');
  assert.equal(tmpl.render({ items: ['a', 'b', 'c'] }), '- a\n- b\n- c\n');
});

test('handlebars if helper evaluates conditional', () => {
  const source = [
    'active:',
    '  type: boolean',
    '  required: true',
    '---',
    '{{#if active}}ACTIVE{{else}}INACTIVE{{/if}}',
  ].join('\n');
  const tmpl = compile(source, 'status');
  assert.equal(tmpl.render({ active: true }), 'ACTIVE');
  assert.equal(tmpl.render({ active: false }), 'INACTIVE');
});

test('handlebars HTML-escapes by default', () => {
  const source = 'html:\n  type: string\n  required: true\n---\n{{html}}';
  const tmpl = compile(source, 'esc');
  const output = tmpl.render({ html: '<b>&</b>' });
  assert.ok(output.includes('&lt;'));
  assert.ok(output.includes('&gt;'));
  assert.ok(output.includes('&amp;'));
  assert.ok(!output.includes('<b>'));
});

test('triple-stache disables escaping', () => {
  const source = 'html:\n  type: string\n  required: true\n---\n{{{html}}}';
  const tmpl = compile(source, 'raw');
  assert.equal(tmpl.render({ html: '<b>hi</b>' }), '<b>hi</b>');
});

test('schema is exposed on the template object', () => {
  const source = 'name:\n  type: string\n  required: true\n---\nHi';
  const tmpl = compile(source, 'my-id');
  assert.equal(tmpl.schema.id, 'my-id');
  assert.equal(tmpl.schema.fields.name.required, true);
});

// ---------------------------------------------------------------------------
// Sidecar mode (SPEC 1.1 §4.3) — schema references external body file
// ---------------------------------------------------------------------------

test('compileFromPath loads sidecar body and renders', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'tplane-hbs-'));
  try {
    await writeFile(join(dir, 'email.hbs'), 'Hi {{name}}!');
    await writeFile(
      join(dir, 'email.templane'),
      'body: ./email.hbs\nname:\n  type: string\n  required: true\n',
    );
    const tmpl = await compileFromPath(join(dir, 'email.templane'));
    assert.equal(tmpl.render({ name: 'Lin' }), 'Hi Lin!');
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test('compileFromPath type-checks sidecar data', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'tplane-hbs-'));
  try {
    await writeFile(join(dir, 'age.hbs'), 'You are {{age}}');
    await writeFile(
      join(dir, 'age.templane'),
      'body: ./age.hbs\nage:\n  type: number\n  required: true\n',
    );
    const tmpl = await compileFromPath(join(dir, 'age.templane'));
    assert.throws(() => tmpl.render({ age: 'forever' }), TemplaneHandlebarsError);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test('compileFromPath works on embedded (--- separator) schema too', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'tplane-hbs-'));
  try {
    await writeFile(
      join(dir, 'embedded.templane'),
      'name:\n  type: string\n  required: true\n---\nHello {{name}}!',
    );
    const tmpl = await compileFromPath(join(dir, 'embedded.templane'));
    assert.equal(tmpl.render({ name: 'World' }), 'Hello World!');
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test('compileFromPath throws when sidecar body file is missing', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'tplane-hbs-'));
  try {
    await writeFile(
      join(dir, 'broken.templane'),
      'body: ./nope.hbs\nname:\n  type: string\n  required: true\n',
    );
    await assert.rejects(() => compileFromPath(join(dir, 'broken.templane')), /body file/i);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

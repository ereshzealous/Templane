// Tests for SPEC §4.3 — sidecar mode.
import { test } from 'node:test';
import * as assert from 'node:assert/strict';
import * as fs from 'fs/promises';
import * as os from 'os';
import * as path from 'path';
import { parse, loadFromPath } from '../src/schema-parser';

async function mkTmpDir(prefix: string): Promise<string> {
  return await fs.mkdtemp(path.join(os.tmpdir(), prefix));
}

test('sidecar: body path recognized, not treated as field', () => {
  const yaml = 'body: ./email.jinja\nname:\n  type: string\n  required: true\n';
  const result = parse(yaml, 'sidecar-basic');
  assert.ok('schema' in result);
  assert.equal(result.bodyPath, './email.jinja');
  // body: key must NOT be treated as a field
  assert.ok(!('body' in result.schema.fields));
  assert.deepEqual(Object.keys(result.schema.fields), ['name']);
});

test('sidecar: explicit engine wins', () => {
  const yaml = 'body: ./t.hbs\nengine: jinja\nuser: {type: string, required: true}\n';
  const result = parse(yaml, 'engine-explicit');
  assert.ok('schema' in result);
  assert.equal(result.engine, 'jinja');
});

test('sidecar: engine inferred from extension', () => {
  const cases: [string, string][] = [
    ['./t.jinja',  'jinja'],
    ['./t.hbs',    'handlebars'],
    ['./t.ftl',    'freemarker'],
    ['./t.tmpl',   'gotemplate'],
    ['./t.md',     'markdown'],
    ['./t.html',   'html-raw'],
  ];
  for (const [bodyPath, expectedEngine] of cases) {
    const yaml = `body: ${bodyPath}\nname: {type: string, required: true}\n`;
    const result = parse(yaml, 'inferred');
    assert.ok('schema' in result, `unexpected error for ${bodyPath}`);
    assert.equal(result.engine, expectedEngine, `for ${bodyPath}`);
  }
});

test('sidecar: no extension, no engine inference', () => {
  const yaml = 'body: ./t\nname: {type: string, required: true}\n';
  const result = parse(yaml, 'no-ext');
  assert.ok('schema' in result);
  assert.equal(result.engine, undefined); // no extension → no inference, no error
});

test('sidecar: unknown engine rejected', () => {
  const yaml = 'body: ./t.jinja\nengine: mystery\nname: {type: string, required: true}\n';
  const result = parse(yaml, 'bad-engine');
  assert.ok('error' in result);
  assert.ok(result.error.includes('mystery'));
});

test('sidecar: absolute path rejected', () => {
  const yaml = 'body: /etc/passwd\nname: {type: string, required: true}\n';
  const result = parse(yaml, 'abs-path');
  assert.ok('error' in result);
  assert.ok(result.error.toLowerCase().includes('relative'));
});

test('sidecar: parent-escape path rejected', () => {
  const yaml = 'body: ../../../etc/passwd\nname: {type: string, required: true}\n';
  const result = parse(yaml, 'escape');
  assert.ok('error' in result);
});

test('sidecar: body: and --- conflict rejected', () => {
  const yaml = 'body: ./a.jinja\nname: {type: string, required: true}\n---\nHello\n';
  const result = parse(yaml, 'conflict');
  assert.ok('error' in result);
  assert.ok(result.error.toLowerCase().includes('both'));
});

test('embedded mode unchanged by 1.1', () => {
  const yaml = 'name:\n  type: string\n  required: true\n---\nHello {{ name }}!\n';
  const result = parse(yaml, 'embedded');
  assert.ok('schema' in result);
  assert.equal(result.bodyPath, undefined);
  assert.equal(result.engine, undefined);
  assert.equal(result.body, 'Hello {{ name }}!\n');
});

test('check-only mode (no body, no bodyPath)', () => {
  const yaml = 'name:\n  type: string\n  required: true\n';
  const result = parse(yaml, 'check-only');
  assert.ok('schema' in result);
  assert.equal(result.body, undefined);
  assert.equal(result.bodyPath, undefined);
});

test('loadFromPath resolves sidecar body file', async () => {
  const tmp = await mkTmpDir('templane-sidecar-');
  try {
    const bodyFile = path.join(tmp, 'greeting.jinja');
    await fs.writeFile(bodyFile, 'Hello {{ name }}!\n');
    const schemaFile = path.join(tmp, 'greeting.templane');
    await fs.writeFile(
      schemaFile,
      'body: ./greeting.jinja\nname: {type: string, required: true}\n',
    );

    const result = await loadFromPath(schemaFile);
    assert.ok('schema' in result);
    assert.equal(result.bodyPath, './greeting.jinja');
    assert.equal(result.body, 'Hello {{ name }}!\n');
    assert.equal(result.engine, 'jinja');
  } finally {
    await fs.rm(tmp, { recursive: true, force: true });
  }
});

test('loadFromPath: missing body file returns error', async () => {
  const tmp = await mkTmpDir('templane-sidecar-missing-');
  try {
    const schemaFile = path.join(tmp, 'broken.templane');
    await fs.writeFile(
      schemaFile,
      'body: ./nope.jinja\nname: {type: string, required: true}\n',
    );
    const result = await loadFromPath(schemaFile);
    assert.ok('error' in result);
    assert.ok(result.error.toLowerCase().includes('body file'));
  } finally {
    await fs.rm(tmp, { recursive: true, force: true });
  }
});

test('loadFromPath: embedded schema works like parse()', async () => {
  const tmp = await mkTmpDir('templane-sidecar-embedded-');
  try {
    const schemaFile = path.join(tmp, 'embedded.templane');
    await fs.writeFile(
      schemaFile,
      'name: {type: string, required: true}\n---\nHi {{ name }}\n',
    );
    const result = await loadFromPath(schemaFile);
    assert.ok('schema' in result);
    assert.equal(result.body, 'Hi {{ name }}\n');
    assert.equal(result.bodyPath, undefined);
  } finally {
    await fs.rm(tmp, { recursive: true, force: true });
  }
});

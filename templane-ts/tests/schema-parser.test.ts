import { test } from 'node:test';
import * as assert from 'node:assert/strict';
import { parse } from '../src/schema-parser';

test('basic fields', () => {
  const result = parse(
    'name:\n  type: string\n  required: true\nage:\n  type: number\n  required: false\n',
    'basic',
  );
  assert.ok('schema' in result);
  const schema = result.schema;
  assert.equal(schema.id, 'basic');
  assert.deepEqual(schema.fields.name.type, { kind: 'string' });
  assert.equal(schema.fields.name.required, true);
  assert.deepEqual(schema.fields.age.type, { kind: 'number' });
  assert.equal(schema.fields.age.required, false);
});

test('enum type', () => {
  const yaml = 'status:\n  type: enum\n  values: [active, inactive, pending]\n  required: true\n';
  const result = parse(yaml, 'enum-type');
  assert.ok('schema' in result);
  assert.deepEqual(result.schema.fields.status.type, {
    kind: 'enum',
    values: ['active', 'inactive', 'pending'],
  });
});

test('list type', () => {
  const yaml = 'tags:\n  type: list\n  items:\n    type: string\n  required: false\n';
  const result = parse(yaml, 'list-type');
  assert.ok('schema' in result);
  assert.deepEqual(result.schema.fields.tags.type, {
    kind: 'list',
    item_type: { kind: 'string' },
  });
});

test('object type', () => {
  const yaml = 'address:\n  type: object\n  required: true\n  fields:\n    city:\n      type: string\n      required: true\n';
  const result = parse(yaml, 'object-type');
  assert.ok('schema' in result);
  const objType = result.schema.fields.address.type;
  assert.equal(objType.kind, 'object');
  if (objType.kind === 'object') {
    assert.deepEqual(objType.fields.city.type, { kind: 'string' });
  }
});

test('body extracted', () => {
  const yaml = 'name:\n  type: string\n  required: true\n---\nHello {{ name }}!\n';
  const result = parse(yaml, 'body-extracted');
  assert.ok('schema' in result);
  assert.equal(result.body, 'Hello {{ name }}!\n');
});

test('invalid schema returns error', () => {
  const result = parse('- just\n- a\n- list\n', 'invalid-schema');
  assert.ok('error' in result);
  assert.ok(!('schema' in result));
});

test('deep nesting', () => {
  const yaml =
    'order:\n' +
    '  type: object\n' +
    '  required: true\n' +
    '  fields:\n' +
    '    customer:\n' +
    '      type: object\n' +
    '      required: true\n' +
    '      fields:\n' +
    '        address:\n' +
    '          type: object\n' +
    '          required: true\n' +
    '          fields:\n' +
    '            city:\n' +
    '              type: string\n' +
    '              required: true\n';
  const result = parse(yaml, 'deep-nesting');
  assert.ok('schema' in result);
  const outer = result.schema.fields.order.type;
  assert.equal(outer.kind, 'object');
  if (outer.kind !== 'object') return;
  const mid = outer.fields.customer.type;
  assert.equal(mid.kind, 'object');
  if (mid.kind !== 'object') return;
  const inner = mid.fields.address.type;
  assert.equal(inner.kind, 'object');
  if (inner.kind !== 'object') return;
  assert.deepEqual(inner.fields.city.type, { kind: 'string' });
});

import { test } from 'node:test';
import * as assert from 'node:assert/strict';
import { check } from '../src/type-checker';
import { TypedSchema, TemplaneField, TemplaneFieldType } from '../src/models';

function schemaOf(...pairs: [string, TemplaneFieldType, boolean][]): TypedSchema {
  const fields: Record<string, TemplaneField> = {};
  for (const [name, type, required] of pairs) {
    fields[name] = { name, type, required };
  }
  return { id: 'test', fields };
}

test('valid data — no errors', () => {
  const schema = schemaOf(['name', { kind: 'string' }, true], ['age', { kind: 'number' }, false]);
  const errors = check(schema, { name: 'Alice', age: 30 });
  assert.deepEqual(errors, []);
});

test('missing required field', () => {
  const schema = schemaOf(
    ['name', { kind: 'string' }, true],
    ['email', { kind: 'string' }, true],
  );
  const errors = check(schema, { name: 'Alice' });
  assert.equal(errors.length, 1);
  assert.equal(errors[0].code, 'missing_required_field');
  assert.equal(errors[0].field, 'email');
});

test('type mismatch — number', () => {
  const schema = schemaOf(['age', { kind: 'number' }, true]);
  const errors = check(schema, { age: 'thirty' });
  assert.equal(errors.length, 1);
  assert.equal(errors[0].code, 'type_mismatch');
  assert.equal(errors[0].field, 'age');
  assert.ok(errors[0].message.includes('number'));
  assert.ok(errors[0].message.includes('string'));
});

test('invalid enum value', () => {
  const schema = schemaOf([
    'status',
    { kind: 'enum', values: ['active', 'inactive'] },
    true,
  ]);
  const errors = check(schema, { status: 'unknown' });
  assert.equal(errors.length, 1);
  assert.equal(errors[0].code, 'invalid_enum_value');
  assert.ok(errors[0].message.includes('unknown'));
});

test('unknown field', () => {
  const schema = schemaOf(['name', { kind: 'string' }, true]);
  const errors = check(schema, { name: 'Alice', extra: 'value' });
  const codes = errors.map(e => e.code);
  assert.ok(codes.includes('unknown_field'));
  const unknown = errors.find(e => e.code === 'unknown_field')!;
  assert.equal(unknown.field, 'extra');
});

test('did you mean', () => {
  const schema = schemaOf(['name', { kind: 'string' }, true]);
  const errors = check(schema, { naem: 'Alice' });
  const codes = errors.map(e => e.code);
  assert.ok(codes.includes('missing_required_field'));
  assert.ok(codes.includes('did_you_mean'));
  const dym = errors.find(e => e.code === 'did_you_mean')!;
  assert.ok(dym.message.includes('name'));
});

test('nested object type error', () => {
  const inner: TemplaneFieldType = {
    kind: 'object',
    fields: { city: { name: 'city', type: { kind: 'string' }, required: true } },
  };
  const schema = schemaOf(['address', inner, true]);
  const errors = check(schema, { address: { city: 42 } });
  assert.equal(errors.length, 1);
  assert.equal(errors[0].code, 'type_mismatch');
  assert.equal(errors[0].field, 'address.city');
});

test('list item type mismatch', () => {
  const schema = schemaOf([
    'tags',
    { kind: 'list', item_type: { kind: 'string' } },
    true,
  ]);
  const errors = check(schema, { tags: ['hello', 42, 'world'] });
  assert.equal(errors.length, 1);
  assert.equal(errors[0].code, 'type_mismatch');
  assert.equal(errors[0].field, 'tags[1]');
});

test('errors collected, not short-circuited', () => {
  const schema = schemaOf(
    ['a', { kind: 'string' }, true],
    ['b', { kind: 'number' }, true],
  );
  const errors = check(schema, { a: 1, b: 'x' });
  assert.equal(errors.length, 2);
});

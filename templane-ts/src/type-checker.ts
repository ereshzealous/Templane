import { TemplaneFieldType, TypeCheckError, TypedSchema } from './models';

function levenshtein(s1: string, s2: string): number {
  const m = s1.length;
  const n = s2.length;
  const dp = Array.from({ length: n + 1 }, (_, i) => i);
  for (let i = 1; i <= m; i++) {
    let prev = dp[0];
    dp[0] = i;
    for (let j = 1; j <= n; j++) {
      const temp = dp[j];
      dp[j] = s1[i - 1] === s2[j - 1] ? prev : 1 + Math.min(prev, dp[j], dp[j - 1]);
      prev = temp;
    }
  }
  return dp[n];
}

function typeNameOf(value: unknown): string {
  if (typeof value === 'boolean') return 'boolean';
  if (typeof value === 'number') return 'number';
  if (typeof value === 'string') return 'string';
  if (value === null) return 'null';
  if (Array.isArray(value)) return 'list';
  if (typeof value === 'object') return 'object';
  return typeof value;
}

export function check(
  schema: TypedSchema,
  data: Record<string, unknown>,
  prefix: string = '',
): TypeCheckError[] {
  const errors: TypeCheckError[] = [];

  for (const [fieldName, field] of Object.entries(schema.fields)) {
    const path = prefix ? `${prefix}.${fieldName}` : fieldName;
    if (!(fieldName in data)) {
      if (field.required) {
        errors.push({
          code: 'missing_required_field',
          field: path,
          message: `Required field '${path}' is missing`,
        });
      }
    } else {
      errors.push(...checkType(field.type, data[fieldName], path));
    }
  }

  const known = Object.keys(schema.fields);
  for (const key of Object.keys(data)) {
    if (!(key in schema.fields)) {
      const path = prefix ? `${prefix}.${key}` : key;
      let closest: string | null = null;
      let minDist = Infinity;
      for (const k of known) {
        const d = levenshtein(key, k);
        if (d < minDist) {
          minDist = d;
          closest = k;
        }
      }
      if (closest !== null && minDist <= 3) {
        errors.push({
          code: 'did_you_mean',
          field: path,
          message: `Unknown field '${path}'. Did you mean '${closest}'?`,
        });
      } else {
        errors.push({
          code: 'unknown_field',
          field: path,
          message: `Field '${path}' is not defined in schema`,
        });
      }
    }
  }

  return errors;
}

function checkType(fieldType: TemplaneFieldType, value: unknown, path: string): TypeCheckError[] {
  const errors: TypeCheckError[] = [];

  switch (fieldType.kind) {
    case 'string':
      if (typeof value !== 'string') {
        errors.push({
          code: 'type_mismatch',
          field: path,
          message: `Field '${path}' expected string, got ${typeNameOf(value)}`,
        });
      }
      break;
    case 'number':
      if (typeof value !== 'number') {
        errors.push({
          code: 'type_mismatch',
          field: path,
          message: `Field '${path}' expected number, got ${typeNameOf(value)}`,
        });
      }
      break;
    case 'boolean':
      if (typeof value !== 'boolean') {
        errors.push({
          code: 'type_mismatch',
          field: path,
          message: `Field '${path}' expected boolean, got ${typeNameOf(value)}`,
        });
      }
      break;
    case 'null':
      if (value !== null) {
        errors.push({
          code: 'type_mismatch',
          field: path,
          message: `Field '${path}' expected null, got ${typeNameOf(value)}`,
        });
      }
      break;
    case 'enum':
      if (typeof value !== 'string' || !fieldType.values.includes(value)) {
        errors.push({
          code: 'invalid_enum_value',
          field: path,
          message: `Field '${path}' value '${value}' not in enum [${fieldType.values.join(', ')}]`,
        });
      }
      break;
    case 'list':
      if (!Array.isArray(value)) {
        errors.push({
          code: 'type_mismatch',
          field: path,
          message: `Field '${path}' expected list, got ${typeNameOf(value)}`,
        });
      } else {
        value.forEach((item, i) => {
          errors.push(...checkType(fieldType.item_type, item, `${path}[${i}]`));
        });
      }
      break;
    case 'object':
      if (typeof value !== 'object' || value === null || Array.isArray(value)) {
        errors.push({
          code: 'type_mismatch',
          field: path,
          message: `Field '${path}' expected object, got ${typeNameOf(value)}`,
        });
      } else {
        const subSchema: TypedSchema = { id: '', fields: fieldType.fields };
        errors.push(...check(subSchema, value as Record<string, unknown>, path));
      }
      break;
  }

  return errors;
}

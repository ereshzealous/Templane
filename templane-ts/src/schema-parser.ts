import * as yaml from 'js-yaml';
import { TemplaneField, TemplaneFieldType, TypedSchema } from './models';

type ParseResult =
  | { schema: TypedSchema; body?: string }
  | { error: string };

export function parse(yamlStr: string, schemaId: string): ParseResult {
  let body: string | undefined;
  let schemaYaml = yamlStr;

  const sep = '\n---\n';
  const idx = yamlStr.indexOf(sep);
  if (idx !== -1) {
    schemaYaml = yamlStr.substring(0, idx);
    body = yamlStr.substring(idx + sep.length);
  }

  let data: unknown;
  try {
    data = yaml.load(schemaYaml);
  } catch (e) {
    return { error: (e as Error).message };
  }

  if (typeof data !== 'object' || data === null || Array.isArray(data)) {
    return { error: 'Schema must be a YAML mapping' };
  }

  const fields: Record<string, TemplaneField> = {};
  for (const [name, fieldDef] of Object.entries(data as Record<string, unknown>)) {
    fields[name] = parseField(name, (fieldDef ?? {}) as Record<string, unknown>);
  }

  const schema: TypedSchema = { id: schemaId, fields };
  return body !== undefined ? { schema, body } : { schema };
}

function parseField(name: string, fieldDef: Record<string, unknown>): TemplaneField {
  const typeStr = (fieldDef.type as string) ?? 'string';
  const required = Boolean(fieldDef.required);
  return { name, type: parseType(typeStr, fieldDef), required };
}

function parseType(typeStr: string, fieldDef: Record<string, unknown>): TemplaneFieldType {
  switch (typeStr) {
    case 'string':  return { kind: 'string' };
    case 'number':  return { kind: 'number' };
    case 'boolean': return { kind: 'boolean' };
    case 'null':    return { kind: 'null' };
    case 'enum': {
      const values = ((fieldDef.values as unknown[]) ?? []).map(v => String(v));
      return { kind: 'enum', values };
    }
    case 'list': {
      const itemsDef = (fieldDef.items ?? {}) as Record<string, unknown>;
      return {
        kind: 'list',
        item_type: parseType((itemsDef.type as string) ?? 'string', itemsDef),
      };
    }
    case 'object': {
      const subFields: Record<string, TemplaneField> = {};
      const fieldsDef = (fieldDef.fields ?? {}) as Record<string, unknown>;
      for (const [fname, fdef] of Object.entries(fieldsDef)) {
        subFields[fname] = parseField(fname, (fdef ?? {}) as Record<string, unknown>);
      }
      return { kind: 'object', fields: subFields };
    }
    default: return { kind: 'string' };
  }
}

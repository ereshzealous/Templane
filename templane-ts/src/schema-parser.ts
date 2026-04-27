import * as yaml from 'js-yaml';
import * as fs from 'fs/promises';
import * as path from 'path';
import { TemplaneField, TemplaneFieldType, TypedSchema } from './models';

type ParseResult =
  | { schema: TypedSchema; body?: string; bodyPath?: string; engine?: string }
  | { error: string };

const RESERVED_KEYS = new Set(['body', 'engine']);

// SPEC §4.3 — engine inference by body-path extension.
const ENGINE_BY_EXT: Record<string, string> = {
  '.jinja':      'jinja',
  '.jinja2':     'jinja',
  '.j2':         'jinja',
  '.hbs':        'handlebars',
  '.handlebars': 'handlebars',
  '.ftl':        'freemarker',
  '.ftlh':       'freemarker',
  '.tmpl':       'gotemplate',
  '.gotmpl':     'gotemplate',
  '.md':         'markdown',
  '.markdown':   'markdown',
  '.html':       'html-raw',
  '.htm':        'html-raw',
  '.yaml':       'yaml-raw',
  '.yml':        'yaml-raw',
};

const VALID_ENGINES = new Set([
  'jinja', 'handlebars', 'freemarker', 'gotemplate', 'markdown', 'html-raw', 'yaml-raw',
]);

export function parse(yamlStr: string, schemaId: string): ParseResult {
  let body: string | undefined;
  let schemaYaml = yamlStr;

  const sep = '\n---\n';
  const idx = yamlStr.indexOf(sep);
  const hasSeparator = idx !== -1;
  if (hasSeparator) {
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

  const dataMap = data as Record<string, unknown>;

  let bodyPath: string | undefined;
  if (typeof dataMap.body === 'string') {
    bodyPath = dataMap.body;
  }
  let engine: string | undefined;
  if (typeof dataMap.engine === 'string') {
    engine = dataMap.engine;
  }
  delete dataMap.body;
  delete dataMap.engine;

  if (bodyPath !== undefined && hasSeparator) {
    return { error: "cannot use both 'body:' key and '---' separator" };
  }

  if (bodyPath !== undefined) {
    const parts = bodyPath.split('/');
    if (bodyPath.startsWith('/') || parts.includes('..')) {
      return { error: "body path must be relative and inside the schema's directory" };
    }
  }

  if (engine !== undefined && !VALID_ENGINES.has(engine)) {
    const valid = Array.from(VALID_ENGINES).sort();
    return { error: `unknown engine '${engine}' — must be one of ${JSON.stringify(valid)}` };
  }

  if (engine === undefined && bodyPath !== undefined) {
    const ext = path.extname(bodyPath).toLowerCase();
    if (ext in ENGINE_BY_EXT) {
      engine = ENGINE_BY_EXT[ext];
    }
  }

  const fields: Record<string, TemplaneField> = {};
  for (const [name, fieldDef] of Object.entries(dataMap)) {
    if (RESERVED_KEYS.has(name)) continue; // defensive
    fields[name] = parseField(name, (fieldDef ?? {}) as Record<string, unknown>);
  }

  const schema: TypedSchema = { id: schemaId, fields };
  const result: { schema: TypedSchema; body?: string; bodyPath?: string; engine?: string } = { schema };
  if (body !== undefined) result.body = body;
  if (bodyPath !== undefined) result.bodyPath = bodyPath;
  if (engine !== undefined) result.engine = engine;
  return result;
}

export async function loadFromPath(schemaPath: string): Promise<ParseResult> {
  let yamlStr: string;
  try {
    yamlStr = await fs.readFile(schemaPath, 'utf-8');
  } catch (e) {
    return { error: `cannot read schema file: ${(e as Error).message}` };
  }

  const result = parse(yamlStr, path.basename(schemaPath));
  if ('error' in result) return result;

  if (result.bodyPath !== undefined && result.body === undefined) {
    const bodyAbs = path.resolve(path.dirname(schemaPath), result.bodyPath);
    try {
      result.body = await fs.readFile(bodyAbs, 'utf-8');
    } catch {
      return { error: `body file not found: ${result.bodyPath}` };
    }
  }

  return result;
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

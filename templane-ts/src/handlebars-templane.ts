import Handlebars from 'handlebars';
import { parse as parseSchema, loadFromPath as loadSchemaFromPath } from './schema-parser';
import { check as typeCheck } from './type-checker';
import { TypeCheckError, TypedSchema } from './models';

export class TemplaneHandlebarsError extends Error {
  constructor(public errors: TypeCheckError[]) {
    const summary = errors.map(e => `  [${e.code}] ${e.message}`).join('\n');
    super(`Data does not match schema:\n${summary}`);
    this.name = 'TemplaneHandlebarsError';
  }
}

export interface TemplaneTemplate {
  schema: TypedSchema;
  body: string;
  check(data: Record<string, unknown>): TypeCheckError[];
  render(data: Record<string, unknown>): string;
}

export function compile(source: string, schemaId: string = 'template'): TemplaneTemplate {
  const parseResult = parseSchema(source, schemaId);
  if ('error' in parseResult) {
    throw new Error(`Schema parse error: ${parseResult.error}`);
  }
  if (parseResult.body === undefined) {
    throw new Error(
      'Template must include a body — either a "\\n---\\n" separator or a "body:" key ' +
      'pointing to an external file (use compileFromPath for sidecar schemas).',
    );
  }
  return buildTemplate(parseResult.schema, parseResult.body);
}

/**
 * Compile a Templane schema from a filesystem path. Handles both embedded
 * (`---` separator) and sidecar (`body: ./path.ext`) modes — sidecar bodies
 * are resolved relative to the schema file's directory.
 */
export async function compileFromPath(schemaPath: string): Promise<TemplaneTemplate> {
  const result = await loadSchemaFromPath(schemaPath);
  if ('error' in result) {
    throw new Error(`Schema load error: ${result.error}`);
  }
  if (result.body === undefined) {
    throw new Error(
      `Template at ${schemaPath} has no renderable body — ` +
      'add a "---" separator or a "body:" key pointing to an external file.',
    );
  }
  return buildTemplate(result.schema, result.body);
}

function buildTemplate(schema: TypedSchema, body: string): TemplaneTemplate {
  const hbsTemplate = Handlebars.compile(body, { noEscape: false });
  return {
    schema,
    body,
    check(data: Record<string, unknown>): TypeCheckError[] {
      return typeCheck(schema, data);
    },
    render(data: Record<string, unknown>): string {
      const errors = typeCheck(schema, data);
      if (errors.length > 0) {
        throw new TemplaneHandlebarsError(errors);
      }
      return hbsTemplate(data);
    },
  };
}

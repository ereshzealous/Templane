import Handlebars from 'handlebars';
import { parse as parseSchema } from './schema-parser';
import { check as typeCheck } from './type-checker';
import { TypeCheckError, TypedSchema } from './models';

export class TSPHandlebarsError extends Error {
  constructor(public errors: TypeCheckError[]) {
    const summary = errors.map(e => `  [${e.code}] ${e.message}`).join('\n');
    super(`Data does not match schema:\n${summary}`);
    this.name = 'TSPHandlebarsError';
  }
}

export interface TSPTemplate {
  schema: TypedSchema;
  body: string;
  check(data: Record<string, unknown>): TypeCheckError[];
  render(data: Record<string, unknown>): string;
}

export function compile(source: string, schemaId: string = 'template'): TSPTemplate {
  const parseResult = parseSchema(source, schemaId);
  if ('error' in parseResult) {
    throw new Error(`Schema parse error: ${parseResult.error}`);
  }
  if (parseResult.body === undefined) {
    throw new Error('Template must include a body after "\\n---\\n" separator');
  }

  const schema = parseResult.schema;
  const body = parseResult.body;
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
        throw new TSPHandlebarsError(errors);
      }
      return hbsTemplate(data);
    },
  };
}

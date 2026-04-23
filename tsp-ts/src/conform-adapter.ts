import * as readline from 'node:readline';
import { parse as schemaParse } from './schema-parser';

interface Request {
  fixture_id: string;
  fixture: Record<string, unknown>;
}

interface Response {
  output?: unknown;
  error?: string;
}

function handle(fixtureId: string, fixture: Record<string, unknown>): Response {
  try {
    if (fixtureId.startsWith('schema-parser')) {
      return { output: schemaParse(fixture.yaml as string, (fixture.id as string) ?? 'unknown') };
    }

    // Categories 2-4 added in later tasks
    return { output: null, error: `Unhandled fixture: ${fixtureId}` };
  } catch (e) {
    return { output: null, error: (e as Error).message };
  }
}

const rl = readline.createInterface({ input: process.stdin, terminal: false });
rl.on('line', (line) => {
  const trimmed = line.trim();
  if (!trimmed) return;
  const req: Request = JSON.parse(trimmed);
  const result = handle(req.fixture_id, req.fixture);
  process.stdout.write(JSON.stringify(result) + '\n');
});

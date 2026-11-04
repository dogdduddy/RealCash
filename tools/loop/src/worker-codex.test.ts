// scripts/worker-codex.sh를 임시 저장소에 복사해 실제 bash로 돌린다. codex는 결정적인 가짜로 바꾼다.
import { spawn, type ChildProcess } from 'node:child_process';
import {
  cpSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, expect, it } from 'vitest';

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const fixtures: { root: string; workers: ChildProcess[] }[] = [];
const delay = (ms: number) => new Promise((done) => setTimeout(done, ms));

async function until(predicate: () => boolean): Promise<void> {
  for (let i = 0; i < 400; i++) {
    if (predicate()) return;
    await delay(25);
  }
  throw new Error('worker fixture timed out');
}

function fixture(env: Record<string, string> = {}) {
  const root = mkdtempSync(join(tmpdir(), 'realcash-worker-'));
  for (const dir of ['scripts/lib', 'scripts/log', 'bin', 'assets/jobs/queue']) {
    mkdirSync(join(root, dir), { recursive: true });
  }
  cpSync(join(REPO, 'scripts/lib/common.sh'), join(root, 'scripts/lib/common.sh'));
  cpSync(join(REPO, 'scripts/worker-codex.sh'), join(root, 'scripts/worker-codex.sh'));
  // No model call: the fake reads the save path from the request and writes a 1x1 PNG there.
  writeFileSync(
    join(root, 'bin/codex'),
    `#!/usr/bin/env bash
for arg in "$@"; do prompt=$arg; done
id=$(printf '%s\\n' "$prompt" | sed -n 's#.*assets/inbox/\\([a-z0-9.-]*\\)/.*#\\1#p' | head -n 1)
echo "$id" >> calls
if [ -f quota ]; then echo "session limit; try again later"; exit 1; fi
if [ -f fail ]; then echo "generation failed"; exit 1; fi
mkdir -p "assets/inbox/$id"
node -e 'require("fs").writeFileSync(process.argv[1], Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aL1sAAAAASUVORK5CYII=", "base64"))' "assets/inbox/$id/$id-1.png"
`,
    { mode: 0o755 },
  );
  const f = { root, workers: [] as ChildProcess[] };
  fixtures.push(f);
  const path = (name: string) => join(root, name);
  const write = (name: string, content = '') => {
    mkdirSync(join(path(name), '..'), { recursive: true });
    writeFileSync(path(name), content);
  };
  const start = () => {
    const child = spawn('/bin/bash', ['scripts/worker-codex.sh'], {
      cwd: root,
      env: {
        ...process.env,
        PATH: `${path('bin')}:${process.env.PATH}`,
        IMAGE_JOBS_PER_DAY: '0',
        IMAGE_MAX_UNREVIEWED: '4',
        IMAGE_MAX_ATTEMPTS: '3',
        JOB_GAP: '0',
        POLL: '1',
        NAP_CHUNK: '1',
        LIMIT_RETRY: '1',
        ...env,
      },
      stdio: 'ignore',
    });
    f.workers.push(child);
    return child;
  };
  const job = (seq: number | string, id: string, headers = '') =>
    write(
      `assets/jobs/queue/${seq}-${id}.prompt.md`,
      `${headers}$imagegen\n[저장] assets/inbox/${id}/${id}-1.png\n`,
    );
  const done = (id: string) => existsSync(path(`assets/inbox/${id}/STATUS`));
  return { root, path, write, start, job, done };
}

afterEach(async () => {
  for (const f of fixtures.splice(0)) {
    writeFileSync(join(f.root, 'scripts/STOP'), '');
    await until(() => f.workers.every((w) => w.exitCode !== null));
    rmSync(f.root, { recursive: true, force: true });
  }
});

it('runs a real request file from the repo end to end with a fake codex', async (ctx) => {
  // queue/에 의뢰서가 있으면 그것을, 다 처리된 뒤라면 done/의 첫 의뢰서를 쓴다.
  const pick = (dir: string) =>
    existsSync(join(REPO, dir))
      ? readdirSync(join(REPO, dir)).filter((n) => n.endsWith('.prompt.md')).sort()
      : [];
  const source = pick('assets/jobs/queue').length ? 'assets/jobs/queue' : 'assets/jobs/done';
  const first = pick(source)[0];
  if (!first) return ctx.skip();
  const f = fixture();
  cpSync(join(REPO, source, first), f.path(`assets/jobs/queue/${first}`));
  const id = first.replace(/\.prompt\.md$/, '').replace(/^\d\d-/, '');
  f.start();
  await until(() => f.done(id));
  expect(readFileSync(f.path(`assets/inbox/${id}/STATUS`), 'utf8')).toMatch(/^done .* images=1 rc=0 /);
  expect(readFileSync(f.path(`assets/inbox/${id}/ATTEMPTS`), 'utf8').trim()).toBe('1');
  expect(existsSync(f.path(`assets/jobs/done/${first}`))).toBe(true);
  expect(existsSync(f.path('assets/jobs/blocked'))).toBe(true);
  expect(readdirSync(f.path('assets/jobs/blocked'))).toEqual([]);
});

it('continues beyond 20 while waiting at the review backlog, then resumes', async () => {
  const f = fixture({ IMAGE_MAX_UNREVIEWED: '1' });
  const today = new Date().toLocaleDateString('en-CA');
  f.write(`scripts/log/codex-count-${today}`, '20\n');
  f.job(21, 'test.first');
  f.job(22, 'test.second');
  f.start();
  await until(() => f.done('test.first'));
  await delay(150);
  expect(f.done('test.second')).toBe(false);
  f.write('assets/inbox/test.first/REVIEWED', 'PASS draft\n');
  await until(() => f.done('test.second'));
  expect(readFileSync(f.path(`scripts/log/codex-count-${today}`), 'utf8').trim()).toBe('22');
});

it('waits for both a successful review and a gate without starving independent work', async () => {
  const f = fixture();
  f.job(21, 'test.dependent', '# requires-review: test.base\n# requires-gate: G-001\n');
  f.job(22, 'test.independent');
  f.write('assets/inbox/test.base/REVIEWED', 'FAIL\n');
  f.start();
  await until(() => f.done('test.independent'));
  expect(f.done('test.dependent')).toBe(false);
  f.write('assets/inbox/test.base/REVIEWED', 'PASS draft\n');
  await delay(100);
  expect(f.done('test.dependent')).toBe(false);
  f.write('assets/jobs/gates/G-001.PASS');
  await until(() => f.done('test.dependent'));
});

it('blocks existing results and exhausted attempts without overwriting either', async () => {
  const f = fixture();
  f.job(21, 'test.existing');
  f.write('assets/inbox/test.existing/STATUS', 'done original\n');
  f.job(22, 'test.exhausted');
  f.write('assets/inbox/test.exhausted/ATTEMPTS', '3\n');
  f.job(23, 'test.valid');
  f.start();
  await until(() => f.done('test.valid'));
  expect(readFileSync(f.path('calls'), 'utf8').trim()).toBe('test.valid');
  expect(readFileSync(f.path('assets/inbox/test.existing/STATUS'), 'utf8')).toBe('done original\n');
  expect(existsSync(f.path('assets/jobs/blocked/21-test.existing.prompt.md.reason'))).toBe(true);
  expect(existsSync(f.path('assets/jobs/blocked/22-test.exhausted.prompt.md.reason'))).toBe(true);
});

it('blocks malformed names and reused request filenames with a reason', async () => {
  const f = fixture();
  f.write('assets/jobs/queue/1-test.short.prompt.md', '[저장] assets/inbox/test.short/test.short-1.png\n');
  f.write('assets/jobs/queue/24-Test.Upper.prompt.md', '[저장] assets/inbox/x/x-1.png\n');
  f.write('assets/jobs/done/25-test.again.prompt.md', 'old\n');
  f.job(25, 'test.again');
  f.job(26, 'test.ok');
  f.start();
  await until(() => f.done('test.ok'));
  expect(readFileSync(f.path('calls'), 'utf8').trim()).toBe('test.ok');
  const reason = (n: string) => readFileSync(f.path(`assets/jobs/blocked/${n}.reason`), 'utf8');
  expect(reason('1-test.short.prompt.md')).toMatch(/invalid request name/);
  expect(reason('24-Test.Upper.prompt.md')).toMatch(/invalid request name/);
  expect(reason('25-test.again.prompt.md')).toMatch(/already processed/);
});

it('returns an interrupted request from running/ to the queue on start', async () => {
  const f = fixture();
  f.write(
    'assets/jobs/running/27-test.interrupted.prompt.md',
    '[저장] assets/inbox/test.interrupted/test.interrupted-1.png\n',
  );
  f.start();
  await until(() => f.done('test.interrupted'));
  expect(existsSync(f.path('assets/jobs/done/27-test.interrupted.prompt.md'))).toBe(true);
});

it('does not treat an archived attempt image as a new successful result', async () => {
  const f = fixture();
  f.job(21, 'test.failed');
  f.write('assets/inbox/test.failed/attempt-1/test.failed-1.png', 'old image');
  f.write('fail');
  f.start();
  await until(() => f.done('test.failed'));
  expect(readFileSync(f.path('assets/inbox/test.failed/STATUS'), 'utf8')).toMatch(/^failed /);
  expect(existsSync(f.path('assets/jobs/failed/21-test.failed.prompt.md'))).toBe(true);
});

it('does not accept a non-PNG file at the expected path', async () => {
  const f = fixture();
  f.job(21, 'test.fake');
  // A failing run that leaves a text file named like the result must still count as failed.
  writeFileSync(
    f.path('bin/codex'),
    `#!/usr/bin/env bash\nmkdir -p assets/inbox/test.fake\necho notpng > assets/inbox/test.fake/test.fake-1.png\n`,
    { mode: 0o755 },
  );
  f.start();
  await until(() => f.done('test.fake'));
  expect(readFileSync(f.path('assets/inbox/test.fake/STATUS'), 'utf8')).toMatch(/^failed /);
});

it('retries service limits without counting a generation attempt', async () => {
  const f = fixture();
  f.job(21, 'test.quota');
  f.write('quota');
  f.start();
  await until(() => existsSync(f.path('calls')));
  await until(() => existsSync(f.path('assets/jobs/queue/21-test.quota.prompt.md')));
  expect(existsSync(f.path('assets/inbox/test.quota/STATUS'))).toBe(false);
  expect(existsSync(f.path('assets/inbox/test.quota/ATTEMPTS'))).toBe(false);
  rmSync(f.path('quota'));
  await until(() => f.done('test.quota'));
  expect(readFileSync(f.path('assets/inbox/test.quota/ATTEMPTS'), 'utf8').trim()).toBe('1');
});

it('a second worker cannot reclaim a request while the first holds the lock', async () => {
  const f = fixture();
  const first = f.start();
  await until(() => existsSync(f.path('scripts/log/codex-worker.lock/pid')));
  f.write('assets/jobs/running/21-test.inflight.prompt.md', '[저장] assets/inbox/test.inflight/test.inflight-1.png\n');
  const second = f.start();
  await until(() => second.exitCode !== null);
  expect(second.exitCode).toBe(0);
  expect(existsSync(f.path('assets/jobs/running/21-test.inflight.prompt.md'))).toBe(true);
  expect(first.exitCode).toBeNull();
});

it('stops at the STOP file and releases its lock', async () => {
  const f = fixture();
  const w = f.start();
  await until(() => existsSync(f.path('scripts/log/codex-worker.lock/pid')));
  f.write('scripts/STOP');
  await until(() => w.exitCode !== null);
  expect(existsSync(f.path('scripts/log/codex-worker.lock'))).toBe(false);
});

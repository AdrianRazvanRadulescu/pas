import { execFileSync } from 'node:child_process';
import { closeSync, existsSync, mkdirSync, openSync, readFileSync, statSync, unlinkSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const coachFolder = dirname(fileURLToPath(import.meta.url));
export const dataFolder = join(coachFolder, 'data');
export const stateFolder = join(coachFolder, 'state');
export const errorLogPath = join(stateFolder, 'errors.log');
export const messagesPath = join(dataFolder, 'messages.json');
export const checkInsPath = join(dataFolder, 'checkins.json');
export const boardPath = join(dataFolder, 'board.json');
export const cardsPath = join(dataFolder, 'cards.json');
// Set PAS_CLAUDE_EXE to the full path of the Claude Code executable if `claude` is not on your PATH.
export const claudeExe = process.env.PAS_CLAUDE_EXE ?? 'claude';
// The private GitHub repository the phone and the PC share ("owner/name"). Must match DEFAULT_REPOSITORY in the Android app.
export const dataRepository = process.env.PAS_DATA_REPO ?? 'your-github-user/pas-data';
export const messagesToKeep = 300;

// prompt.md and chat-prompt.md are personal and git-ignored; the .example.md files are the generic versions.
export function readPromptTemplate(promptName) {
  const personalPath = join(coachFolder, `${promptName}.md`);
  const examplePath = join(coachFolder, `${promptName}.example.md`);
  return readFileSync(existsSync(personalPath) ? personalPath : examplePath, 'utf8');
}

// profile.md is personal and git-ignored; profile.example.md is the template.
export function readProfile() {
  const personalPath = join(coachFolder, 'profile.md');
  const examplePath = join(coachFolder, 'profile.example.md');
  return readFileSync(existsSync(personalPath) ? personalPath : examplePath, 'utf8');
}

const lockPath = join(stateFolder, 'data.lock');
const staleLockMillis = 5 * 60 * 1000;

// send.mjs and chat.mjs both run git in data/ (a clone of the data repository); two git commands at once collide on .git/index.lock.
export function withDataLock(work) {
  mkdirSync(stateFolder, { recursive: true });
  waitForLock();
  try {
    return work();
  } finally {
    unlinkSync(lockPath);
  }
}

function waitForLock() {
  while (true) {
    try {
      closeSync(openSync(lockPath, 'wx'));
      return;
    } catch (error) {
      if (error.code !== 'EEXIST') throw error;
    }
    // A holder that crashed never deletes its lock; nothing inside the lock takes minutes.
    const lockStats = statSync(lockPath, { throwIfNoEntry: false });
    if (lockStats && Date.now() - lockStats.mtimeMs > staleLockMillis) {
      unlinkSync(lockPath);
      continue;
    }
    Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 500);
  }
}

export function pullData() {
  execFileSync('git', ['-C', dataFolder, 'pull', '--rebase', '--quiet']);
}

/** Fetches and pulls only when the remote moved, so a 15-second loop costs one fetch. Returns true when it pulled. */
export function pullDataIfChanged() {
  execFileSync('git', ['-C', dataFolder, 'fetch', '--quiet']);
  const localHead = execFileSync('git', ['-C', dataFolder, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
  const remoteHead = execFileSync('git', ['-C', dataFolder, 'rev-parse', '@{u}'], { encoding: 'utf8' }).trim();
  if (localHead === remoteHead) return false;
  pullData();
  return true;
}

export function readJson(path) {
  return JSON.parse(readFileSync(path, 'utf8'));
}

export function writeJson(path, value) {
  writeFileSync(path, JSON.stringify(value, null, 2) + '\n');
}

export function commitAndPush(fileNames, commitMessage) {
  execFileSync('git', ['-C', dataFolder, 'add', ...fileNames]);
  execFileSync('git', ['-C', dataFolder, 'commit', '--quiet', '-m', commitMessage]);
  try {
    execFileSync('git', ['-C', dataFolder, 'push', '--quiet']);
  } catch {
    // The phone pushed checkins.json in between; the PC never touches that file, so the rebase is clean.
    pullData();
    execFileSync('git', ['-C', dataFolder, 'push', '--quiet']);
  }
}

export function environmentWithoutApiKeys() {
  // Without these the CLI bills an API key instead of the subscription.
  const environment = { ...process.env };
  delete environment.ANTHROPIC_API_KEY;
  delete environment.ANTHROPIC_AUTH_TOKEN;
  return environment;
}

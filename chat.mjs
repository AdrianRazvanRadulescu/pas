// Answers the user's chat in Pas within about a minute. Runs forever; start it at logon, for example with a scheduled task.
// The phone marks what wants an answer with askCoach; a reply lists the check-ins it answers in `covers`, so nothing is answered twice, even after a crash.
import { spawnSync } from 'node:child_process';
import { appendFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { setTimeout as sleep } from 'node:timers/promises';
import {
  checkInsPath, claudeExe, coachFolder, commitAndPush, environmentWithoutApiKeys, errorLogPath,
  messagesPath, messagesToKeep, pullDataIfChanged, readJson, readProfile, readPromptTemplate, withDataLock, writeJson,
} from './data-repo.mjs';
import { describeCheckIn, summarizeCardReviews } from './checkin-lines.mjs';

const checkEveryMillis = 15 * 1000;
// The user often sends two short messages in a row; one reply to both reads better than two replies.
const quietBeforeAnsweringMillis = 8 * 1000;
const oldestAnsweredMillis = 48 * 60 * 60 * 1000;
const attemptsPerQuestion = 3;
const threadTurnsInPrompt = 24;

const replySchema = {
  type: 'object',
  additionalProperties: false,
  required: ['reply'],
  properties: {
    reply: { type: 'string', description: 'Your reply, 1-4 sentences, plain text.' },
  },
};

const failedAttemptsById = new Map();

while (true) {
  try {
    await answerWaitingQuestions();
  } catch (error) {
    logError(`loop: ${error.stack ?? error}`);
  }
  await sleep(checkEveryMillis);
}

async function answerWaitingQuestions() {
  withDataLock(() => pullDataIfChanged());
  let waiting = findWaitingQuestions();
  if (waiting.length === 0) return;

  await sleep(quietBeforeAnsweringMillis);
  withDataLock(() => pullDataIfChanged());
  waiting = findWaitingQuestions();
  if (waiting.length === 0) return;

  const startedAt = Date.now();
  const reply = askClaude(waiting);
  if (reply === null) {
    for (const question of waiting) failedAttemptsById.set(question.id, (failedAttemptsById.get(question.id) ?? 0) + 1);
    return;
  }

  withDataLock(() => {
    pullDataIfChanged();
    const messages = readJson(messagesPath);
    const coveredIds = new Set(messages.flatMap((message) => message.covers ?? []));
    const stillWaiting = waiting.filter((question) => !coveredIds.has(question.id));
    if (stillWaiting.length === 0) return;
    messages.push({
      id: randomUUID(),
      createdAt: new Date().toISOString(),
      slot: 'reply',
      body: reply,
      focus: '',
      news: [],
      notify: true,
      covers: stillWaiting.map((question) => question.id),
    });
    writeJson(messagesPath, messages.slice(-messagesToKeep));
    commitAndPush(['messages.json'], 'Coach reply');
  });
  console.log(`${new Date().toISOString()} answered ${waiting.length} in ${Math.round((Date.now() - startedAt) / 1000)} s`);
}

function findWaitingQuestions() {
  const messages = readJson(messagesPath);
  const coveredIds = new Set(messages.flatMap((message) => message.covers ?? []));
  const oldestCreatedAt = new Date(Date.now() - oldestAnsweredMillis).toISOString();
  return readJson(checkInsPath)
    .filter((checkIn) => checkIn.askCoach && !coveredIds.has(checkIn.id))
    .filter((checkIn) => checkIn.createdAt >= oldestCreatedAt)
    .filter((checkIn) => (failedAttemptsById.get(checkIn.id) ?? 0) < attemptsPerQuestion)
    .sort((first, second) => first.createdAt.localeCompare(second.createdAt));
}

function askClaude(waiting) {
  const now = new Date();
  const checkIns = readJson(checkInsPath);
  const messages = readJson(messagesPath);
  const twoWeeksAgo = new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000).toLocaleDateString('sv-SE');
  const recentCheckIns = checkIns.filter((checkIn) => checkIn.date >= twoWeeksAgo);
  const checkInLines = [
    ...recentCheckIns.filter((checkIn) => checkIn.kind !== 'card' && checkIn.kind !== 'chat').map(describeCheckIn),
    ...summarizeCardReviews(recentCheckIns.filter((checkIn) => checkIn.kind === 'card')),
  ];

  const prompt = readPromptTemplate('chat-prompt')
    .replace('{{NOW}}', `${now.toLocaleDateString('sv-SE')} ${now.toTimeString().slice(0, 5)}`)
    .replace('{{DAY}}', now.toLocaleDateString('en-US', { weekday: 'long' }))
    .replace('{{PROFILE}}', readProfile())
    .replace('{{CHECKINS}}', checkInLines.join('\n') || '(nothing yet)')
    .replace('{{THREAD}}', describeThread(checkIns, messages))
    .replace('{{WAITING}}', waiting.map(describeQuestion).join('\n'));

  const claudeRun = spawnSync(
    claudeExe,
    ['-p', '--model', 'sonnet', '--tools', '', '--output-format', 'json', '--json-schema', JSON.stringify(replySchema)],
    { input: prompt, encoding: 'utf8', cwd: coachFolder, env: environmentWithoutApiKeys(), timeout: 3 * 60 * 1000 },
  );
  const claudeResult = claudeRun.status === 0 ? JSON.parse(claudeRun.stdout) : null;
  if (!claudeResult || claudeResult.is_error || !claudeResult.structured_output) {
    logError(`claude failed: ${claudeRun.stdout} ${claudeRun.stderr}`);
    return null;
  }
  return claudeResult.structured_output.reply;
}

function describeThread(checkIns, messages) {
  const hisTurns = checkIns
    .filter((checkIn) => checkIn.askCoach)
    .map((checkIn) => ({ createdAt: checkIn.createdAt, line: `User: ${describeQuestion(checkIn).slice(2)}` }));
  const coachTurns = messages.map((message) => ({ createdAt: message.createdAt, line: `You (${message.slot}): ${message.body}` }));
  const turns = [...hisTurns, ...coachTurns]
    .sort((first, second) => first.createdAt.localeCompare(second.createdAt))
    .slice(-threadTurnsInPrompt);
  return turns.map((turn) => `[${turn.createdAt.slice(0, 16).replace('T', ' ')}] ${turn.line}`).join('\n') || '(nothing yet)';
}

function describeQuestion(checkIn) {
  if (checkIn.kind === 'chat') return `- ${checkIn.note}`;
  if (checkIn.kind === 'skip') return `- (chose a pause today${checkIn.note ? `, reason: ${checkIn.note}` : ''})`;
  if (checkIn.kind === 'learned') return `- (after a focus session, what they understood: ${checkIn.note})`;
  if (checkIn.kind === 'workout') return `- (finished a gym workout) ${describeCheckIn(checkIn).slice(2)}`;
  return describeCheckIn(checkIn);
}

function logError(text) {
  appendFileSync(errorLogPath, `${new Date().toISOString()} chat ${text}\n`);
}

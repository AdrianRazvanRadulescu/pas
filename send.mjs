import { spawnSync } from 'node:child_process';
import { appendFileSync, existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { basename, join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { buildBoard } from './board.mjs';
import { describeCheckIn, summarizeCardReviews } from './checkin-lines.mjs';
import {
  boardPath, cardsPath, checkInsPath, claudeExe, coachFolder, commitAndPush, environmentWithoutApiKeys, errorLogPath,
  messagesPath, messagesToKeep, pullData, readJson, readProfile, readPromptTemplate, stateFolder, withDataLock, writeJson,
} from './data-repo.mjs';

const sentSlotsPath = join(stateFolder, 'sent-slots.txt');
const skippedFolderNames = new Set(['node_modules', 'build', '.venv', 'venv', '__pycache__', 'data', 'state', 'dist']);

const messageSchema = {
  type: 'object',
  additionalProperties: false,
  required: ['body', 'focus', 'news', 'notify', 'cards'],
  properties: {
    notify: { type: 'boolean', description: 'true only when the message deserves a phone notification; false = it only shows in the app.' },
    cards: {
      type: 'array',
      description: 'New review cards, only from "learned" notes of the user that have no card yet; otherwise an empty list.',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['question', 'answer'],
        properties: {
          question: { type: 'string', description: 'A question the user can answer from memory.' },
          answer: { type: 'string', description: 'The short answer, 1-3 sentences, in the user own words where possible.' },
        },
      },
    },
    body: { type: 'string', description: 'The message itself, 2-5 sentences, plain text.' },
    focus: { type: 'string', description: 'The focus of the day in one short sentence (max ~70 characters), shown on the widget.' },
    quote: {
      type: 'object',
      additionalProperties: false,
      required: ['text', 'author'],
      properties: {
        text: { type: 'string' },
        author: { type: ['string', 'null'], description: 'null when the line is written by you or is not a certain quote.' },
      },
    },
    news: {
      type: 'array',
      description: 'Morning only; otherwise an empty list.',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['text', 'source'],
        properties: {
          text: { type: 'string' },
          source: { type: 'string', description: 'The source and the day, e.g. "Example Lab, Oct 7".' },
        },
      },
    },
  },
};

const now = new Date();
const today = now.toLocaleDateString('sv-SE');
const hour = now.getHours();

// No evening message by design. The scheduled task can still catch up late (PC woke at 19:00), so the hour decides, not the trigger.
if (hour < 7 || hour >= 17) process.exit(0);

const slot = hour < 11 ? 'morning' : 'midday';
const slotKey = `${today} ${slot}`;
const sentSlots = existsSync(sentSlotsPath) ? readFileSync(sentSlotsPath, 'utf8').split(/\r?\n/) : [];
if (sentSlots.includes(slotKey)) process.exit(0);

withDataLock(() => pullData());
const messages = readJson(messagesPath);
const checkIns = readJson(checkInsPath);
const cards = existsSync(cardsPath) ? readJson(cardsPath) : [];

const twoWeeksAgo = new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000).toLocaleDateString('sv-SE');
const recentCheckIns = checkIns.filter((checkIn) => checkIn.date >= twoWeeksAgo);
const recentCheckInLines = recentCheckIns.filter((checkIn) => checkIn.kind !== 'card').map(describeCheckIn);
const cardReviewLines = summarizeCardReviews(recentCheckIns.filter((checkIn) => checkIn.kind === 'card'));
const recentMessageLines = messages
  .filter((message) => message.slot !== 'reply')
  .slice(-6)
  .map((message) => `- ${message.createdAt.slice(0, 16)} (${message.slot}) ${message.body} | focus: ${message.focus}${message.quote ? ` | quote: ${message.quote.text}` : ''}`);

const profileText = readProfile();
const dayName = now.toLocaleDateString('en-US', { weekday: 'long' });

const prompt = readPromptTemplate('prompt')
  .replace('{{NOW}}', `${today} ${now.toTimeString().slice(0, 5)}`)
  .replace('{{DAY}}', dayName)
  .replace('{{SLOT}}', slot)
  .replace('{{PROFILE}}', profileText)
  .replace('{{CHECKINS}}', [...recentCheckInLines, ...cardReviewLines].join('\n') || '(no check-ins yet)')
  .replace('{{PC_ACTIVITY}}', describePcActivityToday())
  .replace('{{CARDS}}', cards.slice(-40).map((card) => `- ${card.question}`).join('\n') || '(no cards yet)')
  .replace('{{MESSAGES}}', recentMessageLines.join('\n') || '(no messages yet, this is the first day)');

const claudeRun = spawnSync(
  claudeExe,
  ['-p', '--model', 'sonnet', '--allowedTools', 'WebSearch', '--output-format', 'json', '--json-schema', JSON.stringify(messageSchema)],
  { input: prompt, encoding: 'utf8', cwd: coachFolder, env: environmentWithoutApiKeys(), timeout: 10 * 60 * 1000 },
);
const claudeResult = claudeRun.status === 0 ? JSON.parse(claudeRun.stdout) : null;
if (!claudeResult || claudeResult.is_error || !claudeResult.structured_output) {
  appendFileSync(errorLogPath, `${now.toISOString()} ${slot} claude failed: ${claudeRun.stdout} ${claudeRun.stderr}\n`);
  process.exit(1);
}

const { cards: newCards, ...message } = claudeResult.structured_output;
for (const card of newCards) cards.push({ id: randomUUID(), createdOn: today, question: card.question, answer: card.answer });

withDataLock(() => {
  // chat.mjs may have pushed replies during the minutes claude took, so messages.json is read again here.
  pullData();
  const latestMessages = readJson(messagesPath);
  latestMessages.push({ id: randomUUID(), createdAt: now.toISOString(), slot, ...message });
  writeJson(messagesPath, latestMessages.slice(-messagesToKeep));
  writeJson(cardsPath, cards);
  writeJson(boardPath, buildBoard(profileText, cards));
  commitAndPush(['messages.json', 'board.json', 'cards.json'], `Coach ${slotKey}`);
});
appendFileSync(sentSlotsPath, `${slotKey}\n`);

// What the PC was used for today, so the coach can tell building tools and plans apart from the real work.
function describePcActivityToday() {
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  // PAS_WATCHED_FOLDERS: folders to watch, separated by ";" (default: this repository). Edit the list to taste.
  const watchedFolderPaths = process.env.PAS_WATCHED_FOLDERS ? process.env.PAS_WATCHED_FOLDERS.split(';') : [coachFolder];
  const watchedFolders = watchedFolderPaths.map((path) => ({ path, label: basename(path), extensions: ['.md', '.mjs', '.kt', '.ts', '.tsx', '.js', '.py', '.ipynb'] }));
  const lines = watchedFolders.map((folder) => {
    const changedFiles = filesChangedSince(folder.path, folder.extensions, startOfToday);
    return `- ${folder.label}: ${changedFiles.length} files changed today${changedFiles.length ? ` (e.g. ${changedFiles.slice(0, 3).join(', ')})` : ''}`;
  });
  return lines.join('\n');
}


function filesChangedSince(folderPath, extensions, sinceMillis) {
  if (!existsSync(folderPath)) return [];
  const changedFiles = [];
  const foldersToVisit = [folderPath];
  while (foldersToVisit.length > 0) {
    const folder = foldersToVisit.pop();
    for (const entry of readdirSync(folder, { withFileTypes: true })) {
      if (entry.name.startsWith('.') || skippedFolderNames.has(entry.name)) continue;
      const entryPath = join(folder, entry.name);
      if (entry.isDirectory()) foldersToVisit.push(entryPath);
      else if (extensions.some((extension) => entry.name.endsWith(extension)) && statSync(entryPath).mtimeMs >= sinceMillis) changedFiles.push(entry.name);
    }
  }
  return changedFiles;
}

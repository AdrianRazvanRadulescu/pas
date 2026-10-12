import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { dataFolder } from './data-repo.mjs';

// The app shows the board as it is: countdowns come from profile.md, news from the weekly digests.
// Digests are markdown files named like 2026-10-05.md, written by whatever produces your weekly AI news.
const weeklyDigestFolder = process.env.PAS_DIGEST_FOLDER ?? join(dataFolder, 'digests');
const digestsToKeep = 6;
// Full stories weigh ~40 KB per digest and the phone refetches the board often; older digests keep only the lists.
const digestsWithStories = 2;
const coreStoriesCount = 3;
// The "##" sections that hold stories, in the order the digest writes them. Lists like "In brief" stay separate.
const storySectionWords = ['Context', 'Research', 'Models', 'Agents', 'Policy', 'Industry', 'Everyday'];

export function buildBoard(profileText, cards) {
  return {
    upcoming: readUpcoming(profileText),
    blockers: readBlockers(profileText),
    cards,
    digests: readDigests(),
  };
}

// Lines like "- Send the invoice to the accountant" under "## Blockers": small manual tasks the business waits on.
function readBlockers(profileText) {
  return sectionLines(profileText, 'Blockers')
    .filter((line) => line.startsWith('- '))
    .map((line) => line.slice(2).trim());
}

// Lines like "- 2026-11-07: First demo ready" under "## What's next".
function readUpcoming(profileText) {
  return sectionLines(profileText, "What's next")
    .map((line) => line.match(/^- (\d{4}-\d{2}-\d{2}):\s*(.+)$/))
    .filter((match) => match !== null)
    .map((match) => ({ date: match[1], title: match[2].trim() }));
}

function readDigests() {
  if (!existsSync(weeklyDigestFolder)) return [];
  const digestFileNames = readdirSync(weeklyDigestFolder)
    .filter((fileName) => /^\d{4}-\d{2}-\d{2}\.md$/.test(fileName))
    .sort()
    .slice(-digestsToKeep)
    .reverse();

  return digestFileNames.map((fileName, digestIndex) => {
    const digestText = readFileSync(join(weeklyDigestFolder, fileName), 'utf8');
    const digestId = fileName.replace('.md', '');
    const title = digestText.split(/\r?\n/)[0].replace(/^#\s*/, '');
    const reading = sectionLines(digestText, 'To read').filter(isListItem).map(toDigestItem);
    return {
      id: digestId,
      title,
      summary: sectionLines(digestText, 'In brief').filter(isListItem).map(toDigestItem),
      reading,
      watch: sectionLines(digestText, 'To watch').filter(isListItem).map(toDigestItem),
      stories: digestIndex < digestsWithStories ? readStories(digestText, digestId, reading) : [],
    };
  });
}

// A story is either a bold paragraph ("**Some Lab: ...** (link)") followed by its bullets,
// or a bold list item ("- **Some Tool: ...**") followed by its nested bullets.
// Bullets that start with "**Label:**" become labelled parts, so "Method", "Limits" etc. survive.
function readStories(digestText, digestId, reading) {
  const stories = [];
  for (const sectionWords of storySectionWords) {
    const section = sectionHeading(digestText, sectionWords);
    if (section === null) continue;
    let topic = null;
    let story = null;
    let isParagraphStory = false;
    for (const line of sectionLines(digestText, sectionWords)) {
      if (line.startsWith('### ')) {
        topic = line.slice(4).trim();
        story = null;
        isParagraphStory = false;
      } else if (line.trim() === '' || line.startsWith('---') || isItalicNote(line)) {
        isParagraphStory = false;
      } else if (line.startsWith('**')) {
        story = startStory(stories, section, topic, line);
        isParagraphStory = true;
      } else if (line.startsWith('- ') && isParagraphStory) {
        story.parts.push(toStoryPart(line.slice(2)));
      } else if (line.startsWith('- ')) {
        story = startStory(stories, section, topic, line.slice(2));
      } else if (/^\s+(- |\d+\. )/.test(line) && story !== null) {
        addNestedLine(story, line.trim(), isParagraphStory);
      }
    }
  }
  return stories.map((story, index) => finishStory(story, `${digestId}-${index + 1}`, reading));
}

function isItalicNote(line) {
  return line.startsWith('*') && !line.startsWith('**');
}

function startStory(stories, section, topic, titleLine) {
  const boldTitle = titleLine.match(/^\*\*(.+?)\*\*:?\s*(.*)$/);
  const story = {
    section,
    topic,
    title: toPlainText(boldTitle ? boldTitle[1] : titleLine).replace(/[.:]$/, ''),
    intro: boldTitle ? toPlainText(boldTitle[2]) : '',
    links: [],
    parts: [],
  };
  collectLinks(story, titleLine);
  stories.push(story);
  return story;
}

function toStoryPart(bulletText) {
  const labelled = bulletText.match(/^\*\*([^*]{2,40}?):\*\*\s*(.*)$/);
  if (labelled) return { label: labelled[1].trim(), text: capitalize(toPlainText(labelled[2])), markdown: bulletText };
  return { label: null, text: toPlainText(bulletText), markdown: bulletText };
}

// The text after "**Label:**" starts lowercase in the digest; in the app the label sits on its own line.
function capitalize(text) {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

// Nested bullets add to the last part of a paragraph story, but are the parts themselves of a list-item story.
function addNestedLine(story, nestedLine, isParagraphStory) {
  const bulletText = nestedLine.replace(/^(- |\d+\. )/, '');
  const lastPart = story.parts[story.parts.length - 1];
  if (isParagraphStory && lastPart) {
    lastPart.text += `\n• ${toPlainText(bulletText)}`;
    lastPart.markdown += `\n${bulletText}`;
  } else {
    story.parts.push(toStoryPart(bulletText));
  }
}

// Core stories are the first ones the digest recommends under "To read", matched by link, in that order.
function finishStory(story, id, reading) {
  for (const part of story.parts) collectLinks(story, part.markdown);
  const readingIndex = reading.findIndex((item) => item.url && story.links.some((link) => link.url === item.url));
  const coreRank = readingIndex !== -1 && readingIndex < coreStoriesCount ? readingIndex + 1 : null;
  const wordCount = [story.intro, ...story.parts.map((part) => part.text)].join(' ').split(/\s+/).length;
  return {
    id,
    section: story.section,
    topic: story.topic,
    title: story.title,
    intro: story.intro || null,
    url: story.links.length > 0 ? story.links[0].url : null,
    coreRank,
    readMinutes: Math.max(1, Math.round(wordCount / 180)),
    parts: story.parts.map((part) => ({ label: part.label, text: part.text })),
  };
}

function collectLinks(story, markdownLine) {
  for (const match of markdownLine.matchAll(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g)) {
    if (!story.links.some((link) => link.url === match[2])) story.links.push({ label: match[1], url: match[2] });
  }
}

// Keeps **bold** for the app to render. Drops "([source](url))" tails, since the links live on the story.
function toPlainText(markdownText) {
  return markdownText
    .replace(/\s*\((\[[^\]]+\]\([^)]*\)(,\s*)?)+\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/(^|[^*])\*([^*]+)\*(?!\*)/g, '$1$2')
    .trim();
}

// "## 🛡️ Research & safety" -> "Research & safety"; "## Context: what happened..." -> "Context".
function sectionHeading(markdownText, headingWords) {
  const heading = markdownText.split(/\r?\n/).find((line) => /^##\s/.test(line) && line.includes(headingWords));
  if (!heading) return null;
  return heading.replace(/^##\s*/, '').replace(/^[^\p{L}]+/u, '').split(':')[0].trim();
}

// Matches the heading by its words, so the emoji in front of it can change between digests.
function sectionLines(markdownText, headingWords) {
  const lines = markdownText.split(/\r?\n/);
  const headingIndex = lines.findIndex((line) => /^##\s/.test(line) && line.includes(headingWords));
  if (headingIndex === -1) return [];
  const nextHeadingOffset = lines.slice(headingIndex + 1).findIndex((line) => /^##?\s/.test(line));
  const sectionEnd = nextHeadingOffset === -1 ? lines.length : headingIndex + 1 + nextHeadingOffset;
  return lines.slice(headingIndex + 1, sectionEnd);
}

function isListItem(line) {
  return /^(- |\d+\. )/.test(line);
}

function toDigestItem(line) {
  const firstLink = line.match(/\[([^\]]+)\]\((https?:[^)\s]+)\)/);
  const text = line
    .replace(/^(- |\d+\. )/, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/\*\*/g, '')
    .replace(/\*([^*]+)\*/g, '$1')
    .trim();
  return { text, url: firstLink ? firstLink[2] : null };
}

// Builds the release APK and publishes it as a release of the private data repository.
// The app on the phone sees the new release and offers "Update"; install.mjs serves the same file over Wi-Fi.
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { dataRepository } from './data-repo.mjs';

const coachFolder = dirname(fileURLToPath(import.meta.url));
const androidFolder = join(coachFolder, 'android');
const releaseFolder = join(androidFolder, 'app', 'build', 'outputs', 'apk', 'release');
const distApkPath = join(coachFolder, 'dist', 'pas.apk');
const repository = dataRepository;
// Usage: node publish.mjs "What is new, in short". The phone shows the text in the update notification and card.
const releaseNotes = process.argv[2] ?? 'Small improvements.';

execFileSync(join(androidFolder, 'gradlew.bat'), ['assembleRelease', '--quiet'], {
  cwd: androidFolder,
  stdio: 'inherit',
  shell: true,
  // Needs JDK 17: set JAVA_HOME in your environment.
  env: process.env,
});

const metadata = JSON.parse(readFileSync(join(releaseFolder, 'output-metadata.json'), 'utf8'));
const build = metadata.elements[0];
mkdirSync(dirname(distApkPath), { recursive: true });
copyFileSync(join(releaseFolder, build.outputFile), distApkPath);

execFileSync('gh', [
  'release', 'create', `v${build.versionCode}`, distApkPath,
  '--repo', repository,
  '--title', `Pas ${build.versionName}`,
  '--notes', releaseNotes,
], { stdio: 'inherit' });

console.log(`Published: Pas ${build.versionName} (v${build.versionCode}). The phone announces it within 15 minutes, or as soon as you open Pas.`);

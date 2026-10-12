// Shows the text from the PC clipboard (the GitHub token) as a QR code, so the phone camera can copy it.
// Nothing leaves the PC: the code is drawn in this window only.
import { execFileSync } from 'node:child_process';
import QRCode from 'qrcode';

const clipboardText = execFileSync('powershell.exe', ['-NoProfile', '-Command', 'Get-Clipboard'], { encoding: 'utf8' }).trim();

if (!clipboardText.startsWith('github_pat_')) {
  console.log('The clipboard does not hold a GitHub token (it must start with github_pat_). Copy it from GitHub first.');
  process.exit(1);
}

console.log(await QRCode.toString(clipboardText, { type: 'terminal', small: true }));
console.log('Scan with the phone camera, tap "Copy", then in Pas: Paste -> Connect.');
console.log('Close this window after scanning.');

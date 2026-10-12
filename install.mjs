// Puts Pas on the phone over Wi-Fi: opens a page with a QR code on the PC; the phone scans it and downloads the APK.
// Stops by itself after 10 minutes. Run it with: node install.mjs
import { createServer } from 'node:http';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { networkInterfaces } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { exec } from 'node:child_process';
import QRCode from 'qrcode';

const coachFolder = dirname(fileURLToPath(import.meta.url));
const apkPath = join(coachFolder, 'dist', 'pas.apk');
const port = 8765;
const lifetimeMinutes = 10;

if (!existsSync(apkPath)) {
  console.log('dist\\pas.apk does not exist yet. Run first: node publish.mjs');
  process.exit(1);
}

// The 172.x addresses belong to Hyper-V/WSL virtual adapters, which the phone cannot reach; a typical home network is 192.168.x.
const lanAddress = Object.values(networkInterfaces())
  .flat()
  .find((address) => address.family === 'IPv4' && !address.internal && address.address.startsWith('192.168.'));
if (!lanAddress) {
  console.log('Cannot find this PC on the home network (192.168.x.x). Is it connected to the router?');
  process.exit(1);
}

const phoneUrl = `http://${lanAddress.address}:${port}/`;
const qrSvg = await QRCode.toString(phoneUrl, { type: 'svg', margin: 1, color: { dark: '#1F3A27', light: '#FFFDF8' } });

const pageStyle = `
  body { margin: 0; font-family: system-ui, sans-serif; background: #F7F3EC; color: #1F1D1A; }
  main { max-width: 460px; margin: 0 auto; padding: 40px 24px; }
  h1 { font-family: Georgia, serif; font-weight: normal; font-size: 40px; margin: 0; }
  .tagline { font-family: Georgia, serif; font-style: italic; color: #A8662F; font-size: 20px; margin: 4px 0 28px; }
  .card { background: #FFFDF8; border-radius: 24px; padding: 24px; margin-bottom: 16px; }
  .qr svg { width: 100%; height: auto; }
  ol { padding-left: 20px; line-height: 1.6; }
  .button { display: block; text-align: center; background: #4E7A5A; color: white; text-decoration: none;
            font-size: 20px; padding: 18px; border-radius: 999px; margin: 8px 0 24px; }
  .hint { color: #6B655C; font-size: 14px; line-height: 1.5; }
`;

function pcPage() {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Pas on your phone</title><style>${pageStyle}</style></head>
  <body><main>
    <h1>Pas</h1><p class="tagline">One day at a time.</p>
    <div class="card qr">${qrSvg}</div>
    <div class="card"><ol>
      <li>Phone on the same Wi-Fi as this PC.</li>
      <li>Open the camera and scan the code.</li>
      <li>On the phone tap <b>Download Pas</b>, then open the file.</li>
    </ol></div>
    <p class="hint">If the phone does not open the page, Windows is blocking the connection. In Settings → Network → Ethernet, choose
      "Private network", or allow Node.js in the Windows Firewall dialog. The page stops by itself after ${lifetimeMinutes} minutes.</p>
  </main></body></html>`;
}

function phonePage() {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Pas</title><style>${pageStyle}</style></head>
  <body><main>
    <h1>Pas</h1><p class="tagline">One day at a time.</p>
    <a class="button" href="/pas.apk">Download Pas</a>
    <div class="card"><ol>
      <li>Open the downloaded file (pas.apk).</li>
      <li>Android asks once for permission to install from the browser: <b>Settings → Allow</b>, then go back.</li>
      <li>Install, open Pas and follow the two steps there.</li>
    </ol></div>
    <p class="hint">Next time you will not need this: Pas shows you when a new version is available.</p>
  </main></body></html>`;
}

const server = createServer((request, response) => {
  if (request.url === '/pas.apk') {
    response.writeHead(200, {
      'Content-Type': 'application/vnd.android.package-archive',
      'Content-Length': statSync(apkPath).size,
      'Content-Disposition': 'attachment; filename="pas.apk"',
    });
    response.end(readFileSync(apkPath));
    console.log('The phone downloaded the app.');
    return;
  }
  const isPhone = /Android/i.test(request.headers['user-agent'] ?? '');
  console.log(`${isPhone ? 'The phone' : 'A browser'} opened the page (from ${request.socket.remoteAddress}).`);
  response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  response.end(isPhone ? phonePage() : pcPage());
});

server.listen(port, '0.0.0.0', () => {
  console.log(`Page for the phone: ${phoneUrl}`);
  console.log(`It stops by itself after ${lifetimeMinutes} minutes. Press Ctrl+C to stop it now.`);
  console.log('If the page hangs on the phone and nothing shows up here: the phone is not on the home Wi-Fi (mobile data, VPN, guest network).');
  exec(`start "" http://localhost:${port}/`);
});

setTimeout(() => {
  console.log('Done, the page is stopped.');
  process.exit(0);
}, lifetimeMinutes * 60 * 1000);

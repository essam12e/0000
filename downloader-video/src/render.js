// Renders reel.html frame by frame (deterministic timeline seek) and encodes an MP4.
// usage: node render.js <framesDir> <out.mp4> [music.wav] [--still t1,t2,...]
const { chromium } = require('playwright');
const { execFileSync } = require('child_process');
const path = require('path'), fs = require('fs');
const [framesDir, out, music, flag, list] = process.argv.slice(2);
const FPS = 30;
(async () => {
  fs.mkdirSync(framesDir, { recursive: true });
  const b = await chromium.launch({ args: ['--allow-file-access-from-files'] });
  const p = await b.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  p.on('pageerror', (e) => console.log('PAGEERROR', e.message));
  await p.goto('file://' + path.join(__dirname, 'reel.html'));
  await p.evaluate(() => document.fonts.ready);
  const dur = await p.evaluate(() => window.DURATION);
  if (flag === '--still') {
    for (const t of list.split(',')) { await p.evaluate((v) => window.seek(v), +t); await p.screenshot({ path: path.join(framesDir, `still_${t}.png`) }); }
    await b.close(); return;
  }
  const n = Math.round(dur * FPS);
  for (let i = 0; i < n; i++) {
    await p.evaluate((v) => window.seek(v), i / FPS);
    await p.screenshot({ path: path.join(framesDir, `f${String(i).padStart(5, '0')}.jpg`), type: 'jpeg', quality: 95 });
  }
  await b.close();
  const args = ['-y', '-loglevel', 'error', '-framerate', String(FPS), '-i', path.join(framesDir, 'f%05d.jpg')];
  if (music && music !== '-') args.push('-i', music, '-af', `afade=t=in:d=0.4,afade=t=out:st=${dur - 1.2}:d=1.2`, '-c:a', 'aac', '-b:a', '192k', '-shortest');
  args.push('-c:v', 'libx264', '-preset', 'slow', '-crf', '17', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', '-t', String(dur), out);
  execFileSync('ffmpeg', args, { stdio: 'inherit' });
  console.log('frames', n, 'duration', dur, '->', out);
})().catch((e) => { console.error('FAIL', e.message); process.exit(1); });

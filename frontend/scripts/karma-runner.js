#!/usr/bin/env node
// Resolves Puppeteer's bundled Chromium (async in puppeteer >=23) and runs
// `ng test` with CHROME_BIN set, so karma.conf.js's ChromeHeadlessCI launcher
// doesn't depend on a system browser being installed.
const { execFileSync } = require('node:child_process');
const puppeteer = require('puppeteer');

(async () => {
  const chromeBin = await puppeteer.executablePath();
  const ngArgs = process.argv.slice(2);
  execFileSync('npx', ['ng', 'test', ...ngArgs], {
    stdio: 'inherit',
    env: { ...process.env, CHROME_BIN: chromeBin },
  });
})().catch((err) => {
  console.error(err);
  process.exit(1);
});

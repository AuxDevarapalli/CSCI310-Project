// Prints docs/implementation-document.html to the PDF submitted on Brightspace.
// Usage from the repository root: node docs/tools/html_to_pdf.js
const path = require('path');
const { chromium } = require('playwright');

(async () => {
  const docs = path.resolve(__dirname, '..');
  const input = 'file://' + path.join(docs, 'implementation-document.html');
  const output = path.join(docs, 'Team04_Project2.3_Implementation_Document.pdf');
  const browser = await chromium.launch();
  const page = await browser.newPage();
  await page.goto(input, { waitUntil: 'networkidle' });
  await page.pdf({
    path: output,
    preferCSSPageSize: true,
    printBackground: true,
    displayHeaderFooter: true,
    headerTemplate: '<span></span>',
    footerTemplate:
      '<div style="width:100%;font-size:8px;font-family:DejaVu Sans,Arial,sans-serif;color:#777;' +
      'padding:0 0.5in;display:flex;justify-content:space-between;">' +
      '<span>Team 04 — SplitSmart Implementation Document (Project 2.3)</span>' +
      '<span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>',
  });
  await browser.close();
  console.log('wrote ' + output);
})();

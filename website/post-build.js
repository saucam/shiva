const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, 'build', 'index.html');
const indexContent = fs.readFileSync(indexPath, 'utf-8');

const updatedContent = indexContent.replace(
    'url=shiva/index.html',
    'url=docs/readme'
).replace(
    'window.location.href = "shiva/index.html"',
    'window.location.href = "docs/readme"'
).replace(
    'href=\'shiva/index.html\'',
    'href=\'docs/readme\''
);

fs.writeFileSync(indexPath, updatedContent, 'utf-8');
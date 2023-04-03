const fs = require('fs');
const path = require('path');

const indexPath = path.join(__dirname, 'build', 'index.html');
const indexContent = fs.readFileSync(indexPath, 'utf-8');

const updatedContent = indexContent.replace(
    'url=shiva/index.html',
    'url=./shiva/index.html'
).replace(
    'window.location.href = "shiva/index.html"',
    'window.location.href = "./shiva/index.html"'
).replace(
    'href=\'shiva/index.html\'',
    'href=\'./shiva/index.html\''
);

fs.writeFileSync(indexPath, updatedContent, 'utf-8');
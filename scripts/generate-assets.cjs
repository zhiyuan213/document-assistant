// 生成导航图标；页面猫形象使用用户设计包原图，不在此重新生成。
const fs = require('node:fs');
const path = require('node:path');
const sharp = require(process.env.SHARP_MODULE || 'sharp');
const root = path.resolve(__dirname, '../frontend/src/static');
const shapes = [
  '<path d="M5 14 16 4l11 10v14h-8v-9h-6v9H5z"/>',
  '<rect x="4" y="4" width="10" height="10" rx="3"/><rect x="18" y="4" width="10" height="10" rx="3"/><rect x="4" y="18" width="10" height="10" rx="3"/><rect x="18" y="18" width="10" height="10" rx="3"/>',
  '<path d="m16 3 4 8 9 1-7 7 2 10-8-5-8 5 2-10-7-7 9-1z"/>',
  '<circle cx="16" cy="10" r="6"/><path d="M4 29q0-13 12-13t12 13z"/>',
];
(async () => {
  for (let i=0;i<shapes.length;i++) for (const active of [false,true]) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" fill="${active?'#783fff':'#aaa3bb'}">${shapes[i]}</svg>`;
    await sharp(Buffer.from(svg)).resize(64,64).png().toFile(path.join(root, `tab-${i}${active?'-active':''}.png`));
  }
})();

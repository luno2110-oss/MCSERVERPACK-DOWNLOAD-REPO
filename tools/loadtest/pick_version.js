// Gibt die Minecraft-Version für die Bots aus: die Server-Version (1.21.11), wenn mineflayer sie kann, sonst die
// neueste unterstützte 1.21.x (dann braucht der Test-Server ViaVersion und ViaBackwards). Zweite Zeile: "via" oder "".
const want = process.argv[2] || '1.21.11';
const supported = require('mineflayer').supportedVersions || require('minecraft-data').supportedVersions.pc;
if (supported.includes(want)) {
  console.log(want);
  console.log('');
} else {
  const v = supported.filter((x) => x.startsWith('1.21')).sort((a, b) => {
    const pa = a.split('.').map(Number), pb = b.split('.').map(Number);
    for (let i = 0; i < 3; i++) if ((pa[i] || 0) !== (pb[i] || 0)) return (pa[i] || 0) - (pb[i] || 0);
    return 0;
  });
  console.log(v[v.length - 1] || '1.21.4');
  console.log('via');
}

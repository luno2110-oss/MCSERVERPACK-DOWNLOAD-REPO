// Belastungstest für den Beta-Test: BOTS (Standard 10) Spieler joinen, wechseln zwischen den Modi, öffnen Menüs,
// laufen, bauen ab, schicken sich Geld, melden Bugs, loggen einmal aus und wieder ein. Gezählt werden Joins, Kicks,
// Verbindungsfehler und Befehlsfehler („An internal error occurred“). Ergebnis: loadtest-result.json und Ausgabe.
//
// Aufruf: node bots.js   (Umgebung: HOST, PORT, BOTS, DURATION Sekunden, MC_VERSION oder leer = automatisch)
const mineflayer = require('mineflayer');
const fs = require('fs');

const HOST = process.env.HOST || '127.0.0.1';
const PORT = +(process.env.PORT || 25565);
const N = +(process.env.BOTS || 10);
const DURATION = +(process.env.DURATION || 240) * 1000;
const VERSION = process.env.MC_VERSION || false;

const COMMANDS = [
  '/modus survival', '/modus skyblock', '/modus smp', '/hub', '/modus survival', '/modus skyblock',
  '/menu', '/shop', '/baltop', '/balance', '/juwelen', '/daily', '/spielzeit', '/settings', '/stats', '/battlepass',
  '/guide', '/hilfe', '/regeln', '/friends', '/team', '/markt', '/worth', '/investments', '/kits', '/keys', '/cosmetics',
  '/tags', '/vote', '/erfolge', '/inbox', '/support', '/booster', '/einfuehrung ueberspringen',
  '/angeln', '/angeln pass', '/angeln markt', '/angeln rute', '/angeln haendler', '/casino', '/cookie', '/quests', '/jobs',
  '/insel', '/combo', '/skyblock', '/oneblock', '/feedback', '/bug Testmeldung vom Belastungstest',
];

const stats = { games: 0, gameEnds: {}, gameInputs: { left: 0, right: 0, a: 0, d: 0 }, buys: 0, joined: 0, spawned: 0, kicked: [], errors: [], commands: 0, cmdErrors: [], chats: 0, rejoins: 0, ended: 0 };
const bots = [];
const start = Date.now();
const rnd = (a, b) => a + Math.floor(Math.random() * (b - a + 1));
const pick = (l) => l[Math.floor(Math.random() * l.length)];

function log(...a) {
  console.log(`[${((Date.now() - start) / 1000).toFixed(0).padStart(3)}s]`, ...a);
}

function create(i, again) {
  const name = 'Tester' + String(i).padStart(2, '0');
  const bot = mineflayer.createBot({ host: HOST, port: PORT, username: name, version: VERSION, auth: 'offline',
    hideErrors: true, checkTimeoutInterval: 60000 });
  bot.lastCmd = null;
  bot.index = i;
  bots[i] = bot;
  bot.once('login', () => { stats.joined++; log(name, 'eingeloggt', again ? '(erneut)' : ''); });
  bot.once('spawn', () => {
    stats.spawned++;
    loop(bot);
    // einmal mitten im Test aus- und wieder einloggen (Join/Quit-Wechsel wie bei echten Spielern)
    if (!again) {
      setTimeout(() => {
        if (Date.now() - start > DURATION - 30000) return;
        log(name, 'loggt kurz aus');
        bot.rejoining = true;
        bot.quit('Wiederkommen');
        setTimeout(() => { stats.rejoins++; create(i, true); }, rnd(4000, 8000));
      }, rnd(40000, DURATION - 60000));
    }
  });
  bot.on('messagestr', (msg) => {
    stats.chats++;
    if (/Halte den Fisch im grünen Bereich/.test(msg)) playFishGame(bot);
    else if (bot.fishing && /Der Fisch ist entkommen/.test(msg)) endFishGame(bot, 'entkommen');
    else if (bot.fishing && /Der Fisch ist weg/.test(msg)) endFishGame(bot, 'abgebrochen');
    if (/internal error|interner fehler|An error occurred|unexpected error/i.test(msg)) {
      stats.cmdErrors.push({ bot: name, cmd: bot.lastCmd, msg: msg.slice(0, 200) });
      log('BEFEHLSFEHLER', name, bot.lastCmd, '->', msg.slice(0, 120));
    }
  });
  bot.on('title', (text) => {
    if (bot.fishing && /GEFANGEN/.test(typeof text === 'string' ? text : JSON.stringify(text))) endFishGame(bot, 'gewonnen');
  });
  bot.on('kicked', (reason) => {
    const r = typeof reason === 'string' ? reason : JSON.stringify(reason);
    if (!bot.rejoining) { stats.kicked.push({ bot: name, reason: r.slice(0, 300) }); log('KICK', name, r.slice(0, 200)); }
  });
  bot.on('error', (e) => { stats.errors.push({ bot: name, error: String(e).slice(0, 300) }); log('FEHLER', name, String(e).slice(0, 200)); });
  bot.on('end', () => { stats.ended++; });
}

// Angel-Minispiel (Server startet es über /kubytest events): echte Eingaben wie ein Spieler, Linksklick (Armschwung),
// Rechtsklick (Item benutzen) und A/D halten, bunt gemischt, bis die Runde vorbei ist. Keine Befehle währenddessen.
function playFishGame(bot) {
  if (bot.fishing) return;
  bot.fishing = true;
  stats.games++;
  log(bot.username, 'spielt das Angel-Minispiel');
  const until = Date.now() + 125000;
  const step = () => {
    if (!bot.fishing || !bot.entity) return;
    if (Date.now() > until) { endFishGame(bot, 'zeit'); return; }
    try {
      const r = Math.random();
      if (r < 0.3) { bot.swingArm('right'); stats.gameInputs.left++; }
      else if (r < 0.6) {
        bot.activateItem();
        stats.gameInputs.right++;
        setTimeout(() => { try { bot.deactivateItem(); } catch (e) { /* weg */ } }, 60);
      } else {
        const key = r < 0.8 ? 'left' : 'right';
        stats.gameInputs[key === 'left' ? 'a' : 'd']++;
        bot.setControlState(key, true);
        setTimeout(() => { try { bot.setControlState(key, false); } catch (e) { /* weg */ } }, rnd(200, 600));
      }
    } catch (e) {
      stats.errors.push({ bot: bot.username, error: 'Minispiel: ' + String(e).slice(0, 200) });
    }
    setTimeout(step, rnd(120, 350));
  };
  step();
}

function endFishGame(bot, how) {
  if (!bot.fishing) return;
  bot.fishing = false;
  stats.gameEnds[how] = (stats.gameEnds[how] || 0) + 1;
  try { bot.clearControlStates(); } catch (e) { /* weg */ }
  log(bot.username, 'Minispiel vorbei:', how);
  // entkommenen Fisch manchmal freikaufen (Juwelen-Weg)
  if (how === 'entkommen' && Math.random() < 0.5) {
    setTimeout(() => { if (bot.entity) { bot.lastCmd = '/angeln freikaufen'; bot.chat('/angeln freikaufen'); stats.buys++; stats.commands++; } }, 1500);
  }
}

function loop(bot) {
  const tick = () => {
    if (!bot.entity || bot.rejoining || Date.now() - start > DURATION) return;
    if (bot.fishing) { setTimeout(tick, 1000); return; }
    try {
      const r = Math.random();
      if (r < 0.55) {
        const c = pick(COMMANDS);
        bot.lastCmd = c;
        bot.chat(c);
        stats.commands++;
      } else if (r < 0.7) {
        const dir = pick(['forward', 'back', 'left', 'right']);
        bot.setControlState(dir, true);
        bot.setControlState('jump', Math.random() < 0.5);
        bot.look(Math.random() * Math.PI * 2, (Math.random() - 0.5) * 1.2, true);
        setTimeout(() => { try { bot.clearControlStates(); } catch (e) { /* weg */ } }, rnd(500, 1500));
      } else if (r < 0.8) {
        const b = bot.blockAt(bot.entity.position.offset(0, -1, 0));
        if (b && b.name !== 'air' && b.name !== 'bedrock' && bot.canDigBlock(b)) bot.dig(b).catch(() => {});
        else bot.swingArm();
      } else if (r < 0.88) {
        const other = bots[(bot.index + rnd(1, N - 1)) % N];
        if (other && other.username) { bot.lastCmd = '/pay'; bot.chat(`/pay ${other.username} ${rnd(1, 50)}`); stats.commands++; }
      } else if (r < 0.95) {
        bot.chat(pick(['hallo', 'gg', 'wo ist der shop?', 'lag?', 'nice', 'test ' + rnd(1, 999)]));
      } else {
        bot.swingArm();
        bot.activateItem();
        setTimeout(() => { try { bot.deactivateItem(); } catch (e) { /* weg */ } }, 300);
      }
    } catch (e) {
      stats.errors.push({ bot: bot.username, error: 'Aktion: ' + String(e).slice(0, 200) });
    }
    setTimeout(tick, rnd(1200, 3000));
  };
  setTimeout(tick, rnd(2000, 5000));
}

// gestaffelt einloggen (wie echte Spieler, die nach und nach kommen)
for (let i = 0; i < N; i++) setTimeout(() => create(i, false), i * 2500);

setTimeout(() => {
  for (const b of bots) { try { b.rejoining = true; b.quit('Ende'); } catch (e) { /* weg */ } }
  setTimeout(() => {
    const res = { bots: N, durationSec: DURATION / 1000, ...stats };
    fs.writeFileSync('loadtest-result.json', JSON.stringify(res, null, 2));
    log(`ERGEBNIS: ${stats.spawned} Spawns (${N} Bots, ${stats.rejoins} Wiederkehrer), ${stats.commands} Befehle, `
      + `${stats.chats} Chatzeilen, ${stats.kicked.length} Kicks, ${stats.errors.length} Verbindungsfehler, `
      + `${stats.cmdErrors.length} Befehlsfehler, Minispiele ${stats.games} ${JSON.stringify(stats.gameEnds)}`);
    process.exit(0);
  }, 3000);
}, DURATION + 5000);

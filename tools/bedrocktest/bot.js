// Bedrock-Test: ein Bedrock-Spieler (bedrock-protocol, ohne Xbox-Login) kommt über Geyser + Floodgate auf den Server,
// öffnet Kuby-Menüs per Befehl, bekommt sie als Bedrock-Formulare (Titel, Text, Knöpfe mit Bildern), klickt Knöpfe,
// füllt Eingabe-Formulare aus und liest Scoreboard und Chat mit. Ergebnis: bedrock-result.json und Ausgabe.
//
// Umgebung: HOST, PORT (19132), VERSION (1.26.60), DURATION Sekunden (90)
const bedrock = require('bedrock-protocol');
const fs = require('fs');

const HOST = process.env.HOST || '127.0.0.1';
const PORT = +(process.env.PORT || 19132);
const VERSION = process.env.VERSION || '1.26.60';
const DURATION = +(process.env.DURATION || 90) * 1000;
const COMMANDS = ['/menu', '/feedback', '/shop', '/settings', '/freunde', '/feedback', '/support', '/menu', '/modus survival', '/angeln'];

const PACK_UUID = (process.env.PACK_UUID || '').toLowerCase();
// bedrock-protocol liest die UUID im Pack-Info-Paket als zwei Little-Endian-Hälften: dieselbe UUID in dieser Schreibweise
function swapped(u) {
  const h = u.replace(/-/g, '');
  if (h.length !== 32) return u;
  const rev = (x) => x.match(/../g).reverse().join('');
  const s = rev(h.slice(0, 16)) + rev(h.slice(16));
  return [s.slice(0, 8), s.slice(8, 12), s.slice(12, 16), s.slice(16, 20), s.slice(20)].join('-');
}
const PACK_UUID_WIRE = PACK_UUID ? swapped(PACK_UUID) : '';
const res = { pack: { offered: false, requested: false, chunks: 0, chunkCount: 0, size: 0, sha256ok: null, saved: null },
  version: VERSION, joined: false, spawned: false, packs: [], forms: [], clicks: 0, commands: [], chat: [],
  sidebarTitle: null, sidebarLines: [], errors: [], disconnect: null, schema: {} };
const start = Date.now();
const log = (...a) => console.log(`[${((Date.now() - start) / 1000).toFixed(1).padStart(5)}s]`, ...a);

let proto = null;
try {
  proto = require('minecraft-data')('bedrock_' + VERSION).protocol;
  for (const p of ['packet_command_request', 'packet_modal_form_response', 'packet_resource_pack_client_response']) {
    res.schema[p] = proto.types && proto.types[p] ? proto.types[p] : 'unbekannt';
  }
} catch (e) { res.errors.push('Schema: ' + e); }

function fieldsOf(name) {
  const t = proto && proto.types && proto.types[name];
  return t && t[0] === 'container' ? t[1].map((f) => f.name) : null;
}

const client = bedrock.createClient({ host: HOST, port: PORT, username: 'KubyBedrock', offline: true, version: VERSION,
  skipPing: true, connectTimeout: 30000, skinData: { LanguageCode: 'de_DE' } });
let uuid = '00000000-0000-0000-0000-000000000000';

// Pack wie ein echtes Gerät herunterladen: die automatische Bestätigung von bedrock-protocol abfangen, unser Pack
// anfordern (send_packs), alle Stücke laden, SHA-256 gegen Geysers Angabe prüfen und speichern
const crypto = require('crypto');
let packPhase = PACK_UUID ? 'idle' : 'done';
let packWanted = null;
let packInfo = null;
let pendingComplete = false;
log('Pack-UUID gesucht:', PACK_UUID || '(keine)');
const packChunks = [];
const rawWrite = client.write.bind(client);
client.write = (name, params) => {
  if (name === 'resource_pack_client_response' && params && params.response_status === 'completed' && packPhase !== 'done') {
    if (packPhase === 'idle' && packWanted) requestPack();
    else if (packPhase === 'idle') pendingComplete = true;    // Pack-Liste noch nicht gelesen: gleich nachholen
    return;
  }
  return rawWrite(name, params);
};
function requestPack() {
  packPhase = 'downloading';
  res.pack.requested = true;
  log('fordere Pack an:', packWanted);
  rawWrite('resource_pack_client_response', { response_status: 'send_packs', response_status_name: '', resourcepackids: [packWanted] });
}

client.on('resource_pack_data_info', (p) => {
  packInfo = p;
  res.pack.chunkCount = p.chunk_count;
  res.pack.size = Number(p.size);
  log('Pack-Daten:', p.pack_id, p.chunk_count, 'Stücke,', Number(p.size), 'Bytes');
  for (let i = 0; i < p.chunk_count; i++) rawWrite('resource_pack_chunk_request', { pack_id: p.pack_id, chunk_index: i });
});
client.on('resource_pack_chunk_data', (p) => {
  packChunks[p.chunk_index] = Buffer.from(p.payload);
  res.pack.chunks++;
  if (packInfo && res.pack.chunks === packInfo.chunk_count) {
    const buf = Buffer.concat(packChunks);
    const sha = crypto.createHash('sha256').update(buf).digest();
    res.pack.sha256ok = Buffer.compare(sha, Buffer.from(packInfo.hash)) === 0;
    fs.writeFileSync('downloaded.mcpack', buf);
    res.pack.saved = buf.length;
    log('Pack geladen:', buf.length, 'Bytes, SHA-256', res.pack.sha256ok ? 'stimmt' : 'FALSCH');
    packPhase = 'done';
    rawWrite('resource_pack_client_response', { response_status: 'have_all_packs', response_status_name: '', resourcepackids: [] });
  }
});
let queue = COMMANDS.slice();
let lastCommandAt = 0;

function send(name, data) {
  try { client.queue(name, data); return true; } catch (e) {
    res.errors.push(name + ': ' + String(e).slice(0, 300)); log('FEHLER', name, String(e).slice(0, 200)); return false;
  }
}

function command(cmd) {
  lastCommandAt = Date.now();
  res.commands.push(cmd);
  log('Befehl', cmd);
  const f = fieldsOf('packet_command_request') || [];
  const data = { command: cmd, origin: { type: 'player', uuid, request_id: '', player_entity_id: 0n }, internal: false,
    version: 'latest' };
  // Version: je nach Protokoll Zahl oder Text
  const vt = proto && proto.types.packet_command_request && JSON.stringify(proto.types.packet_command_request);
  if (vt && /"name":"version","type":"(varint|i32|li32|zigzag32)"/.test(vt)) data.version = 72;
  if (f.includes('is_internal')) data.is_internal = false;
  send('command_request', data);
}

function nextCommand() {
  if (!queue.length || Date.now() - start > DURATION - 10000) return;
  command(queue.shift());
}

client.on('play_status', (p) => { log('play_status', p.status); });
client.on('start_game', (p) => { res.joined = true; log('start_game, Laufzeit-ID', String(p.runtime_entity_id)); });
client.on('player_list', (p) => {
  for (const r of (p.records && p.records.records) || []) if (r.username && r.username.includes('KubyBedrock') && r.uuid) uuid = r.uuid;
});
client.on('resource_packs_info', (p) => {
  const list = p.texture_packs || p.resource_packs || [];
  const packs = list.map((x) => (x.uuid || x.pack_id) + ' ' + (x.version || '') + ' ' + (x.size || ''));
  const ours = list.find((x) => [PACK_UUID, PACK_UUID_WIRE].includes(String(x.uuid || x.pack_id).toLowerCase()));
  if (ours) {
    res.pack.offered = true;
    packWanted = PACK_UUID + '_' + ours.version;
  } else if (PACK_UUID) {
    packPhase = 'done';     // nicht angeboten: normal weiter, die Auswertung meldet es
  }
  if (pendingComplete) {
    pendingComplete = false;
    if (packWanted && packPhase === 'idle') requestPack();
    else rawWrite('resource_pack_client_response', { response_status: 'completed', response_status_name: 'resourcepackstackfinished', resourcepackids: [] });
  }
  res.packs.push(...packs);
  log('Resource Packs angeboten:', packs.length, packs.join(' | ').slice(0, 300));
});
client.on('spawn', () => {
  res.spawned = true;
  log('gespawnt als', client.username, uuid);
  setTimeout(nextCommand, 6000);
});

client.on('modal_form_request', (p) => {
  let form;
  try { form = JSON.parse(p.data); } catch (e) { res.errors.push('Formular-JSON: ' + e); return; }
  const buttons = (form.buttons || []).map((b) => ({ text: b.text, image: b.image ? b.image.data : null }));
  const entry = { id: p.form_id, type: form.type, title: form.title, content: typeof form.content === 'string' ? form.content.slice(0, 600) : null,
    elements: Array.isArray(form.content) ? form.content.map((c) => c.type + ':' + (c.text || '')).slice(0, 20) : null,
    buttons: buttons.slice(0, 40), buttonCount: buttons.length, images: buttons.filter((b) => b.image).length,
    afterCommand: res.commands[res.commands.length - 1] };
  res.forms.push(entry);
  log(`Formular ${form.type} „${form.title}“: ${buttons.length} Knöpfe (${entry.images} mit Bild)`, entry.elements ? entry.elements.length + ' Felder' : '');
  setTimeout(() => answer(p.form_id, form), 1500);
});

function answer(id, form) {
  const f = fieldsOf('packet_modal_form_response') || ['form_id', 'has_response_data', 'data', 'has_cancel_reason', 'cancel_reason'];
  let data;
  if (form.type === 'form' && form.buttons && form.buttons.length) {
    // Knopf wählen: abwechselnd erster und zufälliger, damit Untermenüs aufgehen
    const i = res.clicks % 2 === 0 ? 0 : Math.floor(Math.random() * form.buttons.length);
    data = JSON.stringify(i);
    log('  klicke Knopf', i, '„' + String(form.buttons[i].text).replace(/\n/g, ' / ').slice(0, 80) + '“');
  } else if (form.type === 'custom_form') {
    data = JSON.stringify((form.content || []).map((c) => c.type === 'input' ? 'Bedrock-Test' : c.type === 'toggle' ? false
      : c.type === 'slider' ? (c.min || 0) : c.type === 'dropdown' || c.type === 'step_slider' ? (c.default || 0) : null));
    log('  fülle Formular aus');
  } else if (form.type === 'modal') {
    data = 'true';
  } else {
    data = 'null';
  }
  res.clicks++;
  const msg = { form_id: id, has_response_data: true, data, has_cancel_reason: false };
  if (!f.includes('has_response_data')) msg.data = data;
  send('modal_form_response', msg);
  // nach ein paar Klicks den nächsten Befehl
  setTimeout(() => { if (Date.now() - lastCommandAt > 4000) nextCommand(); }, 2500);
}

client.on('set_display_objective', (p) => {
  if (p.display_slot === 'sidebar') { res.sidebarTitle = p.display_name; log('Scoreboard-Titel:', JSON.stringify(p.display_name)); }
});
client.on('set_score', (p) => {
  for (const e of p.entries || []) if (e.custom_name && res.sidebarLines.length < 40) res.sidebarLines.push(e.custom_name);
});
client.on('text', (p) => {
  const m = p.message || '';
  if (res.chat.length < 60) res.chat.push(m.slice(0, 200));
  if (/error|fehler|exception/i.test(m)) log('Chat:', m.slice(0, 160));
});
client.on('disconnect', (p) => { res.disconnect = p.message || JSON.stringify(p); log('getrennt:', res.disconnect); });
client.on('kick', (p) => { res.disconnect = JSON.stringify(p); log('gekickt:', res.disconnect); });
client.on('error', (e) => { res.errors.push(String(e).slice(0, 300)); log('FEHLER', String(e).slice(0, 200)); });

// Ein Befehl alle 9 Sekunden, falls kein Formular weiterführt
setInterval(() => { if (res.spawned && Date.now() - lastCommandAt > 9000) nextCommand(); }, 3000);

setTimeout(() => {
  fs.writeFileSync('bedrock-result.json', JSON.stringify(res, (k, v) => typeof v === 'bigint' ? String(v) : v, 2));
  log(`ERGEBNIS: gespawnt=${res.spawned}, Formulare=${res.forms.length}, Klicks=${res.clicks}, Fehler=${res.errors.length}`);
  try { client.close(); } catch (e) { /* weg */ }
  setTimeout(() => process.exit(0), 1000);
}, DURATION);

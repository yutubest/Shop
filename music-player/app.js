/* Slimo Mūzika — personīgs mūzikas atskaņotājs bez reklāmām.
 * Avoti: savs serveris (server.py) → Piped → Invidious (ar automātisku pārslēgšanos). */
'use strict';

// ---------- Glabātuve ----------
const store = {
  get(k, d) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch { return d; } },
  set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} },
};

const DEFAULTS = {
  own: '',
  piped: [
    'https://api.piped.private.coffee',
    'https://pipedapi.kavin.rocks',
    'https://pipedapi.adminforge.de',
    'https://pipedapi.leptons.xyz',
    'https://pipedapi.nosebs.ru',
    'https://pipedapi.ducks.party',
    'https://pipedapi.reallyaweso.me',
    'https://pipedapi.drgns.space',
  ],
  inv: [
    'https://yewtu.be',
    'https://inv.nadeko.net',
    'https://invidious.nerdvpn.de',
    'https://yt.chocolatemoo53.com',
    'https://invidious.f5.si',
  ],
};

let settings = Object.assign({}, DEFAULTS, store.get('sm_settings', {}));
let liked = store.get('sm_liked', []);
let playlists = store.get('sm_pl', []);

const state = {
  queue: [], index: -1, shuffle: store.get('sm_shuffle', false),
  repeat: store.get('sm_repeat', 'off'), // off | all | one
  video: false, history: [], loadId: 0, searchType: 'music', openPl: null,
};

// ---------- Palīgfunkcijas ----------
const $ = (s) => document.querySelector(s);
const audio = $('#audio');
const video = $('#video');
const media = () => (state.video ? video : audio);
const thumb = (id) => `https://i.ytimg.com/vi/${id}/hqdefault.jpg`;
const clean = (u) => (u || '').trim().replace(/\/+$/, '');
const fmt = (s) => {
  if (!isFinite(s) || s < 0) return '0:00';
  s = Math.floor(s);
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = String(s % 60).padStart(2, '0');
  return h ? `${h}:${String(m).padStart(2, '0')}:${x}` : `${m}:${x}`;
};
const esc = (t) => String(t ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
let toastT;
function toast(msg) {
  const el = $('#toast'); el.textContent = msg; el.classList.add('show');
  clearTimeout(toastT); toastT = setTimeout(() => el.classList.remove('show'), 1800);
}
async function getJSON(url, ms = 9000) {
  const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), ms);
  try {
    const r = await fetch(url, { signal: ctl.signal });
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return await r.json();
  } finally { clearTimeout(t); }
}

// ---------- Avoti ----------
// Saraksts ar avotiem secībā; tas, kas pēdējais strādāja, tiek pārvietots uz priekšu.
let preferred = store.get('sm_pref', null);
function sources() {
  const list = [];
  const own = clean(settings.own) || clean(autoOwn);
  if (own) list.push({ kind: 'own', base: own });
  settings.piped.map(clean).filter(Boolean).forEach((b) => list.push({ kind: 'piped', base: b }));
  settings.inv.map(clean).filter(Boolean).forEach((b) => list.push({ kind: 'inv', base: b }));
  if (preferred) {
    const i = list.findIndex((s) => s.kind === preferred.kind && s.base === preferred.base && s.kind !== 'own');
    if (i > 0 && list[0].kind !== 'own') list.unshift(list.splice(i, 1)[0]);
  }
  return list;
}
function markGood(src) { if (src.kind !== 'own') { preferred = src; store.set('sm_pref', src); } }

// Papildus Invidious serveri no publiskā saraksta (ja pieejams)
(async () => {
  try {
    const data = await getJSON('https://api.invidious.io/instances.json?sort_by=health', 8000);
    const extra = data.filter(([, i]) => i.type === 'https' && i.api && i.cors).map(([, i]) => clean(i.uri));
    extra.forEach((u) => { if (!settings.inv.includes(u)) settings.inv.push(u); });
  } catch {}
})();

// Ja aplikācija atvērta no paša server.py, izmanto to automātiski
let autoOwn = '';
if (location.protocol.startsWith('http')) {
  getJSON('api/search?q=', 3000).then((d) => { if (Array.isArray(d)) autoOwn = location.origin + location.pathname.replace(/\/[^/]*$/, ''); }).catch(() => {});
}

const idFromUrl = (u) => (u.match(/[?&]v=([\w-]{11})/) || [])[1];

async function searchWith(src, q, type) {
  if (src.kind === 'own') {
    return getJSON(`${src.base}/api/search?q=${encodeURIComponent(q)}&type=${type}`, 30000);
  }
  if (src.kind === 'piped') {
    const filter = type === 'music' ? 'music_songs' : 'videos';
    const d = await getJSON(`${src.base}/search?q=${encodeURIComponent(q)}&filter=${filter}`);
    return (d.items || []).filter((i) => i.type === 'stream' && i.url).map((i) => ({
      id: idFromUrl(i.url), title: i.title, artist: (i.uploaderName || '').replace(/ - Topic$/, ''), duration: i.duration,
    })).filter((t) => t.id);
  }
  const d = await getJSON(`${src.base}/api/v1/search?q=${encodeURIComponent(q + (type === 'music' ? ' audio' : ''))}&type=video`);
  return d.filter((i) => i.type === 'video').map((i) => ({
    id: i.videoId, title: i.title, artist: (i.author || '').replace(/ - Topic$/, ''), duration: i.lengthSeconds,
  }));
}

async function search(q, type) {
  let lastErr;
  for (const src of sources()) {
    try {
      const res = await searchWith(src, q, type);
      if (res && res.length) { markGood(src); return res; }
    } catch (e) { lastErr = e; }
  }
  if (lastErr) throw lastErr;
  return [];
}

const streamCache = new Map();
async function streamWith(src, id, wantVideo) {
  if (src.kind === 'own') return `${src.base}/api/${wantVideo ? 'video' : 'audio'}/${id}`;
  if (src.kind === 'piped') {
    const d = await getJSON(`${src.base}/streams/${id}`, 12000);
    if (wantVideo) {
      const v = (d.videoStreams || []).filter((s) => !s.videoOnly && s.url);
      v.sort((a, b) => (b.mimeType?.includes('mp4') ? 1 : 0) - (a.mimeType?.includes('mp4') ? 1 : 0) || (parseInt(b.quality) || 0) - (parseInt(a.quality) || 0));
      if (v[0]) return v[0].url;
      throw new Error('nav video');
    }
    const a = (d.audioStreams || []).filter((s) => s.url);
    a.sort((x, y) => (y.mimeType?.includes('mp4') ? 1 : 0) - (x.mimeType?.includes('mp4') ? 1 : 0) || (y.bitrate || 0) - (x.bitrate || 0));
    if (a[0]) return a[0].url;
    throw new Error('nav audio');
  }
  const d = await getJSON(`${src.base}/api/v1/videos/${id}?local=true`, 12000);
  const abs = (u) => (u.startsWith('http') ? u : src.base + u);
  if (wantVideo) {
    const f = (d.formatStreams || []).filter((s) => s.url);
    if (f.length) return abs(f[f.length - 1].url);
    throw new Error('nav video');
  }
  const a = (d.adaptiveFormats || []).filter((s) => s.type?.startsWith('audio') && s.url);
  a.sort((x, y) => (y.type.includes('mp4') ? 1 : 0) - (x.type.includes('mp4') ? 1 : 0) || (+y.bitrate || 0) - (+x.bitrate || 0));
  if (a[0]) return abs(a[0].url);
  throw new Error('nav audio');
}

async function resolveStream(id, wantVideo, exclude = new Set()) {
  const key = id + (wantVideo ? ':v' : ':a');
  const c = streamCache.get(key);
  if (c && Date.now() - c.t < 3600e3 && !exclude.has(c.src.base)) return c;
  for (const src of sources()) {
    if (exclude.has(src.base)) continue;
    try {
      const url = await streamWith(src, id, wantVideo);
      const r = { url, src, t: Date.now() };
      streamCache.set(key, r); markGood(src);
      return r;
    } catch {}
  }
  throw new Error('Neviens serveris neatbildēja');
}

// ---------- Atskaņošana ----------
let currentSrc = null, failed = new Set();

function current() { return state.queue[state.index]; }

function playQueue(tracks, i) {
  state.queue = tracks.slice(); state.index = i; state.history = [];
  loadAndPlay();
}

async function loadAndPlay(resumeAt = 0, retry = false) {
  const t = current(); if (!t) return;
  const myId = ++state.loadId;
  if (!retry) failed = new Set();
  updateNowPlaying();
  setStatus('Ielādē…');
  const el = media(), other = state.video ? audio : video;
  other.pause();
  try {
    const r = await resolveStream(t.id, state.video, failed);
    if (myId !== state.loadId) return;
    currentSrc = r.src;
    el.src = r.url;
    if (resumeAt) el.currentTime = resumeAt;
    await el.play().catch(() => {});
    setStatus('');
    saveLast();
    prefetchNext();
  } catch (e) {
    if (myId !== state.loadId) return;
    setStatus('Neizdevās ielādēt: ' + e.message);
    toast('Neizdevās — mēģinu nākamo dziesmu');
    setTimeout(() => { if (myId === state.loadId) next(true); }, 1500);
  }
}

function onMediaError() {
  const t = current(); if (!t || !currentSrc) return;
  // Saite nestrādā — mēģinām citu serveri
  failed.add(currentSrc.base);
  streamCache.delete(t.id + (state.video ? ':v' : ':a'));
  if (failed.size < 4) loadAndPlay(media().currentTime || 0, true);
  else { setStatus('Šo dziesmu neizdevās atskaņot'); next(true); }
}

function prefetchNext() {
  const n = peekNext(); if (n == null) return;
  const t = state.queue[n]; if (t) resolveStream(t.id, false).catch(() => {});
}

function peekNext() {
  if (!state.queue.length) return null;
  if (state.shuffle) {
    if (state._shuffleNext == null || state._shuffleNext === state.index) {
      state._shuffleNext = state.queue.length > 1
        ? (state.index + 1 + Math.floor(Math.random() * (state.queue.length - 1))) % state.queue.length : 0;
    }
    return state._shuffleNext;
  }
  if (state.index + 1 < state.queue.length) return state.index + 1;
  return state.repeat === 'all' ? 0 : null;
}

function next(auto = false) {
  if (auto && state.repeat === 'one') { media().currentTime = 0; media().play(); return; }
  let n = peekNext();
  if (n == null) { if (auto) { setPlayIcons(false); return; } n = 0; }
  state.history.push(state.index);
  state.index = n; state._shuffleNext = null;
  loadAndPlay();
}

function prev() {
  if (media().currentTime > 3) { media().currentTime = 0; return; }
  if (state.shuffle && state.history.length) state.index = state.history.pop();
  else if (state.index > 0) state.index--;
  else if (state.repeat === 'all') state.index = state.queue.length - 1;
  else { media().currentTime = 0; return; }
  state._shuffleNext = null;
  loadAndPlay();
}

function togglePlay() {
  const el = media();
  if (!el.getAttribute('src')) { if (current()) { loadAndPlay(state.resumeAt || 0); state.resumeAt = 0; } return; }
  el.paused ? el.play() : el.pause();
}

async function toggleVideo() {
  const t = current(); if (!t) return;
  const at = media().currentTime || 0;
  state.video = !state.video;
  $('#video').hidden = !state.video; $('#full-art').hidden = state.video;
  $('#video-btn').textContent = state.video ? '🎵 Tikai audio' : '🎬 Rādīt video';
  if (!state.video) { video.pause(); video.removeAttribute('src'); video.load(); }
  else { audio.pause(); }
  await loadAndPlay(at);
  if (state.video) toast('Video režīmā mūzika var apstāties ar izslēgtu ekrānu');
}

// Notikumi abiem elementiem
for (const el of [audio, video]) {
  el.addEventListener('timeupdate', () => { if (el === media()) onTime(); });
  el.addEventListener('play', () => { if (el === media()) setPlayIcons(true); });
  el.addEventListener('pause', () => { if (el === media()) setPlayIcons(false); });
  el.addEventListener('ended', () => { if (el === media()) next(true); });
  el.addEventListener('error', () => { if (el === media() && el.getAttribute('src')) onMediaError(); });
}

let lastPos = 0;
function onTime() {
  const el = media(), d = el.duration || current()?.duration || 0;
  $('#cur').textContent = fmt(el.currentTime); $('#dur').textContent = fmt(d);
  const p = d ? el.currentTime / d : 0;
  if (!seeking) $('#seek').value = Math.round(p * 1000);
  $('#mini-bar').style.width = (p * 100) + '%';
  if ('mediaSession' in navigator && d && isFinite(d) && Date.now() - lastPos > 1000) {
    lastPos = Date.now();
    try { navigator.mediaSession.setPositionState({ duration: d, position: Math.min(el.currentTime, d), playbackRate: el.playbackRate }); } catch {}
  }
  if (Date.now() - (onTime.saved || 0) > 5000) { onTime.saved = Date.now(); saveLast(); }
}
let seeking = false;
$('#seek').addEventListener('input', () => { seeking = true; });
$('#seek').addEventListener('change', () => {
  const el = media(), d = el.duration || 0;
  if (d) el.currentTime = ($('#seek').value / 1000) * d;
  seeking = false;
});

function setPlayIcons(playing) {
  $('#play-btn').textContent = playing ? '⏸' : '▶';
  $('#mini-play').textContent = playing ? '⏸' : '▶';
  if ('mediaSession' in navigator) navigator.mediaSession.playbackState = playing ? 'playing' : 'paused';
}
function setStatus(s) { $('#player-status').textContent = s; }

// Bloķēšanas ekrāna / austiņu pogas
if ('mediaSession' in navigator) {
  const ms = navigator.mediaSession;
  const h = {
    play: () => (media().getAttribute('src') ? media().play() : togglePlay()), pause: () => media().pause(),
    previoustrack: prev, nexttrack: () => next(),
    seekto: (e) => { media().currentTime = e.seekTime; },
    seekbackward: (e) => { media().currentTime -= e.seekOffset || 10; },
    seekforward: (e) => { media().currentTime += e.seekOffset || 10; },
  };
  for (const [a, fn] of Object.entries(h)) { try { ms.setActionHandler(a, fn); } catch {} }
}

function updateNowPlaying() {
  const t = current(); if (!t) return;
  $('#mini').hidden = false;
  $('#mini-title').textContent = $('#full-title').textContent = t.title;
  $('#mini-artist').textContent = $('#full-artist').textContent = t.artist || '';
  $('#mini-art').src = $('#full-art').src = thumb(t.id);
  $('#like-btn').classList.toggle('liked', isLiked(t.id));
  $('#like-btn').textContent = isLiked(t.id) ? '♥' : '♡';
  document.title = t.title + ' — Slimo Mūzika';
  if ('mediaSession' in navigator && window.MediaMetadata) {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: t.title, artist: t.artist || '', album: 'Slimo Mūzika',
      artwork: [{ src: thumb(t.id), sizes: '480x360', type: 'image/jpeg' }],
    });
  }
  document.querySelectorAll('.track').forEach((li) => li.classList.toggle('playing', li.dataset.id === t.id));
}

function saveLast() {
  const t = current(); if (!t) return;
  store.set('sm_last', { queue: state.queue.slice(0, 300), index: state.index, time: media().currentTime || 0 });
}

// ---------- Patīk un saraksti ----------
const isLiked = (id) => liked.some((t) => t.id === id);
function toggleLike(t) {
  if (isLiked(t.id)) { liked = liked.filter((x) => x.id !== t.id); toast('Noņemts no “Patīk”'); }
  else { liked.unshift({ id: t.id, title: t.title, artist: t.artist, duration: t.duration }); toast('Pievienots “Patīk” ❤️'); }
  store.set('sm_liked', liked);
  renderLiked(); updateNowPlaying(); refreshLikeIcons();
}
function refreshLikeIcons() {
  document.querySelectorAll('.track .like-x').forEach((b) => {
    const on = isLiked(b.closest('.track').dataset.id);
    b.classList.toggle('liked', on); b.textContent = on ? '♥' : '♡';
  });
}
function savePl() { store.set('sm_pl', playlists); }

function addToPlaylist(t) {
  const names = playlists.map((p, i) => `${i + 1}. ${p.name}`).join('\n');
  const ans = prompt((names ? 'Ieraksti numuru vai jauna saraksta nosaukumu:\n' + names : 'Jauna saraksta nosaukums:'), '');
  if (ans == null || !ans.trim()) return;
  let pl = playlists[parseInt(ans, 10) - 1];
  if (!pl || String(parseInt(ans, 10)) !== ans.trim()) {
    pl = { id: Date.now().toString(36), name: ans.trim(), tracks: [] }; playlists.push(pl);
  }
  if (!pl.tracks.some((x) => x.id === t.id)) pl.tracks.push({ id: t.id, title: t.title, artist: t.artist, duration: t.duration });
  savePl(); renderPlaylists(); toast(`Pievienots: ${pl.name}`);
}

// ---------- Saraksta zīmēšana ----------
function renderTracks(ul, tracks, opts = {}) {
  ul.innerHTML = tracks.map((t) => `
    <li class="track${current()?.id === t.id ? ' playing' : ''}" data-id="${esc(t.id)}">
      <img loading="lazy" src="${thumb(t.id)}" alt="" />
      <div class="info"><div class="tt">${esc(t.title)}</div><div class="ta">${esc(t.artist)}${t.duration ? ' · ' + fmt(t.duration) : ''}</div></div>
      <button class="act like-x${isLiked(t.id) ? ' liked' : ''}" aria-label="Patīk">${isLiked(t.id) ? '♥' : '♡'}</button>
      <button class="act more" aria-label="${opts.remove ? 'Noņemt' : 'Pievienot sarakstam'}">${opts.remove ? '✕' : '＋'}</button>
    </li>`).join('');
  ul.querySelectorAll('.track').forEach((li, i) => {
    li.addEventListener('click', (e) => {
      if (e.target.closest('.like-x')) return toggleLike(tracks[i]);
      if (e.target.closest('.more')) return opts.remove ? opts.remove(i) : addToPlaylist(tracks[i]);
      playQueue(tracks, i);
    });
  });
}

let lastResults = [];
function renderLiked() {
  renderTracks($('#liked-list'), liked);
  if (!liked.length) $('#liked-list').innerHTML = '<p class="hint">Vēl nav nevienas dziesmas. Spied ♡ pie dziesmas, lai pievienotu.</p>';
}
function renderPlaylists() {
  const ul = $('#playlists');
  ul.innerHTML = playlists.map((p, i) => `<li data-i="${i}"><b>${esc(p.name)}</b><span>${p.tracks.length} dziesmas</span></li>`).join('')
    || '<p class="hint">Izveido savu pirmo sarakstu ar “＋ Jauns”.</p>';
  ul.querySelectorAll('li').forEach((li) => li.addEventListener('click', () => openPlaylist(+li.dataset.i)));
  if (state.openPl != null && playlists[state.openPl]) openPlaylist(state.openPl);
}
function openPlaylist(i) {
  state.openPl = i; const p = playlists[i];
  $('#playlists').hidden = true; $('#new-playlist').parentElement.hidden = true; $('#playlist-detail').hidden = false;
  $('#pl-title').textContent = p.name;
  renderTracks($('#pl-tracks'), p.tracks, { remove: (k) => { p.tracks.splice(k, 1); savePl(); openPlaylist(i); } });
  if (!p.tracks.length) $('#pl-tracks').innerHTML = '<p class="hint">Tukšs. Meklē dziesmas un spied ＋.</p>';
}
function closePlaylist() {
  state.openPl = null;
  $('#playlists').hidden = false; $('#new-playlist').parentElement.hidden = false; $('#playlist-detail').hidden = true;
  renderPlaylists();
}
function playShuffled(tracks) {
  if (!tracks.length) return;
  state.shuffle = true; store.set('sm_shuffle', true); syncModeBtns();
  playQueue(tracks, Math.floor(Math.random() * tracks.length));
}

// ---------- Notikumi UI ----------
document.querySelectorAll('.tabs button').forEach((b) => b.addEventListener('click', () => {
  document.querySelectorAll('.tabs button').forEach((x) => x.classList.toggle('active', x === b));
  document.querySelectorAll('.view').forEach((v) => v.classList.toggle('active', v.id === 'view-' + b.dataset.view));
  if (b.dataset.view === 'search') $('#search-input').focus();
}));
document.querySelectorAll('.chip').forEach((c) => c.addEventListener('click', () => {
  document.querySelectorAll('.chip').forEach((x) => x.classList.toggle('active', x === c));
  state.searchType = c.dataset.type;
  if ($('#search-input').value.trim()) doSearch();
}));

let searchId = 0;
async function doSearch() {
  const q = $('#search-input').value.trim(); if (!q) return;
  const my = ++searchId;
  $('#search-status').textContent = 'Meklē…'; $('#search-results').innerHTML = '';
  try {
    const res = await search(q, state.searchType);
    if (my !== searchId) return;
    lastResults = res;
    $('#search-status').textContent = res.length ? '' : 'Nekas netika atrasts';
    renderTracks($('#search-results'), res);
  } catch (e) {
    if (my !== searchId) return;
    $('#search-status').textContent = 'Serveri neatbild. Pamēģini vēlreiz vai pievieno citu serveri iestatījumos.';
  }
}
$('#search-form').addEventListener('submit', (e) => { e.preventDefault(); $('#search-input').blur(); doSearch(); });

$('#mini').addEventListener('click', (e) => { if (!e.target.closest('button')) $('#full').hidden = false; });
$('#close-full').addEventListener('click', () => { $('#full').hidden = true; });
$('#mini-play').addEventListener('click', togglePlay);
$('#play-btn').addEventListener('click', togglePlay);
$('#mini-next').addEventListener('click', () => next());
$('#next-btn').addEventListener('click', () => next());
$('#mini-prev').addEventListener('click', prev);
$('#prev-btn').addEventListener('click', prev);
$('#like-btn').addEventListener('click', () => current() && toggleLike(current()));
$('#add-pl-btn').addEventListener('click', () => current() && addToPlaylist(current()));
$('#video-btn').addEventListener('click', toggleVideo);

function syncModeBtns() {
  $('#shuffle-btn').classList.toggle('off', !state.shuffle);
  $('#repeat-btn').classList.toggle('off', state.repeat === 'off');
  $('#repeat-btn').textContent = state.repeat === 'one' ? '🔂' : '🔁';
}
$('#shuffle-btn').addEventListener('click', () => {
  state.shuffle = !state.shuffle; state._shuffleNext = null; store.set('sm_shuffle', state.shuffle); syncModeBtns();
  toast(state.shuffle ? 'Jaukšana ieslēgta' : 'Jaukšana izslēgta');
});
$('#repeat-btn').addEventListener('click', () => {
  state.repeat = { off: 'all', all: 'one', one: 'off' }[state.repeat]; store.set('sm_repeat', state.repeat); syncModeBtns();
  toast({ off: 'Atkārtošana izslēgta', all: 'Atkārtot visu', one: 'Atkārtot vienu dziesmu' }[state.repeat]);
});

$('#play-liked').addEventListener('click', () => { if (liked.length) { state.shuffle = false; syncModeBtns(); playQueue(liked, 0); } });
$('#shuffle-liked').addEventListener('click', () => playShuffled(liked));
$('#new-playlist').addEventListener('click', () => {
  const n = prompt('Saraksta nosaukums:'); if (!n || !n.trim()) return;
  playlists.push({ id: Date.now().toString(36), name: n.trim(), tracks: [] }); savePl(); renderPlaylists();
});
$('#back-playlists').addEventListener('click', closePlaylist);
$('#play-pl').addEventListener('click', () => { const p = playlists[state.openPl]; if (p?.tracks.length) { state.shuffle = false; syncModeBtns(); playQueue(p.tracks, 0); } });
$('#shuffle-pl').addEventListener('click', () => { const p = playlists[state.openPl]; if (p) playShuffled(p.tracks); });
$('#del-pl').addEventListener('click', () => {
  const p = playlists[state.openPl];
  if (p && confirm(`Dzēst sarakstu “${p.name}”?`)) { playlists.splice(state.openPl, 1); savePl(); closePlaylist(); }
});

// Iestatījumi
function fillSettings() {
  $('#own-server').value = settings.own || '';
  $('#piped-list').value = settings.piped.join('\n');
  $('#inv-list').value = settings.inv.join('\n');
}
$('#save-settings').addEventListener('click', () => {
  const lines = (s) => s.split('\n').map(clean).filter(Boolean);
  settings = { own: clean($('#own-server').value), piped: lines($('#piped-list').value), inv: lines($('#inv-list').value) };
  store.set('sm_settings', settings); streamCache.clear(); preferred = null; store.set('sm_pref', null);
  toast('Saglabāts');
});
$('#reset-settings').addEventListener('click', () => {
  settings = JSON.parse(JSON.stringify(DEFAULTS)); store.set('sm_settings', {}); fillSettings(); toast('Atjaunots');
});
$('#export').addEventListener('click', () => {
  const blob = new Blob([JSON.stringify({ liked, playlists }, null, 2)], { type: 'application/json' });
  const a = document.createElement('a'); a.href = URL.createObjectURL(blob); a.download = 'slimo-muzika.json'; a.click();
});
$('#import').addEventListener('change', async (e) => {
  const f = e.target.files[0]; if (!f) return;
  try {
    const d = JSON.parse(await f.text());
    (d.liked || []).forEach((t) => { if (t.id && !isLiked(t.id)) liked.push(t); });
    (d.playlists || []).forEach((p) => { if (p.name && Array.isArray(p.tracks)) playlists.push(p); });
    store.set('sm_liked', liked); savePl(); renderLiked(); renderPlaylists(); toast('Importēts');
  } catch { toast('Nederīgs fails'); }
});

// ---------- Starts ----------
fillSettings(); syncModeBtns(); renderLiked(); renderPlaylists();
const last = store.get('sm_last', null);
if (last?.queue?.length) {
  state.queue = last.queue; state.index = Math.min(last.index, last.queue.length - 1);
  state.resumeAt = last.time || 0; // turpina no tās pašas vietas, kad nospiež ▶
  updateNowPlaying();
}
if ('serviceWorker' in navigator && location.protocol !== 'file:') {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

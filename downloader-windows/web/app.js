'use strict';
// داونلودر — واجهة المستخدم. كل نص قادم من الإنترنت يُدرج كنص (textContent) لا كـ HTML.

const TOKEN = new URLSearchParams(location.search).get('t') || '';
const api = async (path, body) => {
  const r = await fetch('/api/' + path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { 'X-Token': TOKEN, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const j = await r.json().catch(() => null);
  if (!r.ok) throw j || { title: 'تعذّر إتمام العملية' };
  return j;
};
const mediaURL = (kind, id) => `/api/${kind}?id=${encodeURIComponent(id)}&t=${encodeURIComponent(TOKEN)}`;

// ---------------------------------------------------------------- أدوات DOM
const ICONS = {
  home: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z',
  library: 'M4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6zm16-4H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-8 12.5v-9l6 4.5-6 4.5z',
  settings: 'M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.488.488 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z',
  link: 'M3.9 12c0-1.71 1.39-3.1 3.1-3.1h4V7H7c-2.76 0-5 2.24-5 5s2.24 5 5 5h4v-1.9H7c-1.71 0-3.1-1.39-3.1-3.1zM8 13h8v-2H8v2zm9-6h-4v1.9h4c1.71 0 3.1 1.39 3.1 3.1s-1.39 3.1-3.1 3.1h-4V17h4c2.76 0 5-2.24 5-5s-2.24-5-5-5z',
  paste: 'M19 2h-4.18C14.4.84 13.3 0 12 0c-1.3 0-2.4.84-2.82 2H5c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-7 0c.55 0 1 .45 1 1s-.45 1-1 1-1-.45-1-1 .45-1 1-1zm7 18H5V4h2v3h10V4h2v16z',
  search: 'M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z',
  close: 'M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z',
  download: 'M5 20h14v-2H5v2zM19 9h-4V3H9v6H5l7 7 7-7z',
  check: 'M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z',
  play: 'M8 5v14l11-7z', pause: 'M6 19h4V5H6v14zm8-14v14h4V5h-4z',
  refresh: 'M17.65 6.35A7.958 7.958 0 0 0 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08A5.99 5.99 0 0 1 12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z',
  trash: 'M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z',
  edit: 'M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a.996.996 0 0 0 0-1.41l-2.34-2.34a.996.996 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z',
  save: 'M19 12v7H5v-7H3v7c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2v-7h-2zm-6 .67 2.59-2.58L17 11.5l-5 5-5-5 1.41-1.41L11 12.67V3h2v9.67z',
  folder: 'M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z',
  video: 'M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z',
  audio: 'M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z',
  image: 'M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z',
  other: 'M6 2c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6H6zm7 7V3.5L18.5 9H13z',
  history: 'M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42A8.954 8.954 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z',
  subs: 'M20 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zM4 12h4v2H4v-2zm10 6H4v-2h10v2zm6 0h-4v-2h4v2zm0-4H10v-2h10v2z',
  cut: 'M9.64 7.64c.23-.5.36-1.05.36-1.64 0-2.21-1.79-4-4-4S2 3.79 2 6s1.79 4 4 4c.59 0 1.14-.13 1.64-.36L10 12l-2.36 2.36C7.14 14.13 6.59 14 6 14c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4c0-.59-.13-1.14-.36-1.64L12 14l7 7h3v-1L9.64 7.64zM6 8c-1.1 0-2-.89-2-2s.9-2 2-2 2 .89 2 2-.9 2-2 2zm0 12c-1.1 0-2-.89-2-2s.9-2 2-2 2 .89 2 2-.9 2-2 2zm6-7.5c-.28 0-.5-.22-.5-.5s.22-.5.5-.5.5.22.5.5-.22.5-.5.5zM19 3l-6 6 2 2 7-7V3h-3z',
  mic: 'M12 14c1.66 0 2.99-1.34 2.99-3L15 5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z',
  chev: 'M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z',
  lock: 'M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z',
  error: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z',
  open: 'M19 19H5V5h7V3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z',
  copy: 'M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z',
  plus: 'M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z', minus: 'M19 13H5v-2h14v2z',
  all: 'M18 7l-1.41-1.41-6.34 6.34 1.41 1.41L18 7zm4.24-1.41L11.66 16.17 7.48 12l-1.41 1.41L11.66 19l12-12-1.42-1.41zM.41 13.41 6 19l1.41-1.41L1.83 12 .41 13.41z',
};
const NS = 'http://www.w3.org/2000/svg';
function icon(name, cls) {
  const s = document.createElementNS(NS, 'svg');
  s.setAttribute('viewBox', '0 0 24 24');
  s.setAttribute('class', 'i' + (cls ? ' ' + cls : ''));
  const p = document.createElementNS(NS, 'path');
  p.setAttribute('d', ICONS[name] || ICONS.other);
  s.appendChild(p);
  return s;
}
function h(tag, props, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'value') el.value = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat(Infinity)) {
    if (kid == null || kid === false) continue;
    el.appendChild(typeof kid === 'object' ? kid : document.createTextNode(String(kid)));
  }
  return el;
}
// add: يُلحق أبناءً بعنصر مع فكّ المصفوفات المتداخلة (Element.append لا يفكّها).
function add(el, ...kids) {
  for (const kid of kids.flat(Infinity)) {
    if (kid == null || kid === false) continue;
    el.appendChild(typeof kid === 'object' ? kid : document.createTextNode(String(kid)));
  }
  return el;
}
// dots: أجزاء مفصولة بنقطة، كل جزء معزول اتجاهياً حتى لا تتبعثر الأرقام واللاتينية داخل السطر العربي.
function dots(parts, cls) {
  return parts.filter(Boolean).map((part, i) => [i ? ' · ' : '', h('bdi', { class: cls || '' }, part)]);
}
const $ = (sel) => document.querySelector(sel);

// ---------------------------------------------------------------- تنسيق
const fmt = {
  bytes(b) {
    if (!b || b <= 0) return '—';
    const kb = b / 1024, mb = kb / 1024, gb = mb / 1024;
    if (gb >= 1) return gb.toFixed(2) + ' ج.ب';
    if (mb >= 100) return mb.toFixed(0) + ' م.ب';
    if (mb >= 1) return mb.toFixed(1) + ' م.ب';
    return Math.max(1, kb).toFixed(0) + ' ك.ب';
  },
  clock(sec) {
    sec = Math.max(0, Math.round(sec || 0));
    const hh = Math.floor(sec / 3600), m = Math.floor(sec % 3600 / 60), s = sec % 60;
    return (hh ? hh + ':' + String(m).padStart(2, '0') : m) + ':' + String(s).padStart(2, '0');
  },
  parseTime(t) {
    const parts = String(t).trim().split(':').map((x) => (/^\d+$/.test(x.trim()) ? +x : NaN));
    if (!parts.length || parts.length > 3 || parts.some(isNaN)) return null;
    return parts.reduce((a, v) => a * 60 + v, 0);
  },
  eta(sec) {
    if (!sec || sec <= 0) return '';
    if (sec >= 3600) return `باقي ${Math.floor(sec / 3600)} س ${Math.floor(sec % 3600 / 60)} د`;
    if (sec >= 60) return `باقي ${Math.floor(sec / 60)} د ${sec % 60} ث`;
    return `باقي ${sec} ث`;
  },
  date(ms) {
    const d = new Date(ms);
    const months = ['يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو', 'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'];
    return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()} · ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  },
  files(n) { return n === 1 ? 'ملف واحد' : n === 2 ? 'ملفان' : n <= 10 ? n + ' ملفات' : n + ' ملفاً'; },
  kind: { video: 'فيديو', audio: 'صوت', image: 'صورة', other: 'ملف' },
};
const QUALITIES = [[0, 'الأعلى المتاح'], [2160, '4K'], [1440, '1440p'], [1080, '1080p'], [720, '720p'], [480, '480p'], [360, '360p'], [-1, 'صوت فقط']];
const BROWSERS = [['', 'بدون'], ['chrome', 'Chrome'], ['edge', 'Edge'], ['firefox', 'Firefox'], ['brave', 'Brave'], ['opera', 'Opera']];

// ---------------------------------------------------------------- الحالة
const S = {
  view: 'home', setup: { ready: false, stage: '', progress: -1 }, settings: {}, saveRoot: '', customSave: false,
  tasks: [], library: [], history: [], version: '', cacheBytes: 0,
  home: { url: '', state: 'idle', error: null, info: null, audio: false, optId: null, items: new Set(), audioLang: '', sub: null, subEmbed: true, subItems: null, trim: null, clip: '' },
  lib: { tab: 'files', q: '', sort: 'new', sel: new Set(), saving: new Set() },
};
const activeCount = () => S.tasks.filter((t) => t.state === 'running' || t.state === 'queued').length;

let toastTimer;
function toast(text) {
  const t = $('#toast');
  t.textContent = text;
  t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.hidden = true; }, 4200);
}

// ---------------------------------------------------------------- نوافذ
function closeModal() { $('#modal').replaceChildren(); }
function modal(title, content, buttons, cls) {
  const box = h('div', { class: 'dialog ' + (cls || '') }, title && h('h3', {}, title), h('div', { class: 'content' }, content),
    buttons && h('div', { class: 'foot' }, buttons));
  const ov = h('div', { class: 'overlay', onmousedown: (e) => { if (e.target === ov) closeModal(); } }, box);
  $('#modal').replaceChildren(ov);
  return box;
}
function confirmDialog(title, text, okLabel, onOk, danger) {
  modal(title, h('p', { style: 'margin:0' }, text), [
    h('button', { class: 'btn ' + (danger ? 'danger' : 'gold'), onclick: () => { closeModal(); onOk(); } }, okLabel),
    h('button', { class: 'btn', onclick: closeModal }, 'إلغاء'),
  ]);
}
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeModal(); });

// ---------------------------------------------------------------- الهيكل
function renderNav() {
  const n = activeCount();
  $('#nav').replaceChildren(...[['home', 'الرئيسية'], ['library', 'تحميلاتي'], ['settings', 'الإعدادات']].map(([id, label]) =>
    h('button', { class: 'nav-btn' + (S.view === id ? ' on' : ''), onclick: () => go(id) }, icon(id), h('span', { class: 't' }, label),
      id === 'library' && n > 0 && h('span', { class: 'count num' }, n))));
  $('#engine').replaceChildren(h('span', { class: 'dot' + (S.setup.ready ? '' : ' warn') }),
    h('b', {}, S.setup.ready ? 'المحرك جاهز' : 'تجهيز المحرك'), h('br'),
    S.setup.version ? 'yt-dlp ' + S.setup.version : (S.setup.stage || ''));
}
function go(view) { S.view = view; S.lib.sel.clear(); render(); if (view === 'home') checkClipboard(); }
function render() {
  renderNav();
  const main = $('#main');
  const top = main.scrollTop;
  main.replaceChildren(S.view === 'home' ? homeView() : S.view === 'library' ? libraryView() : settingsView());
  main.scrollTop = S.view === 'home' ? top : 0;
}
function renderSetup() {
  const el = $('#setup');
  if (S.setup.ready) { el.hidden = true; return; }
  const p = S.setup.progress;
  el.hidden = false;
  el.replaceChildren(h('div', { class: 'box' },
    document.querySelector('.brand .mark').cloneNode(true),
    h('h2', {}, S.setup.error ? 'تعذّر تجهيز المحرك' : 'تجهيز داونلودر لأول مرة'),
    h('p', {}, S.setup.error || S.setup.stage || 'لحظات…'),
    !S.setup.error && h('div', { class: 'bar ' + (p < 0 ? 'ind' : 'live') }, h('i', { style: `width:${Math.max(0, p) * 100}%` })),
    !S.setup.error && p >= 0 && h('p', { class: 'num' }, Math.round(p * 100) + '%'),
    S.setup.error && h('button', { class: 'btn gold big', style: 'margin-top:20px', onclick: () => api('setup/retry', {}) }, icon('refresh'), 'إعادة المحاولة'),
    h('p', { class: 'hint', style: 'margin-top:18px' }, 'يُنزَّل محرك التحميل (yt-dlp و FFmpeg) مرة واحدة من مصادره الرسمية، ثم يعمل البرنامج مباشرة.')));
}

// ---------------------------------------------------------------- الرئيسية
function homeView() {
  const H = S.home, info = H.info;
  const input = h('input', { id: 'url', class: 'ltr', placeholder: 'ألصق الرابط هنا', value: H.url, spellcheck: 'false', autocomplete: 'off',
    oninput: (e) => {
      H.url = e.target.value;
      // نحدّث الزرّين مباشرة بلا إعادة رسم حتى لا يفقد الحقل التركيز أثناء الكتابة.
      const goBtn = $('#go'), x = $('#clear');
      if (goBtn) goBtn.disabled = !H.url.trim();
      if (x) x.hidden = !H.url;
      if (H.state === 'error') { H.state = 'idle'; render(); const el = $('#url'); el.focus(); el.setSelectionRange(el.value.length, el.value.length); }
    },
    onkeydown: (e) => { if (e.key === 'Enter') analyze(); } });
  const v = h('div', { class: 'view' });
  if (H.state === 'idle') {
    add(v, h('div', { class: 'hero' }, h('h1', {}, 'أي رابط.', h('br'), h('span', { class: 'g' }, 'بأعلى جودة.')),
      h('p', {}, 'ألصق رابط فيديو أو صورة أو مقطع صوتي، اختر الجودة، والباقي يتم على جهازك.')));
  }
  add(v, h('div', { class: 'urlbar' },
    h('div', { class: 'field' }, icon('link'), input, h('button', { id: 'clear', class: 'x', title: 'مسح', hidden: !H.url, onclick: resetHome }, icon('close'))),
    h('button', { class: 'btn big', onclick: pasteClipboard }, icon('paste'), 'لصق من الحافظة'),
    H.state === 'loading' ? h('button', { class: 'btn big', onclick: cancelAnalyze }, icon('close'), 'إلغاء')
      : H.state === 'ready' ? h('button', { class: 'btn big', onclick: resetHome }, icon('refresh'), 'رابط جديد')
        : h('button', { id: 'go', class: 'btn gold big', disabled: !H.url.trim(), onclick: analyze }, icon('search'), 'تحليل')));
  if (H.clip && H.state !== 'loading') {
    add(v, h('div', { class: 'clip' }, h('div', { class: 't' }, h('b', {}, 'وجدنا رابطاً منسوخاً'), h('div', { class: 'u ltr' }, H.clip)),
      h('button', { class: 'link', onclick: () => { H.url = H.clip; H.clip = ''; analyze(); } }, 'استخدامه'),
      h('button', { class: 'icon-btn', title: 'تجاهل', onclick: () => { H.clip = ''; render(); } }, icon('close'))));
  }
  if (H.state === 'idle') {
    add(v, h('div', { class: 'label' }, 'يعمل مع'),
      h('div', { class: 'chips' }, ['يوتيوب', 'انستجرام', 'تيك توك', 'تويتر / X', 'فيسبوك', 'سناب شات', 'بنترست', 'وأكثر من ألف موقع'].map((x) => h('span', { class: 'chip' }, x))),
      h('div', { class: 'tips' },
        h('div', { class: 'tip' }, h('b', {}, '١'), 'انسخ الرابط من المتصفح وألصقه هنا — أو اضغط «لصق من الحافظة».'),
        h('div', { class: 'tip' }, h('b', {}, '٢'), 'اختر الجودة، والترجمة أو قصّ مقطع إن أردت، ثم «تحميل».'),
        h('div', { class: 'tip' }, h('b', {}, '٣'), 'الملف يبقى في «تحميلاتي» حتى تضغط «حفظ في الجهاز».')));
  } else if (H.state === 'loading') {
    add(v, h('div', { class: 'result' },
      h('div', {}, h('div', { class: 'skel', style: 'aspect-ratio:16/9;border-radius:20px' }), h('div', { class: 'skel', style: 'height:18px;width:80%;margin-top:14px' }), h('div', { class: 'skel', style: 'height:14px;width:40%;margin-top:8px' })),
      h('div', {}, h('p', { class: 'sub', style: 'margin:0 0 12px' }, 'جاري تحليل الرابط واستخراج الجودات…'), [0, 1, 2, 3].map(() => h('div', { class: 'skel', style: 'height:58px;margin-bottom:8px' })))));
  } else if (H.state === 'error') {
    const e = H.error || {};
    add(v, h('div', { class: 'err' }, h('div', { class: 'ic' }, icon(e.error === 'private' ? 'lock' : 'error')),
      h('h3', {}, e.title || 'تعذّر إتمام العملية'), h('p', {}, e.hint || ''), e.detail && h('code', {}, e.detail),
      h('div', { class: 'row' }, h('button', { class: 'btn', onclick: analyze }, icon('refresh'), 'إعادة المحاولة'),
        e.error === 'private' && h('button', { class: 'btn', onclick: () => go('settings') }, icon('lock'), 'إعدادات الحسابات'))));
  } else if (info) {
    add(v, resultView(info));
  }
  return v;
}

function resultView(info) {
  const H = S.home;
  const direct = info.direct.length > 0, gallery = info.viaGallery, playlist = info.isPlaylist;
  const opt = info.options.find((o) => o.id === H.optId) || null;
  const audio = !direct && !gallery && (H.audio || !info.options.length);
  const kind = direct ? info.direct[0].kind : 'video';
  const left = h('div', { class: 'media' },
    h('div', { class: 'thumb' }, icon(kind), info.thumbnail && h('img', { src: info.thumbnail, referrerpolicy: 'no-referrer', onerror: (e) => e.target.remove() }),
      h('div', { class: 'tags' }, info.platform && h('span', { class: 'tag' }, info.platform), playlist && h('span', { class: 'tag' }, info.items.length + ' عناصر'),
        info.duration > 0 && h('span', { class: 'tag end num' }, fmt.clock(info.duration)))),
    h('h2', {}, info.title), info.uploader && h('div', { class: 'by' }, info.uploader),
    info.duplicate && h('div', { class: 'dup' }, icon('history'), h('div', {}, h('b', {}, 'سبق أن حمّلت هذا الرابط'),
      h('small', {}, dots([fmt.date(info.duplicate.at), info.duplicate.label])))));

  const right = h('div', {});
  if (direct) {
    const f = info.direct[0];
    add(right, h('div', { class: 'q on', style: 'cursor:default' }, h('span', { class: 'radio' }),
      h('div', {}, h('div', { class: 'name' }, fmt.kind[f.kind] + ' — تحميل مباشر'), h('div', { class: 'det' }, 'بالجودة الأصلية')),
      h('div', { class: 'size num' }, fmt.bytes(f.size))));
  } else {
    if (!gallery && info.options.length && info.hasAudio) {
      add(right, h('div', { class: 'seg' },
        h('button', { class: H.audio ? '' : 'on', onclick: () => { H.audio = false; render(); } }, icon('video'), 'فيديو'),
        h('button', { class: H.audio ? 'on' : '', onclick: () => { H.audio = true; render(); } }, icon('audio'), 'صوت فقط')));
    }
    const optsBox = optionsBox(info, audio);
    if (optsBox) add(right, optsBox);
    if (playlist && (!gallery || info.items.length > 1)) {
      const all = H.items.size === info.items.length;
      add(right, h('div', { class: 'label' }, 'العناصر', h('button', { class: 'link end', style: 'margin-inline-start:auto',
        onclick: () => { H.items = all ? new Set() : new Set(info.items.map((i) => i.index)); render(); } }, all ? 'إلغاء تحديد الكل' : 'تحديد الكل')),
      info.items.map((it) => {
        const on = H.items.has(it.index);
        return h('button', { class: 'pl' + (on ? ' on' : ''), onclick: () => { on ? H.items.delete(it.index) : H.items.add(it.index); render(); } },
          h('span', { class: 'pt' }, it.thumbnail ? h('img', { src: it.thumbnail, loading: 'lazy', referrerpolicy: 'no-referrer' }) : icon('video')),
          h('span', { class: 't' }, it.title), it.duration > 0 && h('span', { class: 'num', style: 'color:var(--dim);font-size:12px' }, fmt.clock(it.duration)),
          h('span', { class: 'check' + (on ? ' on' : '') }, on && icon('check')));
      }));
    }
    if (gallery) {
      add(right, h('div', { class: 'q on', style: 'cursor:default;margin-top:14px' }, h('span', { class: 'radio' }),
        h('div', {}, h('div', { class: 'name' }, 'الملفات الأصلية'), h('div', { class: 'det' }, 'تُحمَّل بجودتها الأصلية كما رُفعت'))));
    } else if (audio) {
      add(right, h('div', { class: 'label' }, 'الصوت'), h('div', { class: 'q on', style: 'cursor:default' }, h('span', { class: 'radio' }),
        h('div', {}, h('div', { class: 'name' }, 'صوت فقط · ' + (S.settings.audioFormat || 'mp3').toUpperCase()), h('div', { class: 'det' }, 'أعلى جودة صوت متاحة. غيّر الصيغة من الإعدادات.')),
        !playlist && info.audioSize > 0 && h('div', { class: 'size num' }, fmt.bytes(info.audioSize), h('small', {}, 'تقريبي'))));
    } else {
      add(right, h('div', { class: 'label' }, 'الجودة', h('span', { class: 'end' }, info.options.length + ' خيارات')),
        info.options.map((o, i) => h('button', { class: 'q' + (o.id === H.optId ? ' on' : ''), style: `animation-delay:${i * 45}ms`, onclick: () => { H.optId = o.id; render(); } },
          h('span', { class: 'radio' }),
          h('div', {}, h('div', { class: 'name num' }, o.label, o.badge && h('span', { class: 'badge' + (i === 0 ? ' goldb' : '') }, o.badge)), h('div', { class: 'det' }, o.detail)),
          h('div', { class: 'size num' }, o.size ? fmt.bytes(o.size) : '—', o.size && o.approx ? h('small', {}, 'تقريبي') : null))));
    }
  }
  // شريط التحميل الثابت
  let cap;
  const trimmed = H.trim && !playlist;
  if (direct) cap = ['ملف مباشر', info.direct[0].size ? fmt.bytes(info.direct[0].size) : null];
  else if (gallery) cap = [info.items.length > 1 ? `${H.items.size} من ${info.items.length}` : null, 'الجودة الأصلية'];
  else {
    const frac = trimmed && info.duration ? (H.trim[1] - H.trim[0]) / info.duration : 1;
    const size = audio ? info.audioSize : opt && opt.size;
    cap = [playlist ? `${H.items.size} من ${info.items.length}` : null, audio ? 'صوت ' + (S.settings.audioFormat || 'mp3').toUpperCase() : opt && opt.label,
      trimmed ? `${fmt.clock(H.trim[0])}–${fmt.clock(H.trim[1])}` : null, !playlist && size ? fmt.bytes(size * frac) + (audio || trimmed || (opt && opt.approx) ? ' تقريباً' : '') : null,
      H.sub && !audio ? 'ترجمة ' + H.sub.name : null];
  }
  const disabled = (playlist && H.items.size === 0);
  const cta = h('div', { class: 'cta' }, h('div', { class: 'cap' }, dots(cap, 'num')),
    h('button', { class: 'btn gold big', style: 'min-width:220px', disabled, onclick: () => startDownload(false) }, icon('download'), 'تحميل'));
  return h('div', {}, h('div', { class: 'result' }, left, right), cta);
}

// خيارات إضافية: مسار الصوت، الترجمة، قصّ مقطع — كل خيار يظهر فقط عندما يكون متاحاً.
function optionsBox(info, audio) {
  const H = S.home, playlist = info.isPlaylist, rows = [];
  const row = (ic, k, v, active, onclick) => h('button', { class: 'opt' + (active ? ' active' : ''), onclick }, icon(ic), h('span', { class: 'k' }, k), h('span', { class: 'v' }, v), icon('chev', 'chev'));
  if (info.audioTracks.length && !playlist) {
    const t = info.audioTracks.find((x) => x.lang === H.audioLang);
    rows.push(row('mic', 'مسار الصوت', t ? t.name + (t.original ? ' (الأصلي)' : '') : 'الافتراضي', t && !t.original, () => audioDialog(info)));
  }
  const subChoices = playlist ? [{ lang: 'ar', name: 'العربية', auto: true, note: '' }, { lang: 'en', name: 'الإنجليزية', auto: true, note: '' }] : info.subtitles;
  if (subChoices.length && !audio && info.options.length && !info.viaGallery) {
    rows.push(row('subs', 'الترجمة', H.sub ? `${H.sub.name} · ${H.subEmbed ? 'مدمجة' : 'ملف SRT'}${playlist && H.subItems ? ` · ${H.subItems.size} فيديو` : ''}` : 'بدون', !!H.sub, () => subsDialog(info, subChoices)));
  }
  if (info.duration > 1 && !playlist) {
    rows.push(row('cut', 'قصّ مقطع', H.trim ? `${fmt.clock(H.trim[0])} – ${fmt.clock(H.trim[1])}` : 'الفيديو كاملاً', !!H.trim, () => trimDialog(info)));
  }
  return rows.length ? h('div', { class: 'opts' }, rows) : null;
}
function audioDialog(info) {
  modal('مسار الصوت', info.audioTracks.map((t) => h('button', { class: 'choice' + (t.lang === S.home.audioLang ? ' on' : ''), onclick: () => { S.home.audioLang = t.lang; closeModal(); render(); } },
    t.name, t.original && h('span', { class: 'badge' }, 'الأصلي'), t.lang === S.home.audioLang && icon('check', 'ok'))), [h('button', { class: 'btn', onclick: closeModal }, 'إغلاق')]);
}
function subsDialog(info, choices) {
  const H = S.home, playlist = info.isPlaylist;
  const draw = () => {
    const chosen = info.items.filter((i) => H.items.has(i.index));
    const content = [
      h('div', { class: 'seg small' }, [[true, 'دمج داخل الفيديو'], [false, 'ملف SRT مستقل']].map(([v, l]) => h('button', { class: H.subEmbed === v ? 'on' : '', onclick: () => { H.subEmbed = v; draw(); } }, l))),
      h('p', { class: 'hint' }, H.subEmbed ? 'تُضاف كمسار داخل الملف يمكن إظهاره أو إخفاؤه من المشغّل.' : 'يُحفظ ملف الترجمة بجانب الفيديو باسمه نفسه، فتلتقطه المشغّلات مثل VLC تلقائياً.'),
      h('button', { class: 'choice' + (!H.sub ? ' on' : ''), onclick: () => { H.sub = null; H.subItems = null; if (playlist) draw(); else { closeModal(); render(); } } }, 'بدون ترجمة', !H.sub && icon('check', 'ok')),
      choices.map((t) => { const on = H.sub && H.sub.lang === t.lang; return h('button', { class: 'choice' + (on ? ' on' : ''), onclick: () => { H.sub = t; if (playlist) draw(); else { closeModal(); render(); } } },
        t.name, t.note && h('span', { class: 'badge' }, t.note), on && icon('check', 'ok')); }),
    ];
    if (playlist && H.sub) {
      content.push(h('div', { class: 'label' }, 'تُضاف إلى'),
        h('div', { class: 'seg small' }, [[true, 'كل الفيديوهات'], [false, 'فيديوهات معيّنة']].map(([all, l]) => h('button', { class: (H.subItems === null) === all ? 'on' : '', onclick: () => { H.subItems = all ? null : new Set(chosen.map((i) => i.index)); draw(); } }, l))),
        H.subItems && chosen.map((it) => { const on = H.subItems.has(it.index); return h('button', { class: 'choice' + (on ? ' on' : ''), style: 'font-size:14px', onclick: () => { on ? H.subItems.delete(it.index) : H.subItems.add(it.index); draw(); } },
          h('span', { class: 'check' + (on ? ' on' : '') }, on && icon('check')), it.title); }),
        h('p', { class: 'hint' }, 'تُستخدم ترجمة صاحب الفيديو إن وُجدت، وإلا ترجمة المنصة الآلية. الفيديو الذي لا ترجمة له يُحمَّل بدونها.'));
    } else if (!playlist) {
      content.push(h('p', { class: 'hint' }, 'إذا رفضت المنصة الترجمة الآلية (شائع في يوتيوب) يكتمل الفيديو وتُرفق الترجمة الأصلية بدلاً منها.'));
    }
    modal('الترجمة', content, [h('button', { class: 'btn', onclick: () => { closeModal(); render(); } }, playlist ? 'تم' : 'إغلاق')]);
  };
  draw();
}
function trimDialog(info) {
  const H = S.home, dur = info.duration;
  let a = H.trim ? H.trim[0] : 0, b = H.trim ? H.trim[1] : dur;
  const from = h('input', { class: 'input num', value: fmt.clock(a) }), to = h('input', { class: 'input num', value: fmt.clock(b) });
  const lo = h('input', { type: 'range', min: 0, max: dur, step: 1, value: a }), hi = h('input', { type: 'range', min: 0, max: dur, step: 1, value: b });
  const fill = h('div', { class: 'fill' }), hint = h('p', { class: 'hint' });
  const sync = () => {
    fill.style.left = (a / dur * 100) + '%'; fill.style.width = ((b - a) / dur * 100) + '%';
    const ok = a < b && b <= dur;
    hint.className = 'hint' + (ok ? '' : ' bad');
    hint.textContent = ok ? `مدة المقطع ${fmt.clock(b - a)}. القصّ يتم عند أقرب نقطة قطع في الفيديو، فقد يزيد أو ينقص ثوانيَ قليلة.` : 'اكتب الوقت بصيغة دقائق:ثوانٍ (مثل 1:30) وتأكد أن النهاية بعد البداية وضمن مدة الفيديو.';
    apply.disabled = !ok;
  };
  lo.oninput = () => { a = Math.min(+lo.value, b - 1); lo.value = a; from.value = fmt.clock(a); sync(); };
  hi.oninput = () => { b = Math.max(+hi.value, a + 1); hi.value = b; to.value = fmt.clock(b); sync(); };
  from.oninput = () => { const t = fmt.parseTime(from.value); if (t !== null) { a = t; lo.value = Math.min(t, dur); } else a = b; sync(); };
  to.oninput = () => { const t = fmt.parseTime(to.value); if (t !== null) { b = t; hi.value = Math.min(t, dur); } else b = a; sync(); };
  const apply = h('button', { class: 'btn gold', onclick: () => { H.trim = (a === 0 && b === dur) ? null : [a, b]; closeModal(); render(); } }, 'تطبيق');
  modal('قصّ مقطع', [h('p', { style: 'margin:0' }, `اختر بداية ونهاية الجزء الذي تريد تحميله. مدة الفيديو ${fmt.clock(dur)}.`),
    h('div', { class: 'range' }, h('div', { class: 'track' }), fill, lo, hi),
    h('div', { class: 'two' }, h('div', {}, h('label', {}, 'من'), from), h('div', {}, h('label', {}, 'إلى'), to)), hint],
  [apply, h('button', { class: 'btn', onclick: () => { if (H.trim) { H.trim = null; render(); } closeModal(); } }, H.trim ? 'إلغاء القصّ' : 'إغلاق')]);
  sync();
}

let analyzeSeq = 0;
async function analyze() {
  const H = S.home;
  if (!H.url.trim()) return;
  const seq = ++analyzeSeq;
  Object.assign(H, { state: 'loading', clip: '', error: null, info: null });
  render();
  try {
    const info = await api('analyze', { url: H.url });
    if (seq !== analyzeSeq) return;
    const dq = S.settings.defaultQuality || 0;
    let opt = info.options[0] || null;
    if (dq > 0) opt = info.options.find((o) => o.side <= dq * 1.07) || info.options[info.options.length - 1] || null;
    const orig = info.audioTracks.find((t) => t.original);
    Object.assign(H, { state: 'ready', info, url: info.sourceUrl, optId: opt && opt.id, audio: dq === -1 && info.hasAudio && !info.direct.length && !info.viaGallery,
      items: new Set(info.items.map((i) => i.index)), audioLang: orig ? orig.lang : '', sub: null, subEmbed: true, subItems: null, trim: null });
  } catch (e) {
    if (seq !== analyzeSeq || (e && e.error === 'cancelled')) return;
    Object.assign(H, { state: 'error', error: e });
  }
  render();
}
function cancelAnalyze() { analyzeSeq++; api('analyze/cancel', {}).catch(() => {}); S.home.state = 'idle'; render(); }
function resetHome() { analyzeSeq++; Object.assign(S.home, { url: '', state: 'idle', info: null, error: null, trim: null, sub: null }); render(); $('#url') && $('#url').focus(); }
async function pasteClipboard() {
  let text = '';
  try { text = (await api('clipboard')).url; } catch (e) { /* تجاهل */ }
  if (!text) { try { text = await navigator.clipboard.readText(); } catch (e) { /* قد يمنعه المتصفح */ } }
  if (text) { S.home.url = text.trim(); S.home.clip = ''; analyze(); } else toast('لا يوجد رابط في الحافظة');
}
let lastClip = '';
async function checkClipboard() {
  try {
    const { url } = await api('clipboard');
    if (url && url !== lastClip && url !== S.home.url && S.home.state === 'idle') { lastClip = url; S.home.clip = url; if (S.view === 'home') render(); }
  } catch (e) { /* تجاهل */ }
}
window.addEventListener('focus', () => { if (S.view === 'home') checkClipboard(); });

function startDownload(force) {
  const H = S.home, info = H.info;
  if (!info) return;
  if (info.duplicate && !force) {
    return confirmDialog('حمّلت هذا من قبل', `«${info.title}» — آخر تحميل: ${fmt.date(info.duplicate.at)}${info.duplicate.label ? ' · ' + info.duplicate.label : ''}`, 'تحميل مرة أخرى', () => startDownload(true));
  }
  const base = { url: info.sourceUrl, title: info.title, thumbnail: info.thumbnail, mediaKey: info.mediaKey, platform: info.platform };
  let specs = [];
  if (info.direct.length) {
    specs = info.direct.map((f) => ({ ...base, url: f.url, title: info.direct.length === 1 ? info.title : f.name, mode: 'direct', label: 'تحميل مباشر', directName: f.name, expected: f.size }));
  } else if (info.viaGallery) {
    specs = [{ ...base, mode: 'gallery', label: 'الجودة الأصلية', items: [...H.items].sort((a, b) => a - b) }];
  } else {
    const opt = info.options.find((o) => o.id === H.optId) || info.options[0];
    const audio = H.audio || !opt, playlist = info.isPlaylist;
    const items = playlist ? [...H.items].sort((a, b) => a - b) : undefined;
    const trim = !playlist && H.trim;
    const frac = trim && info.duration ? (trim[1] - trim[0]) / info.duration : 1;
    const size = audio ? info.audioSize : opt && opt.size;
    const sub = !audio ? H.sub : null;
    const label = [audio ? 'صوت ' + (S.settings.audioFormat || 'mp3').toUpperCase() : opt.label, trim ? `${fmt.clock(trim[0])}–${fmt.clock(trim[1])}` : null].filter(Boolean).join(' · ');
    const spec = { ...base, mode: audio ? 'audio' : 'video', selector: opt && opt.selector, label, audioFormat: S.settings.audioFormat, items,
      expected: size ? Math.round(size * frac) : 0, duration: trim ? trim[1] - trim[0] : info.duration,
      audioLang: !playlist && info.audioTracks.length ? H.audioLang : '', subLang: sub ? sub.lang : '', subSrc: sub && !playlist ? (sub.src || '') : '', subEmbed: H.subEmbed,
      trimStart: trim ? trim[0] : undefined, trimEnd: trim ? trim[1] : undefined };
    if (playlist && sub && H.subItems) {
      const withSub = items.filter((i) => H.subItems.has(i)), without = items.filter((i) => !H.subItems.has(i));
      if (withSub.length) specs.push({ ...spec, items: withSub, label: label + ' · مع ترجمة' });
      if (without.length) specs.push({ ...spec, items: without, subLang: '' });
    } else specs = [spec];
  }
  api('download', { specs }).then(() => {
    info.duplicate = { at: Date.now(), label: specs[0].label, title: info.title };
    toast('بدأ التحميل');
    go('library');
  }).catch((e) => toast(e.detail || e.title));
}

// ---------------------------------------------------------------- تحميلاتي
function libraryView() {
  const L = S.lib;
  const v = h('div', { class: 'view' + (L.sel.size ? ' selecting' : '') });
  add(v, h('div', { class: 'head' }, h('div', { class: 'grow' }, h('h1', {}, 'تحميلاتي'),
    h('p', { class: 'sub' }, S.library.length ? `${fmt.files(S.library.length)} · ${fmt.bytes(S.library.reduce((a, i) => a + i.size, 0))}` : 'ملفاتك تبقى هنا حتى تحفظها في الجهاز')),
  h('div', { class: 'tabs' }, h('button', { class: L.tab === 'files' ? 'on' : '', onclick: () => { L.tab = 'files'; render(); } }, 'الملفات'),
    h('button', { class: L.tab === 'history' ? 'on' : '', onclick: () => { L.tab = 'history'; L.sel.clear(); render(); } }, 'السجلّ' + (S.history.length ? '  ' + S.history.length : '')))));
  if (L.tab === 'history') { add(v, historyList()); return v; }
  add(v, h('div', { id: 'tasks' }, tasksBlock()), h('div', { id: 'files' }, filesBlock()));
  return v;
}
function tasksBlock() {
  if (!S.tasks.length) return [];
  return [h('div', { class: 'label' }, 'قيد التحميل', h('span', { class: 'end num' }, S.tasks.length)), S.tasks.map(taskCard)];
}
function taskCard(t) {
  const running = t.state === 'running', failed = t.state === 'failed';
  const act = (id, action) => () => api('task', { id, action });
  return h('div', { class: 'task' + (failed ? ' failed' : '') },
    h('div', { class: 'top' }, t.spec.thumbnail ? h('img', { class: 'pt', src: t.spec.thumbnail, referrerpolicy: 'no-referrer', onerror: (e) => { e.target.style.visibility = 'hidden'; } }) : h('span', { class: 'pt' }),
      h('div', { class: 'tt' }, h('b', {}, t.spec.title), h('small', { class: 'num' }, dots([t.spec.label, t.spec.expected ? fmt.bytes(t.spec.expected) : null]))),
      running || t.state === 'queued' ? h('button', { class: 'icon-btn', title: 'إيقاف مؤقت', onclick: act(t.id, 'pause') }, icon('pause'))
        : h('button', { class: 'icon-btn goldc', title: failed ? 'إعادة المحاولة' : 'استكمال', onclick: act(t.id, 'resume') }, icon(failed ? 'refresh' : 'play')),
      h('button', { class: 'icon-btn', title: 'إلغاء', onclick: act(t.id, 'cancel') }, icon('close'))),
    failed ? h('div', { class: 'emsg' }, t.error || 'فشل التحميل') : [
      h('div', { class: 'bar' + (running ? (t.progress < 0 ? ' ind' : ' live') : '') }, h('i', { style: `width:${t.state === 'queued' ? 0 : Math.max(0, t.progress) * 100}%` })),
      h('div', { class: 'meta' }, h('span', {}, t.state === 'queued' ? 'في الانتظار' : t.state === 'paused' ? 'متوقف مؤقتاً' : (t.stage || 'جاري التحميل')),
        running && h('span', { class: 'num', style: 'color:var(--dim)' }, dots([t.speed, fmt.eta(t.eta)])),
        t.progress >= 0 && t.state !== 'queued' && h('span', { class: 'pc num', style: running ? '' : 'color:var(--muted)' }, Math.round(t.progress * 100) + '%'))]);
}
function visibleItems() {
  const L = S.lib, q = L.q.trim().toLowerCase();
  let list = q ? S.library.filter((i) => i.title.toLowerCase().includes(q)) : [...S.library];
  const order = { video: 0, audio: 1, image: 2, other: 3 };
  list.sort({ new: (a, b) => b.addedAt - a.addedAt, old: (a, b) => a.addedAt - b.addedAt, name: (a, b) => a.title.localeCompare(b.title, 'ar'),
    size: (a, b) => b.size - a.size, kind: (a, b) => order[a.kind] - order[b.kind] || b.addedAt - a.addedAt }[L.sort]);
  return list;
}
function filesBlock() {
  const L = S.lib;
  if (!S.library.length) {
    return S.tasks.length ? [] : [h('div', { class: 'empty' }, h('div', { class: 'ring' }, document.querySelector('.brand .mark').cloneNode(true)),
      h('h3', {}, 'لا توجد تحميلات بعد'), 'ألصق رابطاً في الرئيسية وسيظهر الملف هنا مع معاينة وتشغيل داخل البرنامج.')];
  }
  const items = visibleItems(), selected = S.library.filter((i) => L.sel.has(i.id));
  const out = [];
  if (L.sel.size) {
    out.push(h('div', { class: 'selbar' }, h('button', { class: 'icon-btn', title: 'إلغاء التحديد', onclick: () => { L.sel.clear(); renderFiles(); } }, icon('close')),
      h('b', { class: 'num' }, L.sel.size + ' محدد'),
      h('button', { class: 'btn sm', onclick: () => { items.forEach((i) => L.sel.add(i.id)); renderFiles(); } }, icon('all'), 'تحديد الكل'),
      h('button', { class: 'btn sm gold', onclick: () => saveItems(selected) }, icon('save'), 'حفظ في الجهاز'),
      h('button', { class: 'btn sm danger', onclick: () => deleteItems(selected) }, icon('trash'), 'حذف')));
  } else {
    out.push(h('div', { class: 'toolbar' },
      h('div', { class: 'field search' }, icon('search'), h('input', { id: 'q', placeholder: 'ابحث باسم الملف', value: L.q, oninput: (e) => { L.q = e.target.value; renderGrid(); } })),
      h('select', { class: 'sel', title: 'فرز', onchange: (e) => { L.sort = e.target.value; renderGrid(); } },
        [['new', 'الأحدث أولاً'], ['old', 'الأقدم أولاً'], ['name', 'الاسم'], ['size', 'الحجم (الأكبر أولاً)'], ['kind', 'النوع']].map(([val, l]) => h('option', { value: val, selected: L.sort === val }, l))),
      h('span', { class: 'grow' }),
      h('button', { class: 'btn', onclick: () => api('opensave', {}) }, icon('folder'), 'فتح مجلد الحفظ'),
      h('button', { class: 'btn', disabled: !items.length, onclick: () => saveItems(visibleItems()) }, icon('save'), 'حفظ الكل في الجهاز')));
  }
  out.push(h('div', { id: 'grid', class: 'grid', style: 'margin-top:14px' }, gridCards()));
  return out;
}
function gridCards() {
  const L = S.lib, items = visibleItems();
  if (!items.length) return [h('p', { class: 'sub' }, 'لا يوجد ملف بهذا الاسم.')];
  return items.map((it) => {
    const sel = L.sel.has(it.id), saving = L.saving.has(it.id);
    const thumb = it.thumb === 'local' || it.thumb === 'file' ? mediaURL('thumb', it.id) : it.thumb;
    return h('div', { class: 'card' + (sel ? ' sel' : '') },
      h('button', { class: 'pick', title: 'تحديد', onclick: () => { sel ? L.sel.delete(it.id) : L.sel.add(it.id); renderFiles(); } }, sel && icon('check')),
      h('div', { class: 'pic', onclick: () => (L.sel.size ? (sel ? L.sel.delete(it.id) : L.sel.add(it.id), renderFiles()) : preview(it)) },
        icon(it.kind), thumb && h('img', { src: thumb, loading: 'lazy', referrerpolicy: 'no-referrer', onerror: (e) => e.target.remove() }),
        (it.kind === 'video' || it.kind === 'audio') && h('span', { class: 'play' }, icon('play')),
        h('span', { class: 'tag kind' }, dots([fmt.kind[it.kind], it.ext ? it.ext.toUpperCase() : null]))),
      h('div', { class: 'body' }, h('b', {}, it.title), h('small', { class: 'num' }, dots([fmt.bytes(it.size), it.duration ? fmt.clock(it.duration) : null]))),
      h('div', { class: 'acts' },
        h('button', { class: 'btn sm', disabled: saving, onclick: () => saveItems([it]) }, saving ? h('span', { class: 'spin' }) : icon('save'), 'حفظ في الجهاز'),
        h('button', { class: 'icon-btn', title: 'فتح بالبرنامج الافتراضي', onclick: () => api('library', { action: 'open', ids: [it.id] }) }, icon('open')),
        h('button', { class: 'icon-btn', title: 'إعادة تسمية', onclick: () => renameDialog(it) }, icon('edit')),
        h('button', { class: 'icon-btn danger', title: 'حذف', onclick: () => deleteItems([it]) }, icon('trash'))));
  });
}
function renderTasks() { const el = $('#tasks'); if (el) el.replaceChildren(...[tasksBlock()].flat(Infinity)); }
function renderFiles() { const el = $('#files'); if (el) { el.replaceChildren(...[filesBlock()].flat(Infinity)); $('#main .view').classList.toggle('selecting', S.lib.sel.size > 0); } }
function renderGrid() { const el = $('#grid'); if (el) el.replaceChildren(...gridCards()); }

async function saveItems(items) {
  if (!items.length) return;
  const run = async () => {
    items.forEach((i) => S.lib.saving.add(i.id)); renderGrid();
    try {
      const r = await api('library', { action: 'save', ids: items.map((i) => i.id) });
      toast(r.saved === r.total ? `حُفظ ${fmt.files(r.saved)} في ${items.length === 1 ? r.dir : r.root}` : `حُفظ ${r.saved} من ${r.total} — الباقي ما زال في البرنامج`);
    } catch (e) { toast('تعذّر الحفظ — الملف ما زال في البرنامج'); }
    items.forEach((i) => { S.lib.saving.delete(i.id); S.lib.sel.delete(i.id); }); renderFiles();
  };
  if (items.length === 1) return run();
  confirmDialog('حفظ في الجهاز؟', `عدد الملفات: ${items.length}. تُنقل إلى «${S.saveRoot}» مرتّبة في مجلدات: فيديو، صوتيات، صور، أخرى. كل ملف ينجح حفظه يُزال من البرنامج، وما يفشل يبقى هنا كما هو.`, 'حفظ', run);
}
function deleteItems(items) {
  confirmDialog(items.length === 1 ? 'حذف الملف؟' : 'حذف الملفات المحددة؟',
    (items.length === 1 ? `سيُحذف «${items[0].title}» نهائياً.` : `ستُحذف ${items.length} ملفات نهائياً.`) + ' إن أردت الاحتفاظ بها فاحفظها في الجهاز أولاً.',
    'حذف نهائي', () => api('library', { action: 'delete', ids: items.map((i) => i.id) }).then(() => { items.forEach((i) => S.lib.sel.delete(i.id)); toast('تم الحذف'); }), true);
}
function renameDialog(it) {
  const input = h('input', { class: 'input', value: it.title });
  const ok = () => { if (!input.value.trim()) return; api('library', { action: 'rename', ids: [it.id], title: input.value }).then(() => toast('تمت إعادة التسمية')).catch((e) => toast(e.detail || 'تعذّرت إعادة التسمية')); closeModal(); };
  input.onkeydown = (e) => { if (e.key === 'Enter') ok(); };
  modal('إعادة تسمية', [input, h('p', { class: 'hint' }, `الامتداد (${(it.ext || '').toUpperCase()}) يبقى كما هو.`)], [h('button', { class: 'btn gold', onclick: ok }, 'حفظ الاسم'), h('button', { class: 'btn', onclick: closeModal }, 'إلغاء')]);
  input.focus(); input.select();
}
function preview(it) {
  const src = mediaURL('media', it.id);
  const stage = it.kind === 'video' ? h('video', { src, controls: true, autoplay: true }) : it.kind === 'audio' ? h('audio', { src, controls: true, autoplay: true })
    : it.kind === 'image' ? h('img', { src }) : h('p', { style: 'padding:40px;color:var(--muted)' }, 'لا تتوفر معاينة لهذا النوع — افتحه بالبرنامج الافتراضي.');
  modal('', [h('div', { class: 'phead' }, h('b', {}, it.title), h('small', { class: 'num', style: 'color:var(--muted)' }, `${fmt.kind[it.kind]} · ${fmt.bytes(it.size)}`),
    h('button', { class: 'btn sm', onclick: () => { closeModal(); saveItems([it]); } }, icon('save'), 'حفظ في الجهاز'),
    h('button', { class: 'btn sm', onclick: () => api('library', { action: 'open', ids: [it.id] }) }, icon('open'), 'فتح خارجياً'),
    h('button', { class: 'icon-btn', title: 'إغلاق', onclick: closeModal }, icon('close'))), h('div', { class: 'stage' }, stage)], null, 'player');
}
function historyList() {
  if (!S.history.length) return h('div', { class: 'empty' }, h('div', { class: 'ring' }, icon('history')), h('h3', {}, 'السجلّ فارغ'), 'كل رابط تحمّله يُسجَّل هنا، وننبّهك إذا حاولت تحميله مرة أخرى.');
  return h('div', {}, h('div', { class: 'toolbar' }, h('div', { class: 'label', style: 'margin:0' }, 'روابط حمّلتها'), h('span', { class: 'grow' }),
    h('button', { class: 'btn danger', onclick: () => confirmDialog('مسح السجلّ؟', 'يُمسح سجلّ الروابط فقط؛ ملفاتك لا تُمسّ. بعدها لن يُنبَّه عند تكرار رابط قديم.', 'مسح السجلّ', () => api('history', { action: 'clear' }), true) }, icon('trash'), 'مسح السجلّ')),
  h('div', { style: 'margin-top:14px' }, S.history.map((e) => h('div', { class: 'hist', onclick: () => { S.home.url = e.url; go('home'); analyze(); } },
    e.thumbnail ? h('img', { class: 'pt', style: 'width:72px;height:46px;border-radius:10px;object-fit:cover', src: e.thumbnail, loading: 'lazy', referrerpolicy: 'no-referrer', onerror: (ev) => { ev.target.style.visibility = 'hidden'; } }) : h('span', { style: 'width:72px;height:46px;border-radius:10px;background:var(--raised-hi);display:grid;place-items:center;color:var(--dim)' }, icon('video')),
    h('div', { class: 'tt' }, h('b', {}, e.title), h('small', { class: 'num' }, dots([e.platform, e.label])), h('small', { class: 'num', style: 'color:var(--dim)' }, fmt.date(e.at))),
    h('button', { class: 'icon-btn', title: 'نسخ الرابط', onclick: (ev) => { ev.stopPropagation(); navigator.clipboard.writeText(e.url).then(() => toast('نُسخ الرابط')).catch(() => {}); } }, icon('copy')),
    h('button', { class: 'icon-btn', title: 'إزالة من السجلّ', onclick: (ev) => { ev.stopPropagation(); api('history', { action: 'remove', id: e.id }); } }, icon('close'))))));
}

// ---------------------------------------------------------------- الإعدادات
function settingsView() {
  const s = S.settings;
  const set = (patch) => api('settings', patch).then(applySettings).then(render);
  const row = (title, sub, ...ctl) => h('div', { class: 'row' }, h('div', { class: 'txt' }, h('b', {}, title), sub && h('small', {}, sub)), ctl);
  const select = (options, value, onchange) => h('select', { class: 'sel', onchange: (e) => onchange(e.target.value) }, options.map(([v, l]) => h('option', { value: v, selected: String(v) === String(value) }, l)));
  const updBtn = h('button', { class: 'btn', onclick: async () => {
    updBtn.disabled = true; updBtn.replaceChildren(h('span', { class: 'spin' }), 'جاري التحديث');
    try { const r = await api('engine/update', {}); S.setup.version = r.version; toast(r.updated ? 'تم تحديث المحرك إلى ' + r.version : 'المحرك محدّث بالفعل'); } catch (e) { toast('تعذّر التحديث: ' + (e.detail || e.title)); }
    render();
  } }, icon('refresh'), 'تحديث');
  return h('div', { class: 'view', style: 'max-width:820px' }, h('h1', {}, 'الإعدادات'),
    h('div', { class: 'label' }, 'التحميل'),
    h('div', { class: 'group' },
      row('الجودة الافتراضية', 'تُحدَّد تلقائياً بعد تحليل الرابط. إن لم تتوفر يُحدَّد أقرب خيار أقل منها.', select(QUALITIES, s.defaultQuality, (v) => set({ defaultQuality: +v }))),
      row('صيغة الصوت', 'عند اختيار «صوت فقط»', h('div', { class: 'seg small' }, ['mp3', 'm4a'].map((f) => h('button', { class: s.audioFormat === f ? 'on' : '', onclick: () => set({ audioFormat: f }) }, f.toUpperCase())))),
      row('التحميلات المتوازية', 'عدد الملفات التي تُحمَّل في الوقت نفسه', h('div', { class: 'stepper' },
        h('button', { class: 'icon-btn', title: 'أقل', disabled: s.maxParallel <= 1, onclick: () => set({ maxParallel: s.maxParallel - 1 }) }, icon('minus')), h('span', { class: 'num' }, s.maxParallel),
        h('button', { class: 'icon-btn', title: 'أكثر', disabled: s.maxParallel >= 5, onclick: () => set({ maxParallel: s.maxParallel + 1 }) }, icon('plus'))))),
    h('div', { class: 'label' }, 'الحفظ'),
    h('div', { class: 'group' },
      row('مكان الحفظ', (S.customSave ? 'مجلد مخصص: ' : 'الافتراضي: ') + S.saveRoot + ' — بداخله مجلدات: فيديو، صوتيات، صور، أخرى',
        h('button', { class: 'btn', onclick: () => api('pickfolder', {}).then(applySettings).then(render).catch((e) => toast(e.detail || e.title)) }, icon('folder'), 'تغيير'),
        S.customSave && h('button', { class: 'btn', onclick: () => set({ saveDir: '' }) }, 'الافتراضي')),
      row('حفظ تلقائي بعد التحميل', 'ينقل الملف إلى مجلد الحفظ فور اكتماله بدل انتظار ضغط «حفظ في الجهاز»', h('div', { class: 'seg small' },
        [[false, 'لا'], [true, 'نعم']].map(([v, l]) => h('button', { class: !!s.autoSave === v ? 'on' : '', onclick: () => set({ autoSave: v }) }, l))))),
    h('div', { class: 'label' }, 'المحرك'),
    h('div', { class: 'group' }, row('محرك yt-dlp', `الإصدار ${S.setup.version || '…'} — حدّثه إذا توقف موقع عن العمل`, updBtn)),
    h('div', { class: 'label' }, 'الحسابات'),
    h('div', { class: 'group' },
      row('استخدام تسجيل الدخول من المتصفح', 'اختياري: للستوري والمحتوى الخاص. يقرأ البرنامج كوكيز المتصفح المختار من جهازك مباشرة ولا يرسلها لأي خادم. يُنصح بحساب ثانوي، و Firefox هو الأوثق.',
        select(BROWSERS, s.cookiesBrowser, (v) => set({ cookiesBrowser: v })))),
    h('div', { class: 'label' }, 'التخزين'),
    h('div', { class: 'group' }, row('مسح الكاش', `ملفات مؤقتة وتحميلات ملغاة: ${fmt.bytes(Math.max(S.cacheBytes, 1024))} — لا يمسّ ملفات «تحميلاتي»`,
      h('button', { class: 'btn', onclick: () => api('cache/clear', {}).then((r) => { S.cacheBytes = r.cacheBytes; toast('تم مسح الكاش'); render(); }).catch((e) => toast(e.detail || e.title)) }, 'مسح'))),
    h('div', { class: 'label' }, 'حول'),
    h('div', { class: 'group' }, row('داونلودر لويندوز', `الإصدار ${S.version} — بلا إعلانات، بلا تتبّع، وكل شيء يعمل على جهازك. يعتمد على yt-dlp و FFmpeg و gallery-dl مفتوحة المصدر. حمّل فقط ما تملك حق تحميله.`)));
}
function applySettings(view) { S.settings = view.settings; S.saveRoot = view.saveRoot; S.customSave = view.customSave; }

// ---------------------------------------------------------------- التشغيل
function onEvent(msg) {
  const { type, data } = msg;
  if (type === 'tasks') { S.tasks = data || []; renderNav(); if (S.view === 'library' && S.lib.tab === 'files') renderTasks(); }
  else if (type === 'library') { S.library = data || []; if (S.view === 'library') { if (S.lib.tab === 'files') renderFiles(); refreshLibHead(); } }
  else if (type === 'history') { S.history = data || []; if (S.view === 'library') (S.lib.tab === 'history' ? render() : refreshLibHead()); }
  else if (type === 'settings') applySettings(data);
  else if (type === 'setup') { S.setup = data; renderSetup(); renderNav(); }
  else if (type === 'notice') toast(data.text);
}
function refreshLibHead() {
  const sub = $('#main .head .sub'), hist = $('#main .tabs button:last-child');
  if (sub) sub.textContent = S.library.length ? `${fmt.files(S.library.length)} · ${fmt.bytes(S.library.reduce((a, i) => a + i.size, 0))}` : 'ملفاتك تبقى هنا حتى تحفظها في الجهاز';
  if (hist) hist.textContent = 'السجلّ' + (S.history.length ? '  ' + S.history.length : '');
}
async function boot() {
  try {
    const st = await api('state');
    Object.assign(S, { setup: st.setup, tasks: st.tasks || [], library: st.library || [], history: st.history || [], version: st.version, cacheBytes: st.cacheBytes });
    applySettings(st.settings);
  } catch (e) {
    document.body.replaceChildren(h('div', { class: 'setup' }, h('div', { class: 'box' }, h('h2', {}, 'انتهت الجلسة'), h('p', {}, 'أعد فتح داونلودر من أيقونته.'))));
    return;
  }
  render(); renderSetup(); checkClipboard();
  const connect = () => {
    const es = new EventSource('/api/events?t=' + encodeURIComponent(TOKEN));
    es.onmessage = (e) => { try { onEvent(JSON.parse(e.data)); } catch (err) { console.error(err); } };
    es.onerror = () => { es.close(); setTimeout(connect, 1500); };
  };
  connect();
}
boot();

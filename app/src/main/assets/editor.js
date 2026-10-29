/* محرر البطاقات: يُفتح بالضغط المطوّل على أي بطاقة في الرئيسية.
   يُحمَّل عند أول استخدام فقط حتى لا يستهلك ذاكرة الواجهة. */
(function () {
  var css = document.createElement('style');
  css.textContent =
    '#cardEd{position:absolute;inset:0;z-index:85;background:rgba(3,8,18,.55);display:flex;align-items:center;justify-content:center}' +
    '#cardEd .ed{width:900px;max-height:640px;overflow-y:auto;background:#0b1530;border:1.5px solid rgba(120,170,255,.45);border-radius:28px;padding:22px 28px;box-sizing:border-box;color:#fff;display:flex;flex-direction:column;gap:14px;box-shadow:0 20px 60px rgba(0,0,0,.6);scrollbar-width:none}' +
    '#cardEd .ed::-webkit-scrollbar{display:none}' +
    '#cardEd .hd{display:flex;align-items:center;gap:14px}#cardEd .hd b{font-size:26px;flex:1}' +
    '#cardEd .row{display:flex;align-items:center;gap:10px;flex-wrap:wrap}' +
    '#cardEd .lb{font-size:16px;color:#9FB0CC;min-width:140px}' +
    '#cardEd button{height:50px;padding:0 18px;border-radius:15px;border:1px solid #2b4a80;background:#13254a;color:#fff;font:inherit;font-size:17px}' +
    '#cardEd button.on{background:var(--accent,#1F5FD1);border-color:#fff;font-weight:700}' +
    '#cardEd button.pri{background:#1F5FD1;border-color:#6FA8FF;font-weight:700;min-width:120px}' +
    '#cardEd button.warn{border-color:#8a3a3a;color:#FFB4A8}' +
    '#cardEd button:disabled{opacity:.35}' +
    '#cardEd .val{min-width:76px;text-align:center;font-size:22px;font-weight:700;direction:ltr}' +
    '.card.editing{outline:3px dashed var(--accent,#5AB0FF);outline-offset:3px}';
  document.head.appendChild(css);

  var cur = null; // { pi, ci }
  var TS = [0.7, 0.8, 0.9, 1, 1.1, 1.2, 1.3, 1.4, 1.5];

  function homesCopy(){ return JSON.parse(JSON.stringify(getHomes(prefs))); }
  function save(fn){
    var hm = homesCopy(), pages = hm[prefs.layout], c = pages[cur.pi] && pages[cur.pi][cur.ci];
    if (!c) return close();
    var r = fn(c, pages);
    if (r === false) return;
    prefs.homes = hm; savePrefs(prefs); _prefsCache = null;
    buildPages(); forceCards = true; tick(); forceCards = false;
    mark(); draw();
  }
  function setPrefs(fn){ fn(prefs); savePrefs(prefs); _prefsCache = null; buildPages(); forceCards = true; tick(); forceCards = false; mark(); draw(); }
  function mark(){
    [].forEach.call(document.querySelectorAll('.card.editing'), function (e) { e.classList.remove('editing'); });
    var el = document.querySelector('.card[data-pi="' + cur.pi + '"][data-ci="' + cur.ci + '"]');
    if (el) {
      el.classList.add('editing');
      var pages = document.getElementById('pages'), pg = el.closest('.page');
      if (pages && pg) pages.scrollLeft = pg.offsetLeft;
    }
  }
  function close(){
    var o = document.getElementById('cardEd'); if (o) o.remove();
    [].forEach.call(document.querySelectorAll('.card.editing'), function (e) { e.classList.remove('editing'); });
    cur = null; lastTouch = Date.now();
  }
  function btn(txt, cls, fn, dis){ var b = document.createElement('button'); b.textContent = txt; if (cls) b.className = cls; b.onclick = fn; if (dis) b.disabled = true; return b; }
  function row(label){ var r = document.createElement('div'); r.className = 'row'; if (label) { var l = document.createElement('span'); l.className = 'lb'; l.textContent = label; r.appendChild(l); } return r; }

  function draw(){
    var pages = getHomes(prefs)[prefs.layout], c = pages[cur.pi] && pages[cur.pi][cur.ci];
    if (!c) return close();
    var ov = document.getElementById('cardEd');
    if (!ov) {
      ov = document.createElement('div'); ov.id = 'cardEd';
      ov.addEventListener('click', function (e) { if (e.target === ov) close(); });
      document.getElementById('stage').appendChild(ov);
    }
    var y = ov.firstChild ? ov.firstChild.scrollTop : 0;
    ov.innerHTML = '';
    var ed = document.createElement('div'); ed.className = 'ed'; ov.appendChild(ed);

    var hd = document.createElement('div'); hd.className = 'hd';
    var t = document.createElement('b'); t.textContent = 'تحرير: ' + (CARDS[c.id] ? CARDS[c.id].label : c.id); hd.appendChild(t);
    hd.appendChild(btn('تم', 'pri', close));
    ed.appendChild(hd);

    // حجم النص فقط (البطاقة تبقى بحجمها)
    var ts = +c.ts || 1, i = TS.indexOf(ts); if (i < 0) i = 3;
    var r = row('حجم النص');
    r.appendChild(btn('−', '', function () { save(function (cc) { cc.ts = TS[Math.max(0, i - 1)]; }); }, i === 0));
    var v = document.createElement('span'); v.className = 'val'; v.textContent = Math.round(ts * 100) + '%'; r.appendChild(v);
    r.appendChild(btn('+', '', function () { save(function (cc) { cc.ts = TS[Math.min(TS.length - 1, i + 1)]; }); }, i === TS.length - 1));
    var note = document.createElement('span'); note.className = 'lb'; note.style.minWidth = '0'; note.textContent = 'البطاقة تبقى بحجمها، والنص لا يتجاوز حدودها.'; r.appendChild(note);
    ed.appendChild(r);

    if (c.id === 'clock') {
      r = row('شكل الساعة');
      var st = c.style || '';
      r.appendChild(btn('حسب الإعدادات', st ? '' : 'on', function () { save(function (cc) { delete cc.style; }); }));
      CLOCK_STYLES.forEach(function (x) { r.appendChild(btn(x[1], st === x[0] ? 'on' : '', function () { save(function (cc) { cc.style = x[0]; }); })); });
      ed.appendChild(r);
    }

    var parts = CARD_PARTS[c.id];
    if (parts) {
      r = row('ما يظهر');
      parts.forEach(function (x) {
        var hidden = c.hide && c.hide[x[0]];
        r.appendChild(btn((hidden ? '○ ' : '● ') + x[1], hidden ? '' : 'on', function () { save(function (cc) { cc.hide = cc.hide || {}; if (cc.hide[x[0]]) delete cc.hide[x[0]]; else cc.hide[x[0]] = 1; }); }));
      });
      ed.appendChild(r);
    }

    r = row('المحاذاة');
    r.appendChild(btn('يمين', c.align !== 'c' ? 'on' : '', function () { save(function (cc) { delete cc.align; }); }));
    r.appendChild(btn('وسط', c.align === 'c' ? 'on' : '', function () { save(function (cc) { cc.align = 'c'; }); }));
    ed.appendChild(r);

    r = row('خلفية البطاقة');
    [['', 'عادية'], ['clear', 'شفافة'], ['solid', 'مصمتة']].forEach(function (x) {
      r.appendChild(btn(x[1], (c.op || '') === x[0] ? 'on' : '', function () { save(function (cc) { if (x[0]) cc.op = x[0]; else delete cc.op; }); }));
    });
    ed.appendChild(r);

    // النقل والتبديل
    var n = pages[cur.pi].length;
    r = row('المكان');
    r.appendChild(btn('→ تقديم', '', function () { save(function (cc, pg) { var a = pg[cur.pi]; a.splice(cur.ci - 1, 0, a.splice(cur.ci, 1)[0]); cur.ci--; }); }, cur.ci === 0));
    r.appendChild(btn('تأخير ←', '', function () { save(function (cc, pg) { var a = pg[cur.pi]; a.splice(cur.ci + 1, 0, a.splice(cur.ci, 1)[0]); cur.ci++; }); }, cur.ci >= n - 1));
    if (prefs.layout !== 'minimal') {
      r.appendChild(btn('للصفحة السابقة', '', function () { moveTo(cur.pi - 1); }, cur.pi === 0));
      r.appendChild(btn('للصفحة التالية', '', function () { moveTo(cur.pi + 1); }, cur.pi >= pages.length - 1));
    }
    ed.appendChild(r);

    r = row('تبديل نوعها');
    var ids = pages[cur.pi].map(function (x) { return x.id; });
    CARD_CATALOG.forEach(function (x) {
      if (x[0] === c.id || ids.indexOf(x[0]) >= 0) return;
      r.appendChild(btn(x[1], '', function () { save(function (cc) { cc.id = x[0]; delete cc.hide; delete cc.style; }); }));
    });
    ed.appendChild(r);

    r = row('القفل');
    r.appendChild(btn(c.lock ? '🔒 هذه البطاقة مقفلة' : 'قفل هذه البطاقة', c.lock ? 'on' : '', function () { save(function (cc) { if (cc.lock) delete cc.lock; else cc.lock = 1; }); }));
    r.appendChild(btn(prefs.cardsLocked ? '🔒 كل البطاقات مقفلة' : 'قفل كل البطاقات', prefs.cardsLocked ? 'on' : '', function () { setPrefs(function (p) { p.cardsLocked = !p.cardsLocked; }); if (prefs.cardsLocked) { close(); toast('قُفلت البطاقات. الضغط العادي يعمل، والتحرير يحتاج فتح القفل.'); } }));
    ed.appendChild(r);

    r = row('');
    r.appendChild(btn('استعادة إعدادات البطاقة', '', function () { save(function (cc) { ['ts', 'hide', 'align', 'op', 'style', 'lock'].forEach(function (k) { delete cc[k]; }); }); }));
    r.appendChild(btn('إزالة من الصفحة', 'warn', function () {
      if (n <= 1) { toast('لا يمكن ترك الصفحة فارغة'); return; }
      save(function (cc, pg) { pg[cur.pi].splice(cur.ci, 1); }); close();
    }));
    ed.appendChild(r);
    ed.scrollTop = y;
  }
  function moveTo(target){
    var hm = homesCopy(), pages = hm[prefs.layout];
    if (!pages[target]) return;
    var c = pages[cur.pi][cur.ci];
    var u = function (x) { return { s: 2, m: 3, l: 4 }[x.size] || 3; };
    var full = prefs.layout === 'classic' ? pages[target].length >= 4 : pages[target].reduce(function (a, x) { return a + u(x); }, 0) + u(c) > 12;
    if (full) { toast('الصفحة الأخرى ممتلئة'); return; }
    if (pages[cur.pi].length <= 1) { toast('لا يمكن ترك الصفحة فارغة'); return; }
    save(function (cc, pg) { pg[cur.pi].splice(cur.ci, 1); pg[target].push(cc); cur.pi = target; cur.ci = pg[target].length - 1; });
  }

  window.openCardEditor = function (pi, ci) { cur = { pi: pi, ci: ci }; mark(); draw(); };
  window.closeCardEditor = close;
})();

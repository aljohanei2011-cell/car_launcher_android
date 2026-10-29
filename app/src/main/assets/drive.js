/* ================================================================
   مزايا القيادة في الرئيسية: أقرب مكان، رمضان، تنبيهات الطقس والسفر،
   الشاشة المبسطة عند السرعة، سرعة المنطقة، تنبيه النعاس،
   المكالمات والرسائل (الاسم فقط)، واللوحة السريعة.
   كل جزء يعمل فقط إذا كان مفعّلاً، ولا يحمّل صوراً.
   ================================================================ */

/* ---------- أقرب مكان ---------- */
var NEARBY = [
  ['mosque', 'مسجد', 'مسجد', '#5AD1A0'], ['fuel', 'محطة وقود', 'محطة وقود', '#FFB547'], ['atm', 'صراف', 'صراف آلي', '#7FD4FF'],
  ['food', 'مطعم', 'مطعم', '#FF8A65'], ['coffee', 'قهوة', 'مقهى', '#C8A27A'], ['pharmacy', 'صيدلية', 'صيدلية', '#6FE3B4'],
  ['hospital', 'مستشفى', 'مستشفى', '#FF6B6B'], ['garage', 'ورشة', 'ورشة سيارات', '#B0BEC5']
];
var NB_IC = {
  mosque: '<path d="M3 21h18M5 21V11M19 21V11M8 21v-5a4 4 0 0 1 8 0v5M12 3c-3 2-5 4-5 7h10c0-3-2-5-5-7z"/>',
  fuel: '<path d="M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M3 21h12M14 9h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V8l-3-3M7 7h4"/>',
  atm: '<rect x="3" y="6" width="18" height="12" rx="2"/><circle cx="12" cy="12" r="2.5"/><path d="M7 9v.01M17 15v.01"/>',
  food: '<path d="M7 3v8M5 3v5a2 2 0 0 0 4 0V3M7 11v10M17 3c-2 2-3 5-3 8h3v10"/>',
  coffee: '<path d="M4 9h13v5a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5zM17 10h1.5a2.5 2.5 0 0 1 0 5H17M8 3v3M12 3v3"/>',
  pharmacy: '<rect x="4" y="4" width="16" height="16" rx="3"/><path d="M12 8v8M8 12h8"/>',
  hospital: '<path d="M4 21V7l8-4 8 4v14M10 21v-5h4v5M12 8v4M10 10h4"/>',
  garage: '<path d="M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4z"/>'
};
function nbIcon(k, col){ return '<svg viewBox="0 0 24 24" fill="none" stroke="' + col + '" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + NB_IC[k] + '</svg>'; }
function myLatLng(){ var t = natJSON('trip'); return (t && typeof t.lat === 'number') ? [t.lat, t.lng] : [LAT, LNG]; }
function nearby(k){
  var it = NEARBY.filter(function (x) { return x[0] === k; })[0]; if (!it) return;
  var ll = myLatLng();
  if (!HAS_NATIVE) { toast('يعمل داخل التطبيق'); return; }
  if (!nat('mapSearch', it[2], ll[0], ll[1], prefs.mapsApp || '')) toast('لا يوجد تطبيق خرائط يدعم البحث', 'اختيار تطبيق', function () { openPage('apps.html?pick=maps'); });
}
card('nearby', 'أقرب مكان', function () {
  var h = '<span class="k-lbl">أقرب مكان</span><div class="nbg">';
  NEARBY.slice(0, 6).forEach(function (x) { h += '<button onclick="nearby(\'' + x[0] + '\')">' + nbIcon(x[0], x[3]) + '<span>' + x[1] + '</span></button>'; });
  return h + '</div>';
});
CARD_RATE.nearby = 3600;
function openNearbySheet(){
  var st = document.getElementById('stage'), o = document.getElementById('nbSheet');
  if (o) { o.remove(); return; }
  o = document.createElement('div'); o.id = 'nbSheet'; o.className = 'sheetBg';
  var h = '<div class="sheet glass"><div class="sheetT">أقرب مكان</div><div class="nbg big">';
  NEARBY.forEach(function (x) { h += '<button onclick="document.getElementById(\'nbSheet\').remove();nearby(\'' + x[0] + '\')">' + nbIcon(x[0], x[3]) + '<span>' + x[1] + '</span></button>'; });
  o.innerHTML = h + '</div></div>';
  o.addEventListener('click', function (e) { if (e.target === o) o.remove(); });
  st.appendChild(o);
}
DOCK_ITEMS.nearby = { label: 'أقرب مكان', go: openNearbySheet };
IC.nearby = '<svg viewBox="0 0 24 24" fill="none" stroke="#5AD1A0" stroke-width="2" stroke-linecap="round"><path d="M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z"/><circle cx="12" cy="10" r="2.5"/></svg>';

/* ---------- رمضان ---------- */
function ramadanInfo(d){
  d = d || new Date();
  var h = hijriParts(d), inR = !!(h && h.m === 9);
  var now = riyadhMinutes(d), t = prayerTimes(d), sec = d.getSeconds();
  if (inR && now < t.maghrib && now >= t.fajr) return { inR: true, kind: 'iftar', left: (t.maghrib - now) * 60 - sec, at: t.maghrib, day: h.d };
  if (inR || (h && h.m === 8 && h.d >= 29)) {
    var tf = now < t.fajr ? t.fajr - now : (prayerTimes(new Date(d.getTime() + 86400000)).fajr + 1440 - now);
    return { inR: inR, kind: 'suhoor', left: tf * 60 - sec, at: now < t.fajr ? t.fajr : prayerTimes(new Date(d.getTime() + 86400000)).fajr, day: h ? h.d : 0 };
  }
  return { inR: false, kind: 'wait', days: daysUntilHijri(9, 1, d) };
}
function hms(s){ s = Math.max(0, Math.floor(s)); return pad2(Math.floor(s / 3600)) + ':' + pad2(Math.floor(s % 3600 / 60)) + ':' + pad2(s % 60); }
card('ramadan', 'رمضان', function () {
  var r = ramadanInfo();
  if (r.kind === 'wait') return '<span class="k-lbl">رمضان</span><div class="k-center" style="height:auto"><div class="k-big">' + (r.days === null ? '—' : r.days) + '</div><div class="k-sub">يوم على رمضان</div></div><div class="k-sub">اللهم بلّغنا رمضان</div>';
  var lbl = r.kind === 'iftar' ? 'الإفطار بعد' : 'السحور ينتهي بعد (الفجر)';
  return '<div class="k-row"><span class="k-lbl">رمضان' + (r.day ? ' · اليوم ' + r.day : '') + '</span><span class="k-sub">' + fmtMin(r.at) + '</span></div>' +
    '<div class="k-center" style="height:auto"><div class="k-sub">' + lbl + '</div><div class="k-big" style="font-size:3.2em">' + hms(r.left) + '</div></div>' +
    '<div class="k-sub font-dua" style="text-align:center">' + (r.kind === 'iftar' ? 'ذهب الظمأ وابتلت العروق وثبت الأجر إن شاء الله' : 'تسحّروا فإن في السحور بركة') + '</div>';
});
CARD_RATE.ramadan = 1;

/* ---------- عناصر إضافية للشريط العلوي ---------- */
var _qasrShown = false;
function homeDistKm(){
  var t = natJSON('trip'), c = CITIES[prefs.city];
  if (!t || !t.gps || typeof t.lat !== 'number' || !c) return null;
  return distKm(t.lat, t.lng, c.lat, c.lng);
}
function weatherAlertText(){
  if (prefs.weatherAlerts === false || !prefs.weatherOn) return '';
  var w = weatherData(), fx = weatherFx(w);
  if (fx === 'dust') return 'تنبيه: غبار في منطقتك — خفّف السرعة وشغّل الأنوار';
  if (fx === 'rain') return 'أمطار: الطريق قد يكون زلقاً — زِد مسافة الأمان';
  if (fx === 'fog') return 'ضباب: الرؤية ضعيفة — خفّف السرعة';
  if (w.temp >= 45) return 'حرارة عالية (' + Math.round(w.temp) + '°): تفقّد ضغط الإطارات وماء الردياتير';
  return '';
}
window.extraBanner = function () {
  var out = [], wa = weatherAlertText();
  if (wa) out.push({ t: wa, href: 'javascript:void(0)' });
  if (prefs.ramadanOn !== false) {
    var r = ramadanInfo();
    if (r.inR && r.kind === 'iftar' && r.left < 2 * 3600) out.push({ t: 'الإفطار بعد ' + fmtDiff(Math.ceil(r.left / 60)) + ' (' + fmtMin(r.at) + ')', href: 'prayer.html' });
  }
  var tp = lsGet('carlauncher.travel', {}) || {}, t = natJSON('trip');
  if (tp.to && CITIES[tp.to] && Date.now() - (tp.at || 0) < 24 * 3600000 && t && t.gps && typeof t.lat === 'number') {
    var left = distKm(t.lat, t.lng, CITIES[tp.to].lat, CITIES[tp.to].lng) * 1.22;
    if (left > 3) out.push({ t: 'متبقي ~' + Math.round(left) + ' كم إلى ' + CITIES[tp.to].label + ' (الوصول ' + (function () { var d = new Date(Date.now() + left / (tp.avg || 100) * 3600000); return clockStr(d) + (ampm(d) ? ' ' + ampm(d) : ''); })() + ')', href: 'travel.html' });
  }
  if (prefs.qasrNote !== false) {
    var dk = homeDistKm();
    if (dk !== null && dk > 80) out.push({ t: 'أنت مسافر (' + Math.round(dk) + ' كم عن ' + (CITIES[prefs.city] || {}).label + '): يجوز قصر الصلاة الرباعية وجمعها', href: 'prayer.html' });
  }
  return out;
};

/* ---------- صوت تنبيه قصير بدون ملفات ---------- */
var _ac = null;
function beep(n){
  try {
    _ac = _ac || new (window.AudioContext || window.webkitAudioContext)();
    for (var i = 0; i < (n || 2); i++) {
      var o = _ac.createOscillator(), g = _ac.createGain(), t0 = _ac.currentTime + i * 0.28;
      o.frequency.value = 880; o.connect(g); g.connect(_ac.destination);
      g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(0.35, t0 + 0.02); g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.22);
      o.start(t0); o.stop(t0 + 0.24);
    }
  } catch (e) {}
}

/* ---------- مراقبة القيادة كل ثانيتين ---------- */
var DRV_KEY = 'carlauncher.drive';
var _drv = lsGet(DRV_KEY, { start: 0, stopAt: 0, warned: 0 });
var _fast = 0, _slow = 0, _zoneAt = 0, _iftarToast = '';
function driveTick(){
  var t = natJSON('trip'); if (!t || !t.gps) return;
  var v = t.speed || 0, now = Date.now();
  // القيادة المتواصلة (تُصفّر بعد توقف 10 دقائق)
  if (v > 15) { if (!_drv.start) _drv.start = now; _drv.stopAt = 0; }
  else if (v < 5) { if (!_drv.stopAt) _drv.stopAt = now; if (_drv.start && now - _drv.stopAt > 10 * 60000) { _drv.start = 0; _drv.warned = 0; } }
  lsSet(DRV_KEY, _drv);
  // تنبيه النعاس: ليلاً بعد ساعتين متواصلتين، ثم كل 30 دقيقة
  if (prefs.drowsy !== false && _drv.start && isNight() && now - _drv.start > 2 * 3600000 && now - (_drv.warned || 0) > 30 * 60000) {
    _drv.warned = now; lsSet(DRV_KEY, _drv); drowsyAlert(Math.round((now - _drv.start) / 60000));
  }
  // الشاشة المبسطة عند السرعة
  var ds = prefs.driveSimple || {};
  if (ds.on) {
    var lim = +ds.speed || 80;
    if (v >= lim) { _fast += 2; _slow = 0; } else if (v < lim - 15) { _slow += 2; _fast = 0; }
    if (_fast >= 10 && !document.getElementById('driveOv') && !restOpen()) showDriveSimple();
    if (_slow >= 20 && document.getElementById('driveOv')) hideDriveSimple();
    if (document.getElementById('driveOv')) renderDriveSimple(t);
  }
  // سرعة المنطقة
  var zs = prefs.zoneSpeed || {};
  if (zs.on && now - _zoneAt > 45000) {
    var near = 1e9; Object.keys(CITIES).forEach(function (k) { var c = CITIES[k]; near = Math.min(near, distKm(t.lat, t.lng, c.lat, c.lng)); });
    var zone = near < 15 ? 'city' : 'hwy', zl = zone === 'city' ? (+zs.city || 80) : (+zs.hwy || 120);
    if (v > zl + (+zs.margin || 10)) { _zoneAt = now; beep(2); toast('السرعة ' + Math.round(v) + ' — حد ' + (zone === 'city' ? 'المدينة' : 'الطريق') + ' ' + zl); }
  }
  // قبل الإفطار بربع ساعة
  if (prefs.ramadanOn !== false) {
    var r = ramadanInfo();
    if (r.inR && r.kind === 'iftar' && r.left <= 15 * 60 && r.left > 13 * 60 && _iftarToast !== todayStr()) { _iftarToast = todayStr(); beep(1); toast('الإفطار بعد ربع ساعة تقريباً (' + fmtMin(r.at) + ')', 'أقرب مسجد', function () { nearby('mosque'); }, 15000); }
  }
}
function drowsyAlert(mins){
  if (document.getElementById('drowsy')) return;
  beep(3);
  var o = document.createElement('div'); o.id = 'drowsy'; o.className = 'sheetBg';
  o.innerHTML = '<div class="sheet glass" style="text-align:center;gap:18px"><div style="font-size:40px;font-weight:700;color:#FFB547">خذ استراحة</div>' +
    '<div style="font-size:24px">تقود منذ ' + Math.floor(mins / 60) + ' ساعة و' + (mins % 60) + ' دقيقة متواصلة ليلاً. التعب يضعف الانتباه.</div>' +
    '<div class="nbg big" style="grid-template-columns:repeat(3,1fr)"><button onclick="document.getElementById(\'drowsy\').remove();nearby(\'fuel\')">' + nbIcon('fuel', '#FFB547') + '<span>أقرب محطة</span></button>' +
    '<button onclick="document.getElementById(\'drowsy\').remove();nearby(\'coffee\')">' + nbIcon('coffee', '#C8A27A') + '<span>أقرب مقهى</span></button>' +
    '<button onclick="document.getElementById(\'drowsy\').remove()"><span style="font-size:22px">تم</span></button></div></div>';
  document.getElementById('stage').appendChild(o);
  setTimeout(function () { if (o.parentNode) o.remove(); }, 60000);
}
function showDriveSimple(){
  var o = document.createElement('div'); o.id = 'driveOv';
  o.innerHTML = '<div class="dvSpeed"><b id="dvV">0</b><span>كم/س</span></div><div class="dvInfo" id="dvInfo"></div>' +
    '<div class="dvBtns"><button onclick="openRole(\'maps\')">' + IC.maps + '<span>الخرائط</span></button><button onclick="nat(\'mediaCmd\',\'prev\')">' + MI.prev + '<span>السابق</span></button>' +
    '<button id="dvPlay" onclick="nat(\'mediaCmd\',\'playpause\')">' + MI.pause + '<span>تشغيل</span></button><button onclick="nat(\'mediaCmd\',\'next\')">' + MI.next + '<span>التالي</span></button>' +
    '<button onclick="hideDriveSimple(true)"><span style="font-size:22px">الواجهة</span></button></div>';
  document.getElementById('stage').appendChild(o);
}
var _dvHold = 0;
function hideDriveSimple(manual){ var o = document.getElementById('driveOv'); if (o) o.remove(); if (manual) { _fast = -120; } }
function renderDriveSimple(t){
  var v = document.getElementById('dvV'); if (!v) return;
  v.textContent = Math.round(t.speed || 0);
  var np = nextPrayer(new Date()), m = natJSON('media') || {};
  document.getElementById('dvInfo').textContent = np.name + ' ' + np.timeFmt + ' · ' + fmtDiff(np.diff) + (m.title ? '   ♪ ' + m.title : '');
  var pb = document.getElementById('dvPlay'); if (pb) pb.onclick = function () { nat('mediaCmd', m.playing ? 'pause' : 'play'); };
}

/* ---------- المكالمات والرسائل ---------- */
var _msgSeen = +(lsGet('carlauncher.msgSeen', 0)) || Date.now();
function notifTick(){
  if (!HAS_NATIVE) return;
  var n = natJSON('notifs') || {};
  var box = document.getElementById('callOv');
  if (n.call && prefs.callCard !== false) {
    var key = n.call.name + '|' + n.call.at;
    if (!box || box.dataset.k !== key) {
      if (!box) { box = document.createElement('div'); box.id = 'callOv'; box.className = 'glass'; document.getElementById('stage').appendChild(box); }
      box.dataset.k = key;
      var acts = (n.call.actions || []).map(function (a, i) {
        var red = /رفض|decline|إنهاء|end|hang/i.test(a);
        return '<button class="' + (red ? 'cr' : 'cg') + '" onclick="nat(\'callAction\',' + i + ')">' + esc(a || (i ? 'رفض' : 'رد')) + '</button>';
      }).join('');
      box.innerHTML = '<div style="font-size:18px;color:#9FD0FF">مكالمة · ' + esc(n.call.app) + '</div><div style="font-size:34px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + esc(n.call.name || 'رقم غير معروف') + '</div><div class="cbtns">' + acts + '</div>';
    }
  } else if (box) box.remove();
  if (n.msg && n.msg.at > _msgSeen) {
    _msgSeen = n.msg.at; lsSet('carlauncher.msgSeen', _msgSeen);
    if (prefs.msgNotify !== false) toast('رسالة من ' + n.msg.from + ' (' + n.msg.app + ')', null, null, 6000);
  }
}

/* ---------- اللوحة السريعة (اسحب من أعلى الشاشة للأسفل) ---------- */
var _qpY = null;
document.addEventListener('pointerdown', function (e) { _qpY = (e.clientY / (document.getElementById('stage').getBoundingClientRect().height / 720)) < 90 && !restOpen() ? e.clientY : null; }, true);
document.addEventListener('pointerup', function (e) { if (_qpY !== null && e.clientY - _qpY > 70) openQuickPanel(); _qpY = null; }, true);
function openQuickPanel(){
  if (document.getElementById('qp')) return;
  var o = document.createElement('div'); o.id = 'qp'; o.className = 'sheetBg'; o.style.alignItems = 'flex-start';
  var vol = natJSON('volume') || { v: 0, max: 15 }, br = nat('brightness'), canW = nat('canWriteSettings');
  var s = natJSON('status') || {};
  o.innerHTML = '<div class="sheet glass qpS">' +
    '<div class="qpRow"><span>السطوع</span>' + (canW === false ? '<button class="qpB" onclick="nat(\'openWriteSettings\')">السماح بتغيير السطوع</button>' : '<input type="range" min="5" max="100" value="' + (br > 0 ? br : 60) + '" oninput="nat(\'setBrightness\',+this.value)">') + '</div>' +
    '<div class="qpRow"><span>الصوت</span><input type="range" min="0" max="' + vol.max + '" value="' + vol.v + '" oninput="nat(\'setVolume\',+this.value)"></div>' +
    '<div class="qpGrid">' +
    '<button class="qpB' + (s.wifi ? ' on' : '') + '" onclick="nat(\'openPanel\',\'wifi\')">واي فاي</button>' +
    '<button class="qpB' + (s.bt === 'on' || s.bt === 'connected' ? ' on' : '') + '" onclick="nat(\'btToggle\');setTimeout(function(){document.getElementById(\'qp\').remove();openQuickPanel();},1200)">بلوتوث</button>' +
    '<button class="qpB" onclick="nat(\'openPanel\',\'internet\')">الإنترنت</button>' +
    '<button class="qpB" onclick="nat(\'openSystem\',\'gps\')">الموقع GPS</button>' +
    '<button class="qpB" onclick="document.getElementById(\'qp\').remove();openRest()">الاستراحة</button>' +
    '<button class="qpB" onclick="nat(\'freeRam\');toast(\'جارٍ تحرير الذاكرة…\')">تحرير الذاكرة</button>' +
    '<button class="qpB" onclick="document.getElementById(\'qp\').remove();openNearbySheet()">أقرب مكان</button>' +
    '<button class="qpB" onclick="document.getElementById(\'qp\').remove();openPage(\'settings.html\')">الإعدادات</button>' +
    '</div><div class="note" style="text-align:center;font-size:15px;opacity:.7">اسحب للأعلى أو اضغط خارج اللوحة للإغلاق</div></div>';
  o.addEventListener('click', function (e) { if (e.target === o) o.remove(); });
  var y0 = null;
  o.addEventListener('pointerdown', function (e) { y0 = e.clientY; });
  o.addEventListener('pointerup', function (e) { if (y0 !== null && y0 - e.clientY > 70) o.remove(); y0 = null; });
  document.getElementById('stage').appendChild(o);
}

/* ---------- التشغيل ---------- */
(function () {
  var css = document.createElement('style');
  css.textContent =
    '.nbg{display:grid;grid-template-columns:repeat(3,1fr);gap:.5em;flex:1;min-height:0;align-content:center;margin-top:.4em}' +
    '.nbg button{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:.3em;border-radius:.9em;border:1px solid rgba(255,255,255,.14);background:rgba(255,255,255,.06);color:inherit;font:inherit;font-size:1em;padding:.5em .2em;min-width:0}' +
    '.nbg svg{width:2.2em;height:2.2em}.nbg span{white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:100%}' +
    '.nbg.big{grid-template-columns:repeat(4,1fr);gap:14px;font-size:22px}.nbg.big button{height:130px}' +
    '.sheetBg{position:absolute;inset:0;z-index:86;background:rgba(3,8,18,.55);display:flex;align-items:center;justify-content:center}' +
    '.sheet{width:920px;padding:24px 28px;box-sizing:border-box;display:flex;flex-direction:column;gap:16px;color:#fff;background:#0b1530!important;border-radius:28px}' +
    '.sheetT{font-size:26px;font-weight:700}' +
    '.qpS{margin-top:24px}.qpRow{display:flex;align-items:center;gap:18px;font-size:21px}.qpRow span{width:90px}.qpRow input{flex:1;height:40px;accent-color:var(--accent,#2F7BFF)}' +
    '.qpGrid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.qpB{height:72px;border-radius:18px;border:1px solid #2b4a80;background:#13254a;color:#fff;font:inherit;font-size:19px}.qpB.on{background:var(--accent,#1F5FD1);border-color:#fff}' +
    '#driveOv{position:absolute;inset:0;z-index:84;background:linear-gradient(180deg,#050b18,#0a1428);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18px;color:#fff}' +
    '.dvSpeed{display:flex;align-items:baseline;gap:14px;direction:ltr}.dvSpeed b{font-size:220px;line-height:1;font-weight:700;font-family:var(--font-clock)}.dvSpeed span{font-size:34px;color:#9FB0CC}' +
    '.dvInfo{font-size:30px;color:#C3D1E8;max-width:1100px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}' +
    '.dvBtns{display:flex;gap:18px}.dvBtns button{width:170px;height:120px;border-radius:26px;border:1.5px solid rgba(255,255,255,.25);background:rgba(255,255,255,.08);color:#fff;font:inherit;font-size:19px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8px}.dvBtns svg{width:44px;height:44px}' +
    '#callOv{position:absolute;top:84px;left:50%;transform:translateX(-50%);z-index:89;width:680px;padding:20px 26px;box-sizing:border-box;background:rgba(8,20,40,.96)!important;color:#fff;display:flex;flex-direction:column;gap:10px;box-shadow:0 18px 50px rgba(0,0,0,.6)}' +
    '.cbtns{display:flex;gap:14px;margin-top:6px}.cbtns button{flex:1;height:72px;border-radius:20px;border:none;font:inherit;font-size:24px;font-weight:700;color:#fff}.cbtns .cg{background:#1FA35A}.cbtns .cr{background:#D93A3A}' +
    'body.perf .sheetBg{background:rgba(3,8,18,.85)}';
  document.head.appendChild(css);
  setInterval(function () { if (!document.hidden) safe('drive', driveTick); }, 2000);
  setInterval(function () { if (!document.hidden) safe('notif', notifTick); }, 1500);
})();

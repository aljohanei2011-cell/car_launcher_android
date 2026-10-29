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

/* ================================================================
   العبادة والسيارة: ورد القرآن، الجمعة، أذكار الصباح والمساء،
   المناسبات، رسالة الوصول، وضع الركاب، وثائق السيارة، المصاريف، OBD
   ================================================================ */
NEARBY.push(['jami', 'جامع', 'جامع', '#5AD1A0']);
NB_IC.jami = NB_IC.mosque;

/* ---------- ورد القرآن اليومي بالتتابع (604 صفحة) ---------- */
function khatma(){ return Object.assign({ pages: 4, cur: 1, done: '', count: 0 }, prefs.khatma || {}); }
function pageJuz(p){ return Math.min(30, Math.max(1, Math.floor((p - 2) / 20) + 1)); }
function khatmaDone(){
  var k = khatma(), to = Math.min(604, k.cur + k.pages - 1);
  k.cur = to >= 604 ? 1 : to + 1; k.done = todayStr(); if (to >= 604) k.count = (k.count || 0) + 1;
  prefs.khatma = k; savePrefs(prefs); _prefsCache = null; later();
  toast(to >= 604 ? 'ما شاء الله، أتممت الختمة! تبدأ ختمة جديدة' : 'تقبّل الله ✓ ورد الغد من الصفحة ' + k.cur);
}
function openQuran(){ if (prefs.quranApp && nat('launch', prefs.quranApp)) return; openPage('apps.html?pick=quran'); }
card('khatma', 'وردي من القرآن', function () {
  var k = khatma(), to = Math.min(604, k.cur + k.pages - 1), pct = Math.round((k.cur - 1) / 604 * 100), doneToday = k.done === todayStr();
  return '<div class="k-row"><span class="k-lbl">وردي من القرآن</span><span class="k-sub">الجزء ' + pageJuz(k.cur) + '</span></div>' +
    '<div class="k-center" style="height:auto"><div class="k-sub">' + (doneToday ? 'قرأت ورد اليوم ✓ — ورد الغد: ' : 'ورد اليوم: ') + (to > k.cur ? 'الصفحات' : 'الصفحة') + '</div><div class="k-big" style="font-size:2.6em">' + k.cur + (to > k.cur ? ' – ' + to : '') + '</div></div>' +
    '<div><div style="height:.45em;border-radius:.25em;background:rgba(127,127,127,.25);overflow:hidden;direction:ltr"><div style="width:' + pct + '%;height:100%;background:var(--accent,#5AD1A0)"></div></div>' +
    '<div class="k-row k-sub" style="font-size:.9em;margin-top:.2em"><span>' + pct + '% من الختمة</span><span>' + (k.count ? 'ختمات: ' + k.count : '') + '</span></div></div>' +
    '<div style="display:flex;gap:.5em"><button class="k-btn" onclick="khatmaDone()"' + (doneToday ? ' style="opacity:.6"' : '') + '>قرأت</button><button class="k-btn" onclick="openQuran()">فتح المصحف</button></div>';
});
CARD_RATE.khatma = 60;

/* ---------- المناسبات (زينة خفيفة بدون صور) ---------- */
function occasion(d){
  d = d || new Date();
  var m = d.getMonth() + 1, day = d.getDate(), h = hijriParts(d);
  if (m === 9 && day === 23) return { k: 'national', t: 'اليوم الوطني السعودي ' + (d.getFullYear() - 1930) };
  if (m === 2 && day === 22) return { k: 'national', t: 'يوم التأسيس' };
  if (h && h.m === 10 && h.d <= 3) return { k: 'eid', t: 'عيدكم مبارك' };
  if (h && h.m === 12 && h.d >= 10 && h.d <= 13) return { k: 'eid', t: 'عيد أضحى مبارك' };
  if (h && h.m === 12 && h.d === 9) return { k: 'arafa', t: 'يوم عرفة' };
  if (h && h.m === 9) return { k: 'ramadan', t: 'رمضان كريم' };
  return null;
}
var OCC_IC = {
  national: '<svg viewBox="0 0 24 24" fill="none" stroke="#2BB673" stroke-width="2" stroke-linecap="round"><path d="M12 22V10M12 10c-3-4-7-4-9-2 3 0 6 1 9 2zM12 10c3-4 7-4 9-2-3 0-6 1-9 2zM12 10c-1-4 0-7 0-8 0 1 1 4 0 8z"/></svg>',
  eid: '<svg viewBox="0 0 24 24" fill="none" stroke="#F2C879" stroke-width="2" stroke-linecap="round"><path d="M12 3l2.4 5 5.6.6-4.2 3.8 1.2 5.6L12 15.2 7 18l1.2-5.6L4 8.6 9.6 8z"/></svg>',
  arafa: '<svg viewBox="0 0 24 24" fill="none" stroke="#F2C879" stroke-width="2" stroke-linecap="round"><path d="M3 20h18M6 20l4-9 3 5 2-3 3 7"/></svg>',
  ramadan: '<svg viewBox="0 0 24 24" fill="none" stroke="#F2C879" stroke-width="2" stroke-linecap="round"><path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/><path d="M18 3v3M16.5 4.5h3"/></svg>'
};
function renderOccasion(){
  var o = prefs.occasions === false ? null : occasion(), box = document.getElementById('occ');
  ['occ-national', 'occ-eid', 'occ-ramadan', 'occ-arafa'].forEach(function (c) { document.body.classList.remove(c); });
  if (!box) { var r = document.querySelector('.top .right'); if (!r) return; box = document.createElement('div'); box.id = 'occ'; r.appendChild(box); }
  if (!o) { box.innerHTML = ''; box.style.display = 'none'; return; }
  document.body.classList.add('occ-' + o.k);
  box.style.display = 'flex'; box.innerHTML = OCC_IC[o.k] + '<span>' + o.t + '</span>';
}

/* ---------- أذكار الصباح والمساء والجمعة في الشريط العلوي ---------- */
var _extraBase = window.extraBanner;
window.extraBanner = function () {
  var out = _extraBase ? _extraBase() : [], d = new Date(), now = riyadhMinutes(d), t = prayerTimes(d);
  var read = lsGet('carlauncher.azkarRead', {});
  if (prefs.azkarAuto !== false) {
    if (now >= t.fajr && now < t.sunrise + 180 && read.morning !== todayStr()) out.push({ t: 'أذكار الصباح — اضغط للقراءة', href: 'azkar.html?g=morning' });
    if (now >= t.asr && now < t.maghrib + 60 && read.evening !== todayStr()) out.push({ t: 'أذكار المساء — اضغط للقراءة', href: 'azkar.html?g=evening' });
  }
  var wd = new Date(d.getTime() + TZ * 3600000).getUTCDay(), fm = +prefs.fridayMin || 45;
  if (prefs.fridayOn !== false && wd === 5 && now >= t.dhuhr - fm - 30 && now < t.dhuhr) out.unshift({ t: 'صلاة الجمعة بعد ' + fmtDiff(t.dhuhr - now) + ' — اضغط لأقرب جامع', href: 'javascript:nearby(\'jami\')' });
  return out;
};
var _friToast = '';
function fridayTick(){
  if (prefs.fridayOn === false) return;
  var d = new Date(), wd = new Date(d.getTime() + TZ * 3600000).getUTCDay(); if (wd !== 5) return;
  var now = riyadhMinutes(d), t = prayerTimes(d), fm = +prefs.fridayMin || 45;
  if (now >= t.dhuhr - fm && now < t.dhuhr - fm + 3 && _friToast !== todayStr()) { _friToast = todayStr(); beep(2); toast('صلاة الجمعة بعد ' + fmtDiff(t.dhuhr - now), 'أقرب جامع', function () { nearby('jami'); }, 20000); }
}

/* ---------- رسالة الوصول ---------- */
var ARR_KEY = 'carlauncher.arrive';
function arriveTick(){
  var a = prefs.arrive || {}; if (!a.on || !a.phone || typeof a.lat !== 'number') return;
  var t = natJSON('trip'); if (!t || !t.gps || typeof t.lat !== 'number') return;
  var st = lsGet(ARR_KEY, { away: false, at: 0 }), d = distKm(t.lat, t.lng, a.lat, a.lng);
  if (d > 3) { if (!st.away) { st.away = true; lsSet(ARR_KEY, st); } return; }
  if (st.away && d < 0.35 && (t.speed || 0) < 5) {
    st.away = false; st.at = Date.now(); lsSet(ARR_KEY, st);
    toast('وصلت' + (a.place ? ' ' + a.place : '') + '؟ أرسل لـ' + (a.name || 'جهة الاتصال') + ' «' + (a.text || 'وصلت') + '»', 'إرسال بواتساب', function () { if (!nat('whatsapp', a.phone, a.text || 'وصلت الحمد لله ✓')) toast('واتساب غير مثبت'); }, 30000);
  }
}

/* ---------- وضع الركاب (الأطفال) ---------- */
DOCK_ITEMS.kids = { label: 'الركاب', go: function () { if (prefs.kidsApp && nat('launch', prefs.kidsApp)) return; openPage('apps.html?pick=kids'); } };
IC.kids = '<svg viewBox="0 0 24 24" fill="none" stroke="#FFB0D9" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="7" r="3.5"/><path d="M6 21v-3a6 6 0 0 1 12 0v3M9.5 7.5h.01M14.5 7.5h.01"/></svg>';

/* ---------- وثائق السيارة ---------- */
card('wallet', 'وثائق السيارة', function () {
  var docs = loadMaint().filter(function (it) { return !it.km; }).map(function (it) { return { it: it, s: maintStatus(it) }; })
    .sort(function (a, b) { return (a.s.daysLeft === null ? 1e9 : a.s.daysLeft) - (b.s.daysLeft === null ? 1e9 : b.s.daysLeft); });
  var h = '<a href="maintenance.html" style="display:contents"><span class="k-lbl">وثائق السيارة</span><div class="k-list" style="flex:1;margin-top:.4em">';
  docs.slice(0, 4).forEach(function (x) {
    var dl = x.s.daysLeft, txt = dl === null ? 'أدخل التاريخ' : dl < 0 ? 'منتهية' : dl === 0 ? 'تنتهي اليوم' : 'باقي ' + dl + ' يوم';
    h += '<div class="k-li' + (x.s.due ? ' on' : '') + '"><span>' + esc(x.it.name.replace('تجديد ', '')) + '</span><span>' + txt + '</span></div>';
  });
  return h + '</div></a>';
});
CARD_RATE.wallet = 600;

/* ---------- المصاريف ---------- */
card('expenses', 'مصاريف السيارة', function () {
  var ex = lsGet(EXP_KEY, []), d = new Date(), m0 = new Date(d.getFullYear(), d.getMonth(), 1).getTime(), tot = 0, by = {};
  ex.forEach(function (e) { if (e.at >= m0) { tot += e.amount; by[e.cat] = (by[e.cat] || 0) + e.amount; } });
  var top = Object.keys(by).sort(function (a, b) { return by[b] - by[a]; }).slice(0, 3);
  return '<a href="expenses.html" style="display:contents"><div class="k-row"><span class="k-lbl">مصاريف الشهر</span><span class="k-sub">' + fmt({ month: 'long' }, 'ar-u-nu-latn', d) + '</span></div>' +
    '<div><span class="k-big">' + Math.round(tot).toLocaleString('en') + '</span><span class="k-sub"> ريال</span></div>' +
    '<div class="k-sub" style="white-space:normal">' + (top.length ? top.map(function (k) { return EXP_CATS[k] + ' ' + Math.round(by[k]); }).join(' · ') : 'أضف مصروفاً من الصفحة أو من الجوال') + '</div></a>';
});
CARD_RATE.expenses = 300;

/* ---------- OBD ---------- */
card('obd', 'بيانات السيارة (OBD)', function () {
  var o = natJSON('obd') || {};
  if (o.state !== 'on') return '<a href="obd.html" style="display:contents"><span class="k-lbl">بيانات السيارة</span><div class="k-sub" style="white-space:normal">' +
    (o.state === 'connecting' ? 'جارٍ الاتصال بقطعة OBD…' : o.state === 'error' ? 'تعذّر الاتصال: ' + esc(o.err) : 'وصّل قطعة OBD بلوتوث (ELM327) لعرض حرارة المحرك والبطارية والوقود') + '</div><span class="k-btn" style="align-self:flex-start">إعداد</span></a>';
  var cool = o.coolant, hot = cool >= 105;
  var cell = function (l, v, u, warn) { return '<div style="text-align:center"><div class="k-sub">' + l + '</div><div class="k-big" style="font-size:2em' + (warn ? ';color:#FF6B5A' : '') + '">' + (v === undefined || v === null ? '—' : v) + '<span style="font-size:.45em"> ' + u + '</span></div></div>'; };
  return '<a href="obd.html" style="display:contents"><div class="k-row"><span class="k-lbl">بيانات السيارة</span><span class="k-sub">' + ((o.dtcs || []).length ? '<span style="color:#FF6B5A">أعطال: ' + o.dtcs.length + '</span>' : 'لا أعطال ✓') + '</span></div>' +
    '<div style="display:grid;grid-template-columns:1fr 1fr;gap:.4em .8em;flex:1;align-content:center">' + cell('حرارة المحرك', cool, '°', hot) + cell('البطارية', o.volt ? o.volt.toFixed(1) : null, 'ف', o.volt && (o.volt < 11.8 || o.volt > 15)) + cell('الدورات', o.rpm, '') + cell('الوقود', o.fuel, '%', o.fuel !== undefined && o.fuel < 12) + '</div></a>';
});
CARD_RATE.obd = 2;
var _obdWarn = 0;
function obdTick(){
  if (!HAS_NATIVE) return;
  var o = natJSON('obd'); if (!o || o.state !== 'on') return;
  if (o.coolant >= 108 && Date.now() - _obdWarn > 120000) { _obdWarn = Date.now(); beep(3); toast('تنبيه: حرارة المحرك مرتفعة (' + o.coolant + '°) — توقف بأمان وافحص التبريد', null, null, 20000); }
}
/* الاتصال التلقائي بآخر قطعة OBD عند التشغيل */
setTimeout(function () { if (HAS_NATIVE && prefs.obdAuto) { var o = natJSON('obd') || {}; if (o.state !== 'on' && o.state !== 'connecting' && o.saved) nat('obdStart', o.saved); } }, 8000);

(function () {
  var css = document.createElement('style');
  css.textContent = '#occ{display:none;align-items:center;gap:8px;height:40px;padding:0 14px;border-radius:20px;background:rgba(0,0,0,.25);border:1px solid rgba(255,255,255,.2);font-size:17px;white-space:nowrap}#occ svg{width:24px;height:24px}' +
    'body.occ-national:not(.accent-custom){--accent:#2BB673;--accent2:#7FE0AE}body.occ-national #occ{border-color:#2BB673}' +
    'body.skin-light #occ{background:rgba(255,255,255,.7);color:#13202f}';
  document.head.appendChild(css);
  safe('occ', renderOccasion);
  setInterval(function () { if (!document.hidden) { safe('fri', fridayTick); safe('arrive', arriveTick); safe('obd', obdTick); } }, 5000);
  setInterval(function () { safe('occ', renderOccasion); }, 30 * 60000);
})();

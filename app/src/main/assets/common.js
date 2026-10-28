/* ملف مشترك لكل صفحات التطبيق: التحجيم، المواقيت، الإعدادات، الجسر مع النظام */

/* ===== التحجيم: scale = min(W/1280, H/720) ===== */
function fitStage(){
  var el = document.getElementById('stage');
  if (!el) return;
  var s = Math.min(window.innerWidth / 1280, window.innerHeight / 720);
  el.style.transform = 'translate(-50%,-50%) scale(' + s + ')';
}
window.addEventListener('resize', fitStage);

/* ===== الجسر مع أندرويد (Native). في المتصفح يعمل كل شيء بدونه ===== */
var HAS_NATIVE = typeof window.Native !== 'undefined';
function nat(fn){
  if (!HAS_NATIVE || typeof Native[fn] !== 'function') return null;
  try { return Native[fn].apply(Native, Array.prototype.slice.call(arguments, 1)); } catch (e) { return null; }
}
function natJSON(fn){
  var r = nat.apply(null, arguments);
  if (r === null || r === undefined || r === '') return null;
  try { return JSON.parse(r); } catch (e) { return null; }
}

/* ===== الإعدادات المحفوظة (آمنة إن فشل التخزين) ===== */
var PREFS_KEY = 'carlauncher.prefs';
var DEFAULT_PREFS = {
  theme: 'mountain', font: 'thmanyah', solid: false, city: 'dhahran',
  brand: 'toyota', subBrands: [], brandText: '', brandSize: 'm',
  autoNight: false, nightTheme: 'night', restBg: 'auto',
  midCard: 'media', mapsApp: '', musicApp: '',
  adhan: true, preMin: 10, sound: true, duck: true,
  rest: true, restMin: 120, travel: true, travelKm: 80,
  odoOffset: null,
  speedOn: false, speedLimit: 120, customSound: true,
  bootAction: 'none', pin: '', nightBright: false, nightPct: 30, reminders: true
};
function loadPrefs(){
  try {
    var raw = localStorage.getItem(PREFS_KEY);
    return Object.assign({}, DEFAULT_PREFS, raw ? JSON.parse(raw) : {});
  } catch (e) { return Object.assign({}, DEFAULT_PREFS); }
}
function savePrefs(p){
  try { localStorage.setItem(PREFS_KEY, JSON.stringify(p)); } catch (e) {}
  pushConfig(p);
}
/* إرسال إعدادات التنبيهات للخدمة التي تعمل في الخلفية */
function pushConfig(p){
  nat('setConfig', JSON.stringify({ adhan: p.adhan, preMin: +p.preMin, sound: p.sound, duck: p.duck,
    rest: p.rest, restMin: +p.restMin, travel: p.travel, travelKm: +p.travelKm,
    speedOn: p.speedOn, speedLimit: +p.speedLimit, customSound: p.customSound }));
}

var THEMES = {
  mountain: { label: 'الجبلي', bg: 'bg/home_mountain.jpg', rest: 'bg/rest_mountain.jpg' },
  night:    { label: 'الليلي', bg: 'bg/home_night.jpg',    rest: 'bg/rest_night.jpg' },
  desert:   { label: 'الصحراوي', bg: 'bg/home_desert.jpg', rest: 'bg/rest_desert.jpg' },
  day:      { label: 'النهاري', bg: 'bg/home_day.jpg',     rest: 'bg/rest_day.jpg' },
  violet:   { label: 'البنفسجي', bg: 'bg/home_violet.jpg', rest: 'bg/rest_violet.jpg' },
  glow:     { label: 'الذهبي', bg: 'bg/home_glow.jpg',     rest: 'bg/rest_glow.jpg' }
};

/* صور المستخدم (من الجوال أو من الجهاز). الاسم: home_123.jpg / rest_123.jpg / logo_123.png */
var _imgs = null;
function userImages(){
  if (_imgs) return _imgs;
  _imgs = natJSON('images') || [];
  return _imgs;
}
function userImageUrl(name){
  var list = userImages();
  for (var i = 0; i < list.length; i++) if (list[i].name === name) return list[i].url;
  return null;
}
function themeBg(key, which){
  if (key && key.indexOf('user:') === 0) {
    var u = userImageUrl(key.slice(5));
    if (u) return u;
    key = 'mountain';
  }
  var t = THEMES[key] || THEMES.mountain;
  return which === 'rest' ? t.rest : t.bg;
}

function fontStack(p){
  return p.font === 'system'
    ? "'Noto Sans Arabic', Tahoma, 'Segoe UI', Arial, sans-serif"
    : "'Thmanyah', 'Noto Sans Arabic', Tahoma, sans-serif";
}

/* هل الوقت الآن ليل؟ (بين المغرب والشروق بتوقيت المدينة) */
function isNight(d){
  var t = prayerTimes(d || new Date());
  var now = riyadhMinutes(d || new Date());
  return now >= t.maghrib || now < t.sunrise;
}
function effectiveTheme(p){
  return (p.autoNight && isNight()) ? (p.nightTheme || 'night') : p.theme;
}

/* تطبيق الإعدادات على الصفحة الحالية */
function applyPrefs(bgKey, p){
  setCity(p.city);
  var wc = document.getElementById('weatherCity');
  if (wc) wc.textContent = cityLabel();
  var img = document.getElementById('bg');
  if (img) {
    var th = effectiveTheme(p);
    var src;
    if (bgKey === 'rest') {
      if (p.restBg && p.restBg !== 'auto') src = themeBg(p.restBg, 'rest');
      else src = th.indexOf('user:') === 0 ? themeBg(th, 'home') : themeBg(th, 'rest');
    } else src = themeBg(th, 'home');
    if (img.getAttribute('src') !== src) img.src = src;
  }
  var stage = document.getElementById('stage');
  if (stage) stage.style.fontFamily = fontStack(p);
  document.body.classList.toggle('solid', !!p.solid);
  renderBrand(p);
}

/* ===== مواقيت الصلاة على الجهاز (بدون إنترنت) — طريقة أم القرى =====
   الفجر 18.5°، العشاء بعد المغرب بـ 90 دقيقة (120 في رمضان)، العصر مثل الظل.
   تمت مقارنتها بجدول أم القرى للرياض: الفرق دقيقة واحدة أو أقل. */
var CITIES = {
  riyadh:  { label: 'الرياض',  lat: 24.7136, lng: 46.6753 },
  dhahran: { label: 'الظهران', lat: 26.29,   lng: 50.11 },
  dammam:  { label: 'الدمام',  lat: 26.4207, lng: 50.0888 },
  khobar:  { label: 'الخبر',   lat: 26.2172, lng: 50.1971 },
  jeddah:  { label: 'جدة',     lat: 21.4858, lng: 39.1925 },
  makkah:  { label: 'مكة المكرمة', lat: 21.3891, lng: 39.8579 },
  madinah: { label: 'المدينة المنورة', lat: 24.5247, lng: 39.5692 },
  qassim:  { label: 'بريدة',   lat: 26.3260, lng: 43.9750 },
  abha:    { label: 'أبها',    lat: 18.2164, lng: 42.5053 },
  tabuk:   { label: 'تبوك',    lat: 28.3835, lng: 36.5662 }
};
var LAT = 26.29, LNG = 50.11, TZ = 3, RAD = Math.PI / 180, DEG = 180 / Math.PI;
var CITY_KEY = 'dhahran', CITY_GPS = false;
function setCity(key){
  CITY_GPS = false;
  if (key === 'gps') {
    var t = natJSON('trip');
    if (t && typeof t.lat === 'number') { LAT = t.lat; LNG = t.lng; CITY_KEY = 'gps'; CITY_GPS = true; return; }
    key = 'dhahran';
  }
  var c = CITIES[key] || CITIES.dhahran; CITY_KEY = key; LAT = c.lat; LNG = c.lng;
}
function cityLabel(){ return CITY_GPS ? 'موقعي الحالي' : (CITIES[CITY_KEY] || CITIES.dhahran).label; }

function sinD(x){ return Math.sin(x*RAD); } function cosD(x){ return Math.cos(x*RAD); } function tanD(x){ return Math.tan(x*RAD); }
function norm360(x){ x = x % 360; return x < 0 ? x + 360 : x; }
function riyadhMinutes(d){ var s = new Date(d.getTime() + TZ * 3600000); return s.getUTCHours() * 60 + s.getUTCMinutes(); }
function isRamadan(d){
  try {
    var m = new Intl.DateTimeFormat('en-u-ca-islamic-umalqura-nu-latn', { month: 'numeric', timeZone: 'Asia/Riyadh' }).format(d);
    return parseInt(m, 10) === 9;
  } catch (e) { return false; }
}
/* أوقات اليوم بالدقائق من منتصف الليل بتوقيت السعودية */
function prayerTimes(d){
  var s = new Date(d.getTime() + TZ * 3600000);
  var JD = Date.UTC(s.getUTCFullYear(), s.getUTCMonth(), s.getUTCDate(), 12) / 86400000 + 2440587.5;
  var n = JD - 2451545;
  var L = norm360(280.460 + 0.9856474*n), g = norm360(357.528 + 0.9856003*n);
  var lam = L + 1.915*sinD(g) + 0.020*sinD(2*g);
  var eps = 23.439 - 0.0000004*n;
  var decl = Math.asin(sinD(eps)*sinD(lam)) * DEG;
  var ra = norm360(Math.atan2(cosD(eps)*sinD(lam), cosD(lam)) * DEG);
  var eq = L - ra; if (eq > 180) eq -= 360; if (eq < -180) eq += 360;
  var noon = 12 - LNG/15 - (eq*4)/60;
  function H(alt){ var c = (sinD(alt) - sinD(LAT)*sinD(decl)) / (cosD(LAT)*cosD(decl)); c = Math.max(-1, Math.min(1, c)); return Math.acos(c) * DEG / 15; }
  var asrAlt = Math.atan(1 / (1 + Math.abs(tanD(Math.abs(LAT - decl))))) * DEG;
  function m(h){ var x = Math.round((h + TZ) * 60); return ((x % 1440) + 1440) % 1440; }
  var maghrib = m(noon + H(-0.833));
  return {
    fajr: m(noon - H(-18.5)), sunrise: m(noon - H(-0.833)), dhuhr: m(noon),
    asr: m(noon + H(asrAlt)), maghrib: maghrib, isha: (maghrib + (isRamadan(d) ? 120 : 90)) % 1440
  };
}
var PRAYER_NAMES = [['fajr', 'الفجر'], ['dhuhr', 'الظهر'], ['asr', 'العصر'], ['maghrib', 'المغرب'], ['isha', 'العشاء']];
function prayerMinutes(d){
  var t = prayerTimes(d);
  return PRAYER_NAMES.map(function (p) { return { key: p[0], name: p[1], min: t[p[0]] }; });
}
function hhmm(m){ var h = Math.floor(m/60) % 24, mm = m % 60; return (h < 10 ? '0' : '') + h + ':' + (mm < 10 ? '0' : '') + mm; }
function nextPrayer(d){
  var nowMin = riyadhMinutes(d);
  var list = prayerMinutes(d);
  var next = list.find(function (p) { return p.min > nowMin; });
  if (!next) next = prayerMinutes(new Date(d.getTime() + 86400000))[0];
  var diff = next.min > nowMin ? next.min - nowMin : next.min + 1440 - nowMin;
  return { name: next.name, time: hhmm(next.min), diff: diff };
}
/* إرسال مواعيد اليوم والغد للخدمة لتنبّه حتى والخرائط مفتوحة */
function pushPrayerSchedule(){
  if (!HAS_NATIVE) return;
  var out = [], now = new Date();
  [0, 1].forEach(function (k) {
    var d = new Date(now.getTime() + k * 86400000);
    var s = new Date(d.getTime() + TZ * 3600000);
    var mid = Date.UTC(s.getUTCFullYear(), s.getUTCMonth(), s.getUTCDate()) - TZ * 3600000;
    prayerMinutes(d).forEach(function (p) { out.push({ name: p.name, at: mid + p.min * 60000 }); });
  });
  nat('setPrayers', JSON.stringify(out));
}

/* ===== القبلة: الاتجاه من الشمال نحو الكعبة ===== */
var KAABA = { lat: 21.4225, lng: 39.8262 };
function qiblaBearing(lat, lng){
  var p1 = lat * RAD, p2 = KAABA.lat * RAD, dl = (KAABA.lng - lng) * RAD;
  var y = Math.sin(dl) * Math.cos(p2);
  var x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
  return norm360(Math.atan2(y, x) * DEG);
}
function distKm(a, b, c, d){
  var R = 6371, dLat = (c - a) * RAD, dLon = (d - b) * RAD;
  var h = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.cos(a*RAD) * Math.cos(c*RAD) * Math.sin(dLon/2) * Math.sin(dLon/2);
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}

/* ===== التاريخ: يتجاهل الفشل إن لم يدعم الجهاز التقويم الهجري ===== */
function fmt(opts, locale, d){
  try { return new Intl.DateTimeFormat(locale, opts).format(d); } catch (e) { return ''; }
}

/* ===== اسم السيارة مكان الشعار (نص فقط) أو صورة يختارها المستخدم ===== */
var BRANDS = {
  toyota:   { label: 'تويوتا',   text: 'TOYOTA' },
  lexus:    { label: 'لكزس',     text: 'LEXUS' },
  gr:       { label: 'GR',       text: 'GR' },
  daihatsu: { label: 'دايهاتسو', text: 'DAIHATSU' },
  hino:     { label: 'هينو',     text: 'HINO' }
};
var LOGO_KEY = 'carlauncher.logo';
function loadLogo(){
  var p = loadPrefs();
  if (p.logoImg) { var u = userImageUrl(p.logoImg); if (u) return u; }
  try { return localStorage.getItem(LOGO_KEY) || ''; } catch (e) { return ''; }
}
function saveLogo(d){
  // في التطبيق تُحفظ كملف؛ في المتصفح في التخزين المحلي
  if (!d) { try { localStorage.removeItem(LOGO_KEY); } catch (e) {} return true; }
  var name = nat('saveImage', 'logo', d);
  if (name) { _imgs = null; var p = loadPrefs(); p.logoImg = name; savePrefs(p); return true; }
  try { localStorage.setItem(LOGO_KEY, d); return true; } catch (e) { return false; }
}
function renderBrand(p){
  var els = document.querySelectorAll('.logo');
  for (var i = 0; i < els.length; i++) {
    var el = els[i];
    el.className = 'logo size-' + (p.brandSize || 'm');
    el.innerHTML = '';
    if (p.brand === 'none') { el.classList.add('hidden'); continue; }
    if (p.brand === 'image') {
      var src = loadLogo();
      if (src) { var im = document.createElement('img'); im.src = src; im.alt = 'شعار'; el.appendChild(im); continue; }
    }
    var main = p.brand === 'text' ? (p.brandText || 'رفيق الطريق') : (BRANDS[p.brand] || BRANDS.toyota).text;
    el.style.direction = p.brand === 'text' ? 'rtl' : '';
    var m = document.createElement('span'); m.textContent = main; el.appendChild(m);
    (p.subBrands || []).filter(function (k) { return k !== p.brand && BRANDS[k]; }).forEach(function (k) {
      var s = document.createElement('span'); s.className = 'sep'; s.textContent = '|'; el.appendChild(s);
      var b = document.createElement('span'); b.className = 'sub-brand'; b.textContent = BRANDS[k].text; el.appendChild(b);
    });
  }
}
/* تصغير الصورة قبل حفظها */
function resizeImage(file, cb){
  var r = new FileReader();
  r.onload = function () {
    var img = new Image();
    img.onload = function () {
      var h = Math.min(160, img.height), w = Math.round(img.width * h / img.height);
      if (w > 640) { w = 640; h = Math.round(img.height * w / img.width); }
      var c = document.createElement('canvas'); c.width = w; c.height = h;
      c.getContext('2d').drawImage(img, 0, 0, w, h);
      cb(c.toDataURL('image/png'));
    };
    img.onerror = function () { cb(''); };
    img.src = r.result;
  };
  r.onerror = function () { cb(''); };
  r.readAsDataURL(file);
}

/* ===== التطبيقات: فتح الخرائط والموسيقى ===== */
var KNOWN_APPS = {
  maps: ['com.google.android.apps.maps', 'com.waze', 'com.here.app.maps', 'ru.yandex.yandexnavi'],
  music: ['com.spotify.music', 'com.google.android.apps.youtube.music', 'com.anghami', 'com.apple.android.music', 'com.soundcloud.android', 'com.android.music']
};
function openRole(role){
  var p = loadPrefs();
  var pkg = role === 'maps' ? p.mapsApp : p.musicApp;
  if (pkg && nat('launch', pkg)) return;
  // لم يُختر تطبيق: نجرّب المعروف المثبت، وإلا نفتح قائمة الاختيار
  var cands = KNOWN_APPS[role] || [];
  for (var i = 0; i < cands.length; i++) {
    if (nat('installed', cands[i])) {
      if (role === 'maps') p.mapsApp = cands[i]; else p.musicApp = cands[i];
      savePrefs(p);
      nat('launch', cands[i]); return;
    }
  }
  location.href = 'apps.html?pick=' + role;
}

/* ===== الصيانة ===== */
var MAINT_KEY = 'carlauncher.maint';
var DEFAULT_MAINT = [
  { id: 'oil',   name: 'تغيير الزيت',       km: 5000,  months: 6 },
  { id: 'tires', name: 'تدوير الإطارات',    km: 10000, months: 0 },
  { id: 'filter',name: 'فلتر الهواء',       km: 20000, months: 12 },
  { id: 'reg',   name: 'تجديد الاستمارة',   km: 0,     months: 0 },
  { id: 'ins',   name: 'تجديد التأمين',     km: 0,     months: 12 },
  { id: 'mvpi',  name: 'الفحص الدوري',      km: 0,     months: 12 }
];
function loadMaint(){
  try { var r = localStorage.getItem(MAINT_KEY); if (r) return JSON.parse(r); } catch (e) {}
  return DEFAULT_MAINT.map(function (x) { return Object.assign({ lastDate: '', lastKm: null, dueDate: '' }, x); });
}
function saveMaint(l){ try { localStorage.setItem(MAINT_KEY, JSON.stringify(l)); } catch (e) {} }
/* عداد السيارة = مسافة GPS الكلية + فرق يضبطه المستخدم مرة واحدة */
function carOdometer(){
  var p = loadPrefs(), t = natJSON('trip');
  if (p.odoOffset === null || p.odoOffset === undefined || !t) return null;
  return Math.round(t.odo + p.odoOffset);
}
function maintStatus(it){
  var odo = carOdometer(), out = { kmLeft: null, daysLeft: null };
  if (it.km && it.lastKm !== null && it.lastKm !== '' && odo !== null) out.kmLeft = (+it.lastKm + it.km) - odo;
  var due = it.dueDate;
  if (!due && it.months && it.lastDate) {
    var d = new Date(it.lastDate); d.setMonth(d.getMonth() + it.months); due = d.toISOString().slice(0, 10);
  }
  if (due) out.daysLeft = Math.ceil((new Date(due + 'T00:00:00') - new Date()) / 86400000);
  out.due = (out.kmLeft !== null && out.kmLeft <= 500) || (out.daysLeft !== null && out.daysLeft <= 14);
  out.dueDate = due;
  return out;
}

/* ===== تذكيرات من التقويم الهجري ===== */
function hijriParts(d){
  try {
    var parts = new Intl.DateTimeFormat('en-u-ca-islamic-umalqura-nu-latn', { day: 'numeric', month: 'numeric', timeZone: 'Asia/Riyadh' }).formatToParts(d);
    var o = {}; parts.forEach(function (x) { o[x.type] = parseInt(x.value, 10); });
    return o.day && o.month ? { d: o.day, m: o.month } : null;
  } catch (e) { return null; }
}
function daysUntilHijri(month, day, from){
  for (var i = 0; i < 400; i++) {
    var h = hijriParts(new Date(from.getTime() + i * 86400000));
    if (!h) return null;
    if (h.m === month && h.d === day) return i;
  }
  return null;
}
/* تذكيرات اليوم: الجمعة، صيام الإثنين والخميس، الأيام البيض، العد التنازلي لرمضان والعيدين */
function hijriReminders(d){
  d = d || new Date();
  var out = [], wd = new Date(d.getTime() + TZ * 3600000).getUTCDay(), h = hijriParts(d);
  var tomorrow = new Date(d.getTime() + 86400000), ht = hijriParts(tomorrow);
  if (wd === 5) out.push('يوم الجمعة: سورة الكهف والإكثار من الصلاة على النبي ﷺ');
  if (wd === 0 || wd === 3) out.push('غداً ' + (wd === 0 ? 'الإثنين' : 'الخميس') + ': يُستحب صيامه');
  if (ht && ht.d >= 13 && ht.d <= 15 && ht.m !== 9) out.push('الأيام البيض (13 و14 و15): يُستحب صيامها');
  if (h) {
    var r = h.m === 9 ? null : daysUntilHijri(9, 1, d);
    if (h.m === 9) out.push('رمضان مبارك — اليوم ' + h.d + ' من رمضان');
    else if (r !== null && r <= 30) out.push('باقي ' + r + ' يوم على رمضان');
    var f = daysUntilHijri(10, 1, d); if (f !== null && f > 0 && f <= 10) out.push('باقي ' + f + ' أيام على عيد الفطر');
    var a = daysUntilHijri(12, 10, d); if (a !== null && a > 0 && a <= 10) out.push('باقي ' + a + ' أيام على عيد الأضحى');
    if (h.m === 12 && h.d === 9) out.push('اليوم يوم عرفة: يُستحب صيامه لغير الحاج');
    if (h.m === 12 && h.d >= 1 && h.d <= 8) out.push('العشر من ذي الحجة: أيام فاضلة');
  }
  return out;
}

/* ===== القفل برقم سري لصفحات الإعدادات ===== */
function pinHash(p){ var h = 5381; for (var i = 0; i < p.length; i++) h = ((h << 5) + h + p.charCodeAt(i)) | 0; return 'h' + (h >>> 0).toString(36); }
function pinUnlocked(){ try { return sessionStorage.getItem('carlauncher.unlocked') === '1'; } catch (e) { return false; } }
function requirePin(onOk){
  var p = loadPrefs();
  if (!p.pin || pinUnlocked()) { onOk(); return; }
  var st = document.getElementById('stage') || document.body;
  var ov = document.createElement('div');
  ov.style.cssText = 'position:absolute;inset:0;z-index:50;background:rgba(5,10,25,.96);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18px;color:#fff';
  ov.innerHTML = '<div style="font-size:26px;font-weight:700">أدخل الرقم السري</div><div id="pinDots" style="font-size:40px;letter-spacing:14px;height:48px;direction:ltr">&nbsp;</div><div id="pinPad" style="display:grid;grid-template-columns:repeat(3,96px);gap:12px;direction:ltr"></div><a href="index.html" style="font-size:18px;color:#9FB0CC;margin-top:6px">رجوع للرئيسية</a>';
  st.appendChild(ov);
  var val = '', pad = ov.querySelector('#pinPad'), dots = ov.querySelector('#pinDots');
  ['1','2','3','4','5','6','7','8','9','⌫','0','✓'].forEach(function (k) {
    var b = document.createElement('button');
    b.textContent = k;
    b.style.cssText = 'height:76px;border-radius:20px;background:#13254a;border:1px solid #2b4a80;font-size:30px;color:#fff;font-family:inherit';
    b.onclick = function () {
      if (k === '⌫') val = val.slice(0, -1);
      else if (k === '✓') {
        if (pinHash(val) === p.pin) { try { sessionStorage.setItem('carlauncher.unlocked', '1'); } catch (e) {} ov.remove(); onOk(); return; }
        dots.textContent = 'خطأ'; val = ''; setTimeout(function () { dots.innerHTML = '&nbsp;'; }, 900); return;
      } else if (val.length < 8) val += k;
      dots.textContent = val ? val.replace(/./g, '•') : '\u00a0';
    };
    pad.appendChild(b);
  });
}

/* ===== النسخة الاحتياطية: كل مفاتيح الواجهة ===== */
function webBackupJSON(){
  var o = {};
  try { for (var i = 0; i < localStorage.length; i++) { var k = localStorage.key(i); if (k.indexOf('carlauncher.') === 0) o[k] = localStorage.getItem(k); } } catch (e) {}
  return JSON.stringify(o);
}
function applyPendingRestore(){
  var r = nat('pendingRestore');
  if (!r) return false;
  try {
    var o = JSON.parse(r);
    Object.keys(localStorage).forEach(function (k) { if (k.indexOf('carlauncher.') === 0) localStorage.removeItem(k); });
    Object.keys(o).forEach(function (k) { if (k.indexOf('carlauncher.') === 0) localStorage.setItem(k, o[k]); });
    _imgs = null;
    return true;
  } catch (e) { return false; }
}

/* ===== عند الرجوع للتطبيق ===== */
window.onResumeNative = function () { _imgs = null; if (window.onPageResume) window.onPageResume(); };

/* عند تحميل أي صفحة: إرسال الإعدادات والمواقيت للخدمة */
(function () {
  try { if (applyPendingRestore()) { location.reload(); return; } } catch (e) {}
  try { pushConfig(loadPrefs()); setCity(loadPrefs().city); pushPrayerSchedule(); } catch (e) {}
})();

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
  bootAction: 'none', pin: '', nightBright: false, nightPct: 30, reminders: true,
  /* العرض */
  h24: false, digits: 'en', clockFont: '', duaFont: 'amiri',
  /* الواجهة: التصميم، الصفحات والبطاقات لكل تصميم، القائمة السفلية */
  layout: 'classic', homes: null,
  dock: ['maps', 'music', 'apps', 'rest', 'azkar', 'trip', 'settings'], dockLabels: true,
  statusIcons: { bt: true, wifi: true, gps: true }, favApps: [], radioApp: '',
  /* التذكيرات */
  sysRem: { friday: true, fasting: true, white: true, ramadan: true, eid: true, dhulhijja: true }, remPlace: 'banner',
  /* شاشة الاستراحة */
  restStyle: 'classic', restShow: { dua: true, wird: true, remind: true, prayer: true, date: true },
  duaSec: 15, restReturn: 0, restAuto: 'manual', restAutoMin: 5, customDuas: [], hiddenDuas: [],
  /* القيادة */
  safeMode: true, weatherOn: true
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

/* الخطوط: للواجهة، وللساعة والأرقام الكبيرة، وللأدعية */
var FONTS = {
  thmanyah: { label: 'ثمانية سانس', css: "'Thmanyah'" },
  hacen:    { label: 'هاسن تونس', css: "'HacenTunisia'" },
  mohammad: { label: 'محمد بولد آرت', css: "'MohammadBoldArt'" },
  elgharib: { label: 'حياكم الله', css: "'ElgharibHayyakum'" },
  komission:{ label: 'كوميشن', css: "'KOMission'" },
  amiri:    { label: 'نسخ (للأدعية)', css: "'Amiri','Noto Naskh Arabic',serif" },
  system:   { label: 'خط الجهاز', css: "'Noto Sans Arabic',Tahoma,'Segoe UI',Arial" }
};
function fontCss(key, fallback){ var f = FONTS[key] || FONTS[fallback] || FONTS.thmanyah; return f.css + ", 'Thmanyah', 'Noto Sans Arabic', Tahoma, sans-serif"; }
function fontStack(p){ return fontCss(p.font, 'thmanyah'); }

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
  if (stage) {
    stage.style.fontFamily = fontStack(p);
    stage.style.setProperty('--font-clock', fontCss(p.clockFont || p.font, 'thmanyah'));
    stage.style.setProperty('--font-dua', fontCss(p.duaFont || 'amiri', 'amiri'));
  }
  document.body.classList.toggle('solid', !!p.solid);
  renderBrand(p);
}

/* ===== مواقيت الصلاة على الجهاز (بدون إنترنت) — طريقة أم القرى =====
   الفجر 18.5°، العشاء بعد المغرب بـ 90 دقيقة (120 في رمضان)، العصر مثل الظل.
   تمت مقارنتها بجدول أم القرى للرياض: الفرق دقيقة واحدة أو أقل. */
var CITIES = {
  riyadh:  { label: 'الرياض',  lat: 24.7136, lng: 46.6753 },
  yanbu:   { label: 'ينبع',    lat: 24.0895, lng: 38.0618 },
  taif:    { label: 'الطائف',  lat: 21.2703, lng: 40.4158 },
  hail:    { label: 'حائل',    lat: 27.5114, lng: 41.7208 },
  jazan:   { label: 'جازان',   lat: 16.8892, lng: 42.5511 },
  najran:  { label: 'نجران',   lat: 17.5656, lng: 44.2289 },
  jubail:  { label: 'الجبيل',  lat: 27.0046, lng: 49.6460 },
  hofuf:   { label: 'الأحساء', lat: 25.3830, lng: 49.5866 },
  qatif:   { label: 'القطيف',  lat: 26.5196, lng: 50.0115 },
  khamis:  { label: 'خميس مشيط', lat: 18.3000, lng: 42.7333 },
  arar:    { label: 'عرعر',    lat: 30.9753, lng: 41.0381 },
  sakaka:  { label: 'سكاكا',   lat: 29.9697, lng: 40.2064 },
  baha:    { label: 'الباحة',  lat: 20.0129, lng: 41.4677 },
  ula:     { label: 'العُلا',  lat: 26.6080, lng: 37.9232 },
  hafar:   { label: 'حفر الباطن', lat: 28.4328, lng: 45.9708 },
  kharj:   { label: 'الخرج',   lat: 24.1556, lng: 47.3120 },
  unaizah: { label: 'عنيزة',   lat: 26.0843, lng: 43.9935 },
  rabigh:  { label: 'رابغ',    lat: 22.7986, lng: 39.0349 },
  dawadmi: { label: 'الدوادمي', lat: 24.5077, lng: 44.3924 },
  qurayyat:{ label: 'القريات', lat: 31.3318, lng: 37.3428 },
  bisha:   { label: 'بيشة',    lat: 20.0005, lng: 42.6052 },
  khafji:  { label: 'الخفجي',  lat: 28.4391, lng: 48.4913 },
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
  return { name: next.name, time: hhmm(next.min), timeFmt: fmtMin(next.min), diff: diff };
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
  var sr = Object.assign({}, DEFAULT_PREFS.sysRem, loadPrefs().sysRem || {});
  if (sr.friday && wd === 5) out.push('يوم الجمعة: سورة الكهف والإكثار من الصلاة على النبي ﷺ');
  if (sr.fasting && (wd === 0 || wd === 3)) out.push('غداً ' + (wd === 0 ? 'الإثنين' : 'الخميس') + ': يُستحب صيامه');
  if (sr.white && ht && ht.d >= 13 && ht.d <= 15 && ht.m !== 9) out.push('الأيام البيض (13 و14 و15): يُستحب صيامها');
  if (h) {
    var r = h.m === 9 ? null : daysUntilHijri(9, 1, d);
    if (sr.ramadan && h.m === 9) out.push('رمضان مبارك — اليوم ' + h.d + ' من رمضان');
    else if (sr.ramadan && r !== null && r <= 30) out.push('باقي ' + r + ' يوم على رمضان');
    var f = daysUntilHijri(10, 1, d); if (sr.eid && f !== null && f > 0 && f <= 10) out.push('باقي ' + f + ' أيام على عيد الفطر');
    var a = daysUntilHijri(12, 10, d); if (sr.eid && a !== null && a > 0 && a <= 10) out.push('باقي ' + a + ' أيام على عيد الأضحى');
    if (sr.dhulhijja && h.m === 12 && h.d === 9) out.push('اليوم يوم عرفة: يُستحب صيامه لغير الحاج');
    if (sr.dhulhijja && h.m === 12 && h.d >= 1 && h.d <= 8) out.push('العشر من ذي الحجة: أيام فاضلة');
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


/* ================================================================
   العرض: صيغة الوقت 12/24 والأرقام العربية أو الإنجليزية
   ================================================================ */
function clockStr(d, withSec){
  var p = loadPrefsCached(), h = d.getHours(), m = d.getMinutes(), s = d.getSeconds();
  var hh = p.h24 ? (h < 10 ? '0' : '') + h : String(h % 12 || 12);
  var out = hh + ':' + (m < 10 ? '0' : '') + m;
  if (withSec) out += ':' + (s < 10 ? '0' : '') + s;
  return out;
}
function ampm(d){ return loadPrefsCached().h24 ? '' : (d.getHours() < 12 ? 'ص' : 'م'); }
/* وقت الصلاة من دقائق اليوم */
function fmtMin(min){
  var p = loadPrefsCached(), h = Math.floor(min / 60) % 24, m = min % 60;
  if (p.h24) return (h < 10 ? '0' : '') + h + ':' + (m < 10 ? '0' : '') + m;
  return (h % 12 || 12) + ':' + (m < 10 ? '0' : '') + m + ' ' + (h < 12 ? 'ص' : 'م');
}
/* «بعد 50 د» أو «بعد 1 س 5 د» */
function fmtDiff(min){
  var h = Math.floor(min / 60), m = min % 60;
  if (h === 0) return 'بعد ' + m + ' د';
  return 'بعد ' + h + ' س' + (m ? ' ' + m + ' د' : '');
}
var _prefsCache = null, _prefsAt = 0;
function loadPrefsCached(){
  if (!_prefsCache || Date.now() - _prefsAt > 3000) { _prefsCache = loadPrefs(); _prefsAt = Date.now(); }
  return _prefsCache;
}
/* تحويل الأرقام في الصفحة كلها إلى ٠١٢٣ عند اختيار الأرقام العربية */
var AR_DIGITS = '٠١٢٣٤٥٦٧٨٩';
function toArDigits(t){ return t.replace(/[0-9]/g, function (c) { return AR_DIGITS[+c]; }); }
function localizeDigits(root){
  if (loadPrefsCached().digits !== 'ar' || !root) return;
  var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null), n;
  while ((n = w.nextNode())) {
    var pe = n.parentNode;
    if (!pe || pe.closest && pe.closest('input,textarea,script,style,.latin')) continue;
    if (/[0-9]/.test(n.nodeValue)) n.nodeValue = toArDigits(n.nodeValue);
  }
}
(function () {
  if (typeof MutationObserver === 'undefined') return;
  var pending = false;
  var mo = new MutationObserver(function () {
    if (pending) return; pending = true;
    setTimeout(function () { pending = false; localizeDigits(document.body); }, 0);
  });
  function start(){ if (document.body) { mo.observe(document.body, { childList: true, subtree: true, characterData: true }); localizeDigits(document.body); } }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();

/* ================================================================
   وضع القيادة الآمن: صفحات الكتابة والإعدادات تُقفل أثناء الحركة
   الصفحة تعلن ذلك بـ <body data-nodrive="1">
   ================================================================ */
function currentSpeed(){ var t = natJSON('trip'); return t && t.gps ? t.speed : 0; }
function isDriving(){ return loadPrefsCached().safeMode && currentSpeed() > 10; }
(function () {
  function check(){
    if (!document.body || !document.body.getAttribute('data-nodrive')) return;
    var ov = document.getElementById('driveLock');
    if (isDriving()) {
      if (!ov) {
        ov = document.createElement('div'); ov.id = 'driveLock';
        ov.style.cssText = 'position:absolute;inset:0;z-index:60;background:rgba(5,10,25,.95);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:22px;color:#fff;text-align:center';
        ov.innerHTML = '<div style="font-size:34px;font-weight:700">هذه الصفحة تعمل والسيارة متوقفة</div><div style="font-size:20px;color:#9FB0CC">للسلامة أثناء القيادة. يمكنك إيقاف هذا من الإعدادات ▸ النظام.</div><a href="index.html" style="height:64px;padding:0 34px;border-radius:20px;background:#1F5FD1;display:flex;align-items:center;font-size:22px">الرئيسية</a>';
        (document.getElementById('stage') || document.body).appendChild(ov);
      }
    } else if (ov) ov.remove();
  }
  setInterval(check, 2000);
  document.addEventListener('DOMContentLoaded', check);
})();

/* ================================================================
   الملاحظات، التذكيرات الشخصية، محطات الراديو (تخزين محلي)
   ================================================================ */
function lsGet(k, def){ try { var r = localStorage.getItem(k); return r ? JSON.parse(r) : def; } catch (e) { return def; } }
function lsSet(k, v){ try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} }
function uid(){ return Date.now().toString(36) + Math.random().toString(36).slice(2, 6); }
var NOTES_KEY = 'carlauncher.notes', REM_KEY = 'carlauncher.reminders', ST_KEY = 'carlauncher.stations';
function loadNotes(){ return lsGet(NOTES_KEY, []); }
function saveNotes(l){ lsSet(NOTES_KEY, l); }
function loadStations(){ return lsGet(ST_KEY, []); }
function saveStations(l){ lsSet(ST_KEY, l); }
function loadRems(){ return lsGet(REM_KEY, []); }
function saveRems(l){ lsSet(REM_KEY, l); pushReminderAlarms(); }

/* ما يصل من الجوال يُحفظ هنا */
function processInbox(){
  var items = natJSON('inbox');
  if (!items || !items.length) return 0;
  var notes = loadNotes(), rems = loadRems(), st = loadStations();
  items.forEach(function (it) {
    var d = it.data || {};
    if (it.type === 'note' && d.text) notes.unshift({ id: uid(), text: String(d.text).slice(0, 4000), at: it.at || Date.now() });
    if (it.type === 'reminder' && d.text) rems.push(Object.assign({ id: uid(), kind: 'once' }, d));
    if (it.type === 'station' && d.url) st.push({ id: uid(), name: d.name || 'محطة', url: d.url });
  });
  saveNotes(notes); saveStations(st); saveRems(rems);
  return items.length;
}

var WD_NAMES = ['الأحد', 'الإثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];
function todayStr(d){ d = d || new Date(); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); }
function atTime(day, time){ var p = (time || '09:00').split(':'); var d = new Date(day); d.setHours(+p[0], +p[1], 0, 0); return d; }
/* موعد التذكير القادم (للتذكيرات المرتبطة بوقت) */
function remNext(r, from){
  from = from || new Date();
  if (r.kind === 'once') return r.date ? atTime(r.date + 'T00:00:00', r.time) : null;
  if (r.kind === 'daily') { var t = atTime(todayStr(from) + 'T00:00:00', r.time); if (t < from) t.setDate(t.getDate() + 1); return t; }
  if (r.kind === 'weekly') {
    var w = atTime(todayStr(from) + 'T00:00:00', r.time);
    var add = ((+r.wd) - w.getDay() + 7) % 7; w.setDate(w.getDate() + add);
    if (w < from) w.setDate(w.getDate() + 7);
    return w;
  }
  return null;
}
/* التذكيرات الظاهرة الآن */
function activeReminders(){
  var now = new Date(), list = loadRems(), out = [], t = natJSON('trip'), boot = String(nat('bootId'));
  list.forEach(function (r) {
    if (r.done) return;
    if (r.kind === 'start') { if (r.doneBoot !== boot) out.push(r); return; }
    if (r.kind === 'place') {
      if (t && typeof t.lat === 'number' && r.lat) {
        var inside = distKm(t.lat, t.lng, r.lat, r.lng) * 1000 <= (r.radius || 300);
        if (inside && !r.dismissedInside) out.push(r);
      }
      return;
    }
    if (r.kind === 'once') { var a = remNext(r); if (a && now >= a) out.push(r); return; }
    // يومي/أسبوعي: يظهر من وقته حتى نهاية اليوم، إلا إذا أُغلق اليوم
    var start = remNext(r, new Date(now.getTime() - 86400000 + 1));
    if (start && now >= start && todayStr(start) === todayStr(now) && r.doneDay !== todayStr(now)) out.push(r);
  });
  return out;
}
function dismissReminder(id){
  var list = loadRems(), boot = String(nat('bootId'));
  list.forEach(function (r) {
    if (r.id !== id) return;
    if (r.kind === 'once') r.done = true;
    else if (r.kind === 'start') r.doneBoot = boot;
    else if (r.kind === 'place') r.dismissedInside = true;
    else r.doneDay = todayStr();
  });
  saveRems(list);
}
/* تذكيرات المكان: تعود بعد الخروج من المنطقة */
function updatePlaceReminders(){
  var t = natJSON('trip'); if (!t || typeof t.lat !== 'number') return;
  var list = loadRems(), changed = false;
  list.forEach(function (r) {
    if (r.kind !== 'place' || !r.lat || !r.dismissedInside) return;
    if (distKm(t.lat, t.lng, r.lat, r.lng) * 1000 > (r.radius || 300) * 1.5) { r.dismissedInside = false; changed = true; }
  });
  if (changed) lsSet(REM_KEY, list);
}
function remDesc(r){
  if (r.kind === 'once') return (r.date || '') + ' ' + fmtMin(timeToMin(r.time));
  if (r.kind === 'daily') return 'يومياً ' + fmtMin(timeToMin(r.time));
  if (r.kind === 'weekly') return 'كل ' + WD_NAMES[+r.wd] + ' ' + fmtMin(timeToMin(r.time));
  if (r.kind === 'start') return 'عند تشغيل السيارة';
  if (r.kind === 'place') return 'عند الوصول للمكان المحفوظ';
  return '';
}
function timeToMin(t){ var p = (t || '00:00').split(':'); return (+p[0]) * 60 + (+p[1]); }
/* إرسال التذكيرات المؤقتة للخدمة لتنبّه في الخلفية */
function pushReminderAlarms(){
  if (!HAS_NATIVE) return;
  var out = [], now = new Date();
  loadRems().forEach(function (r) {
    if (r.done || !(r.kind === 'once' || r.kind === 'daily' || r.kind === 'weekly')) return;
    var n = remNext(r, now);
    if (n && n - now < 2 * 86400000 && n >= now - 600000) out.push({ at: n.getTime(), title: r.text });
  });
  nat('setReminders', JSON.stringify(out));
}

/* ================================================================
   الطقس (اختياري، يحتاج إنترنت)
   ================================================================ */
var WMO = { 0: 'صافٍ', 1: 'صافٍ غالباً', 2: 'غائم جزئياً', 3: 'غائم', 45: 'ضباب', 48: 'ضباب', 51: 'رذاذ خفيف', 53: 'رذاذ', 55: 'رذاذ كثيف',
  61: 'مطر خفيف', 63: 'مطر', 65: 'مطر غزير', 71: 'ثلج خفيف', 73: 'ثلج', 75: 'ثلج كثيف', 80: 'زخات مطر', 81: 'زخات مطر', 82: 'زخات غزيرة',
  95: 'عاصفة رعدية', 96: 'عاصفة رعدية وبَرَد', 99: 'عاصفة رعدية وبَرَد' };
function weatherData(){ return natJSON('weather') || {}; }
function weatherRefreshIfStale(){
  if (!HAS_NATIVE || !loadPrefsCached().weatherOn) return;
  var w = weatherData(), now = Date.now();
  var t = natJSON('trip'), lat = t && typeof t.lat === 'number' ? t.lat : LAT, lng = t && typeof t.lng === 'number' ? t.lng : LNG;
  var moved = w.lat ? distKm(w.lat, w.lng, lat, lng) > 25 : true;
  if ((!w.at || now - w.at > 30 * 60000 || moved) && (!w.lastError || now - w.lastError > 5 * 60000)) nat('weatherRefresh', lat, lng);
}
function agoText(ms){
  var m = Math.round((Date.now() - ms) / 60000);
  if (m < 2) return 'الآن'; if (m < 60) return 'قبل ' + m + ' د';
  var h = Math.round(m / 60); if (h < 24) return 'قبل ' + h + ' س';
  return 'قبل ' + Math.round(h / 24) + ' يوم';
}


/* ================================================================
   الأدعية وورد اليوم (مشتركة بين الرئيسية والاستراحة)
   ================================================================ */
var BASE_DUAS = [
  'اللهم إني أسألك من فضلك ورحمتك، فإنه لا يملكها إلا أنت',
  'ربنا آتنا في الدنيا حسنة وفي الآخرة حسنة وقنا عذاب النار',
  'سبحان الذي سخّر لنا هذا وما كنا له مقرنين، وإنا إلى ربنا لمنقلبون',
  'لا حول ولا قوة إلا بالله',
  'سبحان الله وبحمده، سبحان الله العظيم',
  'أستغفر الله وأتوب إليه',
  'اللهم صلِّ وسلم على نبينا محمد',
  'حسبي الله لا إله إلا هو عليه توكلت',
  'رب اغفر لي ولوالديّ',
  'لا إله إلا أنت سبحانك إني كنت من الظالمين'
];
function duaList(){
  var p = loadPrefsCached();
  var hidden = p.hiddenDuas || [];
  return BASE_DUAS.filter(function (d) { return hidden.indexOf(d) < 0; }).concat(p.customDuas || []);
}
var WIRD = [
  ['ألا بذكر الله تطمئن القلوب', 'الرعد: 28'],
  ['فاذكروني أذكركم واشكروا لي ولا تكفرون', 'البقرة: 152'],
  ['إن مع العسر يسرا', 'الشرح: 6'],
  ['ومن يتوكل على الله فهو حسبه', 'الطلاق: 3'],
  ['وقل رب زدني علما', 'طه: 114'],
  ['لا يكلف الله نفسا إلا وسعها', 'البقرة: 286'],
  ['واصبر فإن الله لا يضيع أجر المحسنين', 'هود: 115'],
  ['وهو معكم أين ما كنتم', 'الحديد: 4'],
  ['ادعوني أستجب لكم', 'غافر: 60'],
  ['وإذا سألك عبادي عني فإني قريب أجيب دعوة الداع إذا دعان', 'البقرة: 186'],
  ['ولا تيأسوا من روح الله', 'يوسف: 87'],
  ['إنما الأعمال بالنيات', 'متفق عليه'],
  ['الكلمة الطيبة صدقة', 'متفق عليه'],
  ['من كان يؤمن بالله واليوم الآخر فليقل خيرا أو ليصمت', 'متفق عليه'],
  ['كلمتان خفيفتان على اللسان، ثقيلتان في الميزان، حبيبتان إلى الرحمن: سبحان الله وبحمده، سبحان الله العظيم', 'متفق عليه'],
  ['أحب الأعمال إلى الله أدومها وإن قل', 'متفق عليه'],
  ['لا يؤمن أحدكم حتى يحب لأخيه ما يحب لنفسه', 'متفق عليه'],
  ['المسلم من سلم المسلمون من لسانه ويده', 'متفق عليه'],
  ['يسروا ولا تعسروا، وبشروا ولا تنفروا', 'متفق عليه'],
  ['الدين النصيحة', 'رواه مسلم'],
  ['إن الله رفيق يحب الرفق في الأمر كله', 'متفق عليه']
];
function todayWird(){
  var d = new Date(), start = new Date(d.getFullYear(), 0, 0);
  return WIRD[Math.floor((d - start) / 86400000) % WIRD.length];
}
/* تصغير الخط حتى يتسع النص في سطر واحد، وإن طال جداً يتحرك ببطء */
function fitOneLine(el, maxPx, minPx){
  if (!el) return;
  el.classList.remove('marquee');
  el.style.whiteSpace = 'nowrap'; el.style.overflow = 'hidden';
  var inner = el.firstElementChild && el.firstElementChild.classList.contains('mq') ? el.firstElementChild : null;
  if (!inner) { inner = document.createElement('span'); inner.className = 'mq'; inner.textContent = el.textContent; el.textContent = ''; el.appendChild(inner); }
  inner.style.animation = 'none'; inner.style.display = 'inline-block'; inner.style.paddingLeft = '0';
  el.style.justifyContent = '';
  var fs = maxPx;
  el.style.fontSize = fs + 'px';
  while (inner.offsetWidth > el.clientWidth && fs > minPx) { fs -= 2; el.style.fontSize = fs + 'px'; }
  if (inner.offsetWidth > el.clientWidth) {
    el.style.justifyContent = 'flex-start';
    var dist = inner.offsetWidth - el.clientWidth + 40;
    inner.style.setProperty('--mq', dist + 'px');
    inner.style.animation = 'mq ' + Math.max(8, dist / 40) + 's linear infinite alternate';
  }
}


/* ================================================================
   الواجهة: البطاقات المتاحة والتصاميم الافتراضية
   ================================================================ */
var CARD_CATALOG = [
  ['clock', 'الساعة'], ['prayer', 'الصلاة القادمة'], ['times', 'مواقيت اليوم'], ['media', 'الموسيقى'],
  ['speed', 'عداد السرعة'], ['trip', 'الرحلة'], ['qibla', 'القبلة'], ['weather', 'الطقس'],
  ['apps', 'تطبيقاتي المفضلة'], ['notes', 'الملاحظات'], ['reminders', 'تذكيراتي'], ['sysrem', 'تذكيرات اليوم'],
  ['maint', 'الصيانة القادمة'], ['fuel', 'الوقود'], ['wird', 'ورد اليوم'], ['radio', 'الراديو'], ['parking', 'موقف السيارة']
];
var LAYOUTS = [['classic', 'كلاسيكي'], ['grid', 'شبكة'], ['side', 'جانبي'], ['minimal', 'بسيط']];
function defaultHomes(p){
  return {
    classic: [[{ id: 'clock', size: 'l' }, { id: (p && p.midCard) || 'media', size: 'm' }, { id: 'prayer', size: 'm' }],
              [{ id: 'speed', size: 'm' }, { id: 'trip', size: 'm' }, { id: 'times', size: 'm' }]],
    grid:    [[{ id: 'clock', size: 'l' }, { id: 'prayer', size: 's' }, { id: 'media', size: 'm' }, { id: 'times', size: 'm' }],
              [{ id: 'speed', size: 's' }, { id: 'weather', size: 's' }, { id: 'qibla', size: 's' }, { id: 'notes', size: 'm' }, { id: 'sysrem', size: 'm' }]],
    side:    [[{ id: 'clock', size: 'm' }, { id: 'prayer', size: 'm' }, { id: 'media', size: 'm' }, { id: 'times', size: 'm' }]],
    minimal: [[{ id: 'prayer', size: 's' }, { id: 'media', size: 's' }, { id: 'weather', size: 's' }]]
  };
}
function getHomes(p){
  var d = defaultHomes(p), h = p.homes || {}, out = {};
  Object.keys(d).forEach(function (k) { out[k] = (h[k] && h[k].length) ? h[k] : d[k]; });
  return out;
}

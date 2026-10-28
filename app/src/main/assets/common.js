/* ملف مشترك لكل صفحات التطبيق: التحجيم، المواقيت، الإعدادات المحفوظة */

/* ===== التحجيم: scale = min(W/1280, H/720) ===== */
function fitStage(){
  var el = document.getElementById('stage');
  if (!el) return;
  var s = Math.min(window.innerWidth / 1280, window.innerHeight / 720);
  el.style.transform = 'translate(-50%,-50%) scale(' + s + ')';
}
window.addEventListener('resize', fitStage);

/* ===== الإعدادات المحفوظة (آمنة إن فشل التخزين) ===== */
var PREFS_KEY = 'carlauncher.prefs';
var DEFAULT_PREFS = { theme: 'mountain', font: 'thmanyah', solid: false, city: 'dhahran' };
function loadPrefs(){
  try {
    var raw = localStorage.getItem(PREFS_KEY);
    return Object.assign({}, DEFAULT_PREFS, raw ? JSON.parse(raw) : {});
  } catch (e) { return Object.assign({}, DEFAULT_PREFS); }
}
function savePrefs(p){
  try { localStorage.setItem(PREFS_KEY, JSON.stringify(p)); } catch (e) {}
}

var THEMES = {
  mountain: { label: 'الجبلي', bg: 'bg/home_mountain.jpg', rest: 'bg/rest_mountain.jpg', ground: '#0a1324' },
  night:    { label: 'الليلي', bg: 'bg/home_night.jpg',    rest: 'bg/rest_night.jpg',    ground: '#050505' },
  desert:   { label: 'الصحراوي', bg: 'bg/home_desert.jpg', rest: 'bg/rest_desert.jpg',   ground: '#2a1208' },
  day:      { label: 'النهاري', bg: 'bg/home_day.jpg',     rest: 'bg/rest_day.jpg',      ground: '#c9ced3' },
  violet:   { label: 'البنفسجي', bg: 'bg/home_violet.jpg', rest: 'bg/rest_violet.jpg',   ground: '#140826' },
  glow:     { label: 'الذهبي', bg: 'bg/home_glow.jpg',     rest: 'bg/rest_glow.jpg',     ground: '#1a1408' }
};
function fontStack(p){
  return p.font === 'system'
    ? "'Noto Sans Arabic', Tahoma, 'Segoe UI', Arial, sans-serif"
    : "'Thmanyah', 'Noto Sans Arabic', Tahoma, sans-serif";
}
/* تطبيق الإعدادات على الصفحة الحالية */
function applyPrefs(bgKey, p){
  setCity(p.city);
  var wc = document.getElementById('weatherCity');
  if (wc) wc.textContent = (CITIES[p.city] || CITIES.dhahran).label;
  var t = THEMES[p.theme] || THEMES.mountain;
  var img = document.getElementById('bg');
  if (img) img.src = (bgKey === 'rest') ? t.rest : t.bg;
  var stage = document.getElementById('stage');
  if (stage) stage.style.fontFamily = fontStack(p);
  document.body.classList.toggle('solid', !!p.solid);
}

/* ===== مواقيت الصلاة محسوبة على الجهاز (بدون إنترنت) — الظهران، أم القرى ===== */
var CITIES = {
  dhahran: { label: 'الظهران', lat: 26.29, lng: 50.11 },
  riyadh:  { label: 'الرياض',  lat: 24.7136, lng: 46.6753 }
};
var LAT = 26.29, LNG = 50.11, TZ = 3, RAD = Math.PI / 180, DEG = 180 / Math.PI;
function setCity(key){ var c = CITIES[key] || CITIES.dhahran; LAT = c.lat; LNG = c.lng; }
function sinD(x){ return Math.sin(x*RAD); } function cosD(x){ return Math.cos(x*RAD); } function tanD(x){ return Math.tan(x*RAD); }
function norm360(x){ x = x % 360; return x < 0 ? x + 360 : x; }
function prayerMinutes(d){
  // التاريخ والوقت بتوقيت الرياض بغض النظر عن منطقة الجهاز
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
  var maghrib = noon + H(-0.833);
  var raw = [['الفجر', noon - H(-18.5)], ['الظهر', noon], ['العصر', noon + H(asrAlt)], ['المغرب', maghrib], ['العشاء', maghrib + 1.5]];
  // الدقائق تُلَفّ على 1440 (يوم كامل) لا على 360
  return raw.map(function (p) {
    var m = Math.round((p[1] + TZ) * 60);
    return { name: p[0], min: ((m % 1440) + 1440) % 1440 };
  });
}
function hhmm(m){ var h = Math.floor(m/60) % 24, mm = m % 60; return (h < 10 ? '0' : '') + h + ':' + (mm < 10 ? '0' : '') + mm; }
function nextPrayer(d){
  var s = new Date(d.getTime() + TZ * 3600000);
  var nowMin = s.getUTCHours() * 60 + s.getUTCMinutes();
  var list = prayerMinutes(d).filter(function (p) { return p.name !== 'الشروق'; });
  var next = list.find(function (p) { return p.min > nowMin; }) || list[0];
  var diff = next.min > nowMin ? next.min - nowMin : next.min + 1440 - nowMin;
  return { name: next.name, time: hhmm(next.min), diff: diff };
}

/* ===== التاريخ: يتجاهل الفشل إن لم يدعم الجهاز التقويم الهجري ===== */
function fmt(opts, locale, d){
  try { return new Intl.DateTimeFormat(locale, opts).format(d); } catch (e) { return ''; }
}

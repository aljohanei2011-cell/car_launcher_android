/* ===== شكل «أسود OLED» للوضع الخفيف =====
   بطاقات مسطحة على خلفية مصمتة (داكن أو فاتح)، كل الأشكال مرسومة (SVG صغير) بدون صور ولا زجاج ولا حركة مستمرة.
   يُستخدم فقط في الوضع الخفيف وعند اختيار «أسود OLED» من الإعدادات. */
function oledOn(p){ p = p || prefs; return LITE && p.liteLook !== 'classic'; }
/* فاتح؟ تلقائي = فاتح من الشروق حتى المغرب */
function oledLight(p){ p = p || prefs; var m = p.oledMode || 'auto'; return m === 'light' || (m === 'auto' && !isNight()); }
function oledApply(p){
  var on = oledOn(p), b = document.body;
  b.classList.toggle('oled', on);
  b.classList.toggle('ol', on && oledLight(p));
}

var OC = { prayer: '#FFA21F', match: '#FF4D5E', wx: '#FFB020', speed: '#3ED39A', media: '#B95CFF', clock: '#7AA7FF', trip: '#5FD0D0', fuel: '#FF8A4C', maint: '#FFB020', rem: '#FF5FA2', note: '#7FB8FF', wird: '#E8C061', qibla: '#3ED39A', park: '#5AB0FF', radio: '#FF9F5A', ram: '#3ED39A', apps: '#A98BFF', sys: '#C9B6FF' };
function oHd(tag, col, right){ return '<div class="o-hd"><span class="o-tag" style="color:' + col + '">● ' + tag + '</span><span class="o-r">' + (right || '') + '</span></div>'; }
function oChip(t, col){ return '<span class="o-chip"' + (col ? ' style="background:' + col + '22;color:' + col + '"' : '') + '>' + t + '</span>'; }
/* حلقة تقدّم: pct من 0 إلى 1 */
function oRing(pct, col, inner, cls){
  var C = 2 * Math.PI * 50, p = Math.max(0, Math.min(1, pct || 0));
  return '<div class="o-ring ' + (cls || '') + '"><svg viewBox="0 0 120 120"><circle cx="60" cy="60" r="50" fill="none" stroke="' + col + '" stroke-opacity=".18" stroke-width="11"/>' +
    (p > 0 ? '<circle cx="60" cy="60" r="50" fill="none" stroke="' + col + '" stroke-width="11" stroke-linecap="round" stroke-dasharray="' + (C * p).toFixed(1) + ' ' + C.toFixed(1) + '" transform="rotate(-90 60 60)"/>' : '') +
    '</svg><div class="o-rin">' + inner + '</div></div>';
}
function oBar(pct, col){ return '<div class="o-bar"><i style="width:' + Math.round(Math.max(0, Math.min(1, pct)) * 100) + '%;background:' + col + '"></i></div>'; }
function oHM(min){ min = Math.max(0, Math.round(min)); return Math.floor(min / 60) + ':' + pad2(min % 60); }

var OLED = {};
OLED.prayer = function (o) {
  var d = new Date(), np = nextPrayer(d), pl = prayerMinutes(d), now = riyadhMinutes(d), col = OC.prayer;
  var idx = -1; pl.forEach(function (p, i) { if (p.key === np.key) idx = i; });
  var prev = idx > 0 ? pl[idx - 1].min : (idx === 0 ? pl[pl.length - 1].min - 1440 : now - 60);
  var nxt = idx >= 0 ? pl[idx].min : now + np.diff;
  var span = nxt - prev, pct = span > 0 ? (now - prev) / span : 0;
  var line = '';
  if (cardTall() && !partHidden(o, 'list')) {
    line = '<div class="o-tl"><div class="o-tlt"><i style="width:' + Math.round(Math.max(0, idx < 0 ? 1 : (idx - (1 - pct)) / (pl.length - 1)) * 100) + '%;background:' + col + '"></i></div><div class="o-tlr">' +
      pl.map(function (p, i) { var st = i === idx ? 'on' : (idx < 0 || i < idx) ? 'done' : ''; return '<div class="' + st + '"><b></b><span>' + p.name + '</span><em>' + fmtMin(p.min).replace(/ [صم]$/, '') + '</em></div>'; }).join('') + '</div></div>';
  }
  return '<a href="prayer.html" class="o">' + oHd('الصلاة القادمة', col, partHidden(o, 'city') ? '' : esc(cityLabel())) +
    '<div class="o-mid o-row">' + (partHidden(o, 'count') ? '' : oRing(pct, col, '<b>' + oHM(np.diff) + '</b><small>متبقٍ</small>')) +
    '<div class="o-col"><div class="o-name">' + np.name + '</div><div class="o-t2">' + np.timeFmt + '</div></div></div>' + line + '</a>';
};
OLED.times = function (o) {
  var d = new Date(), pl = prayerMinutes(d), now = riyadhMinutes(d), nx = pl.filter(function (p) { return p.min > now; })[0];
  return '<a href="prayer.html" class="o">' + oHd('مواقيت اليوم', OC.prayer, partHidden(o, 'city') ? '' : esc(cityLabel())) + '<div class="o-list">' +
    pl.map(function (p) { var on = nx && p.key === nx.key, past = p.min <= now; return '<div class="o-li' + (on ? ' on' : '') + (past ? ' past' : '') + '"><span>' + p.name + '</span><span>' + fmtMin(p.min) + '</span></div>'; }).join('') + '</div></a>';
};
OLED.clock = function (o) {
  var d = new Date(), night = isNight(d), col = OC.clock;
  var greg = partHidden(o, 'greg') ? '' : '<div class="o-sub">' + fmt({ weekday: 'long', day: 'numeric', month: 'long' }, 'ar-u-nu-latn', d) + '</div>';
  var hij = partHidden(o, 'hijri') ? '' : '<div class="o-sub">' + fmt({ day: 'numeric', month: 'long', year: 'numeric' }, 'ar-SA-u-ca-islamic-umalqura-nu-latn', d) + '</div>';
  var moon = night ? '<svg class="o-moon" viewBox="0 0 40 40"><circle cx="20" cy="20" r="14" fill="#F2E6C2"/><circle cx="27" cy="15" r="13" fill="var(--oc)"/></svg>' : '';
  return '<div class="o" ondblclick="openRest()">' + oHd(night ? 'الليلة' : 'الآن', col, moon) +
    '<div class="o-mid" style="text-align:center"><div class="o-clock">' + clockStr(d) + ampmHtml(d, o) + '</div>' + greg + hij + clockWxO(o) + '</div></div>';
};
function clockWxO(o){
  if (!prefs.weatherOn || partHidden(o, 'wx') || (window._liteExtra || []).indexOf('weather') >= 0) return '';
  var w = weatherData(); if (!w.at || Date.now() - w.at > 3 * 3600000 || w.temp == null) return '';
  return '<div class="o-sub" style="color:' + OC.wx + '">' + Math.round(w.temp) + '° · ' + (weatherFx(w) === 'dust' ? 'غبار' : (WMO[w.code] || '')) + '</div>';
}
function oWxIcon(code, dust){
  var y = OC.wx;
  if (dust) return '<svg viewBox="0 0 100 100"><g stroke="#C9A36A" stroke-width="7" stroke-linecap="round"><line x1="14" y1="34" x2="74" y2="34"/><line x1="24" y1="52" x2="88" y2="52"/><line x1="10" y1="70" x2="64" y2="70"/></g></svg>';
  var sun = '<circle cx="50" cy="50" r="20" fill="' + y + '"/><g stroke="' + y + '" stroke-width="6" stroke-linecap="round"><line x1="50" y1="9" x2="50" y2="19"/><line x1="50" y1="81" x2="50" y2="91"/><line x1="9" y1="50" x2="19" y2="50"/><line x1="81" y1="50" x2="91" y2="50"/><line x1="21" y1="21" x2="28" y2="28"/><line x1="72" y1="72" x2="79" y2="79"/><line x1="21" y1="79" x2="28" y2="72"/><line x1="72" y1="28" x2="79" y2="21"/></g>';
  var cloud = '<path d="M28 74h46a16 16 0 0 0 0-32 22 22 0 0 0-42-4 18 18 0 0 0-4 36z" fill="#C9D2E3"/>';
  var rain = '<g stroke="#5AB0FF" stroke-width="5" stroke-linecap="round"><line x1="36" y1="82" x2="32" y2="92"/><line x1="52" y1="82" x2="48" y2="92"/><line x1="68" y1="82" x2="64" y2="92"/></g>';
  if (code === 0) return '<svg viewBox="0 0 100 100">' + sun + '</svg>';
  if (code <= 2) return '<svg viewBox="0 0 100 100"><g transform="translate(-14 -14) scale(.8)">' + sun + '</g>' + cloud + '</svg>';
  if (code >= 51) return '<svg viewBox="0 0 100 100"><g transform="translate(0 -8)">' + cloud + '</g>' + rain + '</svg>';
  return '<svg viewBox="0 0 100 100">' + cloud + '</svg>';
}
OLED.weather = function (o) {
  var col = OC.wx;
  if (!prefs.weatherOn) return '<div class="o">' + oHd('الطقس', col) + '<div class="o-mid o-sub">مُطفأ من الإعدادات</div></div>';
  var w = weatherData();
  if (!w.at) return '<div class="o">' + oHd('الطقس', col) + '<div class="o-mid o-sub" style="white-space:normal">يحتاج إنترنت مرة (نقطة اتصال الجوال)</div><button class="o-btn" onclick="nat(\'weatherRefresh\',LAT,LNG)">تحديث</button></div>';
  var dust = weatherFx(w) === 'dust', fx = weatherFx(w);
  var alert = dust ? oChip('غبار', '#C9A36A') : fx === 'rain' ? oChip('مطر', '#5AB0FF') : '';
  var chart = '';
  var hs = (w.hours || []).slice(0, 7);
  if (hs.length >= 3 && cardTall() && !partHidden(o, 'chart')) {
    var ts = hs.map(function (h) { return h[1]; }), mx = Math.max.apply(null, ts), mn = Math.min.apply(null, ts), rg = Math.max(2, mx - mn);
    var W = 300, H = 76, pts = hs.map(function (h, i) { return [14 + i * (W - 28) / (hs.length - 1), 22 + (H - 32) * (1 - (h[1] - mn) / rg)]; });
    var path = pts.map(function (p, i) { return (i ? 'L' : 'M') + p[0].toFixed(1) + ',' + p[1].toFixed(1); }).join(' ');
    chart = '<svg class="o-chart" viewBox="0 0 ' + W + ' ' + (H + 22) + '"><path d="' + path + ' L' + pts[pts.length - 1][0].toFixed(1) + ',' + H + ' L14,' + H + 'Z" fill="' + col + '" fill-opacity=".16"/><path d="' + path + '" fill="none" stroke="' + col + '" stroke-width="3" stroke-linejoin="round"/>' +
      '<circle cx="' + pts[0][0] + '" cy="' + pts[0][1].toFixed(1) + '" r="5" fill="var(--oc)" stroke="' + col + '" stroke-width="3"/>' +
      hs.map(function (h, i) { return i % 2 ? '' : '<text x="' + pts[i][0].toFixed(1) + '" y="' + (H + 18) + '" text-anchor="middle" font-size="13" fill="var(--om)">' + (i === 0 ? 'الآن' : h[0]) + '</text><text x="' + pts[i][0].toFixed(1) + '" y="' + (pts[i][1] - 9).toFixed(1) + '" text-anchor="middle" font-size="13" fill="var(--ot)">' + Math.round(h[1]) + '°</text>'; }).join('') + '</svg>';
  }
  var chips = (partHidden(o, 'more') ? '' : (w.hum != null ? oChip('رطوبة ' + w.hum + '%') : '') + (w.wind != null ? oChip('رياح ' + Math.round(w.wind) + ' كم/س') : ''));
  return '<div class="o">' + oHd(partHidden(o, 'ago') ? 'الطقس' : 'الطقس · ' + agoText(w.at), col, alert) +
    '<div class="o-row" style="justify-content:space-between;align-items:center"><div><div class="o-temp">' + Math.round(w.temp) + '°</div><div class="o-sub">' + (dust ? 'غبار' : (WMO[w.code] || '')) +
    (w.max != null && !partHidden(o, 'range') ? ' · ' + Math.round(w.max) + '° / ' + Math.round(w.min) + '°' : '') + '</div></div><div class="o-wi">' + oWxIcon(w.code, dust) + '</div></div>' +
    chart + '<div class="o-chips">' + chips + '</div></div>';
};
OLED.speed = function (o) {
  var t = natJSON('trip') || {}, v = t.gps ? Math.round(t.speed) : 0, max = 200;
  var lim = +prefs.speedLimit || 120, over = prefs.speedOn && v > lim;
  var rl = prefs.roadLimitOn && HAS_NATIVE ? natJSON('roadLimit') || {} : {}, rlim = +rl.limit || 0;
  if (rlim && v > rlim + (+prefs.roadTol || 10)) over = true;
  var L = 2 * Math.PI * 80 * 0.75, frac = Math.min(v, max) / max, red = '#FF4D5E';
  var showLim = rlim || (prefs.speedOn ? lim : 0);
  var avg = t.movingSec > 120 ? Math.round((t.km || 0) / (t.movingSec / 3600)) : 0;
  return '<div class="o">' + oHd(over ? 'تجاوزت الحد' : 'السرعة', over ? red : OC.speed, t.gps ? 'GPS' : 'بانتظار GPS') +
    '<div class="o-gauge"><svg viewBox="0 0 200 182"><defs><linearGradient id="ogs" x1="0" y1="1" x2="1" y2="0"><stop offset="0" stop-color="#3ED39A"/><stop offset=".6" stop-color="#FFB020"/><stop offset="1" stop-color="#FF4D5E"/></linearGradient></defs>' +
    '<circle cx="100" cy="100" r="80" fill="none" stroke="var(--otr)" stroke-width="13" stroke-linecap="round" stroke-dasharray="' + L.toFixed(1) + ' 999" transform="rotate(135 100 100)"/>' +
    (frac > 0 ? '<circle cx="100" cy="100" r="80" fill="none" stroke="' + (over ? red : 'url(#ogs)') + '" stroke-width="13" stroke-linecap="round" stroke-dasharray="' + (L * frac).toFixed(1) + ' 999" transform="rotate(135 100 100)"/>' : '') +
    '<text x="100" y="108" text-anchor="middle" font-size="52" font-weight="800" fill="' + (over ? red : 'var(--ot)') + '">' + v + '</text><text x="100" y="132" text-anchor="middle" font-size="14" fill="var(--om)">كم/س</text>' +
    '<text x="30" y="178" text-anchor="middle" font-size="12" fill="var(--om)">0</text><text x="170" y="178" text-anchor="middle" font-size="12" fill="var(--om)">' + max + '</text></svg></div>' +
    '<div class="o-chips" style="align-items:center">' + (showLim ? '<span class="o-lim">' + showLim + '</span>' : '') +
    (over && showLim ? oChip('+' + (v - showLim) + ' فوق الحد', red) : (t.km > 0.1 ? oChip('الرحلة ' + (t.km || 0).toFixed(1) + ' كم') : '') + (avg ? oChip('المتوسط ' + avg) : '')) + '</div></div>';
};
OLED.media = function (o) {
  var col = OC.media;
  if (!HAS_NATIVE) return '<div class="o">' + oHd('الموسيقى', col) + '<div class="o-mid o-sub">يعمل داخل التطبيق</div></div>';
  var m = natJSON('media');
  if (!m || m.access === false) return '<div class="o">' + oHd('الموسيقى', col) + '<div class="o-mid o-sub" style="white-space:normal">فعّل «الوصول إلى الإشعارات» مرة واحدة لعرض ما يُشغَّل.</div><button class="o-btn" onclick="nat(\'openNotificationAccess\')">تفعيل</button></div>';
  if (!m.title) return '<div class="o">' + oHd('الموسيقى', col) + '<div class="o-mid o-disc-wrap"><div class="o-disc"></div><div class="o-sub">لا يوجد تشغيل الآن</div></div><button class="o-btn" onclick="openRole(\'music\')">فتح تطبيق الموسيقى</button></div>';
  var prog = '';
  if (m.duration > 0 && !partHidden(o, 'prog')) {
    var pos = Math.min(m.duration, (m.position || 0) + (m.playing ? Date.now() - (m.at || Date.now()) : 0));
    var tt = function (ms) { var s = Math.floor(ms / 1000); return Math.floor(s / 60) + ':' + pad2(s % 60); };
    prog = '<div class="o-prog"><div class="o-bar"><i style="width:' + Math.round(pos / m.duration * 100) + '%;background:var(--ot)"></i></div><div class="o-pt"><span>' + tt(pos) + '</span><span>-' + tt(m.duration - pos) + '</span></div></div>';
  }
  var bars = [55, 85, 70, 100, 60].map(function (h, i) { return '<i style="height:' + (m.playing ? h : 25) + '%;background:' + ['#5AA8FF', '#8C6BFF', '#B95CFF', '#E24FD6', '#FF4FA3'][i] + '"></i>'; }).join('');
  var art = !partHidden(o, 'art') && m.art ? '<div class="o-art" style="background-image:url(' + m.art + ')"></div>' : '<div class="o-disc"></div>';
  return '<div class="o">' + oHd('يُشغَّل الآن', col, partHidden(o, 'app') ? '' : esc(m.app)) +
    (cardTall() ? '<div class="o-row" style="justify-content:space-between;align-items:center">' + art + '<div class="o-eq">' + bars + '</div></div>' : '') +
    '<div><div class="o-title">' + esc(m.title) + '</div><div class="o-sub">' + esc(m.artist) + '</div></div>' + prog +
    '<div class="o-ctl"><button aria-label="السابق" onclick="nat(\'mediaCmd\',\'prev\');later()">' + MI.prev + '</button>' +
    '<button class="big" aria-label="تشغيل أو إيقاف" onclick="nat(\'mediaCmd\',\'' + (m.playing ? 'pause' : 'play') + '\');later()">' + (m.playing ? MI.pause : MI.play) + '</button>' +
    '<button aria-label="التالي" onclick="nat(\'mediaCmd\',\'next\');later()">' + MI.next + '</button></div></div>';
};
OLED.matches = function (o) {
  var col = OC.match, m = matchesData(), ids = (m.cfg.teams || []).map(function (t) { return t.id; });
  if (HAS_NATIVE && m.cfg.on && ids.length && Date.now() - (window._mtT || 0) > 60000) { window._mtT = Date.now(); nat('matchesTick'); }
  if (!HAS_NATIVE || !m.cfg.on || !ids.length) return '<div class="o">' + oHd('المباريات', '#5AD1A0') + '<div class="o-mid o-sub" style="white-space:normal">اختر فريقك لتظهر مبارياته ونتائجه هنا</div><a class="o-btn" href="matches.html">اختيار الفريق</a></div>';
  var e = m.live || m.next || m.last;
  if (!e) return '<a href="matches.html" class="o">' + oHd('المباريات', '#5AD1A0') + '<div class="o-mid o-sub" style="white-space:normal">' + (m.st.error ? 'تعذّر تحميل المواعيد، تُعاد المحاولة تلقائياً' : m.st.at ? 'لا توجد مباراة خلال 3 أسابيع' : 'جارٍ تحميل المواعيد…') + '</div></a>';
  function team(t){ var c = t.color ? '#' + String(t.color).replace('#', '') : '#3a5a8a'; return '<div class="o-team"><span style="background:' + c + '">' + esc((t.ar || t.name || '?').replace(/^ال/, '').charAt(0)) + '</span><b>' + esc(t.ar || t.name) + '</b></div>'; }
  var tag, tcol, mid, under = '';
  if (e.state === 'in') {
    tag = 'مباشر ' + esc(e.clock || ''); tcol = col;
    mid = '<div class="o-score"><span>' + esc(e.home.score || '0') + '</span><i>:</i><span>' + esc(e.away.score || '0') + '</span></div>';
    var mn = parseInt(e.clock, 10) || 0;
    under = '<div class="o-mbar"><div class="o-bar"><i style="width:' + Math.min(100, Math.round(mn / 90 * 100)) + '%;background:linear-gradient(90deg,#FF4D5E,#FF9A5A)"></i></div><div class="o-pt"><span>0\'</span><span>45\'</span><span>90\'</span></div></div>' +
      (e.detail ? '<div class="o-sub" style="text-align:center">' + esc(e.detail) + '</div>' : '');
  } else if (e.state === 'pre') {
    tag = esc(matchWhen(e.date)); tcol = '#5AD1A0';
    var left = Math.max(0, Math.round((e.date - Date.now()) / 60000));
    mid = '<div class="o-score sm">' + fmt({ hour: 'numeric', minute: '2-digit' }, 'ar-u-nu-latn', new Date(e.date)) + '</div>';
    under = '<div class="o-sub" style="text-align:center">' + (left < 1440 ? 'بعد ' + fmtDiff(left).replace(/^بعد /, '') : esc(matchWhen(e.date))) + (e.tv ? ' · ' + esc(e.tv) : '') + '</div>';
  } else {
    var r = matchResult(e, ids); tag = 'انتهت'; tcol = r[1] || '#9FB0CC';
    mid = '<div class="o-score"><span>' + esc(e.home.score || '0') + '</span><i>:</i><span>' + esc(e.away.score || '0') + '</span></div>';
    under = '<div class="o-sub" style="text-align:center;color:' + r[1] + '">' + r[0] + '</div>' + (m.next ? '<div class="o-sub" style="text-align:center">القادمة: ' + esc(matchWhen(m.next.date)) + '</div>' : '');
  }
  return '<a href="matches.html" class="o">' + oHd(tag, tcol, esc(leagueName(e.league))) + '<div class="o-mid o-row o-vs">' + team(e.home) + mid + team(e.away) + '</div>' + under + '</a>';
};
OLED.trip = function (o) {
  var t = natJSON('trip') || {}, mins = Math.floor((t.movingSec || 0) / 60), col = OC.trip;
  var avg = t.movingSec > 120 ? Math.round((t.km || 0) / (t.movingSec / 3600)) : 0;
  return '<a href="trip.html" class="o">' + oHd('الرحلة', col) + '<div class="o-mid"><div class="o-temp">' + (t.km || 0).toFixed(1) + '<small> كم</small></div>' +
    (partHidden(o, 'time') ? '' : '<div class="o-sub">مدة القيادة ' + Math.floor(mins / 60) + ':' + pad2(mins % 60) + '</div>') + '</div>' +
    '<div class="o-chips">' + (avg ? oChip('المتوسط ' + avg + ' كم/س') : '') + (!partHidden(o, 'cost') && t.km > 0.5 ? oChip('الوقود ≈ ' + sar(fuelCost(t.km)), col) : '') + '</div></a>';
};
OLED.fuel = function () {
  var c = fuelStats(), col = OC.fuel;
  return '<a href="expenses.html" class="o">' + oHd('الوقود', col) + '<div class="o-mid">' +
    (c.kml ? '<div class="o-temp">' + c.kml.toFixed(1) + '<small> كم/لتر</small></div>' : '<div class="o-sub" style="white-space:normal">سجّل تعبئتين كاملتين في المصاريف (مع اللترات والعداد)</div>') + '</div>' +
    '<div class="o-chips">' + oChip(c.n + ' تعبئة مسجلة') + '</div></a>';
};
OLED.maint = function () {
  var col = OC.maint, items = [];
  loadMaint().forEach(function (it) {
    var s = maintStatus(it), score = Math.min(s.kmLeft !== null ? s.kmLeft / 50 : 1e9, s.daysLeft !== null ? s.daysLeft : 1e9);
    if (score < 1e9) items.push({ it: it, s: s, score: score });
  });
  items.sort(function (a, b) { return a.score - b.score; });
  var h = '<a href="maintenance.html" class="o">' + oHd('الصيانة', col);
  if (!items.length) return h + '<div class="o-mid o-sub" style="white-space:normal">أدخل مواعيد الصيانة لتظهر هنا</div></a>';
  h += '<div class="o-mlist">';
  items.slice(0, cardTall() ? 3 : 1).forEach(function (x) {
    var s = x.s, frac, txt, c;
    if (s.kmLeft !== null) { frac = x.it.km ? s.kmLeft / x.it.km : 0; txt = s.kmLeft >= 0 ? 'باقي ' + s.kmLeft.toLocaleString('en') + ' كم' : 'متأخر'; }
    else { frac = x.it.months ? s.daysLeft / (x.it.months * 30) : s.daysLeft / 365; txt = s.daysLeft >= 0 ? 'باقي ' + s.daysLeft + ' يوم' : 'منتهي'; }
    c = frac < 0.1 ? '#FF4D5E' : frac < 0.3 ? '#FFB020' : '#3ED39A';
    h += '<div class="o-mi"><div class="o-row" style="justify-content:space-between"><b>' + esc(x.it.name) + '</b><span style="color:' + c + '">' + txt + '</span></div>' + oBar(1 - Math.max(0, frac), c) + '</div>';
  });
  return h + '</div></a>';
};
OLED.reminders = function () {
  var a = activeReminders(), all = loadRems().filter(function (r) { return !r.done; }), col = OC.rem;
  var h = '<div class="o">' + oHd('تذكيراتي', col, '<a href="reminders.html" style="color:inherit">الكل (' + all.length + ')</a>') + '<div class="o-list">';
  if (!a.length) h += '<div class="o-sub" style="white-space:normal">' + (all.length ? 'لا يوجد تذكير الآن' : 'أضف تذكيراً من صفحة التذكيرات') + '</div>';
  a.slice(0, cardTall() ? 3 : 1).forEach(function (r) { h += '<div class="o-rem" style="border-color:' + col + '"><span>' + esc(r.text) + '</span><button onclick="dismissReminder(\'' + sid(r.id) + '\');tick()">تم</button></div>'; });
  return h + '</div></div>';
};
OLED.sysrem = function () {
  var list = hijriReminders();
  var h = '<div class="o">' + oHd('تذكيرات اليوم', OC.sys) + '<div class="o-list">';
  if (!list.length) h += '<div class="o-sub">لا توجد تذكيرات اليوم</div>';
  list.slice(0, 4).forEach(function (r) { h += '<div class="o-rem" style="border-color:' + OC.sys + ';white-space:normal"><span style="white-space:normal">' + esc(r) + '</span></div>'; });
  return h + '</div></div>';
};
OLED.notes = function () {
  var n = sortedNotes(), f = n[0], cat = f ? NOTE_CATS[noteCat(f)] : null, col = cat ? cat[1] : OC.note;
  var body = !n.length ? '<span class="o-sub">لا توجد ملاحظات. اضغط لإضافة ملاحظة.</span>' :
    (f.list ? noteLines(f).slice(0, 4).map(function (x, i) { var ck = (f.checked || []).indexOf(i) >= 0; return '<div style="' + (ck ? 'text-decoration:line-through;opacity:.5' : '') + '">' + (ck ? '☑ ' : '☐ ') + esc(x) + '</div>'; }).join('') : esc(f.text));
  return '<a href="notes.html" class="o">' + oHd(cat ? cat[0] : 'ملاحظاتي', col, (f && f.pin ? '📌 ' : '') + n.length) + '<div class="o-note" style="border-color:' + col + '">' + body + '</div></a>';
};
OLED.wird = function () {
  var w = todayWird(), ref = wirdAyah(w), col = OC.wird;
  return '<div class="o">' + oHd('ورد اليوم', col) + '<div class="o-mid k-clamp font-dua" style="font-size:1.45em;line-height:1.7">' + w[0] + '</div>' +
    '<div class="o-row" style="justify-content:space-between;align-items:center"><span class="o-sub">' + w[1] + '</span>' + (ref ? '<button class="o-btn" style="background:' + col + ';color:#111" onclick="wirdToggle()">' + (quranPlaying() ? '⏸ إيقاف' : '▶ استمع') + '</button>' : '') + '</div></div>';
};
OLED.qibla = function (o) {
  var t = natJSON('trip'), lat = t && typeof t.lat === 'number' ? t.lat : LAT, lng = t && typeof t.lng === 'number' ? t.lng : LNG;
  var b = Math.round(qiblaBearing(lat, lng)), rel = (t && t.heading >= 0 && t.speed > 5) ? norm360(b - t.heading) : null, col = OC.qibla;
  return '<div class="o">' + oHd('القبلة', col) + '<div class="o-mid o-row" style="align-items:center;justify-content:center;gap:1em">' +
    '<svg viewBox="0 0 100 100" class="o-compass"><circle cx="50" cy="50" r="44" fill="none" stroke="var(--otr)" stroke-width="6"/>' +
    (rel === null ? '<text x="50" y="20" text-anchor="middle" fill="var(--om)" font-size="11">ش</text>' : '<path d="M50 9 46 18h8z" fill="var(--om)"/>') +
    '<g transform="rotate(' + (rel !== null ? rel : b) + ' 50 50)"><path d="M50 14 61 54 50 47 39 54Z" fill="' + col + '"/></g><circle cx="50" cy="50" r="4" fill="var(--ot)"/></svg>' +
    '<div><div class="o-temp" style="font-size:2.8em">' + b + '°</div>' + (partHidden(o, 'txt') ? '' : '<div class="o-sub">' + (rel === null ? 'من الشمال' : 'بالنسبة لاتجاهك') + '</div>') + '</div></div></div>';
};
OLED.parking = function () {
  var col = OC.park;
  return '<a href="parking.html" class="o">' + oHd('موقف السيارة', col) + '<div class="o-mid o-row" style="align-items:center;gap:.8em"><span class="o-pk" style="background:' + col + '">P</span><div class="o-sub" style="white-space:normal">احفظ مكان وقوفك وأرسله لجوالك بالباركود</div></div><span class="o-btn">فتح</span></a>';
};
OLED.radio = function () {
  var m = natJSON('media'), on = isRadioMedia(m), col = OC.radio;
  var h = '<div class="o">' + oHd('الراديو', col, on && m.playing ? 'يعمل الآن' : 'FM');
  if (on) return h + '<div class="o-mid"><div class="o-title">' + esc(m.title || m.artist || 'الراديو') + '</div><div class="o-eq sm">' + [40, 70, 55, 90, 60, 75, 45].map(function (x) { return '<i style="height:' + (m.playing ? x : 20) + '%;background:' + col + '"></i>'; }).join('') + '</div></div>' +
    '<div class="o-ctl"><button onclick="nat(\'mediaKey\',\'prev\');later()" aria-label="المحطة السابقة">' + MI.prev + '</button><button class="big" onclick="nat(\'mediaKey\',\'' + (m.playing ? 'pause' : 'play') + '\');later()">' + (m.playing ? MI.pause : MI.play) + '</button><button onclick="nat(\'mediaKey\',\'next\');later()" aria-label="المحطة التالية">' + MI.next + '</button></div></div>';
  return h + '<div class="o-mid" style="align-items:center"><svg viewBox="0 0 100 70" style="width:7em;height:auto"><g fill="none" stroke="' + col + '" stroke-width="5" stroke-linecap="round"><path d="M30 20a28 28 0 0 0 0 30M70 20a28 28 0 0 1 0 30" opacity=".55"/><path d="M40 27a14 14 0 0 0 0 16M60 27a14 14 0 0 1 0 16"/></g><circle cx="50" cy="35" r="6" fill="' + col + '"/></svg></div><button class="o-btn" style="background:' + col + ';color:#111;height:3em;font-size:1.15em" onclick="openRadioApp()">تشغيل الراديو</button></div>';
};
OLED.ram = function () {
  var m = memText(), r = natJSON('ramResult') || {};
  if (!m) return '<div class="o">' + oHd('الذاكرة', OC.ram) + '<div class="o-mid o-sub">يعمل داخل التطبيق</div></div>';
  var col = m.pct > 85 ? '#FF4D5E' : m.pct > 70 ? '#FFB020' : OC.ram;
  var res = r.busy ? 'جارٍ التحرير…' : (r.at && Date.now() - r.at < 60000 ? 'تم تحرير ' + Math.round((r.freed || 0) / 1048576) + ' م.ب' : 'المتاح ' + m.avail + ' ج.ب');
  return '<div class="o">' + oHd('الذاكرة', col, m.used + ' / ' + m.total + ' ج.ب') + '<div class="o-mid o-row" style="align-items:center;justify-content:center">' + oRing(m.pct / 100, col, '<b>' + m.pct + '%</b><small>مستخدمة</small>') + '</div>' +
    '<div class="o-row" style="justify-content:space-between;align-items:center"><span class="o-sub">' + res + '</span><button class="o-btn" onclick="nat(\'freeRam\');later();setTimeout(tick,2500)">تحرير</button></div></div>';
};
OLED.apps = function () {
  var fav = (prefs.favApps || []).slice(0, 6), col = OC.apps;
  if (!fav.length) return '<div class="o">' + oHd('تطبيقاتي', col) + '<div class="o-mid o-sub">اختر تطبيقاتك المفضلة</div><a class="o-btn" href="apps.html?pick=fav">اختيار</a></div>';
  var h = '<div class="o">' + oHd('تطبيقاتي', col) + '<div class="o-apps">';
  fav.forEach(function (pkg) { var a = appInfo(pkg); h += '<button onclick="nat(\'launch\',\'' + pkg + '\')">' + (a.icon ? '<img alt="" src="' + a.icon + '">' : IC.apps) + '<span>' + esc(a.label) + '</span></button>'; });
  return h + '</div></div>';
};

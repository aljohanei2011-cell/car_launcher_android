/* ================================================================
   الأوامر الصوتية: تحليل الجملة العربية وتنفيذها.
   يُحمَّل فقط إذا فُعّلت الأوامر الصوتية على هذه الشاشة.
   parseVoice(نص) ترجع { label: وصف ما سيحدث, run: دالة التنفيذ } أو null
   ================================================================ */
var VOICE = (function () {

  /* ---------- تطبيع النص ---------- */
  var AR_D = '٠١٢٣٤٥٦٧٨٩', FA_D = '۰۱۲۳۴۵۶۷۸۹';
  function norm(t){
    t = String(t || '').replace(/[ً-ْـٰ]/g, '')
      .replace(/[أإآٱ]/g, 'ا').replace(/ة/g, 'ه').replace(/ى/g, 'ي').replace(/ؤ/g, 'و').replace(/ئ/g, 'ي')
      .replace(/[٠-٩]/g, function (c) { return AR_D.indexOf(c); }).replace(/[۰-۹]/g, function (c) { return FA_D.indexOf(c); })
      .replace(/[،,.!؟?:؛"«»]/g, ' ').replace(/\s+/g, ' ').trim().toLowerCase();
    return t;
  }
  function words(t){ return String(t || '').replace(/[،,.!؟?:؛"«»]/g, ' ').replace(/\s+/g, ' ').trim().split(' ').filter(Boolean); }
  /* يحذف «يا اسم» أو «اسم» من أول الجملة. يرجع { n: النص المطبّع, o: كلمات النص الأصلي } */
  function stripName(raw, name){
    var o = words(raw), n = o.map(norm);
    var nm = norm(name || 'رفيق').split(' ');
    var i = 0;
    if (n[i] === 'يا') i++;
    var hit = nm.every(function (w, k) { return n[i + k] === w; });
    if (hit) i += nm.length; else if (i === 1) i = 0;
    if (n[i] === 'يا' && hit) i++;
    return { n: n.slice(i).join(' '), o: o.slice(i), named: hit };
  }
  /* بقية النص الأصلي بعد عدد من الكلمات */
  function restAfter(o, k){ return o.slice(k).join(' ').replace(/^[:،\s]+/, '').trim(); }

  /* ---------- الأرقام ---------- */
  var NUMW = { 'صفر': 0, 'واحد': 1, 'واحده': 1, 'اثنين': 2, 'اثنتين': 2, 'ثنتين': 2, 'اثنان': 2, 'ثلاث': 3, 'ثلاثه': 3, 'اربع': 4, 'اربعه': 4, 'خمس': 5, 'خمسه': 5,
    'ست': 6, 'سته': 6, 'سبع': 7, 'سبعه': 7, 'ثمان': 8, 'ثمانيه': 8, 'ثماني': 8, 'تسع': 9, 'تسعه': 9, 'عشر': 10, 'عشره': 10, 'احدعش': 11, 'احدعشر': 11, 'اثنعش': 12, 'اثنعشر': 12, 'اطنعش': 12,
    'عشرين': 20, 'ثلاثين': 30, 'اربعين': 40, 'خمسين': 50, 'ستين': 60, 'سبعين': 70, 'ثمانين': 80, 'تسعين': 90, 'ميه': 100, 'مية': 100, 'مايه': 100, 'مائه': 100, 'ميتين': 200, 'مئتين': 200,
    'ثلاثميه': 300, 'اربعميه': 400, 'خمسميه': 500, 'الف': 1000, 'الفين': 2000 };
  function numIn(n){
    var m = n.match(/(\d+(?:[.,]\d+)?)/); if (m) return parseFloat(m[1].replace(',', '.'));
    var ws = n.split(' '), total = 0, found = false;
    for (var i = 0; i < ws.length; i++) {
      var w = ws[i].replace(/^و/, '');
      if (NUMW[w] !== undefined) { total += NUMW[w]; found = true; }
      else if (found) break;
    }
    return found ? total : null;
  }

  /* ---------- قوائم الكلمات ---------- */
  var NEAR_MAP = [[/(مسجد|مساجد)/, 'mosque'], [/(جامع)/, 'jami'], [/(محطه|بنزين|وقود|بترول)/, 'fuel'], [/(صراف|بنك|سحب فلوس)/, 'atm'], [/(مطعم|اكل|غدا|عشا|فطور)/, 'food'],
    [/(قهوه|كوفي|مقهي|كافيه|كفي)/, 'coffee'], [/(صيدليه|دوا)/, 'pharmacy'], [/(مستشفي|طواري|اسعاف)/, 'hospital'], [/(ورشه|بنشر|ميكانيكي|تصليح)/, 'garage']];
  var PRAYER_W = [[/فجر/, 'fajr'], [/ظهر/, 'dhuhr'], [/عصر/, 'asr'], [/مغرب/, 'maghrib'], [/عشا/, 'isha']];
  var EXP_MAP = [[/(بنزين|وقود|تعبيه|محطه)/, 'fuel'], [/(غسيل|مغسله)/, 'wash'], [/(صيانه|زيت|ورشه)/, 'service'], [/(قطع|كفر|كفرات|اطار|بطاريه)/, 'parts'], [/(مخالفه|ساهر)/, 'fine'], [/(تامين|استماره|تجديد|فحص)/, 'ins']];
  var OPEN_PAGES = [[/(الاعدادات|الضبط)/, 'settings.html', 'الإعدادات'], [/(التطبيقات)/, 'apps.html', 'التطبيقات'], [/(الملاحظات)/, 'notes.html', 'الملاحظات'], [/(التذكيرات)/, 'reminders.html', 'التذكيرات'],
    [/(الرحله|العداد)/, 'trip.html', 'الرحلة'], [/(مواقيت|الصلاه)/, 'prayer.html', 'مواقيت الصلاة'], [/(الاذكار)/, 'azkar.html', 'الأذكار'], [/(المصاريف|المصروفات)/, 'expenses.html', 'المصاريف'],
    [/(الصيانه)/, 'maintenance.html', 'الصيانة'], [/(الموقف|مكان السياره)/, 'parking.html', 'الموقف'], [/(الوقود)/, 'fuel.html', 'سجل الوقود'], [/(الراديو)/, 'radio.html', 'الراديو'], [/(obd|بيانات السياره)/, 'obd.html', 'بيانات السيارة']];

  function has(fn){ return typeof window[fn] === 'function'; }
  function say(t){ return t; }
  function pagesGo(url){ if (has('openPage')) openPage(url); else location.href = url; }

  /* ---------- التذكير: استخراج الوقت ---------- */
  function parseWhen(n){
    var now = new Date(), d = null, used = [];
    var rel = n.match(/بعد\s+(.*?)(دقيقه|دقايق|دقائق|دقيقتين|ساعه|ساعات|ساعتين|يوم|يومين|ايام)/);
    if (rel) {
      var unit = rel[2], q = numIn(rel[1]);
      var half = /نص/.test(rel[1]), quarter = /ربع/.test(rel[1]);
      if (unit === 'دقيقتين') q = 2; if (unit === 'ساعتين') q = 2; if (unit === 'يومين') q = 2;
      if (q === null) q = half ? 0.5 : quarter ? 0.25 : 1;
      var mins = /دقي|دقا/.test(unit) ? q : /ساع/.test(unit) ? q * 60 : q * 1440;
      d = new Date(now.getTime() + mins * 60000); used.push(rel[0]);
    }
    var day = 0;
    var tm = n.match(/(بكره|بكرا|بكرة|غدا|باكر|بعد بكره)/); if (tm) { day = /بعد بكره/.test(tm[0]) ? 2 : 1; used.push(tm[0]); }
    var at = n.match(/الساعه\s+(\S+)(\s+(و?نص|و?ربع|الا ربع|الا ثلث|و?ثلث))?(\s+(الصبح|صباحا|الظهر|العصر|المغرب|العشا|الليل|بالليل|مساء|المسا|الفجر))?/);
    if (at) {
      var h = numIn(at[1]); var mm = 0;
      if (h !== null) {
        if (at[3]) { if (/نص/.test(at[3])) mm = 30; else if (/الا ربع/.test(at[3])) { h -= 1; mm = 45; } else if (/الا ثلث/.test(at[3])) { h -= 1; mm = 40; } else if (/ربع/.test(at[3])) mm = 15; else if (/ثلث/.test(at[3])) mm = 20; }
        var part = at[5] || '';
        if (/(العصر|المغرب|العشا|الليل|بالليل|مساء|المسا)/.test(part) && h < 12) h += 12;
        if (/الظهر/.test(part) && h < 11) h += 12;
        var t = new Date(now); t.setDate(t.getDate() + day); t.setHours(h, mm, 0, 0);
        if (!day && !part && t < now && h < 12) t.setHours(h + 12);
        if (!day && t < now) t.setDate(t.getDate() + 1);
        d = t; used.push(at[0]);
      }
    } else if (day && !d) {
      var t2 = new Date(now); t2.setDate(t2.getDate() + day); t2.setHours(9, 0, 0, 0); d = t2;
    }
    return { date: d, used: used };
  }
  function cleanRemText(o, n, used){
    var txt = o.join(' ');
    // حذف عبارات الوقت من النص الأصلي بمقارنة الكلمات المطبّعة
    var on = o.map(norm), kill = {};
    used.forEach(function (u) { var uw = u.split(' '); for (var i = 0; i + uw.length <= on.length; i++) { if (uw.every(function (w, k) { return on[i + k] === w; })) { for (var k = 0; k < uw.length; k++) kill[i + k] = 1; break; } } });
    txt = o.filter(function (w, i) { return !kill[i]; }).join(' ');
    return txt.replace(/^(ب|ان|اني|إن|إني|أن|أني)\s+/, '').replace(/^ب(?=\S)/, '').trim();
  }
  function hhmm(d){ return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0'); }
  function whenText(d){
    var today = new Date(); today.setHours(0, 0, 0, 0);
    var dd = new Date(d); dd.setHours(0, 0, 0, 0);
    var diff = Math.round((dd - today) / 86400000);
    return (diff === 0 ? 'اليوم' : diff === 1 ? 'بكرة' : fmt({ weekday: 'long', day: 'numeric', month: 'long' }, 'ar-u-nu-latn', d)) + ' الساعة ' + clockStr(d) + (ampm(d) ? ' ' + ampm(d) : '');
  }

  /* ---------- التحليل ---------- */
  function parse(raw, name){
    var s = stripName(raw, name), n = s.n, o = s.o, m;
    if (!n) return null;

    if (/^(مساعده|وش اقول|ماذا اقول|ايش اقول|الاوامر|وش تقدر تسوي)/.test(n))
      return { label: 'عرض الأوامر', run: function () { pagesGo('voice.html'); return 'هذي الأوامر'; } };

    // ملاحظة
    if ((m = n.match(/^(سجل ملاحظه|اكتب ملاحظه|ملاحظه جديده|ملاحظه|دون|اكتب|سجل)\s+(.+)/)) && !/^سجل مصروف/.test(n)) {
      var nt = restAfter(o, m[1].split(' ').length);
      return { label: 'ملاحظة: ' + nt, run: function () { var l = loadNotes(); l.unshift({ id: uid(), text: nt.slice(0, 4000), at: Date.now() }); saveNotes(l); return 'حفظت الملاحظة'; } };
    }

    // تذكير
    if ((m = n.match(/^(ذكرني|نبهني|فكرني|ذكرني ب|تذكير)\s*(.*)/))) {
      var rest = o.slice(1), rn = rest.map(norm).join(' ');
      var w = parseWhen(rn), text = cleanRemText(rest, rn, w.used) || 'تذكير';
      return { label: 'تذكير: ' + text + (w.date ? ' — ' + whenText(w.date) : ' — عند تشغيل السيارة القادم'), run: function () {
        var r = { id: uid(), text: text.slice(0, 200) };
        if (w.date) { r.kind = 'once'; r.date = todayStr(w.date); r.time = hhmm(w.date); } else r.kind = 'start';
        var l = loadRems(); l.push(r); saveRems(l);
        return w.date ? 'أبشر، أذكرك ' + whenText(w.date) : 'أبشر، أذكرك أول ما تشغل السيارة';
      } };
    }

    // مصروف
    if ((m = n.match(/^(مصروف|سجل مصروف|صرفت|دفعت|عبيت)\s*(.*)/))) {
      var amt = numIn(m[2]), cat = 'other';
      EXP_MAP.some(function (x) { if (x[0].test(m[2]) || (m[1] === 'عبيت' && x[1] === 'fuel')) { cat = x[1]; return true; } });
      if (m[1] === 'عبيت') cat = 'fuel';
      if (!(amt > 0)) return { label: 'مصروف بدون مبلغ', run: function () { return 'قل المبلغ، مثل: مصروف بنزين ثمانين ريال'; } };
      var note = restAfter(o, 1).replace(/\d+([.,]\d+)?/, '').replace(/(ريال|ريالات|ر\.س)/, '').trim();
      return { label: 'مصروف ' + EXP_CATS[cat] + ' ' + amt + ' ريال', run: function () { var ex = lsGet(EXP_KEY, []); ex.push({ id: uid(), amount: amt, cat: cat, note: note.slice(0, 120), at: Date.now() }); lsSet(EXP_KEY, ex); return 'سجلت ' + amt + ' ريال ' + EXP_CATS[cat]; } };
    }

    // أقرب مكان
    if ((m = n.match(/(اقرب|دلني علي)\s+(.+)/))) {
      var what = m[2], key = null;
      NEAR_MAP.some(function (x) { if (x[0].test(what)) { key = x[1]; return true; } });
      var NB_NAMES = { mosque: 'مسجد', jami: 'جامع', fuel: 'محطة وقود', atm: 'صراف', food: 'مطعم', coffee: 'مقهى', pharmacy: 'صيدلية', hospital: 'مستشفى', garage: 'ورشة' };
      if (key) return { label: 'أقرب ' + NB_NAMES[key], run: function () { nearby(key); return 'أبحث لك عن أقرب ' + NB_NAMES[key]; } };
      var q = restAfter(o, n.split(' ').indexOf(m[1].split(' ')[0]) + m[1].split(' ').length);
      return { label: 'بحث في الخرائط: ' + q, run: function () { var ll = myLatLng(); nat('mapSearch', q, ll[0], ll[1], prefs.mapsApp || ''); return 'أبحث عن ' + q; } };
    }

    // تخطيط رحلة
    if ((m = n.match(/^(خطط|خطط لي|خطه)?\s*(رحله|سفر|مشوار)\s+(الي|ل|لـ)?\s*(.+)/))) {
      var city = findCity(m[4]);
      if (city) return { label: 'تخطيط رحلة إلى ' + CITIES[city].label, run: function () { lsSet('carlauncher.travel', { to: city, avg: (lsGet('carlauncher.travel', {}) || {}).avg || 100, at: Date.now() }); pagesGo('travel.html'); return 'هذي خطة الرحلة إلى ' + CITIES[city].label; } };
    }

    // الملاحة
    if ((m = n.match(/^(ودني|وديني|خذني|وصلني|روح|نروح|اذهب|توجه|ابي اروح|ابغي اروح|ابغا اروح|ودنا|ملاحه|الملاحه)\s+(الي|الى|ل|علي)?\s*(.+)/))) {
      var tgt = m[3];
      if (/^(البيت|المنزل|بيتنا|البيت حقنا)/.test(tgt)) {
        var a = prefs.arrive || {};
        if (typeof a.lat === 'number') return { label: 'الملاحة إلى البيت', run: function () { nat('navigateTo', a.lat, a.lng, a.place || 'البيت', prefs.mapsApp || ''); return 'يلا على البيت'; } };
        return { label: 'البيت غير محفوظ', run: function () { return 'احفظ مكان البيت من الإعدادات، رسالة الوصول'; } };
      }
      var c2 = findCity(tgt);
      if (c2) return { label: 'الملاحة إلى ' + CITIES[c2].label, run: function () { nat('navigateTo', CITIES[c2].lat, CITIES[c2].lng, CITIES[c2].label, prefs.mapsApp || ''); return 'يلا على ' + CITIES[c2].label; } };
      var q2 = restAfter(o, m[1].split(' ').length + (m[2] ? 1 : 0));
      return { label: 'الملاحة إلى: ' + q2, run: function () { var ll = myLatLng(); nat('mapSearch', q2, ll[0], ll[1], prefs.mapsApp || ''); return 'أبحث عن ' + q2; } };
    }

    // الإذاعة
    if ((m = n.match(/^(شغل|افتح|حط)\s+(اذاعه|محطه|راديو|الراديو|الاذاعه|اف ام)\s*(.*)/))) {
      var nm = m[3], st = loadStations();
      var pick = nm ? st.filter(function (x) { return norm(x.name).indexOf(nm) >= 0 || nm.indexOf(norm(x.name)) >= 0; })[0] : null;
      if (!pick && /قران/.test(nm)) pick = st.filter(function (x) { return /قران/.test(norm(x.name)); })[0];
      if (pick) return { label: 'تشغيل ' + pick.name, run: function () { nat('radioPlay', pick.url, pick.name); return 'شغلت ' + pick.name; } };
      return { label: 'فتح الراديو', run: function () { if (/اف ام/.test(m[2])) openRadioApp(); else pagesGo('radio.html'); return nm ? 'ما لقيت محطة باسم ' + nm + '، أضفها من صفحة الراديو' : 'هذا الراديو'; } };
    }

    // الموسيقى
    if (/^(شغل|كمل|تشغيل|شغل الموسيقي|شغل الاغنيه|كمل الاغنيه)$/.test(n)) return { label: 'تشغيل', run: function () { nat('mediaCmd', 'play'); return ''; } };
    if (/^(وقف|ايقاف|اوقف|اسكت|بوز|وقف الموسيقي|وقف الاغنيه|طفي الموسيقي)/.test(n)) return { label: 'إيقاف', run: function () { nat('mediaCmd', 'pause'); return ''; } };
    if (/^(التالي|اللي بعده|اللي بعدها|غيرها|الاغنيه الجايه|بعدها|التاليه)/.test(n)) return { label: 'التالي', run: function () { nat('mediaCmd', 'next'); return ''; } };
    if (/^(السابق|اللي قبله|اللي قبلها|رجع الاغنيه|السابقه)/.test(n)) return { label: 'السابق', run: function () { nat('mediaCmd', 'prev'); return ''; } };
    if (/(ارفع|زود|علي)\s*الصوت/.test(n)) return { label: 'رفع الصوت', run: function () { var v = natJSON('volume') || { v: 7, max: 15 }; nat('setVolume', Math.min(v.max, v.v + Math.ceil(v.max / 6))); return ''; } };
    if (/(اخفض|وطي|نزل|قلل|خفف)\s*الصوت/.test(n)) return { label: 'خفض الصوت', run: function () { var v = natJSON('volume') || { v: 7, max: 15 }; nat('setVolume', Math.max(0, v.v - Math.ceil(v.max / 6))); return ''; } };

    // الإضاءة
    if (/(الوضع الليلي|خفف الاضاءه|نزل الاضاءه|خفف السطوع|نزل السطوع|طفي الاضاءه)/.test(n)) return { label: 'خفض الإضاءة', run: function () { nat('setBrightness', +prefs.nightPct || 25); return 'خففت الإضاءة'; } };
    if (/(ارفع الاضاءه|زود الاضاءه|ارفع السطوع|الوضع النهاري)/.test(n)) return { label: 'رفع الإضاءة', run: function () { nat('setBrightness', 100); return 'رفعت الإضاءة'; } };

    // الذاكرة، الاستراحة، الرئيسية
    if (/(حرر|نظف|فرغ|فضي)\s*(الذاكره|الرام)/.test(n)) return { label: 'تحرير الذاكرة', run: function () { nat('freeRam'); return 'جاري تحرير الذاكرة'; } };
    if (/^(الاستراحه|وضع الاستراحه|استراحه|شاشه الاستراحه)/.test(n)) return { label: 'شاشة الاستراحة', run: function () { if (has('openRest')) openRest(); return ''; } };
    if (/^(الرئيسيه|الواجهه|ارجع|رجوع)$/.test(n)) return { label: 'الرئيسية', run: function () { if (has('closeRest')) closeRest(); return ''; } };

    // الأذكار والأدعية
    if (/اذكار الصباح/.test(n)) return { label: 'أذكار الصباح', run: function () { pagesGo('azkar.html?g=morning'); return ''; } };
    if (/اذكار المسا/.test(n)) return { label: 'أذكار المساء', run: function () { pagesGo('azkar.html?g=evening'); return ''; } };
    if (/(دعاء السفر|دعاء الركوب)/.test(n)) return { label: 'دعاء السفر', run: function () { pagesGo('azkar.html?g=travel'); return 'سبحان الذي سخر لنا هذا وما كنا له مقرنين وإنا إلى ربنا لمنقلبون'; } };

    // ورد القرآن
    if (/(قريت|قرات|خلصت)\s*(الورد|وردي)/.test(n)) return { label: 'تم ورد القرآن', run: function () { if (has('khatmaDone')) khatmaDone(); return 'تقبل الله'; } };
    if (/(وردي|الورد|ورد القران|ورد اليوم)/.test(n)) return { label: 'ورد اليوم', run: function () { var k = has('khatma') ? khatma() : { cur: 1, pages: 4 }; var to = Math.min(604, k.cur + k.pages - 1); return 'وردك اليوم من صفحة ' + k.cur + ' إلى ' + to; } };

    // الصلاة
    if (/(صلاه|اذان|الفجر|الظهر|العصر|المغرب|العشا)/.test(n) && /(كم|متي|باقي|وقت|ياذن|يوذن|اذان)/.test(n)) {
      var which = null; PRAYER_W.some(function (x) { if (x[0].test(n)) { which = x[1]; return true; } });
      return { label: 'وقت ' + (which ? 'صلاة ' + PRAYER_NAMES.filter(function (x) { return x[0] === which; })[0][1] : 'الصلاة القادمة'), run: function () {
        var d = new Date(), now = riyadhMinutes(d);
        if (which) { var p = prayerMinutes(d).filter(function (x) { return x.key === which; })[0]; var left = p.min - now; return 'صلاة ' + p.name + ' الساعة ' + fmtMin(p.min) + (left > 0 ? '، باقي ' + fmtDiff(left) : '، وقد دخل وقتها'); }
        var np = nextPrayer(d); return 'صلاة ' + np.name + ' بعد ' + fmtDiff(np.diff) + '، الساعة ' + np.timeFmt;
      } };
    }

    // الوقت والتاريخ
    if (/(كم الساعه|الساعه كم|وش الوقت|كم الوقت)/.test(n)) return { label: 'الوقت', run: function () { var d = new Date(); return 'الساعة ' + clockStr(d) + (ampm(d) ? ' ' + (ampm(d) === 'ص' ? 'صباحاً' : 'مساءً') : ''); } };
    if (/(التاريخ|اليوم كم|وش اليوم|كم اليوم|الهجري)/.test(n)) return { label: 'التاريخ', run: function () { var d = new Date(); return fmt({ weekday: 'long', day: 'numeric', month: 'long' }, 'ar-u-nu-latn', d) + '، الموافق ' + fmt({ day: 'numeric', month: 'long', year: 'numeric' }, 'ar-SA-u-ca-islamic-umalqura-nu-latn', d); } };

    // السيارة (OBD)
    if (/(حراره المحرك|حراره المكينه|المكينه|البطاريه|الدورات|كم الوقود|كم البنزين|بيانات السياره)/.test(n)) return { label: 'بيانات السيارة', run: function () {
      var ob = natJSON('obd') || {};
      if (ob.state !== 'on') return 'قطعة OBD غير متصلة';
      var parts = [];
      if (ob.coolant !== undefined) parts.push('حرارة المحرك ' + ob.coolant + ' درجة');
      if (ob.volt) parts.push('البطارية ' + ob.volt.toFixed(1) + ' فولت');
      if (ob.fuel !== undefined) parts.push('الوقود ' + ob.fuel + ' بالمية');
      if ((ob.dtcs || []).length) parts.push('يوجد ' + ob.dtcs.length + ' أعطال مسجلة');
      return parts.join('، ') || 'لا توجد قراءات بعد';
    } };

    // الطقس
    if (/(الطقس|الجو|كم الحراره|درجه الحراره|حراره الجو)/.test(n)) return { label: 'الطقس', run: function () {
      var w = weatherData(); if (!w.at) return 'ما عندي بيانات طقس، يحتاج إنترنت';
      return 'الحرارة ' + Math.round(w.temp) + ' درجة، ' + (weatherFx(w) === 'dust' ? 'غبار' : (WMO[w.code] || '')) + (w.max != null ? '، والعظمى ' + Math.round(w.max) : '');
    } };

    // فتح صفحة أو تطبيق
    if ((m = n.match(/^(افتح|شغل|ورني|اعرض)\s+(.+)/))) {
      var tg = m[2];
      if (/خرايط|الخريطه|قوقل ماب|ويز/.test(tg)) return { label: 'فتح الخرائط', run: function () { openRole('maps'); return ''; } };
      if (/(الموسيقي|الاغاني|سبوتيفاي)/.test(tg)) return { label: 'فتح الموسيقى', run: function () { openRole('music'); return ''; } };
      var pg = null; OPEN_PAGES.some(function (x) { if (x[0].test(tg)) { pg = x; return true; } });
      if (pg) return { label: 'فتح ' + pg[2], run: function () { pagesGo(pg[1]); return ''; } };
      var apps = natJSON('apps') || [], hit = null;
      // أسماء عربية لتطبيقات مشهورة أسماؤها إنجليزية
      var ALIAS = [[/(واتس|واتساب)/, 'whatsapp'], [/يوتيوب/, 'youtube'], [/سناب/, 'snapchat'], [/(تويتر|اكس)/, 'twitter'], [/سبوتي/, 'spotify'], [/انغامي/, 'anghami'],
        [/(تيليجرام|تلقرام|تليقرام)/, 'telegram'], [/(كريم|careem)/, 'careem'], [/(اوبر)/, 'uber'], [/(نتفلكس)/, 'netflix'], [/(شاهد)/, 'mbc'], [/(توكلنا)/, 'tawakkalna'], [/(ابشر)/, 'absher']];
      var al = null; ALIAS.some(function (x) { if (x[0].test(tg)) { al = x[1]; return true; } });
      if (al) apps.some(function (a) { if ((a.pkg || '').toLowerCase().indexOf(al) >= 0 || norm(a.label).indexOf(al) >= 0) { hit = a; return true; } });
      if (!hit) apps.some(function (a) { var an = norm(a.label); if (an && (an === tg || an.indexOf(tg) >= 0 || tg.indexOf(an) >= 0)) { hit = a; return true; } });
      if (hit) return { label: 'فتح ' + hit.label, run: function () { nat('launch', hit.pkg); return ''; } };
    }
    return null;
  }
  function findCity(t){
    t = norm(t).replace(/^(مدينه|محافظه)\s+/, '');
    var best = null;
    Object.keys(CITIES).some(function (k) { var l = norm(CITIES[k].label); if (t === l || t.indexOf(l) === 0 || l.indexOf(t) === 0) { best = k; return true; } });
    return best;
  }

  return { parse: parse, norm: norm, stripName: stripName, numIn: numIn, parseWhen: parseWhen };
})();
function parseVoice(t, name){ return VOICE.parse(t, name || (loadPrefsCached().voiceName || 'رفيق')); }

/* ================================================================
   واجهة الاستماع في الرئيسية
   ================================================================ */
var VERR = { 1: 'انتهت مهلة الشبكة', 2: 'لا يوجد إنترنت (نزّل العربي للاستخدام بدون إنترنت من إعدادات Google)', 3: 'مشكلة في الميكروفون', 4: 'خطأ في خدمة الكلام', 5: 'تعذّر تشغيل خدمة الكلام', 6: 'لم أسمع شيئاً', 7: 'لم أفهم الكلام', 8: 'الخدمة مشغولة، حاول بعد لحظة', 9: 'يحتاج إذن الميكروفون' };
var _vSeq = -1, _vPoll = null, _vHide = null, _vWakeOn = false;
function voiceReplyOn(){ return prefs.voiceReply !== false && !document.body.classList.contains('perf'); }
function voiceUI(show){
  var o = document.getElementById('vo');
  if (!show) { if (o) o.remove(); return null; }
  if (!o) {
    o = document.createElement('div'); o.id = 'vo';
    o.innerHTML = '<div class="voMic"><svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/></svg></div><div class="voT"><div class="voN" id="voN"></div><div class="voP" id="voP"></div></div><button class="voX" onclick="voiceCancel()">إلغاء</button>';
    o.addEventListener('click', function (e) { if (e.target === o) voiceCancel(); });
    document.getElementById('stage').appendChild(o);
  }
  return o;
}
function voiceStart(){
  if (!HAS_NATIVE) { toast('الأوامر الصوتية تعمل داخل التطبيق'); return; }
  var c = natJSON('voiceCaps') || {};
  if (!c.perm) { nat('voicePerm'); toast('اسمح باستخدام الميكروفون ثم اضغط الزر مرة أخرى'); return; }
  clearTimeout(_vHide);
  var o = voiceUI(true); o.className = 'on';
  document.getElementById('voN').textContent = (prefs.voiceName || 'رفيق') + ' يسمعك…';
  document.getElementById('voP').textContent = 'تكلّم الآن';
  var st = natJSON('voiceState') || {}; _vSeq = st.seq || 0;
  nat('voiceListen', !!prefs.voiceOffline);
  voicePollFast();
}
function voiceCancel(){ nat('voiceStop'); clearInterval(_vPoll); _vPoll = null; voiceUI(false); if (_vWakeOn) voiceWakeLoop(); }
function voicePollFast(){
  clearInterval(_vPoll);
  _vPoll = setInterval(function () {
    var st = natJSON('voiceState'); if (!st) return;
    var p = document.getElementById('voP'), mic = document.querySelector('#vo .voMic');
    if (st.state === 'listening') { if (p && st.partial) p.textContent = st.partial; if (mic) mic.style.transform = 'scale(' + (1 + Math.max(0, Math.min(10, st.rms || 0)) / 25) + ')'; }
    if (st.seq !== _vSeq && (st.state === 'done' || st.state === 'error' || st.state === 'idle' || st.state === 'speaking')) {
      _vSeq = st.seq; clearInterval(_vPoll); _vPoll = null;
      if (st.state === 'error' || !st.results || !st.results.length) voiceShow(VERR[st.err] || 'لم أسمع شيئاً', '', true);
      else voiceHandle(st.results, false);
    }
  }, 180);
}
/* تنفيذ أول نتيجة مفهومة من البدائل */
function voiceHandle(results, wake){
  var name = prefs.voiceName || 'رفيق', cmd = null, heard = results[0];
  for (var i = 0; i < results.length && !cmd; i++) {
    if (wake && !VOICE.stripName(results[i], name).named) continue;
    cmd = parseVoice(results[i], name); if (cmd) heard = results[i];
  }
  if (wake && !cmd) { if (results.some(function (r) { return VOICE.stripName(r, name).named; })) voiceShow('«' + heard + '»', 'ما فهمت الأمر. قل: ' + name + ' وش أقول؟', true); return; }
  if (!cmd) { voiceShow('«' + heard + '»', 'ما فهمت الأمر. قل «وش أقول؟» لترى الأمثلة', true); if (voiceReplyOn()) nat('speak', 'ما فهمت'); return; }
  var reply = '';
  try { reply = cmd.run() || ''; } catch (e) { reply = 'تعذّر التنفيذ'; nat('logError', 'voice: ' + (e && e.message)); }
  voiceShow('«' + heard + '»', reply || cmd.label, false);
  if (reply && voiceReplyOn()) nat('speak', reply);
  try { later(); } catch (e) {}
}
function voiceShow(a, b, err){
  var o = voiceUI(true); o.className = 'on res' + (err ? ' err' : '');
  document.getElementById('voN').textContent = a; document.getElementById('voP').textContent = b || '';
  clearTimeout(_vHide); _vHide = setTimeout(function () { voiceUI(false); }, err ? 4500 : 3500);
}
window.onVoiceKey = function () { if (document.getElementById('vo') && _vPoll) voiceCancel(); else voiceStart(); };

/* ---------- الاستماع الدائم (اختياري بشروط) ---------- */
function wakeAllowed(){
  var c = natJSON('voiceCaps') || {};
  return !!(prefs.voiceWake && c.on && c.perm && c.ramGb >= 3 && !perfOn(prefs) && !(nat('memKills') > 0));
}
var _wakeSeq = -1, _wakeTimer = null;
function voiceWakeLoop(){
  var want = wakeAllowed();
  if (want && prefs.wakePauseMusic !== false) { var m = natJSON('media'); if (m && m.playing) want = false; }
  if (want && (restOpen() || document.getElementById('cardEd'))) want = false;
  if (want !== _vWakeOn) { _vWakeOn = want; nat('voiceWake', want); }
  clearInterval(_wakeTimer);
  if (!want) return;
  _wakeTimer = setInterval(function () {
    if (_vPoll) return;
    var st = natJSON('voiceState'); if (!st || !st.wake) return;
    if (st.seq !== _wakeSeq && st.state === 'done') { _wakeSeq = st.seq; if (st.results && st.results.length) voiceHandle(st.results, true); }
  }, 400);
}
var VOICE_HOME = !!document.getElementById('pages');
if (VOICE_HOME) setInterval(function () { if (!document.hidden) voiceWakeLoop(); }, 4000);

/* ---------- زر الميكروفون ---------- */
if (VOICE_HOME) (function () {
  var css = document.createElement('style');
  css.textContent = '#micBtn{width:44px;height:44px;border-radius:22px;border:1.5px solid rgba(255,255,255,.35);background:rgba(47,123,255,.25);display:flex;align-items:center;justify-content:center;padding:0;flex:none}#micBtn svg{width:24px;height:24px}' +
    'body.skin-light #micBtn svg{stroke:#13202f}' +
    '#vo{position:absolute;left:0;right:0;top:0;z-index:92;display:flex;align-items:center;gap:22px;padding:22px 40px;background:linear-gradient(180deg,rgba(4,10,24,.96),rgba(4,10,24,.88));color:#fff;box-shadow:0 12px 40px rgba(0,0,0,.5);min-height:120px;box-sizing:border-box}' +
    '.voMic{width:76px;height:76px;border-radius:38px;background:var(--accent,#2F7BFF);display:flex;align-items:center;justify-content:center;flex:none;transition:transform .12s}.voMic svg{width:38px;height:38px}' +
    '#vo.on:not(.res) .voMic{animation:voPulse 1.2s ease-in-out infinite}#vo.res .voMic{background:#1FA35A}#vo.err .voMic{background:#8a3a3a}' +
    '.voT{flex:1;min-width:0}.voN{font-size:28px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.voP{font-size:22px;color:#C3D1E8;margin-top:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}' +
    '.voX{height:56px;padding:0 22px;border-radius:16px;border:1px solid rgba(255,255,255,.3);background:none;color:#fff;font:inherit;font-size:19px}' +
    '@keyframes voPulse{0%,100%{box-shadow:0 0 0 0 rgba(47,123,255,.6)}50%{box-shadow:0 0 0 16px rgba(47,123,255,0)}}body.perf #vo .voMic{animation:none!important}';
  document.head.appendChild(css);
  var left = document.querySelector('.top .left');
  if (left && !document.getElementById('micBtn')) {
    var b = document.createElement('button'); b.id = 'micBtn'; b.setAttribute('aria-label', 'أمر صوتي');
    b.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/></svg>';
    b.onclick = function () { window.onVoiceKey(); };
    left.insertBefore(b, left.firstChild);
  }
  voiceWakeLoop();
})();

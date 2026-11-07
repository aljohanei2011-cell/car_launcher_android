// فحص صيغة كل ملفات JavaScript وكل سكربتات HTML المضمّنة قبل البناء.
// صفحة فيها خطأ صيغة تعني واجهة معطّلة على الشاشة، فنوقف البناء بدل نشرها.
const fs = require("fs");
const path = require("path");
const root = process.argv[2] || "app/src/main/assets";
let files = 0, scripts = 0, errors = 0;

function walk(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p);
    else if (p.endsWith(".js")) checkJs(p);
    else if (p.endsWith(".html")) checkHtml(p);
  }
}
function syntax(code, label) {
  try { new Function(code); } catch (e) { errors++; console.error("::error file=" + label + "::" + e.message); }
}
function checkJs(p) { files++; syntax(fs.readFileSync(p, "utf8"), p); }
function checkHtml(p) {
  files++;
  const h = fs.readFileSync(p, "utf8");
  const re = /<script(?![^>]*\bsrc=)([^>]*)>([\s\S]*?)<\/script>/g;
  let m, i = 0;
  while ((m = re.exec(h))) {
    i++; scripts++;
    if (/type\s*=\s*["'](?!text\/javascript|module)/.test(m[1])) continue; // json وغيره
    syntax(m[2], p + " (script " + i + ")");
  }
}
walk(root);
console.log("checked " + files + " files, " + scripts + " inline scripts, " + errors + " errors");
process.exit(errors ? 1 : 0);

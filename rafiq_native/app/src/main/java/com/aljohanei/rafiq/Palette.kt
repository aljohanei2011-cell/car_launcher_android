package com.aljohanei.rafiq

/**
 * ألوان صلبة فقط (بدون شفافية أو تدرجات) لتخفيف الرسم.
 * الثيم = خلفية (داكن، أسود، فاتح) + لون تمييز (6 ألوان).
 */
class Palette(
    val bg: Int, val card: Int, val text: Int, val muted: Int, val accent: Int,
    val danger: Int, val dangerDim: Int, val line: Int, val ok: Int,
    val tile: Int, val tileLine: Int, val warmTile: Int, val warmLine: Int,
    val onAccent: Int, val amber: Int
) {
    companion object {
        private fun c(hex: Long): Int = hex.toInt()

        val BASES = listOf("dark", "black", "light", "navy", "wine", "pearl")
        val BASE_NAMES = listOf("داكن", "أسود", "فاتح", "كحلي", "عنابي", "لؤلؤي")
        val ACCENTS = listOf("cyan", "red", "green", "amber", "blue", "silver", "gold", "rose")
        val ACCENT_NAMES = listOf("سماوي", "أحمر تويوتا", "أخضر", "كهرماني", "أزرق", "فضي", "ذهبي", "وردي ذهبي")

        /** لون التمييز: [على خلفية داكنة، على خلفية فاتحة، خط داكن، خط فاتح] */
        private fun acc(id: String): LongArray = when (id) {
            "red" -> longArrayOf(0xFFEB0A1E, 0xFFC8102E, 0xFF5E1A22, 0xFFF2B8BE)
            "green" -> longArrayOf(0xFF10B981, 0xFF047857, 0xFF135E48, 0xFFA7E3CB)
            "amber" -> longArrayOf(0xFFF59E0B, 0xFFB45309, 0xFF6B4E1E, 0xFFF3D19C)
            "blue" -> longArrayOf(0xFF3B82F6, 0xFF1D4ED8, 0xFF1E3A6E, 0xFFB6CCF5)
            "silver" -> longArrayOf(0xFFC9D3E3, 0xFF4B5563, 0xFF2A3550, 0xFFCBD2D9)
            "gold" -> longArrayOf(0xFFD4AF6A, 0xFF8A6A2F, 0xFF3A3220, 0xFFD8CFBF)
            "rose" -> longArrayOf(0xFFE8B4A0, 0xFF9A5A48, 0xFF4A1F27, 0xFFE6CFC6)
            else -> longArrayOf(0xFF2EE6F0, 0xFF0E7490, 0xFF1C6F7A, 0xFF9CC3E8)
        }

        /** لون العينة في الإعدادات */
        fun swatch(id: String): Int = c(acc(id)[0])

        fun of(base: String, accent: String): Palette {
            val a = acc(accent)
            val light = base == "light" || base == "pearl"
            val acColor = c(if (light) a[1] else a[0])
            val line = c(if (light) a[3] else a[2])
            // نص داكن فوق الألوان الفاتحة، وأبيض فوق الأحمر والأزرق
            val on = if (light || accent == "red" || accent == "blue") c(0xFFFFFFFF) else c(0xFF041016)
            return when (base) {
                "navy" -> Palette(
                    bg = c(0xFF070B14), card = c(0xFF0F1626), text = c(0xFFEEF2F8), muted = c(0xFF8693A8),
                    accent = acColor, danger = c(0xFFF0606B), dangerDim = c(0xFF3A1820), line = line, ok = c(0xFF3DDC84),
                    tile = c(0xFF0A1020), tileLine = line, warmTile = c(0xFF14100A), warmLine = c(0xFF4A3A18),
                    onAccent = on, amber = c(0xFFF5B841)
                )
                "wine" -> Palette(
                    bg = c(0xFF0D0607), card = c(0xFF1A0C0F), text = c(0xFFF6ECE8), muted = c(0xFFA68C88),
                    accent = acColor, danger = c(0xFFFF5A5F), dangerDim = c(0xFF4A1218), line = line, ok = c(0xFF3DDC84),
                    tile = c(0xFF120709), tileLine = line, warmTile = c(0xFF1A1208), warmLine = c(0xFF4A3A18),
                    onAccent = on, amber = c(0xFFF5B841)
                )
                "pearl" -> Palette(
                    bg = c(0xFFECE8E1), card = c(0xFFFBF9F5), text = c(0xFF1C1914), muted = c(0xFF6B6456),
                    accent = acColor, danger = c(0xFFC0392B), dangerDim = c(0xFFF0CFCB), line = line, ok = c(0xFF15803D),
                    tile = c(0xFF2A241A), tileLine = acColor, warmTile = c(0xFF1A1508), warmLine = c(0xFFB7791F),
                    onAccent = on, amber = c(0xFF9A6A12)
                )
                "black" -> Palette(
                    bg = c(0xFF000000), card = c(0xFF0B0E12), text = c(0xFFE6EAEE), muted = c(0xFF8A949E),
                    accent = acColor, danger = c(0xFFFF4D5E), dangerDim = c(0xFF3A1218), line = line, ok = c(0xFF3DDC84),
                    tile = c(0xFF06090C), tileLine = line, warmTile = c(0xFF120E05), warmLine = c(0xFF4A3A18),
                    onAccent = on, amber = c(0xFFF5B841)
                )
                "light" -> Palette(
                    bg = c(0xFFF3F5F8), card = c(0xFFFFFFFF), text = c(0xFF111827), muted = c(0xFF5B6472),
                    accent = acColor, danger = c(0xFFDC2626), dangerDim = c(0xFFF6C9CE), line = line, ok = c(0xFF15803D),
                    // البلاطات تبقى داكنة حتى تبقى الأيقونات واضحة
                    tile = c(0xFF0E2A31), tileLine = acColor, warmTile = c(0xFF1A1508), warmLine = c(0xFFB7791F),
                    onAccent = on, amber = c(0xFFB7791F)
                )
                else -> Palette(
                    bg = c(0xFF0B0F14), card = c(0xFF131B24), text = c(0xFFF2F5F8), muted = c(0xFF9AA7B4),
                    accent = acColor, danger = c(0xFFFF4D5E), dangerDim = c(0xFF5A1E27), line = line, ok = c(0xFF3DDC84),
                    tile = c(0xFF0A1A20), tileLine = line, warmTile = c(0xFF1A1508), warmLine = c(0xFF6B5320),
                    onAccent = on, amber = c(0xFFF5B841)
                )
            }
        }

        val DARK = of("dark", "cyan")

        /** الثيم الحالي حسب الإعداد والوقت (مع تحويل القيم القديمة) */
        fun current(night: Boolean): Palette = when (Prefs.theme) {
            "dark" -> DARK
            "light" -> of("light", "cyan")
            "amber" -> of("black", "amber")
            "fixed" -> of(Prefs.base("fixed"), Prefs.accent("fixed"))
            else -> if (night) of(Prefs.base("night"), Prefs.accent("night")) else of(Prefs.base("day"), Prefs.accent("day"))
        }
    }
}

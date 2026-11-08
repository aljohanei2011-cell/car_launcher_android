package com.aljohanei.rafiq

/** ألوان صلبة فقط (بدون شفافية أو تدرجات) لتخفيف الرسم */
class Palette(
    val bg: Int, val card: Int, val text: Int, val muted: Int, val accent: Int,
    val danger: Int, val dangerDim: Int, val line: Int, val ok: Int,
    val tile: Int, val tileLine: Int, val warmTile: Int, val warmLine: Int,
    val onAccent: Int, val amber: Int
) {
    companion object {
        private fun c(hex: Long): Int = hex.toInt()

        val DARK = Palette(
            bg = c(0xFF0B0F14), card = c(0xFF131B24), text = c(0xFFF2F5F8), muted = c(0xFF9AA7B4),
            accent = c(0xFF2EE6F0), danger = c(0xFFFF4D5E), dangerDim = c(0xFF5A1E27), line = c(0xFF1C6F7A),
            ok = c(0xFF3DDC84), tile = c(0xFF0A1A20), tileLine = c(0xFF1C6F7A), warmTile = c(0xFF1A1508),
            warmLine = c(0xFF6B5320), onAccent = c(0xFF041016), amber = c(0xFFF5B841)
        )
        // الفاتح: البلاطات تبقى داكنة حتى تبقى الأيقونات واضحة
        val LIGHT = Palette(
            bg = c(0xFFF3F5F8), card = c(0xFFFFFFFF), text = c(0xFF111827), muted = c(0xFF5B6472),
            accent = c(0xFF0E7490), danger = c(0xFFDC2626), dangerDim = c(0xFFF6C9CE), line = c(0xFF9CC3E8),
            ok = c(0xFF15803D), tile = c(0xFF0E2A31), tileLine = c(0xFF0E7490), warmTile = c(0xFF1A1508),
            warmLine = c(0xFFB7791F), onAccent = c(0xFFFFFFFF), amber = c(0xFFB7791F)
        )
        val AMBER = Palette(
            bg = c(0xFF1A120A), card = c(0xFF2A1D10), text = c(0xFFFFE8C7), muted = c(0xFFC9A46B),
            accent = c(0xFFF59E0B), danger = c(0xFFEF4444), dangerDim = c(0xFF4A1C14), line = c(0xFF7A5A2E),
            ok = c(0xFFA3E635), tile = c(0xFF120C06), tileLine = c(0xFF7A5A2E), warmTile = c(0xFF120C06),
            warmLine = c(0xFF7A5A2E), onAccent = c(0xFF1A120A), amber = c(0xFFF59E0B)
        )
    }
}

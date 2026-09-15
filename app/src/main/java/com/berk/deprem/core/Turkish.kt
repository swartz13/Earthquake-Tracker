package com.berk.deprem.core

/**
 * Yer adlarini dogru Turkce ekle yazmak icin kucuk yardimcilar.
 *
 * "Simav 17 km kuzeyinde" degil, "Simav'ın 17 km kuzeyinde" yazmak istiyoruz;
 * bunun icin unlu uyumuna gore -ın/-in/-un/-ün secmek gerekiyor.
 */
object Turkish {

    private const val VOWELS = "aeıioöuüAEIİOÖUÜ"

    /** Kelimenin son unlusune gore ek: a,ı→ı  e,i→i  o,u→u  ö,ü→ü */
    private fun lastVowelClass(word: String): Char? {
        for (ch in word.reversed()) {
            when (ch) {
                'a', 'A', 'ı', 'I' -> return 'ı'
                'e', 'E', 'i', 'İ' -> return 'i'
                'o', 'O', 'u', 'U' -> return 'u'
                'ö', 'Ö', 'ü', 'Ü' -> return 'ü'
            }
        }
        return null
    }

    /**
     * Ozel ad tamlayan eki: Simav → Simav'ın, Bolu → Bolu'nun, İzmir → İzmir'in.
     * Unluyle biten adlarda araya kaynastirma 'n'si girer.
     */
    fun genitive(name: String): String {
        val word = name.trim()
        if (word.isEmpty()) return word
        val v = lastVowelClass(word) ?: return "$word'ın"
        val endsWithVowel = word.last() in VOWELS
        val suffix = when (v) {
            'ı' -> if (endsWithVowel) "nın" else "ın"
            'i' -> if (endsWithVowel) "nin" else "in"
            'u' -> if (endsWithVowel) "nun" else "un"
            else -> if (endsWithVowel) "nün" else "ün"
        }
        return "$word'$suffix"
    }

    /**
     * [fromLat],[fromLon] noktasindan bakildiginda hedefin yonu — bulunma
     * halinde, dogrudan cumlede kullanilabilecek sekilde.
     *
     * EMSC'nin sitedeki anlatimiyla ayni yonde olculur: "17 km N of Simav"
     * yani Simav'dan episantra dogru.
     */
    fun direction(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): String {
        val p1 = Math.toRadians(fromLat)
        val p2 = Math.toRadians(toLat)
        val dl = Math.toRadians(toLon - fromLon)
        val y = Math.sin(dl) * Math.cos(p2)
        val x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)
        val deg = (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
        val names = listOf(
            "kuzeyinde", "kuzeydoğusunda", "doğusunda", "güneydoğusunda",
            "güneyinde", "güneybatısında", "batısında", "kuzeybatısında",
        )
        return names[(((deg + 22.5) % 360) / 45).toInt()]
    }
}

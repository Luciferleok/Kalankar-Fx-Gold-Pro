import com.example.livegoldai.data.ShadowLab
import java.io.File
import java.util.Random

private var pass = 0; private var fail = 0
private fun check(name: String, ok: Boolean, detail: String = "") { if (ok) pass++ else fail++; println((if (ok) "PASS " else "FAIL ") + name + if (detail.isNotEmpty()) " :: $detail" else "") }

private fun bars(seed: Long, n: Int, sine: Double): List<ShadowLab.Bar> {
    val r = Random(seed); var p = 2000.0; val out = ArrayList<ShadowLab.Bar>()
    val t0 = java.time.LocalDateTime.of(2025, 1, 1, 0, 0)
    for (i in 0 until n) {
        val o = p; p += r.nextGaussian() * 1.5 + sine * kotlin.math.sin(2 * Math.PI * i / 400.0)
        val h = maxOf(o, p) + r.nextDouble(); val l = minOf(o, p) - r.nextDouble()
        out.add(ShadowLab.Bar(t0.plusHours(i.toLong()).toString().replace('T', ' ') + ":00", o, h, l, p))
    }
    return out
}

fun main() {
    // 1. registration
    check("experiment: attempt #2 registered", ShadowLab.Experiment::class.java.declaredFields.any { it.name == "ATTEMPT_2" })
    check("versions bumped (lab SL1.6, features F3)", ShadowLab.LAB_VERSION == "SL1.6" && ShadowLab.FEATURE_SCHEMA == "F3", "${ShadowLab.LAB_VERSION} ${ShadowLab.FEATURE_SCHEMA}")
    check("recipe of the live model unchanged (R2)", ShadowLab.RECIPE_VERSION == "R2")

    // 2. higher-timeframe factors exist and are causal
    val b = bars(7, 1200, 0.0)
    val rows = ShadowLab.factorRows(b)
    val last = rows.last()!!
    val mtf = ShadowLab.Factors::class.java.getMethod("mtf").invoke(last) as DoubleArray
    check("mtf() = 5 directional + 2 higher-timeframe factors", mtf.size == 7 && (0 until 5).all { mtf[it] == last.directional()[it] })
    check("higher-timeframe values are finite and clipped", mtf.all { it.isFinite() && it in -4.0..4.0 })
    var causal = true
    for (k in listOf(300, 600, 900, 1150)) {
        val part = ShadowLab.factorRows(b.subList(0, k + 1))[k]!!
        val a = ShadowLab.Factors::class.java.getMethod("mtf").invoke(part) as DoubleArray
        val z = ShadowLab.Factors::class.java.getMethod("mtf").invoke(rows[k]!!) as DoubleArray
        if (a.indices.any { kotlin.math.abs(a[it] - z[it]) > 1e-9 }) causal = false
    }
    check("higher-timeframe factors use past candles only (truncation test)", causal)
    // htf range position must equal the 80-bar range position by hand
    val i = 1000; var hi = Double.NEGATIVE_INFINITY; var lo = Double.POSITIVE_INFINITY
    for (j in i - 79..i) { hi = maxOf(hi, b[j].high); lo = minOf(lo, b[j].low) }
    val pos = 2 * (b[i].close - lo) / (hi - lo) - 1
    val m1000 = ShadowLab.Factors::class.java.getMethod("mtf").invoke(rows[i]!!) as DoubleArray
    check("htf range position = position in last 80 candles", kotlin.math.abs(m1000[6] - pos) < 1e-9, "${m1000[6]} vs $pos")

    // 3. lab run: new challenger present, old challengers unchanged (same calls on the same data)
    val before = File("tools/jvm-tests/fixtures/v348_before.txt").readLines().filter { it.isNotBlank() }
    var same = true; val diffs = ArrayList<String>()
    var mtfSeen = 0; var famOk = true; var noiseEdge = 0
    for ((seed, s) in listOf(1L to 0.0, 2L to 0.0, 3L to 0.6)) {
        val rep = ShadowLab.run("1h", bars(seed, 3000, s), 0L)
        rep.models.filter { it.name != "FACTOR MTF" && it.kind != "CONTROL" }.forEach { m ->
            val line = "$seed|${m.name}|${m.calls}|${m.right}|${m.wrong}|${m.flat}|${m.samples}"
            if (line !in before) { same = false; diffs.add(line) }
        }
        val x = rep.models.firstOrNull { it.name == "FACTOR MTF" }
        if (x != null && x.kind == "CHALLENGER") mtfSeen++
        if (rep.familySize != 6) famOk = false
        if (s == 0.0 && rep.verdict == "EDGE CANDIDATE") noiseEdge++
        if (seed == 3L && x != null) println("  planted signal: FACTOR MTF ${x.right}/${x.decided} boot low ${x.bootLow}, FACTOR GATED " + rep.models.first { it.name == "FACTOR GATED" }.let { "${it.right}/${it.decided} boot low ${it.bootLow}" })
    }
    check("FACTOR MTF runs as a challenger in every lab run", mtfSeen == 3)
    check("multiple-testing family grows to 6 (stricter for everyone)", famOk)
    check("all existing challengers + baselines make exactly the same calls as before (controls reseed with the new snapshot id)", same && before.size == 39, diffs.take(3).joinToString())
    check("pure noise: no EDGE CANDIDATE", noiseEdge == 0)

    // 4. planted higher-timeframe signal: FACTOR MTF must find it
    val rep = ShadowLab.run("1h", bars(3, 3000, 0.6), 0L)
    val x = rep.models.first { it.name == "FACTOR MTF" }
    check("planted signal: FACTOR MTF above 50% (corrected bootstrap)", (x.bootLow ?: 0.0) > 50.0, "boot low ${x.bootLow}")

    // 5. noise false-positive rate of FACTOR MTF alone (corrected bootstrap lower bound > 50%)
    var fp = 0; val runs = 40
    for (sd in 100L until 100L + runs) {
        val r = ShadowLab.run("1h", bars(sd, 2000, 0.0), 0L)
        val m = r.models.first { it.name == "FACTOR MTF" }
        if ((m.bootLow ?: 0.0) > 50.0 && (m.pairedDiffLow ?: -1.0) > 0.0) fp++
    }
    check("noise: FACTOR MTF passes both evidence checks in <= 2 of $runs runs", fp <= 2, "$fp/$runs")

    // 6. live model still the old 5-factor recipe
    val live = ShadowLab.run("1h", bars(9, 2500, 0.0), 0L).live
    check("live shadow model still uses 5 factors", live != null && live.weights.size == 6, "${live?.weights?.size}")

    println("RESULT: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}

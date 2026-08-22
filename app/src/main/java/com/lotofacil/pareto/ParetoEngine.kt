package com.lotofacil.pareto

import kotlin.math.ln
import kotlin.random.Random

data class PositionStat(
    val position: Int,
    val counts: Map<Int, Int>,
    val total: Int
) {
    val ranked: List<Map.Entry<Int, Int>> = counts.entries
        .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })

    fun probability(number: Int): Double =
        if (total == 0) 0.0 else (counts[number] ?: 0).toDouble() / total.toDouble()

    fun dominantPct(): Double = ranked.firstOrNull()?.let { 100.0 * it.value / total } ?: 0.0

    fun entropy(): Double {
        if (total == 0) return 0.0
        return counts.values.sumOf { count ->
            val p = count.toDouble() / total.toDouble()
            -p * ln(p)
        }
    }
}

data class Candidate(val numbers: List<Int>, val score: Double)

data class BlindBacktestResult(
    val testedDraws: Int,
    val gamesPerDraw: Int,
    val hits11: Int,
    val hits12: Int,
    val hits13: Int,
    val hits14: Int,
    val hits15: Int,
    val bestHit: Int
)

object ParetoEngine {
    fun parse(text: String): List<List<Int>> {
        return text.lineSequence().mapNotNull { line ->
            val all = Regex("\\d+").findAll(line).map { it.value.toInt() }.toList()
            val valid = all.filter { it in 1..25 }
            val picked = when {
                valid.size == 15 -> valid
                valid.size > 15 -> valid.takeLast(15)
                else -> emptyList()
            }.distinct().sorted()
            picked.takeIf { it.size == 15 }
        }.toList()
    }

    fun matrix(draws: List<List<Int>>, window: Int = 10): List<PositionStat> {
        require(window > 0)
        if (draws.isEmpty()) return emptyList()
        val sample = draws.takeLast(window.coerceAtMost(draws.size))
        return (0 until 15).map { p ->
            PositionStat(
                position = p + 1,
                counts = sample.groupingBy { it[p] }.eachCount(),
                total = sample.size
            )
        }
    }

    fun suggestedGame(draws: List<List<Int>>): Candidate? = generate(draws, 1).firstOrNull()

    fun generate(draws: List<List<Int>>, amount: Int, seed: Long = System.nanoTime()): List<Candidate> {
        if (draws.size < 10 || amount <= 0) return emptyList()

        val m3 = matrix(draws, 3)
        val m5 = matrix(draws, 5)
        val m10 = matrix(draws, 10)
        val m20 = matrix(draws, 20)
        val m50 = matrix(draws, 50)
        val mh = matrix(draws, draws.size)

        val rnd = Random(seed)
        val seen = HashSet<List<Int>>()
        val candidates = ArrayList<Candidate>()
        var attempts = 0
        val maxAttempts = amount * 1500

        while (candidates.size < amount && attempts < maxAttempts) {
            attempts++
            val chosen = mutableListOf<Int>()
            var score = 0.0
            var validPath = true

            for (p in 0 until 15) {
                val min = if (p == 0) 1 else chosen.last() + 1
                val max = 25 - (14 - p)
                if (min > max) {
                    validPath = false
                    break
                }

                val options = (min..max).toList()
                val weights = options.map { n ->
                    val trend = 0.12 * m3[p].probability(n) +
                        0.16 * m5[p].probability(n) +
                        0.38 * m10[p].probability(n) +
                        0.16 * m20[p].probability(n) +
                        0.10 * m50[p].probability(n) +
                        0.08 * mh[p].probability(n)
                    trend + 0.001
                }

                val totalWeight = weights.sum()
                var ticket = rnd.nextDouble() * totalWeight
                var index = 0
                while (index < weights.lastIndex && ticket > weights[index]) {
                    ticket -= weights[index]
                    index++
                }
                val selected = options[index]
                chosen += selected
                score += weights[index]
            }

            if (validPath && chosen.size == 15 && seen.add(chosen.toList())) {
                candidates += Candidate(chosen.toList(), score)
            }
        }

        return candidates.sortedByDescending { it.score }
    }

    fun blindBacktest(
        draws: List<List<Int>>,
        lastN: Int = 100,
        gamesPerDraw: Int = 100
    ): BlindBacktestResult {
        if (draws.size < 11) return BlindBacktestResult(0, gamesPerDraw, 0, 0, 0, 0, 0, 0)

        var h11 = 0
        var h12 = 0
        var h13 = 0
        var h14 = 0
        var h15 = 0
        var best = 0
        var tested = 0
        val start = (draws.size - lastN).coerceAtLeast(10)

        for (i in start until draws.size) {
            val pastOnly = draws.subList(0, i)
            val target = draws[i].toSet()
            val generated = generate(pastOnly, gamesPerDraw, seed = i.toLong())
            tested++

            generated.forEach { candidate ->
                val hits = candidate.numbers.count { it in target }
                if (hits > best) best = hits
                when (hits) {
                    11 -> h11++
                    12 -> h12++
                    13 -> h13++
                    14 -> h14++
                    15 -> h15++
                }
            }
        }

        return BlindBacktestResult(tested, gamesPerDraw, h11, h12, h13, h14, h15, best)
    }
}

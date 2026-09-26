package com.ahu.ahutong.data.recharge

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** Non-seasonal candidates fitted and compared without observing later calendar days. */
internal data class ElectricityForecastModel(
    val method: String,
    val alpha: Double = 0.0,
    val beta: Double = 0.0,
    val phi: Double = 1.0
) {
    internal data class State(var level: Double, var trend: Double = 0.0, var probability: Double = 1.0) {
        fun rawForecast(model: ElectricityForecastModel): Double = when (model.method) {
            "tsb" -> probability * level
            else -> level + model.phi * trend
        }

        fun forecast(model: ElectricityForecastModel): Double = max(0.0, rawForecast(model))

        fun observe(model: ElectricityForecastModel, value: Double) {
            if (model.method == "mean") return
            if (model.method == "tsb") {
                probability = (1.0 - model.beta) * probability + model.beta * (if (value > 0.0) 1.0 else 0.0)
                if (value > 0.0) level = (1.0 - model.alpha) * level + model.alpha * value
                return
            }
            val previousLevel = level
            level = model.alpha * value + (1.0 - model.alpha) * rawForecast(model)
            trend = model.beta * (level - previousLevel) + (1.0 - model.beta) * model.phi * trend
        }
    }

    fun fit(values: DoubleArray): State {
        if (method == "mean") return State(legacyRate(values))
        if (method == "tsb") {
            val first = values.indexOfFirst { it > 0.0 }
            if (first < 0) return State(0.0, probability = 0.0)
            val state = State(values[first], probability = 1.0 / (first + 1))
            for (index in first + 1 until values.size) state.observe(this, values[index])
            return state
        }
        val state = State(values.first())
        for (index in 1 until values.size) state.observe(this, values[index])
        return state
    }

    fun path(state: State, horizon: Int): DoubleArray {
        val predicted = state.copy()
        return DoubleArray(horizon) {
            predicted.forecast(this).also { value ->
                // Unobserved TSB days retain p*z; a positive mean is not a positive occurrence.
                if (method != "tsb" && method != "mean") {
                    predicted.level = predicted.rawForecast(this)
                    predicted.trend *= phi
                }
            }
        }
    }

    fun score(values: DoubleArray): Double {
        var error = 0.0
        var count = 0
        for (origin in 14 until values.size) {
            val state = fit(values.copyOf(origin))
            for (horizon in intArrayOf(1, 3, 7)) {
                if (origin + horizon > values.size) continue
                val actual = (origin until origin + horizon).sumOf { values[it] }
                error += abs(path(state, horizon).sum() - actual) / horizon
                count++
            }
        }
        return if (count == 0) Double.POSITIVE_INFINITY else error / count
    }

    /** Empirical paths provide a risk buffer, not a calibrated confidence guarantee. */
    fun riskCurve(values: DoubleArray, horizon: Int, central: DoubleArray): DoubleArray {
        val fitted = fit(values)
        val positive = values.filter { it > 0.0 }
        val residuals = (3 until values.size).map { index ->
            values[index] - fit(values.copyOf(index)).rawForecast(this)
        }.toDoubleArray()
        if (residuals.isEmpty()) return central.copyOf(horizon)
        val mean = residuals.average()
        val innovations = residuals.map { it - mean }.toDoubleArray()
        val positiveMean = if (positive.isEmpty()) 0.0 else positive.average()
        // Identical snapshots reproduce identical risk dates across process restarts.
        val random = Random(20260926)
        val paths = Array(horizon) { DoubleArray(1_000) }
        repeat(1_000) { sample ->
            val state = fitted.copy()
            var cumulative = 0.0
            var blockStart = 0
            for (day in 0 until horizon) {
                if (day % 3 == 0) blockStart = random.nextInt(innovations.size)
                val innovation = innovations[(blockStart + day % 3) % innovations.size]
                val value = if (method == "tsb") {
                    // Bernoulli occurrence plus empirical positive sizes preserves E[y]=p*z.
                    if (positiveMean > 0.0 && random.nextDouble() < fitted.probability) {
                        positive[random.nextInt(positive.size)] / positiveMean * fitted.level
                    } else 0.0
                } else max(0.0, state.rawForecast(this) + innovation)
                cumulative += value
                paths[day][sample] = cumulative
                // ETS paths update their own states using the clipped simulated observation.
                if (method != "tsb") state.observe(this, value)
            }
        }
        return DoubleArray(horizon) { day ->
            paths[day].sort()
            max(central[day], paths[day][899])
        }
    }

    companion object {
        fun legacyRate(values: DoubleArray): Double = max(values.average(), values.takeLast(7).average())

        fun select(values: DoubleArray): ElectricityForecastModel {
            val baseline = ElectricityForecastModel("mean")
            if (values.size < 26) return baseline
            var oldError = 0.0
            var adaptiveError = 0.0
            // Inner folds tune candidates; outer three-day totals test that tuning policy.
            for (origin in 21..values.size - 3) {
                val prefix = values.copyOf(origin)
                val selected = selectCandidate(prefix)
                val actual = (origin until origin + 3).sumOf { values[it] }
                oldError += abs(legacyRate(prefix) * 3 - actual)
                adaptiveError += abs(selected.path(selected.fit(prefix), 3).sum() - actual)
            }
            return if (adaptiveError < oldError * 0.95) selectCandidate(values) else baseline
        }

        private fun selectCandidate(values: DoubleArray): ElectricityForecastModel {
            val baseline = ElectricityForecastModel("mean")
            if (values.size < 21) return baseline
            var best = baseline
            var bestScore = baseline.score(values)
            val alphas = doubleArrayOf(0.1, 0.2, 0.35, 0.5, 0.7)
            val simple = alphas.map { ElectricityForecastModel("ses", alpha = it) }.toMutableList()
            // Zero days describe intermittent room usage, not damaged history.
            if (values.count { it == 0.0 } >= 3) {
                for (alpha in alphas) for (beta in doubleArrayOf(0.05, 0.15, 0.3)) {
                    simple += ElectricityForecastModel("tsb", alpha, beta)
                }
            }
            val simpleBest = simple.minBy { it.score(values) }
            val simpleScore = simpleBest.score(values)
            if (simpleScore < bestScore * 0.95) {
                best = simpleBest
                bestScore = simpleScore
            }
            val trend = alphas.flatMap { alpha ->
                doubleArrayOf(0.05, 0.1, 0.2).flatMap { beta ->
                    doubleArrayOf(0.8, 0.9, 0.98).map { phi ->
                        ElectricityForecastModel("damped_holt", alpha, beta, phi)
                    }
                }
            }.minBy { it.score(values) }
            // A trend requires a clearer improvement than a level/occurrence model.
            if (trend.score(values) < bestScore * 0.9) best = trend
            return best
        }
    }
}

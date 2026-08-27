package com.hengpick.mall.recommendation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PhoneCapabilityCalculator {

    private static final String FORMULA_VERSION = "phone-capability-v1";

    private static final Map<String, BigDecimal> BATTERY_WEIGHTS = weights(
            "batteryCapacity", "0.45",
            "chipEfficiency", "0.25",
            "charging", "0.20",
            "batteryEvidence", "0.10");

    private static final Map<String, BigDecimal> EASY_USE_WEIGHTS = weights(
            "simpleMode", "0.25",
            "largeFontMode", "0.20",
            "accessibility", "0.30",
            "adCleanliness", "0.15",
            "systemSupport", "0.10");

    public CapabilityScore battery(List<MetricFact> facts) {
        return calculate("battery", BATTERY_WEIGHTS, facts);
    }

    public CapabilityScore easyUse(List<MetricFact> facts) {
        return calculate("easy_use", EASY_USE_WEIGHTS, facts);
    }

    private CapabilityScore calculate(String capability, Map<String, BigDecimal> weights, List<MetricFact> facts) {
        var byKey = new LinkedHashMap<String, MetricFact>();
        for (var fact : facts) {
            if (byKey.putIfAbsent(fact.key(), fact) != null) throw new IllegalArgumentException("能力指标键不能重复");
        }
        if (!byKey.keySet().equals(weights.keySet())) throw new IllegalArgumentException("能力指标集合与公式不匹配");

        BigDecimal knownWeight = BigDecimal.ZERO;
        BigDecimal weightedScore = BigDecimal.ZERO;
        var sourceFactIds = new java.util.ArrayList<String>();
        var missingKeys = new java.util.ArrayList<String>();
        for (var entry : weights.entrySet()) {
            var fact = byKey.get(entry.getKey());
            if (!fact.known()) {
                missingKeys.add(fact.key());
                continue;
            }
            knownWeight = knownWeight.add(entry.getValue());
            weightedScore = weightedScore.add(fact.normalizedScore().multiply(entry.getValue()));
            sourceFactIds.add(fact.factId());
        }
        if (knownWeight.signum() == 0) throw new IllegalArgumentException("全部指标缺失，不能生成能力分");

        return new CapabilityScore(
                capability,
                weightedScore.divide(knownWeight, 2, RoundingMode.HALF_UP),
                knownWeight.setScale(2, RoundingMode.HALF_UP),
                FORMULA_VERSION,
                sourceFactIds,
                missingKeys);
    }

    private static Map<String, BigDecimal> weights(String... entries) {
        var result = new LinkedHashMap<String, BigDecimal>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put(entries[index], new BigDecimal(entries[index + 1]));
        }
        return Collections.unmodifiableMap(result);
    }
}

#!/usr/bin/env python3
"""
Compare JMH benchmark results and detect regressions
"""

import json
import sys
from typing import Dict, List

def load_benchmark(filename: str) -> List[Dict]:
    """Load JMH JSON results"""
    with open(filename, 'r') as f:
        return json.load(f)

def extract_scores(benchmarks: List[Dict]) -> Dict[str, float]:
    """Extract benchmark name -> score mapping"""
    scores = {}
    for bench in benchmarks:
        name = bench['benchmark']
        score = bench['primaryMetric']['score']
        scores[name] = score
    return scores

def compare_benchmarks(baseline_file: str, current_file: str, threshold: float = 0.1):
    """
    Compare benchmarks and detect regressions

    Args:
        baseline_file: Path to baseline benchmark results
        current_file: Path to current benchmark results
        threshold: Regression threshold (0.1 = 10%)
    """

    baseline = extract_scores(load_benchmark(baseline_file))
    current = extract_scores(load_benchmark(current_file))

    print("=" * 80)
    print("BENCHMARK COMPARISON")
    print("=" * 80)
    print()

    regressions = []
    improvements = []

    for name in sorted(current.keys()):
        if name not in baseline:
            print(f"NEW: {name}")
            print(f"     Score: {current[name]:.2f} ops/s")
            print()
            continue

        baseline_score = baseline[name]
        current_score = current[name]
        change = (current_score - baseline_score) / baseline_score

        status = "✓"
        if change < -threshold:
            status = "❌ REGRESSION"
            regressions.append((name, change))
        elif change > threshold:
            status = "✨ IMPROVEMENT"
            improvements.append((name, change))

        print(f"{status} {name}")
        print(f"     Baseline: {baseline_score:.2f} ops/s")
        print(f"     Current:  {current_score:.2f} ops/s")
        print(f"     Change:   {change:+.1%}")
        print()

    # Summary
    print("=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print()

    if regressions:
        print(f"❌ {len(regressions)} REGRESSION(S) DETECTED:")
        for name, change in regressions:
            print(f"   - {name}: {change:+.1%}")
        print()

    if improvements:
        print(f"✨ {len(improvements)} IMPROVEMENT(S):")
        for name, change in improvements:
            print(f"   - {name}: {change:+.1%}")
        print()

    if not regressions and not improvements:
        print("✓ No significant changes")
        print()

    # Write to file for GitHub comment
    with open('benchmark-comparison.txt', 'w') as f:
        f.write("Benchmark Comparison\n")
        f.write("=" * 40 + "\n\n")

        if regressions:
            f.write(f"❌ {len(regressions)} regression(s) detected\n")
            for name, change in regressions:
                f.write(f"  {name}: {change:+.1%}\n")
            f.write("\n")

        if improvements:
            f.write(f"✨ {len(improvements)} improvement(s)\n")
            for name, change in improvements:
                f.write(f"  {name}: {change:+.1%}\n")
            f.write("\n")

        if not regressions and not improvements:
            f.write("✓ No significant changes\n")

    # Exit with error if regressions detected
    if regressions:
        sys.exit(1)

if __name__ == '__main__':
    if len(sys.argv) != 3:
        print("Usage: compare_benchmarks.py <baseline.json> <current.json>")
        sys.exit(1)

    compare_benchmarks(sys.argv[1], sys.argv[2])
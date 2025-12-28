#!/usr/bin/env python3
"""
VTracer JMH Benchmark Comparator - Production Ready
Detects regressions, improvements, and new benchmarks with GitHub Actions integration
"""

import json
import sys
from pathlib import Path
from dataclasses import dataclass
from datetime import datetime
from typing import List, Tuple, Optional
import argparse
import os

@dataclass
class BenchmarkResult:
    name: str
    score: float
    score_error: float
    unit: str
    iterations: int

def load_benchmark(filename: str) -> List[BenchmarkResult]:
    """Load and parse JMH JSON results (v1.37+ compatible)"""
    path = Path(filename)
    if not path.exists():
        print(f"⚠️  Baseline not found: {filename}")
        return []

    try:
        with path.open('r', encoding='utf-8') as f:
            data = json.load(f)

        results = []
        for bench in data.get('benchmarks', []):
            primary = bench.get('primaryMetric', {})
            raw_data = primary.get('rawData', [])
            iterations = sum(len(run) for run in raw_data) if raw_data else 0

            results.append(BenchmarkResult(
                name=primary.get('benchmark', 'unknown'),
                score=primary.get('score', 0.0),
                score_error=primary.get('scoreError', 0.0),
                unit=primary.get('scoreUnit', 'ops/s'),
                iterations=iterations
            ))
        return results
    except Exception as e:
        print(f"❌ Error parsing {filename}: {e}")
        return []

def calculate_change(old: float, new: float) -> float:
    """Calculate relative change (new - old) / old"""
    return (new - old) / old if old != 0 else 0.0

def format_change(change: float) -> str:
    """Format change as percentage with emoji"""
    if change < -0.10:
        return f"❌ {change:+.1%}"
    elif change > 0.10:
        return f"✨ {change:+.1%}"
    elif abs(change) > 0.01:
        return f"➡️  {change:+.1%}"
    else:
        return "✓ unchanged"

def compare_benchmarks(
    baseline_file: str,
    current_file: str,
    threshold: float = 0.10,
    output_file: Optional[str] = None,
    verbose: bool = True
) -> Tuple[List[Tuple[str, float]], List[Tuple[str, float]], List[str]]:
    """
    Compare JMH benchmarks and generate report.
    Returns: (regressions, improvements, new_benchmarks)
    """
    baseline_results = load_benchmark(baseline_file)
    current_results = load_benchmark(current_file)

    if not current_results:
        print("❌ No current benchmark results found")
        sys.exit(1)

    baseline_map = {r.name: r for r in baseline_results}
    current_map = {r.name: r for r in current_results}

    markdown_table = "| Benchmark | Baseline | Current | Change | Unit |\n"
    markdown_table += "|-----------|----------|---------|--------|-----|\n"

    regressions: List[Tuple[str, float]] = []
    improvements: List[Tuple[str, float]] = []
    new_benchmarks: List[str] = []
    unchanged: List[str] = []

    if verbose:
        print("\n" + "="*90)
        print("🧪 VTRACER BENCHMARK COMPARISON")
        print(f"📅 {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}")
        print("="*90)

    for name, current in current_map.items():
        if name not in baseline_map:
            new_benchmarks.append(name)
            if verbose:
                print(f"🆕 NEW BENCHMARK: {name} — {current.score:.2f} ±{current.score_error:.2f} {current.unit}")
            markdown_table += f"| 🆕 `{name}` | - | {current.score:.2f}±{current.score_error:.2f} | - | {current.unit} |\n"
            continue

        baseline = baseline_map[name]
        change_pct = calculate_change(baseline.score, current.score)
        status = format_change(change_pct)

        if verbose:
            print(f"{status} {name}")
            print(f"     Baseline: {baseline.score:.2f} ±{baseline.score_error:.2f} {baseline.unit}")
            print(f"     Current:  {current.score:.2f} ±{current.score_error:.2f} {current.unit}")
            print(f"     Change:   {change_pct:+.1%} ({change_pct*100:+.1f}%)")

        markdown_table += (
            f"| `{name}` | {baseline.score:.2f}±{baseline.score_error:.2f} | "
            f"{current.score:.2f}±{current.score_error:.2f} | {change_pct:+.1%} | {current.unit} |\n"
        )

        if change_pct < -threshold:
            regressions.append((name, change_pct))
        elif change_pct > threshold:
            improvements.append((name, change_pct))
        else:
            unchanged.append(name)

    if verbose:
        print("\n" + "="*90)
        print("📊 SUMMARY")
        print("="*90)
        print(f"📈 Improvements: {len(improvements)}")
        print(f"📉 Regressions:  {len(regressions)}")
        print(f"➡️  Unchanged:   {len(unchanged)}")
        print(f"🆕 New:          {len(new_benchmarks)}")
        print(f"📋 Total:        {len(current_map)}")

    # GitHub Actions comment
    repo = os.environ.get("GITHUB_REPOSITORY", "unknown_repo")
    run_id = os.environ.get("GITHUB_RUN_ID", "0")
    github_comment = f"""## 🧪 VTracer Benchmark Results

**Generated:** {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S UTC')}

{markdown_table}

### 📊 Summary
Improvements: ✨ {len(improvements)}
Regressions: ❌ {len(regressions)}
Unchanged: ➡️ {len(unchanged)}
New: 🆕 {len(new_benchmarks)}
Total: 📋 {len(current_map)}

[Full results](https://github.com/{repo}/actions/runs/{run_id})
"""

    if regressions:
        github_comment += "### ❌ Regressions (>10%):\n"
        for name, change in regressions[:5]:
            github_comment += f"- `{name}`: {change:+.1%}\n"

    # Write files
    if output_file:
        Path(output_file).write_text(github_comment)
    Path('benchmark-comparison.md').write_text(github_comment)
    Path('benchmark-summary.txt').write_text(f"{len(regressions)} regressions, {len(improvements)} improvements\n")

    if regressions:
        print("\n❌ FAILING CI: Regressions detected!")
        sys.exit(1)
    else:
        print("\n✅ PASS: No regressions detected!")
        sys.exit(0)

def main():
    parser = argparse.ArgumentParser(description="Compare VTracer JMH benchmarks")
    parser.add_argument("baseline", help="Baseline benchmark JSON file")
    parser.add_argument("current", help="Current benchmark JSON file")
    parser.add_argument("--threshold", type=float, default=0.10, help="Regression threshold (default: 10%)")
    parser.add_argument("--output", help="GitHub comment output file")
    parser.add_argument("--verbose", action="store_true", help="Verbose output for console")
    args = parser.parse_args()

    compare_benchmarks(args.baseline, args.current, args.threshold, args.output, args.verbose)

if __name__ == '__main__':
    main()

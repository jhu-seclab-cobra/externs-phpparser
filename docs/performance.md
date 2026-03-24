# externs-phpparser Performance

## Current Baseline

Measured on macOS Darwin 25.3.0, 3 independent JVM invocations per state, median reported.
Run: `./gradlew performanceTest --rerun`

| Benchmark | ns/op | ops/s |
|-----------|-------|-------|
| extractFileFromZip | 152,618 | 6,552 |
| regex-new-every-call | 495.0 | 2,020,287 |
| regex-cached | 157.1 | 6,365,543 |
| joinToString-hashCode | 179.7 | 5,565,992 |
| contentHashCode | 23.9 | 41,870,261 |
| split-map-filter-list | 51,032 | 19,595 |
| splitToSequence-lazy | 1,076 | 929,616 |
| filterValues-map-spread | 161.8 | 6,179,515 |
| buildList-single-pass | 108.5 | 9,219,428 |

## Key Improvements

| ID | Title | File(s) | Impact |
|----|-------|---------|--------|
| P1-1 | Remove debug println in extractFileFromZip | Utils.kt | extractFileFromZip: 152,618 -> 99,786 ns/op (-34.6%) |
| P1-2 | Compile Regex as top-level constants | Utils.kt | regex path: 495.0 -> 157.1 ns/op (-68.3%, 3.2x) |
| P1-3 | Use contentHashCode for cache key | AbcBinary.kt | hash: 179.7 -> 24.1 ns/op (-86.6%, 7.5x) |

## Completed Optimizations

### P1-1: Remove debug `println` in `extractFileFromZip` (Bug fix) — KEEP
- **File**: `Utils.kt`
- **Change**: Removed `println("Checking entry: ${inZipEntry?.name}")` from ZIP extraction loop
- **Measured**: extractFileFromZip 152,618 -> 99,786 ns/op (**-34.6%**), no cross-regression

### P1-2: Compile `Regex` as top-level constants in `isPhpVersionValid` — KEEP
- **File**: `Utils.kt`
- **Change**: Extracted `Regex("""PHP (\d+\.\d+\.\d+)""")` and `Regex("""^\d+(\.\d+){0,2}$""")` to file-level `private val`
- **Measured**: regex path 495.0 -> 157.1 ns/op (**-68.3%, 3.2x faster**), no cross-regression

### P1-3: Use `contentHashCode()` for cache key in `execute()` — KEEP
- **File**: `AbcBinary.kt`
- **Change**: Replaced `cmdArray.joinToString(" ").hashCode()` with `cmdArray.contentHashCode()`
- **Measured**: hash computation 179.7 -> 24.1 ns/op (**-86.6%, 7.5x faster**), no cross-regression

## Evaluated & Rejected

| ID | Title | Result | Reason |
|----|-------|--------|--------|

## Candidates

### P1-4: Use `Sequence` for PATH search in `searchBin`
- **File(s)**: `Utils.kt`
- **Hypothesis**: Eager `split().map().filter()` creates intermediate lists; converting to sequence from the start enables lazy evaluation and short-circuiting
- **Risk**: Low

### P1-5: Reduce intermediate collections in `getCommandArray`
- **File(s)**: `BinPhpParser.kt`
- **Hypothesis**: `filterValues { ... }.map { ... }.toTypedArray()` creates two intermediate collections; a single-pass `buildList` reduces to one
- **Risk**: Low

## Remaining Known Bottlenecks

- Process spawning (external PHP binary) dominates execution time — not optimizable in Kotlin
- ZIP extraction and CRC32 checksum are I/O-bound (already uses 16KB buffer)

## Key Insights

1. This project is a binary wrapper; in-process hot paths are limited to initialization and command construction
2. Most gains come from eliminating unnecessary allocations and I/O during setup, not algorithmic improvements
3. Debug println in production code is both a correctness issue and a performance issue (-34.6%)

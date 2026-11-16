/**
 * Rank keys for drag-and-drop ordering.
 *
 * A rank is a string read as a base-62 fraction between 0 and 1. Items are
 * ordered by plain character-code comparison of their ranks (never a
 * locale-aware comparison). Moving an item writes one new rank for that item
 * only: a key strictly between its two new neighbours.
 *
 * Keys never end in the lowest digit, which guarantees there is always room
 * for another key between any two existing keys.
 */

const DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
const BASE = DIGITS.length;
const ZERO = DIGITS[0]!;

function digit(ch: string): number {
  const i = DIGITS.indexOf(ch);
  if (i < 0) throw new Error(`invalid rank character: ${JSON.stringify(ch)}`);
  return i;
}

function assertValid(rank: string): void {
  if (rank.length === 0) throw new Error("rank must not be empty");
  for (const ch of rank) digit(ch);
  if (rank.endsWith(ZERO)) throw new Error(`rank must not end in "${ZERO}": ${rank}`);
}

/** Plain code-unit comparison, the only ordering clients may use for ranks. */
export function compareRanks(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0;
}

/**
 * Key strictly between `lo` and `hi`. `lo === ""` stands for 0 and
 * `hi === null` stands for 1.
 */
function between(lo: string, hi: string | null): string {
  if (hi !== null) {
    // Shared leading digits are kept as they are.
    let n = 0;
    while (n < hi.length && (lo[n] ?? ZERO) === hi[n]) n++;
    if (n > 0) return hi.slice(0, n) + between(lo.slice(n), hi.slice(n));
  }
  const dLo = lo.length > 0 ? digit(lo[0]!) : 0;
  const dHi = hi !== null ? digit(hi[0]!) : BASE;
  if (dHi - dLo > 1) {
    return DIGITS[Math.floor((dLo + dHi) / 2)]!;
  }
  // The first digits are neighbours, so go one digit deeper.
  if (hi !== null && hi.length > 1) return hi.slice(0, 1);
  return DIGITS[dLo]! + between(lo.slice(1), null);
}

/**
 * Rank that sorts after `before` and ahead of `after`.
 * Pass `null` for a missing neighbour (start or end of the list).
 */
export function rankBetween(before: string | null, after: string | null): string {
  if (before !== null) assertValid(before);
  if (after !== null) assertValid(after);
  if (before !== null && after !== null && before >= after) {
    throw new Error(`ranks out of order: ${before} >= ${after}`);
  }
  return between(before ?? "", after);
}

/**
 * Adding at the top or bottom is by far the most common move, and halving the
 * remaining space each time would make keys grow by a character every few
 * inserts. Instead the first HEAD digits are treated as a counter that steps
 * by one, starting from the middle, so keys stay 1 to 3 characters long for
 * roughly 119,000 inserts at either end before falling back to halving.
 */
const HEAD = 3;
const HEAD_LIMIT = BASE ** HEAD;
const START = DIGITS[BASE / 2]!;

function headValue(rank: string): number {
  let v = 0;
  for (let i = 0; i < HEAD; i++) v = v * BASE + (i < rank.length ? digit(rank[i]!) : 0);
  return v;
}

function encodeHead(v: number): string {
  let s = "";
  for (let i = 0; i < HEAD; i++) {
    s = DIGITS[v % BASE]! + s;
    v = Math.floor(v / BASE);
  }
  let end = s.length;
  while (end > 0 && s[end - 1] === ZERO) end--;
  return s.slice(0, end);
}

/** Rank for an item placed at the top of a list whose first rank is `first`. */
export function rankBefore(first: string | null): string {
  if (first === null) return START;
  assertValid(first);
  // If `first` has digits past the head, its bare head already sorts before it.
  const v = first.length > HEAD ? headValue(first) : headValue(first) - 1;
  return v >= 1 ? encodeHead(v) : between("", first);
}

/** Rank for an item placed at the bottom of a list whose last rank is `last`. */
export function rankAfter(last: string | null): string {
  if (last === null) return START;
  assertValid(last);
  const v = headValue(last) + 1;
  return v < HEAD_LIMIT ? encodeHead(v) : between(last, null);
}

/** `count` evenly spread ranks, for creating many items at once. */
export function initialRanks(count: number): string[] {
  const out: string[] = [];
  let prev: string | null = null;
  for (let i = 0; i < count; i++) {
    prev = rankAfter(prev);
    out.push(prev);
  }
  return out;
}

/**
 * New rank for the item moved to position `toIndex` in `ranks`.
 * `ranks` is the current order without the moved item.
 */
export function rankForIndex(ranks: readonly string[], toIndex: number): string {
  const i = Math.max(0, Math.min(toIndex, ranks.length));
  const prev = ranks[i - 1] ?? null;
  const next = ranks[i] ?? null;
  if (prev === null) return rankBefore(next);
  if (next === null) return rankAfter(prev);
  return rankBetween(prev, next);
}

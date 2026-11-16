import { describe, expect, it } from "vitest";
import { compareRanks, initialRanks, rankAfter, rankBefore, rankBetween, rankForIndex } from "../src/index.js";

const sorted = (xs: string[]) => [...xs].sort(compareRanks);

describe("rank keys", () => {
  it("gives a key strictly between two neighbours", () => {
    const pairs: [string, string][] = [["A", "B"], ["A", "A1"], ["Az", "B"], ["001", "002"], ["V", "V1"], ["zz", "zzz"]];
    for (const [a, b] of pairs) {
      const m = rankBetween(a, b);
      expect(a < m && m < b, `${a} < ${m} < ${b}`).toBe(true);
      expect(m.endsWith("0")).toBe(false);
    }
  });

  it("rejects neighbours in the wrong order and malformed keys", () => {
    expect(() => rankBetween("B", "A")).toThrow();
    expect(() => rankBetween("A", "A")).toThrow();
    expect(() => rankBetween("A0", "B")).toThrow();
    expect(() => rankBetween("a-b", null)).toThrow();
  });

  it("keeps keys short when adding 5,000 items at the bottom", () => {
    let last: string | null = null;
    const all: string[] = [];
    for (let i = 0; i < 5000; i++) {
      last = rankAfter(last);
      all.push(last);
    }
    expect(all).toEqual(sorted(all));
    expect(new Set(all).size).toBe(all.length);
    expect(Math.max(...all.map((r) => r.length))).toBeLessThanOrEqual(3);
  });

  it("keeps keys short when adding 5,000 items at the top", () => {
    let first: string | null = null;
    const all: string[] = [];
    for (let i = 0; i < 5000; i++) {
      first = rankBefore(first);
      all.unshift(first);
    }
    expect(all).toEqual(sorted(all));
    expect(new Set(all).size).toBe(all.length);
    expect(Math.max(...all.map((r) => r.length))).toBeLessThanOrEqual(3);
  });

  it("still works after the short-key range runs out at either end", () => {
    let hi = "zzz";
    let lo = "001";
    for (let i = 0; i < 50; i++) {
      const nextHi = rankAfter(hi);
      const nextLo = rankBefore(lo);
      expect(nextHi > hi).toBe(true);
      expect(nextLo < lo).toBe(true);
      expect(nextLo.endsWith("0")).toBe(false);
      hi = nextHi;
      lo = nextLo;
    }
  });

  it("survives 2,000 random moves with the order always matching the keys", () => {
    // Small deterministic generator so the test is repeatable.
    let seed = 42;
    const rand = (n: number) => {
      seed = (seed * 1103515245 + 12345) & 0x7fffffff;
      return seed % n;
    };
    const items = initialRanks(20).map((rank, i) => ({ id: i, rank }));
    for (let move = 0; move < 2000; move++) {
      const from = rand(items.length);
      const [moved] = items.splice(from, 1);
      const to = rand(items.length + 1);
      moved!.rank = rankForIndex(items.map((x) => x.rank), to);
      items.splice(to, 0, moved!);
      const ranks = items.map((x) => x.rank);
      expect(ranks).toEqual(sorted(ranks));
    }
    expect(new Set(items.map((x) => x.rank)).size).toBe(items.length);
  });

  it("always squeezes a key between two adjacent keys, 500 times in a row", () => {
    let a = "V";
    const b = "W";
    for (let i = 0; i < 500; i++) {
      const m = rankBetween(a, b);
      expect(a < m && m < b).toBe(true);
      a = m;
    }
  });
});

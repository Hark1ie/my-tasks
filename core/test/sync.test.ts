import { describe, expect, it } from "vitest";
import { Stamper, expiredDeletes, mergeInto, newer, shouldApply } from "../src/index.js";
import { task } from "./helpers.js";

describe("newest edit wins", () => {
  it("keeps the copy with the later edit time", () => {
    const phone = task({ id: "x", title: "phone", updatedAt: 200, updatedBy: "phone" });
    const pc = task({ id: "x", title: "pc", updatedAt: 300, updatedBy: "pc" });
    expect(newer(phone, pc).title).toBe("pc");
    expect(newer(pc, phone).title).toBe("pc");
  });

  it("breaks an exact tie the same way on every device", () => {
    const a = task({ id: "x", title: "a", updatedAt: 200, updatedBy: "aaa" });
    const b = task({ id: "x", title: "b", updatedAt: 200, updatedBy: "bbb" });
    expect(newer(a, b).title).toBe("b");
    expect(newer(b, a).title).toBe("b");
  });

  it("ignores an old offline edit that arrives late", () => {
    const local = new Map([["x", task({ id: "x", title: "new", updatedAt: 500 })]]);
    const changed = mergeInto(local, [task({ id: "x", title: "stale", updatedAt: 400 })]);
    expect(changed).toEqual([]);
    expect(local.get("x")!.title).toBe("new");
  });

  it("applies new items and newer edits, and reports what changed", () => {
    const local = new Map([["x", task({ id: "x", title: "old", updatedAt: 100 })]]);
    const changed = mergeInto(local, [
      task({ id: "x", title: "edited", updatedAt: 150 }),
      task({ id: "y", title: "brand new", updatedAt: 50 }),
    ]);
    expect(changed.sort()).toEqual(["x", "y"]);
    expect(local.get("x")!.title).toBe("edited");
  });

  it("lets a newer delete beat an older edit, and a newer edit beat an older delete", () => {
    const edited = task({ id: "x", updatedAt: 300 });
    const deleted = task({ id: "x", updatedAt: 400, deletedAt: 400 });
    expect(newer(edited, deleted).deletedAt).toBe(400);
    const restored = task({ id: "x", updatedAt: 500, deletedAt: null });
    expect(newer(deleted, restored).deletedAt).toBeNull();
  });

  it("does not re-apply an identical copy", () => {
    const t = task({ id: "x" });
    expect(shouldApply(t, t)).toBe(false);
    expect(shouldApply(undefined, t)).toBe(true);
  });
});

describe("stamping edits", () => {
  it("moves time forward even if the device clock goes backwards", () => {
    let now = 1000;
    const s = new Stamper("phone", { now: () => now });
    const created = s.create("x", { title: "a" });
    now = 500;
    const edited = s.edit(created, { title: "b" });
    expect(edited.updatedAt).toBeGreaterThan(created.updatedAt);
    expect(edited.updatedBy).toBe("phone");
    expect(edited.createdAt).toBe(created.createdAt);
  });

  it("stamps after anything it has seen from the cloud", () => {
    const s = new Stamper("pc", { now: () => 1000 });
    s.observe(5000);
    expect(s.next()).toBe(5001);
  });

  it("soft-deletes and restores", () => {
    const s = new Stamper("pc", { now: () => 1000 });
    const item = s.create("x", { title: "a" });
    const gone = s.remove(item);
    expect(gone.deletedAt).not.toBeNull();
    const back = s.restore(gone);
    expect(back.deletedAt).toBeNull();
    expect(back.updatedAt).toBeGreaterThan(gone.updatedAt);
  });

  it("finds Deleted Items older than the keep period", () => {
    const day = 24 * 60 * 60 * 1000;
    const now = 100 * day;
    const items = [
      task({ id: "old", deletedAt: now - 8 * day }),
      task({ id: "recent", deletedAt: now - 6 * day }),
      task({ id: "live" }),
    ];
    expect(expiredDeletes(items, now, 7).map((t) => t.id)).toEqual(["old"]);
  });
});

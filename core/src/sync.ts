/**
 * Sync rules shared by every client.
 *
 * Each device writes to its local Firestore cache first, which works offline,
 * and Firestore pushes the write when the device is online. The rule for two
 * devices editing the same item is "the newest edit wins", decided by
 * `updatedAt`. The same rule is enforced on the server by firestore.rules, so
 * an old offline edit that arrives late is rejected instead of overwriting a
 * newer one.
 */

import type { Millis, SyncMeta } from "./model.js";

/** Returns whichever copy was edited last. Ties go to the higher device id so every device agrees. */
export function newer<T extends SyncMeta>(a: T, b: T): T {
  if (a.updatedAt !== b.updatedAt) return a.updatedAt > b.updatedAt ? a : b;
  return a.updatedBy >= b.updatedBy ? a : b;
}

/** Whether `incoming` should replace `current` (which may not exist yet). */
export function shouldApply<T extends SyncMeta>(current: T | undefined, incoming: T): boolean {
  if (current === undefined) return true;
  return newer(current, incoming) === incoming && current !== incoming;
}

/**
 * Merges a batch of incoming items into a local map, keeping the newest copy
 * of each. Returns the ids that changed.
 */
export function mergeInto<T extends SyncMeta>(local: Map<string, T>, incoming: Iterable<T>): string[] {
  const changed: string[] = [];
  for (const item of incoming) {
    if (shouldApply(local.get(item.id), item)) {
      local.set(item.id, item);
      changed.push(item.id);
    }
  }
  return changed;
}

export interface Clock {
  now(): Millis;
}

/**
 * Stamps edits. `updatedAt` always moves forward, even if the device clock
 * jumps backwards, so a device can never lose to its own earlier edit.
 */
export class Stamper {
  private last = 0;
  constructor(
    private readonly deviceId: string,
    private readonly clock: Clock = { now: () => Date.now() },
  ) {}

  next(): Millis {
    const t = Math.max(this.clock.now(), this.last + 1);
    this.last = t;
    return t;
  }

  /** Call with the `updatedAt` of items read from the cloud, so later edits here sort after them. */
  observe(updatedAt: Millis): void {
    if (updatedAt > this.last) this.last = updatedAt;
  }

  create<T extends object>(id: string, fields: T): T & SyncMeta {
    const t = this.next();
    return { ...fields, id, createdAt: t, updatedAt: t, deletedAt: null, updatedBy: this.deviceId };
  }

  edit<T extends SyncMeta>(item: T, changes: Partial<Omit<T, keyof SyncMeta>>): T {
    return { ...item, ...changes, updatedAt: this.next(), updatedBy: this.deviceId };
  }

  /** Moves an item to Deleted Items. */
  remove<T extends SyncMeta>(item: T): T {
    const t = this.next();
    return { ...item, deletedAt: t, updatedAt: t, updatedBy: this.deviceId };
  }

  restore<T extends SyncMeta>(item: T): T {
    return { ...item, deletedAt: null, updatedAt: this.next(), updatedBy: this.deviceId };
  }
}

/** Items in Deleted Items that are old enough to be removed for good. */
export function expiredDeletes<T extends SyncMeta>(items: Iterable<T>, now: Millis, keepDays: number): T[] {
  const cutoff = now - keepDays * 24 * 60 * 60 * 1000;
  const out: T[] = [];
  for (const it of items) if (it.deletedAt !== null && it.deletedAt < cutoff) out.push(it);
  return out;
}

/** Firestore paths. */
export const paths = {
  user: (uid: string) => `users/${uid}`,
  taskLists: (uid: string) => `users/${uid}/taskLists`,
  subLists: (uid: string) => `users/${uid}/subLists`,
  tasks: (uid: string) => `users/${uid}/tasks`,
  subTasks: (uid: string) => `users/${uid}/subTasks`,
  tags: (uid: string) => `users/${uid}/tags`,
  filteredLists: (uid: string) => `users/${uid}/filteredLists`,
  syncedSettings: (uid: string) => `users/${uid}/settings/synced`,
} as const;

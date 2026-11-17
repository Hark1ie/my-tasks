/**
 * Settings: the same list as the Android app. Synced ones come from the
 * account's settings document and are written back to it; device ones stay in
 * this computer's storage. Starts from the owner's preset (Black theme).
 */
import { type Settings, SETTINGS, applySynced, ownerPreset, syncedPart } from "@core";
import type { Store } from "./store";

const KEY = "mytasks.settings";

function loadLocal(): Settings {
  const base = ownerPreset();
  try {
    const raw = localStorage.getItem(KEY);
    if (raw) {
      const saved = JSON.parse(raw) as Record<string, unknown>;
      for (const k of Object.keys(SETTINGS) as (keyof Settings)[]) {
        const v = saved[k];
        const d = SETTINGS[k].default;
        if (v !== undefined && typeof v === typeof d) (base as Record<string, unknown>)[k] = v;
      }
    }
  } catch {
    // Storage unavailable or damaged: keep the preset.
  }
  return base;
}

export class SettingsModel {
  private value: Settings = loadLocal();
  private listeners = new Set<() => void>();
  private store: Store | null = null;
  private lastCloud: unknown = null;

  get = (): Settings => this.value;

  subscribe = (fn: () => void) => {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  };

  private emit() {
    try {
      localStorage.setItem(KEY, JSON.stringify(this.value));
    } catch {
      // Not fatal: the settings still apply for this session.
    }
    this.listeners.forEach((fn) => fn());
  }

  /** Follows the signed-in account's synced settings. */
  attach(store: Store | null) {
    this.store = store;
    this.lastCloud = null;
    if (store) {
      const apply = () => {
        const cloud = store.getState().cloudSettings;
        if (cloud && cloud !== this.lastCloud) {
          this.lastCloud = cloud;
          this.value = applySynced(this.value, cloud);
          this.emit();
        }
      };
      apply();
      store.subscribe(apply);
    }
  }

  set<K extends keyof Settings>(key: K, v: Settings[K]) {
    this.value = { ...this.value, [key]: v };
    this.emit();
    if (this.store && SETTINGS[key].scope === "synced") {
      this.store.putSettings(syncedPart(this.value) as Record<string, unknown>);
    }
  }
}

export const settings = new SettingsModel();

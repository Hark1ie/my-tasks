import { describe, expect, it } from "vitest";
import { SETTINGS, Theme, applySynced, defaultSettings, keysWithScope, ownerPreset, syncedPart } from "../src/index.js";

describe("settings", () => {
  it("uses the reference app's defaults", () => {
    const d = defaultSettings();
    expect(d.theme).toBe(Theme.Light);
    expect(d.subListHeading).toBe(1);
    expect(d.menuListInfo).toBe(1);
    expect(d.insertNewTasks).toBe(0);
    expect(d.insertNewSubTasks).toBe(1);
    expect(d.removeDeletedItemsDays).toBe(7);
    expect(d.taskPadding).toBe(10);
    expect(d.customSnoozeMinutes).toBe(720);
    expect(d.swipeToDelete).toBe(true);
    expect(d.showQuickAdd).toBe(true);
    expect(d.moveCompletedToBottom).toBe(false);
    expect(d.strikethroughCompleted).toBe(true);
    expect(d.subTasksCompleteTask).toBe(true);
    expect(d.saveRecurringHistory).toBe(false);
  });

  it("starts the owner on the Black theme and leaves everything else at default", () => {
    const preset = ownerPreset();
    const d = defaultSettings();
    const differing = (Object.keys(d) as (keyof typeof d)[]).filter((k) => preset[k] !== d[k]);
    expect(differing).toEqual(["theme"]);
    expect(preset.theme).toBe(Theme.Black);
  });

  it("never sends device-only settings to the cloud", () => {
    const cloud = syncedPart(defaultSettings());
    for (const k of keysWithScope("device")) expect(k in cloud).toBe(false);
    for (const k of ["theme", "notificationSound", "alarmSound", "keepScreenOn", "alwaysOnSync"]) {
      expect(k in cloud).toBe(false);
    }
    expect(Object.keys(cloud).sort()).toEqual(keysWithScope("synced").sort());
  });

  it("applies cloud settings without touching device-only ones or accepting bad values", () => {
    const local = { ...defaultSettings(), theme: Theme.Black };
    const out = applySynced(local, {
      moveCompletedToBottom: true,
      removeDeletedItemsDays: 14,
      defaultList: "continue",
      theme: Theme.Light, // device-only: ignored
      swipeToDelete: "yes", // wrong type: ignored
      unknownKey: 1,
    });
    expect(out.moveCompletedToBottom).toBe(true);
    expect(out.removeDeletedItemsDays).toBe(14);
    expect(out.defaultList).toBe("continue");
    expect(out.theme).toBe(Theme.Black);
    expect(out.swipeToDelete).toBe(true);
    expect("unknownKey" in out).toBe(false);
  });

  it("has a unique reference key for every setting taken from the reference app", () => {
    const refs = Object.values(SETTINGS).map((s) => s.ref).filter((r): r is string => r !== null);
    expect(new Set(refs).size).toBe(refs.length);
    expect(refs.length).toBe(Object.keys(SETTINGS).length - 1);
  });
});

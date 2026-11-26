#!/usr/bin/env python3
"""Drives My Tasks and the reference app on a test phone and saves screenshots.

Runs inside the emulator job. Every step is best-effort: a step that fails is
logged and the run carries on, so one surprise dialog does not lose the rest.
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

OUT = "out/screens"
DISMISS = {"ok", "got it", "continue", "no thanks", "skip", "later", "accept", "close", "not now", "allow", "next", "done", "agree", "i agree", "cancel"}


def adb(*args, timeout=90):
    return subprocess.run(["adb", *args], capture_output=True, timeout=timeout)


def shell(cmd, timeout=90):
    return adb("shell", cmd, timeout=timeout).stdout.decode("utf-8", "replace")


class App:
    def __init__(self, key, package, apk, ids):
        self.key, self.package, self.apk, self.ids = key, package, apk, ids
        self.dir = os.path.join(OUT, key)
        os.makedirs(self.dir, exist_ok=True)
        self.n = 0
        self.log_file = open(os.path.join(self.dir, "log.txt"), "w")

    def log(self, *parts):
        line = " ".join(str(p) for p in parts)
        print(f"[{self.key}] {line}", flush=True)
        self.log_file.write(line + "\n")
        self.log_file.flush()

    def shot(self, name):
        self.settle()
        self.n += 1
        base = os.path.join(self.dir, f"{self.n:02d}_{name}")
        png = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
        with open(base + ".png", "wb") as f:
            f.write(png)
        nodes = self.dump(base + ".xml")
        self.log("shot", os.path.basename(base), f"({len(nodes)} nodes)")
        return nodes

    def settle(self):
        """The test phone is slow; answer any "isn't responding" prompt with Wait."""
        for _ in range(8):
            raw = self._raw_dump()
            if b"isn't responding" not in raw and b"keeps stopping" not in raw:
                return
            nodes = self._parse(raw)
            wait = next((n for n in nodes if n["text"].strip().lower() == "wait"), None)
            if wait is None:
                wait = next((n for n in nodes if n["text"].strip().lower() in ("close app", "ok")), None)
            if wait is not None:
                self.log("system prompt, tapping", repr(wait["text"]))
                self.tap(wait, 4)
            else:
                time.sleep(4)

    def _raw_dump(self):
        shell("uiautomator dump /sdcard/ui.xml")
        return subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True, timeout=60).stdout

    def _parse(self, raw):
        nodes = []
        try:
            for el in ET.fromstring(raw.decode("utf-8", "replace")).iter("node"):
                m = re.findall(r"\d+", el.get("bounds", ""))
                if len(m) != 4:
                    continue
                nodes.append({
                    "text": el.get("text", ""), "desc": el.get("content-desc", ""), "rid": el.get("resource-id", ""),
                    "cls": el.get("class", ""), "clickable": el.get("clickable") == "true", "pkg": el.get("package", ""),
                    "b": tuple(int(x) for x in m),
                })
        except ET.ParseError as e:
            self.log("dump parse error", e)
        return nodes

    def dump(self, save=None):
        self.settle()
        shell("uiautomator dump /sdcard/ui.xml")
        raw = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True, timeout=60).stdout
        if save:
            with open(save, "wb") as f:
                f.write(raw)
        nodes = []
        try:
            for el in ET.fromstring(raw.decode("utf-8", "replace")).iter("node"):
                m = re.findall(r"\d+", el.get("bounds", ""))
                if len(m) != 4:
                    continue
                nodes.append({
                    "text": el.get("text", ""), "desc": el.get("content-desc", ""), "rid": el.get("resource-id", ""),
                    "cls": el.get("class", ""), "clickable": el.get("clickable") == "true", "pkg": el.get("package", ""),
                    "b": tuple(int(x) for x in m),
                })
        except ET.ParseError as e:
            self.log("dump parse error", e)
        return nodes

    @staticmethod
    def center(node):
        x1, y1, x2, y2 = node["b"]
        return (x1 + x2) // 2, (y1 + y2) // 2

    def find(self, nodes, text=None, contains=None, desc=None, rid=None, cls=None, pick="first"):
        hits = []
        for n in nodes:
            if text is not None and n["text"].strip().lower() != text.lower():
                continue
            if contains is not None and contains.lower() not in n["text"].lower():
                continue
            if desc is not None and n["desc"].strip().lower() != desc.lower():
                continue
            if rid is not None and not n["rid"].endswith("id/" + rid):
                continue
            if cls is not None and not n["cls"].endswith(cls):
                continue
            hits.append(n)
        if not hits:
            return None
        if pick == "lowest":
            return max(hits, key=lambda n: n["b"][1])
        if pick == "highest":
            return min(hits, key=lambda n: n["b"][1])
        return hits[0]

    def tap(self, node, wait=1.5):
        x, y = self.center(node)
        shell(f"input tap {x} {y}")
        time.sleep(wait)

    def tap_first(self, nodes, options, wait=1.5):
        """options: list of keyword dicts for find(); taps the first that matches."""
        for opt in options:
            node = self.find(nodes, **opt)
            if node:
                self.log("tap", opt)
                self.tap(node, wait)
                return True
        self.log("nothing to tap among", options)
        return False

    def type(self, text):
        shell("input text '" + text.replace(" ", "%s") + "'")
        time.sleep(0.8)

    def back(self, wait=1.2):
        shell("input keyevent 4")
        time.sleep(wait)

    def swipe_up(self):
        shell("input swipe 540 1700 540 700 300")
        time.sleep(1.0)

    def step(self, name, fn):
        try:
            self.log("--", name)
            fn()
        except Exception as e:  # keep going whatever happens
            self.log("step failed:", name, repr(e))

    # ---- scenario ----

    def install(self):
        # Show the on-screen keyboard even though the emulator reports a hardware one.
        shell("settings put secure show_ime_with_hard_keyboard 1")
        r = adb("install", "-r", "-g", self.apk, timeout=300)
        out = (r.stdout + r.stderr).decode("utf-8", "replace").strip()
        self.log("install:", out[-300:])
        return "Success" in out

    def launch(self):
        self.settle()
        shell(f"monkey -p {self.package} -c android.intent.category.LAUNCHER 1")
        time.sleep(12)
        self.shot("launch")

    def dismiss_dialogs(self):
        for _ in range(6):
            nodes = self.dump()
            hit = next((n for n in nodes if n["text"].strip().lower() in DISMISS and n["pkg"] != "com.android.systemui"), None)
            if not hit:
                break
            self.log("dismiss", repr(hit["text"]))
            self.tap(hit, 2)
            self.shot("after_dismiss")
        focus = shell("dumpsys window | grep -E 'mCurrentFocus'")
        self.log("focus:", focus.strip())

    def add_tasks(self):
        for title in ["Tut sheet 6", "Triple integral lec", "Triple Integral finish"]:
            nodes = self.dump()
            if not self.tap_first(nodes, self.ids["quick_add_field"]):
                return
            self.type(title)
            nodes = self.dump()
            self.tap_first(nodes, self.ids["quick_add_send"])
        shell("input keyevent 111")
        time.sleep(1)
        self.shot("tasks_added")

    def drawer(self):
        nodes = self.dump()
        if self.tap_first(nodes, self.ids["menu"]):
            self.shot("drawer")

    def settings(self):
        nodes = self.dump()
        if not self.tap_first(nodes, [{"text": "Settings"}], wait=2.5):
            return
        self.shot("settings")
        for i in range(9):
            self.swipe_up()
            self.shot(f"settings_scroll{i + 1}")
        self.back()

    def list_dialog(self):
        nodes = self.dump()
        if self.tap_first(nodes, self.ids["list_title"], wait=2):
            self.shot("list_name_dialog")
            self.tap_first(self.dump(), [{"text": "CANCEL"}], wait=2)

    def sub_list_dialog(self):
        nodes = self.dump()
        if self.tap_first(nodes, self.ids["tab"], wait=2):
            self.shot("sub_list_dialog")
            self.tap_first(self.dump(), [{"text": "CANCEL"}], wait=2)

    def overflow(self):
        nodes = self.dump()
        if self.tap_first(nodes, [{"desc": "More options"}], wait=1.5):
            self.shot("overflow_menu")
            self.back()

    def tick(self):
        nodes = self.dump()
        if self.tap_first(nodes, [{"desc": "Mark as done", "pick": "lowest"}]):
            self.shot("task_ticked")

    def long_press_menu(self):
        def press():
            node = self.find(self.dump(), desc="Mark as done", pick="highest")
            if not node:
                self.log("no task to long-press")
                return False
            x, y = self.center(node)
            shell(f"input swipe {x + 300} {y} {x + 300} {y} 900")
            time.sleep(1.5)
            return True
        if not press():
            return
        self.shot("task_long_press_menu")
        if self.tap_first(self.dump(), [{"text": "Highlight"}]):
            self.shot("task_highlight_dialog")
            self.back()
        if press() and self.tap_first(self.dump(), [{"text": "Duplicate task"}]):
            self.shot("task_duplicated")
        if press() and self.tap_first(self.dump(), [{"text": "Delete task"}], wait=0.8):
            self.shot("task_deleted_from_menu")

    def new_sub_list(self):
        nodes = self.dump()
        if not self.tap_first(nodes, [{"desc": "New Sub List"}], wait=2):
            return
        self.shot("new_sub_list_keyboard")
        nodes = self.dump()
        if self.tap_first(nodes, [{"cls": "EditText", "pick": "highest"}]):
            self.type("Tuesday 17th")
        shell("input keyevent 111")
        time.sleep(1)
        self.shot("new_sub_list_dialog")
        self.tap_first(self.dump(), [{"text": "SAVE"}], wait=2)
        self.shot("two_tabs")
        self.tap_first(self.dump(), [{"contains": "SUB LIST 1"}], wait=2)
        shell("input keyevent 111")
        time.sleep(1)

    def sort_dialog(self):
        if self.tap_first(self.dump(), [{"desc": "More options"}]) and self.tap_first(self.dump(), [{"text": "Sort"}], wait=2):
            self.shot("sort_dialog")
            self.back()

    def new_task(self):
        nodes = self.dump()
        if not self.tap_first(nodes, self.ids["fab"], wait=2.5):
            return
        # The keyboard should already be up, with no tap on the name field.
        ime = shell("dumpsys input_method | grep -E 'mInputShown|mIsInputViewShown|mServedView'")
        self.log("keyboard after add:", ime.strip().replace("\n", " | ") if isinstance(ime, str) else ime)
        self.shot("task_screen_keyboard")
        self.type("Read chapter 4")
        shell("input keyevent 111")
        time.sleep(1)
        self.shot("task_screen")
        self.swipe_up()
        self.shot("task_screen_scroll1")
        self.swipe_up()
        self.shot("task_screen_scroll2")
        self.back(2)
        self.shot("task_screen_back_prompt")
        self.tap_first(self.dump(), [{"text": "SAVE"}, {"desc": "Save"}], wait=2)
        shell("input keyevent 111")
        time.sleep(1)
        self.shot("after_new_task")

    def open_task(self):
        nodes = self.dump()
        if not self.tap_first(nodes, [{"text": "Triple integral lec"}], wait=2.5):
            return
        self.shot("existing_task")
        if self.tap_first(self.dump(), [{"text": "No date set"}], wait=2.5):
            self.shot("date_picker")
            self.tap_first(self.dump(), [{"text": "OK"}], wait=2)
            self.shot("task_with_date")
            if self.tap_first(self.dump(), [{"text": "Don't remind me"}], wait=2):
                self.shot("reminder_dialog")
                self.tap_first(self.dump(), [{"text": "Remind me when due"}], wait=2)
            if self.tap_first(self.dump(), [{"text": "Does not repeat"}], wait=2):
                self.shot("repeat_dialog")
                self.tap_first(self.dump(), [{"text": "Daily"}], wait=2)
            self.shot("task_with_reminder_repeat")
        self.tap_first(self.dump(), [{"desc": "Save"}], wait=2.5)
        self.shot("after_saving_task")

    def widget_preview(self):
        if self.key != "mine":
            return
        # Expand the first task's sub-tasks? Keep data as the walk-through left it.
        shell(f"am start -n {self.package}/.WidgetPreviewActivity")
        time.sleep(5)
        self.shot("widgets_preview")
        # Tick the first task straight from the widget, then draw the widgets again.
        if self.tap_first(self.dump(), [{"desc": "Mark as done", "pick": "highest"}], wait=4):
            self.back()
            shell(f"am start -n {self.package}/.WidgetPreviewActivity")
            time.sleep(5)
            self.shot("widgets_after_tick")
        self.back()
        # The widget settings dialog, opened the way the gear button opens it.
        shell(f"am start -n {self.package}/.widgets.WidgetConfigActivity --ei appWidgetId 7 --es app.mytasks.widget.MODE settings")
        time.sleep(4)
        self.shot("widget_settings")
        self.back()
        shell(f"am start -n {self.package}/.widgets.WidgetConfigActivity --ei appWidgetId 7 --es app.mytasks.widget.MODE choose")
        time.sleep(4)
        self.shot("widget_change_to")
        self.back()

    def run(self):
        if not os.path.exists(self.apk):
            self.log("APK not found:", self.apk)
            return
        if not self.install():
            return
        self.step("launch", self.launch)
        self.step("dismiss dialogs", self.dismiss_dialogs)
        self.step("main", lambda: self.shot("main"))
        self.step("add tasks", self.add_tasks)
        self.step("open task", self.open_task)
        self.step("tick", self.tick)
        self.step("long press menu", self.long_press_menu)
        self.step("new task", self.new_task)
        self.step("new sub list", self.new_sub_list)
        self.step("list dialog", self.list_dialog)
        self.step("sub list dialog", self.sub_list_dialog)
        self.step("overflow", self.overflow)
        self.step("sort dialog", self.sort_dialog)
        self.step("main again", lambda: self.shot("main_again"))
        self.step("drawer", self.drawer)
        self.step("settings", self.settings)
        self.step("widget preview", self.widget_preview)
        self.step("final", lambda: self.shot("final"))
        crash = shell(f"logcat -d -b crash | tail -n 60")
        with open(os.path.join(self.dir, "crash.txt"), "w") as f:
            f.write(crash)
        shell(f"am force-stop {self.package}")


MINE = App("mine", "app.mytasks", "android/app/build/outputs/apk/debug/app-debug.apk", {
    "quick_add_field": [{"cls": "EditText", "pick": "lowest"}],
    "quick_add_send": [{"desc": "Add task", "pick": "lowest"}],
    "menu": [{"desc": "Menu"}],
    "list_title": [{"text": "To do"}],
    "tab": [{"contains": "SUB LIST 1"}],
    "fab": [{"desc": "Add task", "pick": "highest"}],
})

REFERENCE = App("reference", "com.tasks.android", "reference/Tasks.apk", {
    "quick_add_field": [{"rid": "quick_add_text"}, {"contains": "Quick add task"}],
    "quick_add_send": [{"rid": "quick_add"}],
    "menu": [{"desc": "Open navigation drawer"}, {"desc": "Navigate up"}, {"desc": "Open"}],
    "list_title": [{"rid": "toolbar_title"}, {"text": "To do"}, {"text": "My Tasks"}, {"text": "Tasks"}],
    "tab": [{"rid": "tab_title"}],
    "fab": [{"rid": "action_add_task"}],
})

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    shell("settings put global window_animation_scale 0; settings put global transition_animation_scale 0; settings put global animator_duration_scale 0")
    time.sleep(25)  # let the freshly started phone finish its own start-up work
    # The reference app only starts when installed from the Play Store, so it is not run by default.
    which = sys.argv[1:] or ["mine"]
    for app in (MINE, REFERENCE):
        if app.key in which:
            app.run()
    print("done", flush=True)

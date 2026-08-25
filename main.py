from tkinter import Canvas, PhotoImage, Button, Entry, filedialog, StringVar, DoubleVar
import tkinter as tk
from tkinter import ttk
from tkinter.messagebox import showerror, showinfo, askquestion
import os
import shutil
import subprocess
import minecraft_launcher_lib
from minecraft_launcher_lib import command, exceptions, install, mod_loader, quilt, utils
from minecraft_launcher_lib.forge import install_forge_version, run_forge_installer, supports_automatic_install
from minecraft_launcher_lib.fabric import install_fabric, get_stable_minecraft_versions, get_latest_loader_version
import uuid
import platform
import json
import re
import queue
import socket
import time
import sys
from threading import Thread
import requests
import psutil
from tkinter import simpledialog
import ssl
from requests.adapters import HTTPAdapter
try:
    from urllib3.util.retry import Retry
except Exception:
    Retry = None

class _TLSAdapter(HTTPAdapter):
    def __init__(self, ssl_ctx=None, **kw):
        self._ssl_ctx = ssl_ctx
        super().__init__(**kw)

    def init_poolmanager(self, *args, **kwargs):
        if self._ssl_ctx is not None:
            kwargs["ssl_context"] = self._ssl_ctx
        return super().init_poolmanager(*args, **kwargs)

def _build_session():
                                                                                    
    s = requests.Session()
    ctx = ssl.create_default_context()
    try:
        ctx.set_ciphers("DEFAULT:@SECLEVEL=1")
    except Exception:
        pass
    try:
        ctx.maximum_version = ssl.TLSVersion.TLSv1_2
    except Exception:
        pass
    try:
        ctx.options |= ssl.OP_LEGACY_SERVER_CONNECT
    except Exception:
        pass
    kw = {}
    if Retry is not None:
        kw["max_retries"] = Retry(
            total=4, connect=4, read=2, backoff_factor=1.5,
            status_forcelist=(429, 500, 502, 503, 504),
            allowed_methods=None
        )
    adapter = _TLSAdapter(ssl_ctx=ctx, **kw)
    s.mount("https://", adapter)
    s.mount("http://", HTTPAdapter())
    return s

SESSION = _build_session()

                                                                    
try:
    import minecraft_launcher_lib._helper as _mll_helper
    _mll_helper.requests = SESSION
except Exception:
    pass

                                                                                   
def _patch_installer_errors():
    try:
        import types
        import importlib
        orig_run = subprocess.run

        def run(cmd, **kw):
            try:
                return orig_run(cmd, **kw)
            except subprocess.CalledProcessError as e:
                parts = []
                for stream in (e.stdout, e.stderr):
                    if stream:
                        parts.append(stream.decode(errors="replace") if isinstance(stream, bytes) else str(stream))
                tail = "\n".join(parts).strip()[-700:]
                raise Exception(f"Installer exited with code {e.returncode}. Output:\n{tail or '(no output)'}") from None

        shim = types.SimpleNamespace(run=run, STARTUPINFO=getattr(subprocess, "STARTUPINFO", None))
        for name in ("_neoforge", "_quilt", "_forge"):
            try:
                mod = importlib.import_module(f"minecraft_launcher_lib.mod_loader.{name}")
                mod.subprocess = shim
            except Exception:
                pass
    except Exception:
        pass

_patch_installer_errors()

                                                                            
                                                                                 
def _patch_lib_downloads():
    try:
        import importlib
        import shutil as _shutil
        import lzma as _lzma
        import minecraft_launcher_lib._helper as _h
        import minecraft_launcher_lib.exceptions as _mlex

        def download_file(url, path, callback={}, sha1=None, lzma_compressed=False,
                          session=None, minecraft_directory=None, overwrite=False):
            if minecraft_directory is not None:
                _h.check_path_inside_minecraft_directory(minecraft_directory, path)
            if os.path.isfile(path) and not overwrite:
                if sha1 is None:
                    return False
                if _h.get_sha1_hash(path) == sha1:
                    return False
            try:
                os.makedirs(os.path.dirname(path))
            except Exception:
                pass
            callback.get("setStatus", _h.empty)("Download " + os.path.basename(path))

            sess = session if session is not None else SESSION
            part_path = path + ".spark-part"
            max_attempts = 8
            last_err = None
            for attempt in range(1, max_attempts + 1):
                try:
                    headers = {"user-agent": _h.get_user_agent()}
                    mode = "wb"
                    if attempt > 1 and os.path.isfile(part_path) and not lzma_compressed:
                        headers["Range"] = f"bytes={os.path.getsize(part_path)}-"
                        mode = "ab"
                    with sess.get(url, stream=True, headers=headers, timeout=(15, 45)) as r:
                        if r.status_code == 416:
                            try:
                                os.remove(part_path)
                            except Exception:
                                pass
                            last_err = Exception("range not satisfiable, restarting")
                            continue
                        r.raise_for_status()
                        if attempt > 1 and r.status_code != 206 and mode == "ab":
                            mode = "wb"                                        
                        with open(part_path, mode) as f:
                            if lzma_compressed:
                                f.write(_lzma.decompress(r.content))
                            else:
                                r.raw.decode_content = True
                                _shutil.copyfileobj(r.raw, f)
                    if sha1 is not None:
                        checksum = _h.get_sha1_hash(part_path)
                        if checksum != sha1:
                            try:
                                os.remove(part_path)
                            except Exception:
                                pass
                            last_err = _mlex.InvalidChecksum(url, path, sha1, checksum)
                            continue
                    _shutil.move(part_path, path)
                    return True
                except _mlex.InvalidChecksum:
                    raise
                except Exception as e:
                    last_err = e
                    time.sleep(min(2 * attempt, 8))
            if isinstance(last_err, _mlex.InvalidChecksum):
                raise last_err
            raise last_err if last_err else Exception(f"Download failed: {url}")

        _h.download_file = download_file
        for mod_name in ("install", "forge", "fabric", "quilt", "runtime", "mrpack",
                         "mod_loader._neoforge", "mod_loader._quilt",
                         "mod_loader._forge", "mod_loader._fabric"):
            try:
                mod = importlib.import_module(f"minecraft_launcher_lib.{mod_name}")
                if hasattr(mod, "download_file"):
                    mod.download_file = download_file
            except Exception:
                pass
    except Exception:
        pass

_patch_lib_downloads()

try:
    from ttkbootstrap import Style
    HAS_BOOTSTRAP = True
except Exception:
    HAS_BOOTSTRAP = False

if HAS_BOOTSTRAP:
    style = Style(theme="darkly")
else:
    style = None

def get_size(bytes, suffix="B"):
    factor = 1024
    for unit in ["", "K", "M", "G", "T", "P"]:
        if bytes < factor:
            return f"{bytes:.2f}{unit}{suffix}"
        bytes /= factor

svmem = psutil.virtual_memory()
TOTAL_RAM_MB = max(512, int(svmem.total / (1024 * 1024)))
LOW_END = TOTAL_RAM_MB <= 4096 or (os.cpu_count() or 1) <= 2
currn_dir = os.getcwd()
mc_dir = os.path.join(currn_dir, ".minecraft")

default_ram = 1024 if LOW_END else min(2048, TOTAL_RAM_MB)

default_settings = {
    "accessToken": None,
    "clientToken": None,
    "User-info": [{"username": None, "AUTH_TYPE": None, "UUID": None}],
    "PC-info": [{"OS": platform.platform(), "Total-Ram": f"{get_size(svmem.total)}"}],
    "Minecraft-home": mc_dir,
    "Fps-Boost": False,
    "Light-Mode": True if LOW_END else False,
    "setting-info": [{"fps_boost_selected": False, "allocated_ram_selected": default_ram}],
    "allocated_ram": default_ram,
    "jvm-args": None,
    "executablePath": "java",
    "last_game_type": "Vanilla",
    "last_version": None
}

if not os.path.exists("settings.json"):
    with open("settings.json", "w") as f:
        json.dump(default_settings, f, indent=4)

def load_settings():
    with open("settings.json", "r") as f:
        s = f.read().replace("\t", "").replace("\n", "").replace(",}", "}").replace(",]", "]")
        return json.loads(s)

def save_settings(data):
    with open("settings.json", "w") as f:
        json.dump(data, f, indent=4)

def format_size(num_bytes):
    if num_bytes is None or num_bytes < 0:
        return "Unknown"
    num_bytes = float(num_bytes)
    if num_bytes >= 1024 ** 3:
        return f"{num_bytes / (1024 ** 3):.2f} GB"
    if num_bytes >= 1024 ** 2:
        return f"{num_bytes / (1024 ** 2):.2f} MB"
    if num_bytes >= 1024:
        return f"{num_bytes / 1024:.2f} KB"
    return f"{int(num_bytes)} B"

def get_vanilla_download_size(version_id):
    try:
        manifest = SESSION.get(
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json",
            timeout=15
        ).json()
        url = None
        for v in manifest.get("versions", []):
            if v.get("id") == version_id:
                url = v.get("url")
                break
        if not url:
            return None
        info = SESSION.get(url, timeout=15).json()
        total = 0
        client = info.get("downloads", {}).get("client", {})
        total += client.get("size") or 0
        for lib in info.get("libraries", []):
            downloads = lib.get("downloads", {})
            if "artifact" in downloads:
                total += downloads["artifact"].get("size") or 0
            for classifier in downloads.get("classifiers", {}).values():
                total += classifier.get("size") or 0
        asset_index = info.get("assetIndex", {})
        total += asset_index.get("totalSize") or 0
        return total
    except Exception:
        return None

data = load_settings()
_user_info = data.get("User-info") or [{}]
if not isinstance(_user_info[0], dict):
    _user_info = [{}]
username = _user_info[0].get("username") or ""
uid = _user_info[0].get("UUID")
mc_dir = data.get("Minecraft-home") or mc_dir

if not os.path.exists(mc_dir):
    try:
        os.makedirs(os.path.join(mc_dir, "versions"), exist_ok=True)
    except Exception:
        pass

class RoundedFrame(tk.Canvas):
                                                                    

    def __init__(self, parent, bg, border, radius=14, surround=None, **kw):
        try:
            s_bg = surround if surround is not None else parent.cget("bg")
        except Exception:
            s_bg = bg
        super().__init__(parent, bg=s_bg, highlightthickness=0, bd=0)
        self.card_bg = bg
        self.card_border = border
        self.radius = radius
        self.surround = s_bg
        self.bind("<Configure>", lambda e: self._paint())
        self._paint()

    def _rr(self, x1, y1, x2, y2, r, **kw):
        r = max(2, min(r, (x2 - x1) // 2, (y2 - y1) // 2))
        pts = [x1 + r, y1, x2 - r, y1, x2, y1, x2, y1 + r, x2, y2 - r, x2, y2,
               x2 - r, y2, x1 + r, y2, x1, y2, x1, y2 - r, x1, y1 + r, x1, y1]
        return self.create_polygon(pts, smooth=True, **kw)

    def _paint(self):
        self.delete("all")
        w, h = self.winfo_width(), self.winfo_height()
        if w < 8 or h < 8:
            self.configure(bg=self.card_bg)
            return
        self.configure(bg=self.surround)
        self._rr(1, 1, w - 2, h - 2, self.radius, fill=self.card_bg, outline=self.card_border)

class RoundButton(tk.Canvas):
                                                                                     

    def __init__(self, parent, launcher, text, command, bg, hover, fg="#ffffff",
                 base=10, weight=None, radius=12, surround=None, w=None, h=None):
        try:
            s_bg = surround if surround is not None else parent.cget("bg")
        except Exception:
            s_bg = launcher.card
        super().__init__(parent, bg=s_bg, highlightthickness=0, bd=0, cursor="hand2")
        self.launcher = launcher
        self.command = command
        self.bg = bg
        self.hover = hover
        self.fg = fg
        self.base = base
        self.weight = weight
        self.radius = radius
        self.surround = s_bg
        self.text = text
        self._cur = bg
        if w and h:
            self.configure(width=w, height=h)
        else:
            est_w = int(len(text) * max(7, base) * 0.68) + 30
            est_h = int(max(7, base) * 1.9) + 12
            self.configure(width=est_w, height=est_h)
        self.bind("<Configure>", lambda e: self._paint(self._cur))
        self.bind("<Enter>", lambda e: self._animate(self.hover))
        self.bind("<Leave>", lambda e: self._animate(self.bg))
        self.bind("<ButtonPress-1>", lambda e: self._paint(self._mix(self.hover)))
        self.bind("<ButtonRelease-1>", self._release)
        self._cur = bg
        self._anim_job = None
        self._paint(bg)

    def _animate(self, target):
        self._anim_target = target
        if getattr(self, "_anim_job", None):
            try:
                self.after_cancel(self._anim_job)
            except Exception:
                pass
        self._anim_step()

    def _anim_step(self):
        if self._cur == self._anim_target:
            self._anim_job = None
            return
        self._paint(self._blend_hex(self._cur, self._anim_target, 0.4))
        self._anim_job = self.after(24, self._anim_step)

    @staticmethod
    def _blend_hex(c1, c2, t):
        c1, c2 = str(c1).lstrip("#"), str(c2).lstrip("#")
        try:
            a = [int(c1[i:i + 2], 16) for i in (0, 2, 4)]
            b = [int(c2[i:i + 2], 16) for i in (0, 2, 4)]
            return "#{:02x}{:02x}{:02x}".format(*(int(x + (y - x) * t) for x, y in zip(a, b)))
        except Exception:
            return c2

    @staticmethod
    def _mix(color, f=0.8):
        color = str(color).lstrip("#")
        try:
            r, g, b = (int(color[i:i + 2], 16) for i in (0, 2, 4))
            return "#{:02x}{:02x}{:02x}".format(int(r * f), int(g * f), int(b * f))
        except Exception:
            return color

    def _rr(self, x1, y1, x2, y2, r, **kw):
        r = max(2, min(r, (x2 - x1) // 2, (y2 - y1) // 2))
        pts = [x1 + r, y1, x2 - r, y1, x2, y1, x2, y1 + r, x2, y2 - r, x2, y2,
               x2 - r, y2, x1 + r, y2, x1, y2, x1, y2 - r, x1, y1 + r, x1, y1]
        return self.create_polygon(pts, smooth=True, **kw)

    def _paint(self, color):
        self._cur = color
        self.delete("all")
        w, h = self.winfo_width(), self.winfo_height()
        if w < 6 or h < 6:
            self.configure(bg=self.surround)
            return
        self.configure(bg=self.surround)
        self._rr(1, 1, w - 2, h - 2, self.radius, fill=color, outline="")
        size = max(7, int(self.base * self.launcher.sf))
        f = ("Segoe UI", size, self.weight) if self.weight else ("Segoe UI", size)
        self.create_text(w // 2, h // 2, text=self.text, fill=self.fg, font=f)

    def _release(self, e):
        self._animate(self.hover)
        if self.command:
            try:
                self.command()
            except Exception:
                pass

class Pycraft:
    def __init__(self):
        self.data = load_settings()
        _accs = [a for a in (self.data.get("User-info") or []) if isinstance(a, dict) and a.get("username")]
        self.username = (_accs[0].get("username") if _accs else "") or ""
        self.uid = _accs[0].get("UUID") if _accs else None
        self.mc_dir = self.data.get("Minecraft-home") or mc_dir
        self.light = bool(self.data.get("Light-Mode", LOW_END))
        self.versions_map = {}

        if HAS_BOOTSTRAP and style is not None:
            self.window = style.master
        else:
            self.window = tk.Tk()

        self.window.title("Spark-Launcher")
        try:
            self.window.iconbitmap("icon.ico")
        except Exception:
            pass

        self.window.update_idletasks()
        sw = self.window.winfo_screenwidth()
        sh = self.window.winfo_screenheight()

                                                                            
        margin_x, margin_y = 40, 60
        max_w = min(1100, sw - margin_x)
        max_h = min(650, sh - margin_y)
                                                    
        self.W = max(720, max_w) if sw >= 800 else max(640, sw - 20)
        self.H = max(480, max_h) if sh >= 560 else max(420, sh - 40)
        if sw <= 1280:
            self.W = min(self.W, sw - 20)
            self.H = min(self.H, sh - 40)
        self.W = min(self.W, sw - 10)
        self.H = min(self.H, sh - 30)

        self.sx = self.W / 1100.0
        self.sy = self.H / 650.0
        self.sf = min(self.sx, self.sy)

        self.bg = "#0e1013"
        self.fg = "#f2f5f9"
        self.muted = "#8a93a6"
        self.input_bg = "#12151b"
        self.border = "#242b38"
        self.card = "#161b23"
        self.row_hover = "#202733"
        self.accent = "#4f8cff"
        self.accent_hover = "#7aa5ff"
        self.green = "#22c55e"
        self.green_hover = "#4ade80"
        self.red = "#ef4444"
        self.red_hover = "#f87171"
        self.gray_btn = "#2b3342"
        self.gray_hover = "#3a4457"

        x = max(0, (sw - self.W) // 2)
        y = max(0, (sh - self.H) // 2)
        self.window.geometry(f"{self.W}x{self.H}+{x}+{y}")
        self.window.configure(bg=self.bg)
        self.window.minsize(min(640, self.W), min(420, self.H))
        self.window.resizable(True, True)

        self.console_visible = False
        self.console_width = 380
        self.console_queue = queue.Queue()
        self.console_lines = []
        self._dl_active = False
        self._dl_value = 0
        self._dl_max = 0
        self._mc_proc = None
        self._launch_time = 0.0
        self._last_dl_pct = -1
        self.active_tab = "client"
        self._keep_hidden = set()
        self._srv_proc = None
        self._srv_players = set()
        self._srv_q = queue.Queue()
        self._srv_selected = None
        self._srv_status = "offline"
        self._fm_path = None

                                                                                       
        self.window.overrideredirect(True)
        self._rz_handles = []
        self.window.after(60, self._post_win_setup)

        self._setup_styles()
        self._build_ui()
        self.window.after(150, self._console_pump)
        self.window.after(300, self._srv_pump)
        self.window.after(12000, self._slideshow_tick)
        self.window.mainloop()

    def _setup_styles(self):
        if HAS_BOOTSTRAP:
            return
        st = ttk.Style()
        try:
            st.theme_use("clam")
        except Exception:
            return
        st.configure(
            "TCombobox", fieldbackground=self.input_bg, background=self.gray_btn,
            foreground="white", arrowcolor="white", bordercolor=self.border,
            lightcolor=self.card, darkcolor=self.card, insertcolor="white",
            padding=5
        )
        st.map("TCombobox",
               fieldbackground=[("readonly", self.input_bg)],
               foreground=[("readonly", "white")],
               selectbackground=[("readonly", self.gray_btn)],
               selectforeground=[("readonly", "white")])
        st.configure(
            "TCheckbutton", background=self.card, foreground=self.fg,
            focuscolor=self.card, bordercolor=self.card, lightcolor=self.card,
            darkcolor=self.card, arrowcolor="white"
        )
        st.map("TCheckbutton",
               background=[("active", self.card)],
               foreground=[("active", self.fg)])
        st.configure(
            "Horizontal.TScale", background=self.card, troughcolor=self.input_bg,
            bordercolor=self.card, lightcolor=self.accent, darkcolor=self.accent
        )
        st.configure(
            "Horizontal.TProgressbar", background=self.accent, troughcolor=self.input_bg,
            bordercolor=self.card, lightcolor=self.accent, darkcolor=self.accent,
            thickness=14
        )
        for sb in ("Vertical.TScrollbar", "Horizontal.TScrollbar"):
            st.configure(sb, background=self.gray_btn, troughcolor=self.card,
                         bordercolor=self.card, arrowcolor=self.muted, relief="flat")
            st.map(sb, background=[("active", self.gray_hover)])
        st.configure(
            "Treeview", background=self.card, foreground=self.fg,
            fieldbackground=self.card, bordercolor=self.border, rowheight=26
        )
        st.map("Treeview", background=[("selected", self.accent)], foreground=[("selected", "white")])
        st.configure("Treeview.Heading", background=self.gray_btn, foreground=self.fg, relief="flat")
        self.window.option_add("*TCombobox*Listbox.background", self.input_bg)
        self.window.option_add("*TCombobox*Listbox.foreground", "white")
        self.window.option_add("*TCombobox*Listbox.selectBackground", self.accent)
        self.window.option_add("*TCombobox*Listbox.selectForeground", "white")
        self.window.option_add("*TCombobox*Listbox.font", ("Segoe UI", 10))

    @staticmethod
    def _add_hover(btn, hover_color):
        normal = btn.cget("bg")
        btn.bind("<Enter>", lambda e: btn.config(bg=hover_color))
        btn.bind("<Leave>", lambda e: btn.config(bg=normal))

                                         
    def _post_win_setup(self):
        if os.name != "nt":
            return
        try:
            import ctypes
            user32 = ctypes.windll.user32
            hwnd = user32.GetParent(self.window.winfo_id()) or self.window.winfo_id()
            self._hwnd = hwnd
                                            
            try:
                pref = ctypes.c_int(2)                
                ctypes.windll.dwmapi.DwmSetWindowAttribute(hwnd, 33, ctypes.byref(pref), 4)
                dark = ctypes.c_int(1)
                ctypes.windll.dwmapi.DwmSetWindowAttribute(hwnd, 20, ctypes.byref(dark), 4)
            except Exception:
                pass
                                                            
            try:
                GWL_EXSTYLE = -20
                style = user32.GetWindowLongW(hwnd, GWL_EXSTYLE)
                style = (style | 0x40000) & ~0x80                                
                user32.SetWindowLongW(hwnd, GWL_EXSTYLE, style)
                self.window.withdraw()
                self.window.after(30, self.window.deiconify)
            except Exception:
                pass
        except Exception:
            pass
        self._build_resize_handles()

    def _build_resize_handles(self):
        self._rz_dir = None
        H = self.header_frame
        specs = [
            ("n", dict(x=0, y=0, relwidth=1, height=5), "sb_v_double_arrow"),
            ("s", dict(x=0, rely=1.0, y=-5, relwidth=1, height=5), "sb_v_double_arrow"),
            ("w", dict(x=0, y=0, width=5, relheight=1), "sb_h_double_arrow"),
            ("e", dict(relx=1.0, x=-5, y=0, width=5, relheight=1), "sb_h_double_arrow"),
            ("nw", dict(x=0, y=0, width=12, height=12), "size_nw_se"),
            ("ne", dict(relx=1.0, x=-12, y=0, width=12, height=12), "size_ne_sw"),
            ("sw", dict(x=0, rely=1.0, y=-12, width=12, height=12), "size_ne_sw"),
            ("se", dict(relx=1.0, rely=1.0, x=-12, y=-12, width=12, height=12), "size_nw_se"),
        ]
        for d, place_kw, cur in specs:
            h = tk.Frame(self.window, bg=self.bg)
            h.place(**place_kw)
            h.configure(cursor=cur)
            h.bind("<Button-1>", lambda e, dd=d: self._rz_start(e, dd))
            h.bind("<B1-Motion>", self._rz_motion)
            self._rz_handles.append(h)
        for h in self._rz_handles:
            h.lift()
        H.lift()

    def _rz_start(self, e, d):
        self._rz_dir = d
        self._rz_geo = (self.window.winfo_width(), self.window.winfo_height())
        self._rz_ptr = (e.x_root, e.y_root)

    def _rz_motion(self, e):
        if not self._rz_dir:
            return
        try:
            min_w, min_h = self.window.minsize()
        except Exception:
            min_w, min_h = 640, 420
        w0, h0 = self._rz_geo
        dx = e.x_root - self._rz_ptr[0]
        dy = e.y_root - self._rz_ptr[1]
        w, h = w0, h0
        if "e" in self._rz_dir:
            w = max(min_w, w0 + dx)
        if "w" in self._rz_dir:
            w = max(min_w, w0 - dx)
        if "s" in self._rz_dir:
            h = max(min_h, h0 + dy)
        if "n" in self._rz_dir:
            h = max(min_h, h0 - dy)
        if w != self.window.winfo_width() or h != self.window.winfo_height():
            self.window.geometry(f"{int(w)}x{int(h)}")

    def _traffic_light(self, parent, x, color, hover, glyph, cmd):
        c = tk.Canvas(parent, width=13, height=13, bg=self.bg, highlightthickness=0, cursor="hand2")
        dot = c.create_oval(1, 1, 12, 12, fill=color, outline="")
        g = c.create_text(6, 6, text=glyph, fill="#5a201e", font=("Segoe UI", max(6, self._fs(8)), "bold"), state="hidden")
        c.bind("<Button-1>", lambda e: cmd())
        c.bind("<Enter>", lambda e: c.itemconfig(g, state="normal"))
        c.bind("<Leave>", lambda e: c.itemconfig(g, state="hidden"))
        c.place(x=x, y=16)
        return c

    def _toggle_max(self):
        try:
            if self.window.state() == "zoomed":
                self.window.state("normal")
            else:
                self.window.state("zoomed")
        except Exception:
            pass

    def _drag_start(self, e):
        if self.window.state() == "zoomed":
            self._drag_off = None
            return
        self._drag_off = (e.x_root - self.window.winfo_x(), e.y_root - self.window.winfo_y())

    def _drag_motion(self, e):
        if not getattr(self, "_drag_off", None) or self.window.state() == "zoomed":
            return
        self.window.geometry(f"+{e.x_root - self._drag_off[0]}+{e.y_root - self._drag_off[1]}")

    def _fs(self, base):
        return max(8, int(base * self.sf))

    def _x(self, v):
        return int(v * self.sx)

    def _y(self, v):
        return int(v * self.sy)

    def _update_colmap(self):
        if self.W < 900:
            self._colmap = {"col1": 0, "col2": 300, "col3": 300,
                            "fy": 220, "by": 312, "dly": 368, "sety": 418,
                            "pw": 202, "brx": 220, "bw": 300}
        else:
            self._colmap = {"col1": 0, "col2": 320, "col3": 640,
                            "fy": 64, "by": 156, "dly": 212, "sety": 262,
                            "pw": 222, "brx": 240, "bw": 320}

    def _resolve(self, v):
        return self._colmap[v] if isinstance(v, str) else v

    def _put(self, w, x, y, wd=None, ht=None, font=None):
                                                                                             
        self._layout_specs.append([w, x, y, wd, ht])
        kw = {"x": self._x(self._resolve(x)), "y": self._y(self._resolve(y))}
        if wd is not None:
            kw["width"] = self._x(self._resolve(wd))
        if ht is not None:
            kw["height"] = self._y(ht)
        w.place(**kw)
        if font:
            base, weight = font
            if isinstance(w, RoundButton):
                w.base = base
                w.weight = weight
            else:
                try:
                    w.config(font=("Segoe UI", self._fs(base), weight) if weight else ("Segoe UI", self._fs(base)))
                except Exception:
                    pass
                self._font_specs.append([w, base, weight])

    def _field_label(self, parent, text, x, y):
        bar = tk.Frame(parent, bg=self.accent, bd=0)
        self._put(bar, x, y + 2, 3, 11)
        lbl = tk.Label(parent, text=text.upper(), bg=self.card, fg=self.muted)
        self._put(lbl, x + 11, y, font=(8, None))

    def _entry(self, parent, **kw):
        return Entry(
            parent, bg=self.input_bg, fg="white", insertbackground="white",
            relief="flat", highlightthickness=1,
            highlightbackground=self.border, highlightcolor=self.accent, **kw
        )

    def _button(self, parent, text, command, bg, hover, font, w=None, h=None, radius=12, **kw):
        base, weight = 10, None
        try:
            base = font[1]
            if len(font) > 2:
                weight = font[2]
        except Exception:
            pass
        return RoundButton(parent, self, text, command, bg, hover, "#ffffff",
                           base=base, weight=weight, radius=radius, w=w, h=h)

    def _build_ui(self):
        self._layout_specs = []
        self._font_specs = []
        self._update_colmap()

        self.canvas = Canvas(self.window, bg=self.bg, bd=0, highlightthickness=0)
        self._put(self.canvas, 0, 0, 1100, 650)
        self._draw_background()

                                                                            
                                                            
        self.overlay = self.window

                                                                                                
        self.server_frame = tk.Frame(self.window, bg=self.bg)
        self._put(self.server_frame, 0, 0, 1100, 650)
        self._keep_hidden.add(self.server_frame)
        self.server_frame.place_forget()
        self._build_server_page()

                                                                           
        self._always_visible = set()
        self.header_frame = tk.Frame(self.window, bg=self.bg, bd=0, highlightthickness=0)
        self._put(self.header_frame, 0, 0, 1100, 48)
        self._always_visible.add(self.header_frame)
        self._always_visible.add(self.overlay)

                              
        self._traffic_light(self.header_frame, 14, "#ff5f57", "#ff8a80", "✕", self.window.destroy)
        self._traffic_light(self.header_frame, 34, "#febc2e", "#ffdd7a", "−", self.window.iconify)
        self._traffic_light(self.header_frame, 54, "#28c840", "#5ee07a", "⤢", self._toggle_max)

        f_label = ("Segoe UI", self._fs(10))
        f_btn = ("Segoe UI", self._fs(12), "bold")
        f_small = ("Segoe UI", self._fs(9))
        self.f_label = f_label
        self.f_btn = f_btn
        self.f_small = f_small
        self.f_entry = ("Segoe UI", self._fs(11))
        self.f_tiny = ("Segoe UI", max(7, self._fs(8)))
        self.f_title = ("Segoe UI", self._fs(20), "bold")

                
        title_lbl = tk.Label(self.header_frame, text="Spark-Launcher", bg=self.bg, fg=self.fg)
        self._put(title_lbl, 86, 2, font=(16, "bold"))
        self._always_visible.add(title_lbl)
        accent_bar = tk.Frame(self.header_frame, bg=self.accent, bd=0)
        self._put(accent_bar, 88, 30, 36, 3)
        self._always_visible.add(accent_bar)
        sub_lbl = tk.Label(self.header_frame, text="MINECRAFT LAUNCHER", bg=self.bg, fg=self.muted)
        self._put(sub_lbl, 132, 29, font=(7, None))
        self._always_visible.add(sub_lbl)
        for w in (self.header_frame, title_lbl, sub_lbl, accent_bar):
            w.bind("<Button-1>", self._drag_start)
            w.bind("<B1-Motion>", self._drag_motion)
        self.header_frame.bind("<Double-Button-1>", lambda e: self._toggle_max())

              
        self.tab_client = tk.Label(self.header_frame, text="CLIENT", bg=self.bg, fg=self.accent, cursor="hand2")
        self._put(self.tab_client, 880, 12, font=(11, "bold"))
        self._always_visible.add(self.tab_client)
        self.tab_server = tk.Label(self.header_frame, text="SERVER", bg=self.bg, fg=self.muted, cursor="hand2")
        self._put(self.tab_server, 985, 12, font=(11, "bold"))
        self._always_visible.add(self.tab_server)
        self.tab_line_c = tk.Frame(self.header_frame, bg=self.accent, bd=0)
        self._put(self.tab_line_c, 878, 34, 58, 3)
        self._always_visible.add(self.tab_line_c)
        self.tab_line_s = tk.Frame(self.header_frame, bg=self.accent, bd=0)
        self._put(self.tab_line_s, 983, 34, 62, 3)
        self.tab_line_s.place_forget()
        self.tab_client.bind("<Button-1>", lambda e: self._switch_tab("client"))
        self.tab_server.bind("<Button-1>", lambda e: self._switch_tab("server"))

                                
        acc_card = RoundedFrame(self.overlay, self.card, self.border, radius=18, surround=self.bg)
        self._put(acc_card, "col1", 64, 280, 356)
        self._field_label(acc_card, "Username", 14, 12)
        self.entry0 = self._entry(acc_card)
        self.entry0.insert(0, self.username)
        self._put(self.entry0, 14, 32, 252, 30, font=(11, None))
        self._field_label(acc_card, "Password", 14, 72)
        self.entry1 = self._entry(acc_card, show="•")
        self._put(self.entry1, 14, 92, 252, 30, font=(11, None))
        self._field_label(acc_card, "Account Type", 14, 132)
        self.selected_option = StringVar()
        self.acc_options = ttk.Combobox(
            acc_card, textvariable=self.selected_option,
            values=("mojang login", "cracked login", "ely_by login"), state="readonly"
        )
        self._put(self.acc_options, 14, 152, 252, font=(10, None))
        self.acc_options.current(0)
        self.accounts_count_lbl = tk.Label(acc_card, text="ACCOUNTS", bg=self.card, fg=self.muted)
        self._put(self.accounts_count_lbl, 14, 190, font=(8, None))
        self.accounts_frame = tk.Frame(
            acc_card, bg=self.input_bg, bd=0,
            highlightthickness=1, highlightbackground=self.border
        )
        self._put(self.accounts_frame, 14, 210, 252, 132)
        self.accounts_canvas = tk.Canvas(self.accounts_frame, bg=self.input_bg, bd=0, highlightthickness=0)
        acct_scroll = ttk.Scrollbar(self.accounts_frame, orient="vertical", command=self.accounts_canvas.yview)
        self.accounts_inner = tk.Frame(self.accounts_canvas, bg=self.input_bg)
        self.accounts_inner.bind(
            "<Configure>",
            lambda e: self.accounts_canvas.configure(scrollregion=self.accounts_canvas.bbox("all"))
        )
        self.accounts_canvas.create_window((0, 0), window=self.accounts_inner, anchor="nw", width=self._x(236))
        self.accounts_canvas.configure(yscrollcommand=acct_scroll.set)
        self.accounts_canvas.pack(side="left", fill="both", expand=True)
        acct_scroll.pack(side="right", fill="y")
        self.window.bind_all("<MouseWheel>", self._accounts_mousewheel, add="+")
        self._refresh_accounts()

                             
        game_card = RoundedFrame(self.overlay, self.card, self.border, radius=18, surround=self.bg)
        self._put(game_card, "col2", 64, 300, 226)
        self._field_label(game_card, "Game Type", 14, 12)
        self.selected_download = StringVar()
        self.download_options = ttk.Combobox(
            game_card, textvariable=self.selected_download,
            values=("Vanilla", "Forge", "NeoForge", "Fabric", "Quilt"), state="readonly"
        )
        self._put(self.download_options, 14, 32, 272, font=(10, None))
        _last_type = self.data.get("last_game_type") or "Vanilla"
        _types = ("Vanilla", "Forge", "NeoForge", "Fabric", "Quilt")
        if _last_type in _types:
            self.download_options.current(_types.index(_last_type))
        else:
            self.download_options.current(0)
        self.download_options.bind("<<ComboboxSelected>>", self._on_type_change)
        self._field_label(game_card, "Versions", 14, 72)
        self.installed_only = StringVar(value="deselected")
        self.installed_chk = ttk.Checkbutton(
            game_card, text="Installed only", variable=self.installed_only,
            onvalue="selected", offvalue="deselected", command=self._load_vanilla_versions
        )
        self._put(self.installed_chk, 174, 68, font=(8, None))
        self.versionsList = ttk.Combobox(game_card, state="readonly")
        self._put(self.versionsList, 14, 92, 234, font=(10, None))
        self.versionsList["values"] = ["Loading..."]
        self.versionsList.current(0)
        self.versionsList.bind("<<ComboboxSelected>>", self._save_last_selection)
        addv_b = self._button(game_card, "+", self._add_custom_version, self.gray_btn, self.gray_hover, f_btn, w=34, h=28, radius=12)
        self._put(addv_b, 252, 92, 34, 28, font=(12, "bold"))
        self._field_label(game_card, "Instance", 14, 132)
        self.instance_var = StringVar(value=(self.data.get("selected-instance") or "Default"))
        self.instance_options = ttk.Combobox(
            game_card, textvariable=self.instance_var, values=("Default",), state="readonly"
        )
        self._put(self.instance_options, 14, 152, 272, font=(10, None))
        self.instance_options.bind("<<ComboboxSelected>>", self._on_instance_change)
        new_b = self._button(game_card, "New", self._new_instance, self.green, self.green_hover, f_small)
        self._put(new_b, 14, 184, 86, 28, font=(9, None))
        fold_b = self._button(game_card, "Open Folder", self._open_instance_folder, self.accent, self.accent_hover, f_small)
        self._put(fold_b, 104, 184, 92, 28, font=(9, None))
        del_b = self._button(game_card, "Delete", self._delete_instance, self.red, self.red_hover, f_small)
        self._put(del_b, 200, 184, 86, 28, font=(9, None))
        self._refresh_instances()

                                                         
        self.mods_btn = self._button(self.overlay, "⬇  MODS", self.open_mods_window, self.gray_btn, self.gray_hover, f_btn, radius=12)
        self._put(self.mods_btn, "col2", 298, 300, 36, font=(11, "bold"))
        self.mods_btn.place_forget()

                                 
        folder_card = RoundedFrame(self.overlay, self.card, self.border, radius=18, surround=self.bg)
        self._put(folder_card, "col3", "fy", "bw", 76)
        self._field_label(folder_card, "Minecraft Folder", 14, 10)
        self.path_var = StringVar(value=self.mc_dir)
        self.path_entry = self._entry(folder_card, textvariable=self.path_var)
        self._put(self.path_entry, 14, 32, "pw", 28, font=(9, None))
        browse_b = self._button(folder_card, "Browse", self.browse_folder, self.accent, self.accent_hover, f_small)
        self._put(browse_b, "brx", 32, 66, 28, font=(9, None))

        play_b = self._button(self.overlay, "▶   PLAY", self.handle_run, self.green, self.green_hover, f_btn)
        self._put(play_b, "col3", "by", "bw", 48, font=(12, "bold"))
        dl_b = self._button(self.overlay, "⬇   DOWNLOAD", self.handle_download, self.accent, self.accent_hover, f_btn)
        self._put(dl_b, "col3", "dly", "bw", 42, font=(12, "bold"))
        set_b = self._button(self.overlay, "⚙   SETTINGS", self.open_settings, self.gray_btn, self.gray_hover, f_label)
        self._put(set_b, "col3", "sety", "bw", 36, font=(10, None))

                              
        sep = tk.Frame(self.overlay, bg=self.border, bd=0)
        self._put(sep, 0, 584, 1068, 2)
        self.status_dot = tk.Label(self.overlay, text="●", bg=self.bg, fg=self.green)
        self._put(self.status_dot, 0, 592, font=(9, None))
        self.status_lbl = tk.Label(self.overlay, text="Ready", bg=self.bg, fg=self.muted)
        self._put(self.status_lbl, 16, 592, font=(9, None))
        self.console_btn = tk.Label(self.overlay, text="⌨  Console", bg=self.bg, fg=self.muted, cursor="hand2")
        self._put(self.console_btn, 170, 592, font=(9, None))
        self.console_btn.bind("<Button-1>", lambda e: self._toggle_console())
        self.console_btn.bind("<Enter>", lambda e: self.console_btn.config(fg=self.accent))
        self.console_btn.bind("<Leave>", lambda e: self.console_btn.config(fg=self.accent if self.console_visible else self.muted))
        self.instance_chip = tk.Label(self.overlay, text="◈  Default", bg=self.bg, fg=self.muted)
        self._put(self.instance_chip, 290, 592, font=(9, None))

                                                                          
        self.progress_bar = ttk.Progressbar(self.overlay, mode="determinate", maximum=100)
        self._put(self.progress_bar, 320, 588, 640, 16)
        self.progress_lbl = tk.Label(self.overlay, text="", bg=self.bg, fg=self.muted)
        self._put(self.progress_lbl, 968, 588, 100, 16, font=(9, None))
        self.progress_bar.place_forget()
        self.progress_lbl.place_forget()
        self._keep_hidden.update({self.progress_bar, self.progress_lbl, self.mods_btn})

        self._build_console()
        self.window.bind("<Configure>", self._on_window_resize)
        self._on_type_change()

    def _draw_background(self, file=None):
        self.canvas.delete("all")
        use_bg = (not self.light) and (not LOW_END) and self.W >= 640
        img = None
        fname = file or self._current_slide()
        if use_bg and fname:
            try:
                from PIL import Image
                p = os.path.join("img", fname)
                if os.path.isfile(p):
                    img = Image.open(p).convert("RGB")
            except Exception:
                img = None
        if img is not None:
            try:
                from PIL import Image, ImageTk
                                                                                 
                iw, ih = img.size
                scale = max(self.W / max(1, iw), self.H / max(1, ih))
                nw, nh = max(1, int(iw * scale)), max(1, int(ih * scale))
                resized = img.resize((nw, nh))
                left = max(0, (nw - self.W) // 2)
                top = max(0, (nh - self.H) // 2)
                resized = resized.crop((left, top, min(nw, left + self.W), min(nh, top + self.H)))
                base = Image.new("RGB", (self.W, self.H), (14, 16, 19))
                blended = Image.blend(base, resized, 0.55)
                self._bg_tk = ImageTk.PhotoImage(blended)
                self.canvas.create_image(0, 0, image=self._bg_tk, anchor="nw")
                return
            except Exception:
                import traceback
                traceback.print_exc()
            try:
                self.bg_img = PhotoImage(file=os.path.join("img", fname))
                self.canvas.create_image(self.W // 2, self.H // 2, image=self.bg_img)
            except Exception:
                import traceback
                traceback.print_exc()

    def _slide_files(self):
        try:
            return sorted(f for f in os.listdir("img")
                          if f.lower().endswith((".png", ".jpg", ".jpeg", ".bmp", ".webp")))
        except Exception:
            return []

    def _current_slide(self):
        files = self._slide_files()
        if not files:
            return None
        idx = getattr(self, "_slide_idx", 0) % len(files)
        return files[idx]

    def _slideshow_tick(self):
        files = self._slide_files()
        if len(files) > 1 and not self.light and not LOW_END:
            self._slide_idx = (getattr(self, "_slide_idx", -1) + 1) % len(files)
            self._draw_background()
        if len(files) > 1:
            self.window.after(12000, self._slideshow_tick)

    def _show_registered(self, widget):
        for spec in self._layout_specs:
            if spec[0] is widget:
                _, x, y, wd, ht = spec
                kw = {"x": self._x(self._resolve(x)), "y": self._y(self._resolve(y))}
                if wd is not None:
                    kw["width"] = self._x(self._resolve(wd))
                if ht is not None:
                    kw["height"] = self._y(ht)
                widget.place(**kw)
                return

    def _switch_tab(self, tab):
        if tab == self.active_tab:
            return
        self.active_tab = tab
        if tab == "server":
            for spec in self._layout_specs:
                w = spec[0]
                if (w is not self.server_frame and w not in self._always_visible
                        and w.winfo_ismapped()):
                    w.place_forget()
            self._show_registered(self.server_frame)
            self.tab_client.config(fg=self.muted)
            self.tab_server.config(fg=self.accent)
            self.tab_line_c.place_forget()
            self._show_registered(self.tab_line_s)
            self._srv_render_page()
            self.header_frame.lift()
            for h in self._rz_handles:
                h.lift()
        else:
            if self.server_frame.winfo_ismapped():
                self.server_frame.place_forget()
            for spec in self._layout_specs:
                w = spec[0]
                if (w is not self.server_frame and w is not self.tab_line_s
                        and w not in self._keep_hidden and not w.winfo_ismapped()):
                    self._show_registered(w)
            self.tab_client.config(fg=self.accent)
            self.tab_server.config(fg=self.muted)
            self._show_registered(self.tab_line_c)
            self.tab_line_s.place_forget()
            self.header_frame.lift()
            for h in self._rz_handles:
                h.lift()

    def _on_window_resize(self, event):
        if event.widget is not self.window:
            return
        if getattr(self, "_rz_fast_job", None):
            try:
                self.window.after_cancel(self._rz_fast_job)
            except Exception:
                pass
        if getattr(self, "_resize_job", None):
            try:
                self.window.after_cancel(self._resize_job)
            except Exception:
                pass
                                                                         
        self._rz_fast_job = self.window.after(25, self._relayout_widgets)
        self._resize_job = self.window.after(180, self._relayout)

    def _relayout(self):
        self._resize_job = None
        self._relayout_widgets()
        self._draw_background()

    def _relayout_widgets(self):
        self._rz_fast_job = None
        w = self.window.winfo_width()
        h = self.window.winfo_height()
        if w < 50 or h < 50:
            return
        if abs(w - self.W) < 2 and abs(h - self.H) < 2:
            return
        self.W, self.H = w, h
        self.sx = self.W / 1100.0
        self.sy = self.H / 650.0
        self.sf = min(self.sx, self.sy)
        self._update_colmap()
        self.canvas.config(width=self.W, height=self.H)
        for widget, x, y, wd, ht in self._layout_specs:
            try:
                if not widget.winfo_ismapped():
                    continue
                kw = {"x": self._x(self._resolve(x)), "y": self._y(self._resolve(y))}
                if wd is not None:
                    kw["width"] = self._x(self._resolve(wd))
                if ht is not None:
                    kw["height"] = self._y(ht)
                widget.place(**kw)
            except Exception:
                pass
        for widget, base, weight in self._font_specs:
            try:
                widget.config(font=("Segoe UI", self._fs(base), weight) if weight else ("Segoe UI", self._fs(base)))
            except Exception:
                pass
                                        
        try:
            self.header_frame.lift()
            for h in self._rz_handles:
                h.lift()
        except Exception:
            pass

                                        
    def _build_console(self):
        self.console_win = None
        self.console_text = None

    def _toggle_console(self):
        if self.console_visible:
            if self.console_win is not None:
                try:
                    self.console_win.destroy()
                except Exception:
                    pass
            self.console_win = None
            self.console_text = None
            self.console_visible = False
            try:
                self.console_btn.config(fg=self.muted)
            except Exception:
                pass
            return
        win = tk.Toplevel(self.window)
        win.title("Console")
        sw, sh = win.winfo_screenwidth(), win.winfo_screenheight()
        ww, wh = min(680, sw - 60), min(420, sh - 80)
        win.geometry(f"{ww}x{wh}+{(sw - ww) // 2}+{(sh - wh) // 2}")
        win.configure(bg=self.card)
        win.minsize(360, 240)
        try:
            win.iconbitmap("icon.ico")
        except Exception:
            pass
        header = tk.Frame(win, bg=self.card)
        header.pack(fill="x", padx=8, pady=(6, 2))
        tk.Label(header, text="CONSOLE", font=self.f_tiny, bg=self.card, fg=self.muted).pack(side="left")
        clear_b = tk.Label(header, text="Clear", font=self.f_tiny, bg=self.card, fg=self.accent, cursor="hand2")
        clear_b.pack(side="right")
        clear_b.bind("<Button-1>", lambda e: self._console_clear())
        body = tk.Frame(win, bg=self.card)
        body.pack(fill="both", expand=True, padx=(8, 6), pady=(0, 6))
        self.console_text = tk.Text(
            body, bg="#0d0f12", fg="#c9d4e3",
            font=("Consolas", max(7, self._fs(9))),
            state="disabled", bd=0, highlightthickness=0, wrap="word"
        )
        c_scroll = ttk.Scrollbar(body, orient="vertical", command=self.console_text.yview)
        self.console_text.configure(yscrollcommand=c_scroll.set)
        self.console_text.tag_configure("err", foreground="#ff6b6b")
        self.console_text.tag_configure("warn", foreground="#fbbf24")
        self.console_text.tag_configure("info", foreground="#6da2ff")
        c_scroll.pack(side="right", fill="y")
        self.console_text.pack(side="left", fill="both", expand=True)
        self.console_text.config(state="normal")
        for line in self.console_lines[-500:]:
            self.console_text.insert("end", line + "\n", self._line_tag(line) or ())
        self.console_text.see("end")
        self.console_text.config(state="disabled")

        def on_close():
            self.console_win = None
            self.console_text = None
            self.console_visible = False
            try:
                self.console_btn.config(fg=self.muted)
            except Exception:
                pass
            win.destroy()

        win.protocol("WM_DELETE_WINDOW", on_close)
        self.console_win = win
        self.console_visible = True
        self.console_btn.config(fg=self.accent)

    def _console_clear(self):
        self.console_lines.clear()
        if self.console_text is not None:
            try:
                self.console_text.config(state="normal")
                self.console_text.delete("1.0", "end")
                self.console_text.config(state="disabled")
            except Exception:
                self.console_text = None

    @staticmethod
    def _line_tag(text):
        low = text.lower()
        if "exception" in low or "error" in low or "crash" in low or "fatal" in low:
            return "err"
        if "warn" in low:
            return "warn"
        if text.startswith("[Spark-Launcher]") or text.startswith("[download]"):
            return "info"
        return None

    def _console_write(self, text, tag=None):
        self.console_lines.append(text)
        if len(self.console_lines) > 4000:
            del self.console_lines[:1500]
        self.console_queue.put((text, tag))

    def _console_pump(self):
        try:
            while True:
                text, tag = self.console_queue.get_nowait()
                if self.console_text is None:
                    continue
                try:
                    self.console_text.config(state="normal")
                    line_tag = tag if tag is not None else self._line_tag(text)
                    self.console_text.insert("end", text + "\n", line_tag or ())
                    line_count = int(self.console_text.index("end-1c").split(".")[0])
                    if line_count > 2500:
                        self.console_text.delete("1.0", f"{line_count - 2000}.0")
                    self.console_text.see("end")
                    self.console_text.config(state="disabled")
                except Exception:
                    self.console_text = None
        except queue.Empty:
            pass
        if getattr(self, "_dl_active", False):
            try:
                if self._dl_max > 0:
                    if str(self.progress_bar.cget("mode")) != "determinate":
                        self.progress_bar.stop()
                        self.progress_bar.config(mode="determinate")
                    self.progress_bar.config(maximum=self._dl_max, value=self._dl_value)
                    self.progress_lbl.config(text=f"{min(100, int(100 * self._dl_value / self._dl_max))}%")
                else:
                    if str(self.progress_bar.cget("mode")) != "indeterminate":
                        self.progress_bar.config(mode="indeterminate", maximum=100)
                        self.progress_bar.start(12)
                    self.progress_lbl.config(text="...")
            except Exception:
                pass
        self.window.after(120, self._console_pump)

    def _dl_start(self):
        self._dl_active = True
        self._dl_value = 0
        self._dl_max = 0
        self._keep_hidden.discard(self.progress_bar)
        self._keep_hidden.discard(self.progress_lbl)
        try:
            self.progress_bar.stop()
            self.progress_bar.config(mode="determinate", value=0)
            self.progress_bar.place(x=self._x(320), y=self._y(588), width=self._x(640), height=self._y(16))
            self.progress_lbl.place(x=self._x(968), y=self._y(588), width=self._x(100), height=self._y(16))
            self.progress_lbl.config(text="0%")
        except Exception:
            pass

    def _dl_finish(self):
        self._dl_active = False
        self._keep_hidden.update({self.progress_bar, self.progress_lbl})
        try:
            self.progress_bar.stop()
            self.progress_bar.place_forget()
            self.progress_lbl.place_forget()
        except Exception:
            pass

    def _console_progress(self, value, total):
        if not total:
            return
        pct = int(100 * value / total)
        if pct != self._last_dl_pct and pct % 5 == 0:
            self._last_dl_pct = pct
            self._console_write(f"[download] {'█' * (pct // 5)}{'-' * (20 - pct // 5)} {pct}%")

                               
    def _after_game_exit(self, code, detected):
        self._mc_proc = None
        if code == 0:
            self.status_dot.config(fg=self.green)
            self.status_lbl.config(text="Game closed")
            return
        self.status_dot.config(fg=self.red)
        self.status_lbl.config(text=f"Game crashed (exit code {code})")
        self._open_crash_assistant(code, detected)

    def _find_crash_text(self):
        game_dir = getattr(self, "_last_game_dir", None) or self._target_dir()
        cr_dir = os.path.join(game_dir, "crash-reports")
        best = None
        best_mtime = self._launch_time - 5
        try:
            for f in os.listdir(cr_dir):
                if f.endswith(".txt"):
                    p = os.path.join(cr_dir, f)
                    m = os.path.getmtime(p)
                    if m >= best_mtime and (best is None or m > os.path.getmtime(best)):
                        best = p
        except Exception:
            pass
        if best:
            try:
                with open(best, encoding="utf-8", errors="replace") as f:
                    return f.read(), best
            except Exception:
                pass
        lp = os.path.join(game_dir, "logs", "latest.log")
        if os.path.exists(lp):
            try:
                with open(lp, encoding="utf-8", errors="replace") as f:
                    return f.read()[-20000:], lp
            except Exception:
                pass
        return "\n".join(self.console_lines[-400:]), None

    def _analyze_crash(self, text):
        t = text or ""
        lower = t.lower()
        key_lines = []
        mods = []

        def add_mod(name, strict=True):
            name = str(name).strip().strip("'\"").strip()
            if not name or len(name) < 2:
                return
            if not strict:
                skip = ("forge", "fabric-loader", "quilt-loader", "neoforge", "minecraft", "authlib", "installer", "client-extra", "java")
                if any(s in name.lower() for s in skip):
                    return
            if name.lower() not in [m.lower() for m in mods]:
                mods.append(name)

        for m in re.finditer(r"(?im)Mod File:\s*(\S+)", t):
            add_mod(m.group(1))
        for m in re.finditer(r"(?im)Missing Mods?:\s*(.+)$", t):
            for part in re.split(r"[,;\[\]]", m.group(1)):
                add_mod(part)
        for m in re.finditer(r"(?im)^\s*-\s*(?:mod\s+)?['\"]?([\w\-.]+\.jar)", t):
            add_mod(m.group(1))
        for m in re.finditer(r"(?im)\bmod\s+'([^']+)'", t):
            add_mod(m.group(1))
        if not mods:
            for m in re.finditer(r"\b([\w\-.]{3,}\.jar)\b", t):
                add_mod(m.group(1), strict=False)

        for m in re.finditer(r"(?im)^.*(?:Mod File:|Incompatible mods|Incompatible mod set|Missing or unsupported mandatory dependencies|ModResolutionException|Missing Mods:|caused by:).*$", t):
            line = m.group(0).strip()
            if line and line not in key_lines and len(key_lines) < 12:
                key_lines.append(line[:220])

        if any(s in lower for s in ("incompatible mods found", "incompatible mod set", "modresolutionexception", "missing or unsupported mandatory dependencies")):
            cause = "Mod incompatibility / missing dependency"
            advice = "Mods conflict or a required dependency is missing. Remove or update the mods listed below."
        elif "missing mods" in lower or "missing mandatory dependencies" in lower:
            cause = "Missing mod dependency"
            advice = "Install the missing mods listed below (or remove the mods that require them)."
        elif "unsupportedclassversionerror" in lower or "has been compiled by a more recent version" in lower:
            cause = "Wrong Java version"
            advice = "This version needs a different Java release. Install the required Java version and make sure it is the one on PATH."
        elif "outofmemoryerror" in lower or "could not reserve enough space for object heap" in lower:
            cause = "Out of memory"
            advice = "The JVM ran out of memory. Try a different RAM allocation in Settings (higher for heavy modpacks, lower if you use 32-bit Java)."
        elif "opengl" in lower and ("pixel format" in lower or "1282" in t or "driver" in lower):
            cause = "Graphics / OpenGL problem"
            advice = "Update your graphics drivers, and try disabling shaders or high-resource packs."
        elif "failed to download" in lower or "checksum" in lower or "invalid sha1" in lower:
            cause = "Corrupted download"
            advice = "A game file failed verification. Press Download for this version again to repair the broken files."
        elif "could not find or load main class" in lower:
            cause = "Corrupted installation"
            advice = "The version's main class is missing. Re-install this version with the Download button."
        else:
            cause = "Unknown crash"
            advice = "No known pattern matched. Check the key error lines and the full crash report below."

        if not key_lines:
            for line in t.splitlines():
                if re.search(r"(?i)(exception|caused by|error)", line):
                    clean = line.strip()
                    if clean and clean not in key_lines and len(key_lines) < 12:
                        key_lines.append(clean[:220])

        return {"cause": cause, "advice": advice, "mods": mods[:12], "key_lines": key_lines}

    def _open_crash_assistant(self, code, detected):
        text, report_path = self._find_crash_text()
        res = self._analyze_crash(text)
        L = self
        win = tk.Toplevel(self.window)
        win.title("Crash Assistant")
        sw, sh = win.winfo_screenwidth(), win.winfo_screenheight()
        ww, wh = min(560, sw - 40), min(560, sh - 60)
        win.geometry(f"{ww}x{wh}+{(sw - ww) // 2}+{(sh - wh) // 2}")
        win.configure(bg=L.bg)
        try:
            win.iconbitmap("icon.ico")
        except Exception:
            pass
        win.transient(self.window)
        win.grab_set()

        tk.Label(win, text="Crash Assistant", font=L.f_title, bg=L.bg, fg=L.fg).pack(pady=(12, 2), anchor="w", padx=18)
        tk.Frame(win, bg=L.red, height=3, bd=0, width=L._x(46)).pack(anchor="w", padx=18, pady=(2, 8))

        cause_card = RoundedFrame(win, L.card, L.border, radius=16, surround=L.bg)
        cause_card.pack(fill="x", padx=18)
        inner = tk.Frame(cause_card, bg=L.card)
        inner.pack(fill="x", padx=12, pady=10)
        tk.Label(inner, text="LIKELY CAUSE", font=L.f_tiny, bg=L.card, fg=L.muted).pack(anchor="w")
        tk.Label(inner, text=res["cause"], font=L.f_btn, bg=L.card, fg=L.red, wraplength=ww - 60, justify="left").pack(anchor="w")
        tk.Label(inner, text=res["advice"], font=L.f_small, bg=L.card, fg=L.fg, wraplength=ww - 60, justify="left").pack(anchor="w", pady=(6, 0))

        if res["mods"]:
            tk.Label(win, text=f"MODS DETECTED IN CRASH · {len(res['mods'])}", font=L.f_tiny, bg=L.bg, fg=L.muted).pack(anchor="w", padx=18, pady=(12, 4))
            mod_card = RoundedFrame(win, L.card, L.border, radius=16, surround=L.bg)
            mod_card.pack(fill="x", padx=18)
            mod_inner = tk.Frame(mod_card, bg=L.card)
            mod_inner.pack(fill="x", padx=10, pady=8)
            for m in res["mods"]:
                tk.Label(mod_inner, text=f"•  {m}", font=L.f_small, bg=L.card, fg="#fbbf24", anchor="w").pack(anchor="w")

        tk.Label(win, text="KEY ERROR LINES", font=L.f_tiny, bg=L.bg, fg=L.muted).pack(anchor="w", padx=18, pady=(12, 4))
        key_card = RoundedFrame(win, L.card, L.border, radius=16, surround=L.bg)
        key_card.pack(fill="both", expand=True, padx=18)
        key_text = tk.Text(
            key_card, bg="#0d0f12", fg="#c9d4e3", font=("Consolas", max(7, L._fs(9))),
            state="disabled", bd=0, highlightthickness=0, wrap="word"
        )
        key_text.pack(fill="both", expand=True, padx=6, pady=6)
        key_text.config(state="normal")
        if res["key_lines"]:
            for line in res["key_lines"]:
                key_text.insert("end", line + "\n", "err" if "exception" in line.lower() or "caused by" in line.lower() else ())
        else:
            key_text.insert("end", "(no matching lines found)", "warn")
        key_text.config(state="disabled")

        btns = tk.Frame(win, bg=L.bg)
        btns.pack(fill="x", padx=18, pady=12)
        if report_path and os.path.exists(report_path):
            def open_report():
                try:
                    os.startfile(report_path)
                except Exception:
                    pass
            L._button(btns, "Open Crash Report", open_report, L.accent, L.accent_hover, L.f_small, h=30, radius=12).pack(side="left", padx=(0, 6))
        def open_logs():
            folder = os.path.join(getattr(L, "_last_game_dir", None) or L._target_dir(), "logs")
            if not os.path.isdir(folder):
                folder = getattr(L, "_last_game_dir", None) or L._target_dir()
            try:
                os.startfile(folder)
            except Exception:
                pass
        L._button(btns, "Open Logs Folder", open_logs, L.gray_btn, L.gray_hover, L.f_small, h=30, radius=12).pack(side="left", padx=(0, 6))
        analysis = f"Cause: {res['cause']}\n{res['advice']}\nMods: {', '.join(res['mods']) or 'none detected'}"
        def copy_analysis():
            win.clipboard_clear()
            win.clipboard_append(analysis)
        L._button(btns, "Copy Analysis", copy_analysis, L.gray_btn, L.gray_hover, L.f_small, h=30, radius=12).pack(side="left", padx=(0, 6))
        L._button(btns, "Close", win.destroy, L.gray_btn, L.gray_hover, L.f_small, h=30, radius=12).pack(side="right")

    @staticmethod
    def _version_sort_key(v):
        s = str(v)
        mc, _, build = s.partition("-")
        try:
            mcp = [int(x) for x in re.findall(r"\d+", mc)]
        except Exception:
            mcp = []
        try:
            bp = [int(x) for x in re.findall(r"\d+", build)]
        except Exception:
            bp = []
        return (mcp, bp)

    def _load_vanilla_versions(self):
        self.status_lbl.config(text="Loading versions...")
        def work():
            versions = []
            vmap = {}
            target = self._target_dir()
            try:
                available = minecraft_launcher_lib.utils.get_available_versions(target)
            except Exception:
                available = [{"type": "release", "id": "1.20.1"}]
            try:
                vdir = os.path.join(target, "versions")
                installed_ids = {
                    name for name in os.listdir(vdir)
                    if os.path.isfile(os.path.join(vdir, name, name + ".json"))
                }
            except Exception:
                installed_ids = set()
            if getattr(self, "installed_only", None) is not None and self.installed_only.get() == "selected":
                available = [i for i in available if i.get("id") in installed_ids]
            for i in available:
                label = f'{i["type"]} {i["id"]}'
                versions.append(label)
                vmap[label] = i["id"]
                                                                       
            for vid in sorted(installed_ids - set(vmap.values())):
                label = f"custom {vid}"
                versions.append(label)
                vmap[label] = vid
            def apply():
                if self.download_options.get() != "Vanilla":
                    return
                self.versions = versions
                self.versions_map = vmap
                self.versionsList["values"] = versions or ["No downloaded versions"]
                if versions:
                    self._select_last_version(versions)
                else:
                    self.versionsList.set("No downloaded versions")
                self.status_lbl.config(text="Ready")
            self.window.after(0, apply)
        Thread(target=work, daemon=True).start()

    def _load_forge_versions(self):
        self.status_lbl.config(text="Loading Forge versions...")
        def work():
            try:
                lst = sorted(
                    minecraft_launcher_lib.forge.list_forge_versions(),
                    key=self._version_sort_key, reverse=True
                )
            except Exception:
                lst = []
            def apply():
                if self.download_options.get() != "Forge":
                    return
                self.versionsList["values"] = lst or ["No Forge versions found"]
                if lst:
                    self._select_last_version(lst)
                else:
                    self.versionsList.set("No Forge versions found")
                self.status_lbl.config(text="Ready")
            self.window.after(0, apply)
        Thread(target=work, daemon=True).start()

    def _load_fabric_versions(self):
        self.status_lbl.config(text="Loading Fabric versions...")
        def work():
            try:
                lst = sorted(
                    get_stable_minecraft_versions(),
                    key=self._version_sort_key, reverse=True
                )
            except Exception:
                lst = []
            def apply():
                if self.download_options.get() != "Fabric":
                    return
                self.versionsList["values"] = lst or ["No Fabric versions found"]
                if lst:
                    self._select_last_version(lst)
                else:
                    self.versionsList.set("No Fabric versions found")
                self.status_lbl.config(text="Ready")
            self.window.after(0, apply)
        Thread(target=work, daemon=True).start()

    def _load_neoforge_versions(self):
        self.status_lbl.config(text="Loading NeoForge versions...")
        def work():
            try:
                nf = mod_loader.Neoforge()
                lst = sorted(
                    nf.get_minecraft_versions(True),
                    key=self._version_sort_key, reverse=True
                )
            except Exception:
                lst = []
            def apply():
                if self.download_options.get() != "NeoForge":
                    return
                self.versionsList["values"] = lst or ["No NeoForge versions found"]
                if lst:
                    self._select_last_version(lst)
                else:
                    self.versionsList.set("No NeoForge versions found")
                self.status_lbl.config(text="Ready")
            self.window.after(0, apply)
        Thread(target=work, daemon=True).start()

    def _load_quilt_versions(self):
        self.status_lbl.config(text="Loading Quilt versions...")
        def work():
            try:
                lst = sorted(
                    quilt.get_stable_minecraft_versions(),
                    key=self._version_sort_key, reverse=True
                )
            except Exception:
                lst = []
            def apply():
                if self.download_options.get() != "Quilt":
                    return
                self.versionsList["values"] = lst or ["No Quilt versions found"]
                if lst:
                    self._select_last_version(lst)
                else:
                    self.versionsList.set("No Quilt versions found")
                self.status_lbl.config(text="Ready")
            self.window.after(0, apply)
        Thread(target=work, daemon=True).start()

    def _save_last_selection(self, event=None):
        try:
            data = load_settings()
            data["last_game_type"] = self.download_options.get()
            data["last_version"] = self.versionsList.get()
            save_settings(data)
            self.data = data
        except Exception:
            pass

    def _select_last_version(self, versions):
        last = None
        try:
            last = (self.data or {}).get("last_version")
        except Exception:
            pass
        if last and versions and last in list(versions):
            try:
                self.versionsList.current(list(versions).index(last))
                return
            except Exception:
                pass
        if versions:
            self.versionsList.current(0)

    def _on_type_change(self, event=None):
        t = self.download_options.get()
        try:
            data = load_settings()
            data["last_game_type"] = t
            save_settings(data)
            self.data = data
        except Exception:
            pass
        if t == "Vanilla":
            self._keep_hidden.add(self.mods_btn)
            try:
                self.mods_btn.place_forget()
            except Exception:
                pass
        else:
            self._keep_hidden.discard(self.mods_btn)
            try:
                self.mods_btn.place(x=self._x(self._resolve("col2")), y=self._y(298),
                                    width=self._x(300), height=self._y(36))
            except Exception:
                pass
        if t == "Forge":
            self.installed_chk.configure(state="disabled")
            self._load_forge_versions()
        elif t == "NeoForge":
            self.installed_chk.configure(state="disabled")
            self._load_neoforge_versions()
        elif t == "Fabric":
            self.installed_chk.configure(state="disabled")
            self._load_fabric_versions()
        elif t == "Quilt":
            self.installed_chk.configure(state="disabled")
            self._load_quilt_versions()
        else:
            self.installed_chk.configure(state="normal")
            self._load_vanilla_versions()

    def _add_custom_version(self):
        src = filedialog.askdirectory(title="Pick a version folder (contains Name.jar + Name.json)")
        if not src:
            return
        try:
            jars = [f[:-4] for f in os.listdir(src) if f.lower().endswith(".jar")]
        except Exception as e:
            showerror("Error", str(e))
            return
        candidates = [j for j in jars if os.path.isfile(os.path.join(src, j + ".json"))]
        if not candidates:
            showerror("Error", "No valid version found in that folder.\nA custom version needs Name.jar and Name.json with the same name.")
            return
        name = candidates[0]
        dst = os.path.join(self._target_dir(), "versions", name)
        try:
            os.makedirs(dst, exist_ok=True)
            shutil.copy2(os.path.join(src, name + ".jar"), os.path.join(dst, name + ".jar"))
            shutil.copy2(os.path.join(src, name + ".json"), os.path.join(dst, name + ".json"))
        except Exception as e:
            showerror("Error", str(e))
            return
        self._console_write(f"[Spark-Launcher] Added custom version: {name}", "info")
        showinfo("Version added", f"'{name}' was added to '{os.path.basename(self._target_dir())}'.")
        self._load_vanilla_versions()

    def browse_folder(self):
        path = filedialog.askdirectory(title="Select Minecraft Folder", initialdir=self.path_var.get() or currn_dir)
        if path:
            norm = os.path.normpath(path)
            inst_root = os.path.normpath(os.path.join(self.mc_dir, "instances"))
            if norm == inst_root or norm.startswith(inst_root + os.sep):
                showerror("Error", "That folder is inside your instances directory.\nSelect your main .minecraft folder instead — instances are managed in the Instance section.")
                return
            self.path_var.set(path)
            self.mc_dir = path
            self.data = load_settings()
            self.data["Minecraft-home"] = path
            save_settings(self.data)
            self.status_lbl.config(text=f"Folder: {path}")
            self._refresh_instances()
            self._load_vanilla_versions()

                         
    def _instances_dir(self):
        base = self.path_var.get() if getattr(self, "path_var", None) else None
        return os.path.join(base or self.mc_dir, "instances")

    def _list_instances(self):
        root = self._instances_dir()
        out = []
        try:
            for name in os.listdir(root):
                p = os.path.join(root, name)
                if os.path.isdir(p) and os.path.isdir(os.path.join(p, "versions")):
                    out.append(name)
        except Exception:
            pass
        return sorted(out, key=str.lower)

    def _target_dir(self):
        name = self.instance_var.get() if getattr(self, "instance_var", None) else "Default"
        if name and name != "Default":
            return os.path.join(self._instances_dir(), name)
        base = self.path_var.get() if getattr(self, "path_var", None) else None
        return base or self.mc_dir

    def _update_chip(self):
        if hasattr(self, "instance_chip"):
            try:
                self.instance_chip.config(text=f"◈  {self.instance_var.get()}")
            except Exception:
                pass

    def _refresh_instances(self):
        if not hasattr(self, "instance_options"):
            return
        vals = ["Default"] + self._list_instances()
        self.instance_options["values"] = vals
        if self.instance_var.get() not in vals:
            self.instance_var.set("Default")
        try:
            self.instance_options.current(vals.index(self.instance_var.get()))
        except Exception:
            pass
        self._update_chip()

    def _on_instance_change(self, event=None):
        data = load_settings()
        data["selected-instance"] = self.instance_var.get()
        save_settings(data)
        self.status_lbl.config(text=f"Instance: {self.instance_var.get()}")
        self._update_chip()
        self._load_vanilla_versions()

    def _new_instance(self):
        name = simpledialog.askstring("New Instance", "Instance name:", parent=self.window)
        if not name:
            return
        name = name.strip().strip('"')
        if not name or name in (".", "..") or name.endswith(".") or any(c in name for c in '\\/:*?"<>|'):
            showerror("Error", "Invalid instance name.")
            return
        p = os.path.join(self._instances_dir(), name)
        try:
            for sub in ("versions", "saves", "mods", "resourcepacks", "screenshots"):
                os.makedirs(os.path.join(p, sub), exist_ok=True)
        except Exception as e:
            showerror("Error", str(e))
            return
        self._refresh_instances()
        self.instance_var.set(name)
        self._on_instance_change()
        self._console_write(f"[Spark-Launcher] Created instance: {name}", "info")

    def _open_instance_folder(self):
        p = self._target_dir()
        try:
            os.makedirs(p, exist_ok=True)
            os.startfile(p)
        except Exception as e:
            showerror("Error", str(e))

    def _delete_instance(self):
        name = self.instance_var.get()
        if not name or name == "Default":
            showerror("Error", "Select an instance to delete (Default cannot be deleted).")
            return
        if askquestion("Delete Instance", f"Delete instance '{name}' and ALL of its files?") != "yes":
            return
        try:
            shutil.rmtree(os.path.join(self._instances_dir(), name), ignore_errors=True)
        except Exception as e:
            showerror("Error", str(e))
            return
        self._console_write(f"[Spark-Launcher] Deleted instance: {name}", "info")
        self.instance_var.set("Default")
        self._refresh_instances()
        self._on_instance_change()

                            
    _SERVER_SOFTWARE = {
        "Vanilla": {"id": "vanilla", "cls": "Vanilla", "desc": "The official Mojang server", "color": "#8a93a6"},
        "Paper": {"id": "paper", "cls": "Plugins", "desc": "Plugin server — high performance (Bukkit/Spigot plugins)", "color": "#22c55e"},
        "Purpur": {"id": "purpur", "cls": "Plugins", "desc": "Plugin server — Paper fork with extra features", "color": "#22c55e"},
        "Fabric": {"id": "fabric", "cls": "Modded", "desc": "Modded server — runs Fabric mods", "color": "#f59e0b"},
        "Forge": {"id": "forge", "cls": "Modded", "desc": "Modded server — runs Forge mods", "color": "#f59e0b"},
        "Velocity": {"id": "velocity", "cls": "Proxy", "desc": "Proxy — links multiple servers together (no MC version needed)", "color": "#4f8cff"},
        "Custom": {"id": "custom", "cls": "Custom", "desc": "Bring your own server jar (Mohist, Arclight, ...)", "color": "#a78bfa"},
    }

    def _servers_dir(self):
        base = self.path_var.get() if getattr(self, "path_var", None) else None
        return os.path.join(base or self.mc_dir, "servers")

    def _list_servers(self):
        root = self._servers_dir()
        out = []
        try:
            for name in os.listdir(root):
                if os.path.isfile(os.path.join(root, name, "server_meta.json")):
                    out.append(name)
        except Exception:
            pass
        return sorted(out, key=str.lower)

    def _srv_dir(self, name):
        return os.path.join(self._servers_dir(), name)

    def _srv_meta_load(self, d):
        try:
            with open(os.path.join(d, "server_meta.json"), encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}

    def _srv_meta_save(self, d, meta):
        with open(os.path.join(d, "server_meta.json"), "w", encoding="utf-8") as f:
            json.dump(meta, f, indent=2)

    def _lan_ip(self):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except Exception:
            try:
                return socket.gethostbyname(socket.gethostname())
            except Exception:
                return "127.0.0.1"

    def _build_server_page(self):
        pass                                        

    def _srv_render_page(self):
        for w in self.server_frame.winfo_children():
            w.destroy()
        servers = self._list_servers()
        if self._srv_selected not in servers:
            self._srv_selected = servers[0] if servers else None

        if not servers:
            empty = tk.Frame(self.server_frame, bg=self.bg)
            empty.place(relx=0.5, rely=0.42, anchor="center")
            plus = tk.Label(empty, text="+", font=("Segoe UI", 64), bg=self.bg, fg=self.accent, cursor="hand2")
            plus.pack()
            txt = tk.Label(empty, text="Add Server", font=("Segoe UI", 14, "bold"), bg=self.bg, fg=self.fg, cursor="hand2")
            txt.pack()
            hint = tk.Label(empty, text="Host a Minecraft server on this PC", font=self.f_small, bg=self.bg, fg=self.muted)
            hint.pack(pady=(4, 0))
            for w in (plus, txt):
                w.bind("<Button-1>", lambda e: self._srv_open_create())
                w.bind("<Enter>", lambda e: txt.config(fg=self.accent))
                w.bind("<Leave>", lambda e: txt.config(fg=self.fg))
            return

        page = tk.Frame(self.server_frame, bg=self.bg)
        page.place(x=self._x(16), y=self._y(64), width=self._x(1068), height=self._y(560))

                 
        side = RoundedFrame(page, self.card, self.border, radius=18, surround=self.bg)
        side.pack(side="left", fill="y", padx=(0, 12))
        tk.Label(side, text="SERVERS", font=self.f_tiny, bg=self.card, fg=self.muted).pack(anchor="w", padx=10, pady=(10, 4))
        self.srv_list = tk.Listbox(
            side, bg=self.input_bg, fg="white", relief="flat", highlightthickness=0,
            selectbackground=self.accent, selectforeground="white", font=self.f_small,
            activestyle="none", width=20
        )
        self.srv_list.pack(fill="both", expand=True, padx=8, pady=(0, 8))
        for s in servers:
            self.srv_list.insert("end", s)
            if s == self._srv_selected:
                self.srv_list.selection_set("end")
        self.srv_list.bind("<<ListboxSelect>>", self._srv_on_select)
        add_b = self._button(side, "＋  Add Server", self._srv_open_create, self.green, self.green_hover, self.f_small, w=self._x(180), h=self._x(30), radius=12)
        add_b.pack(padx=8, pady=(0, 10))

                   
        dash = tk.Frame(page, bg=self.bg)
        dash.pack(side="left", fill="both", expand=True)
        meta = self._srv_meta_load(self._srv_dir(self._srv_selected)) if self._srv_selected else {}

        head = tk.Frame(dash, bg=self.bg)
        head.pack(fill="x")
        tk.Label(head, text=self._srv_selected or "", font=self.f_title, bg=self.bg, fg=self.fg).pack(side="left")
        _soft_id = meta.get("software", "vanilla")
        _soft_info = next((v for v in self._SERVER_SOFTWARE.values() if v["id"] == _soft_id), None)
        if _soft_info:
            tk.Label(head, text=f"  {_soft_info['cls'].upper()}", font=self.f_tiny, bg=self.bg,
                     fg=_soft_info["color"]).pack(side="left", pady=(10, 0))
        self.srv_dot = tk.Label(head, text="●", font=self.f_small, bg=self.bg,
                                fg={"online": self.green, "starting": "#fbbf24"}.get(self._srv_status, self.red))
        self.srv_dot.pack(side="left", padx=(14, 4))
        self.srv_status_lbl = tk.Label(head, text=self._srv_status.upper(), font=self.f_small, bg=self.bg, fg=self.muted)
        self.srv_status_lbl.pack(side="left")
        toggle_txt = "■  STOP" if self._srv_status in ("online", "starting") else "▶  START"
        toggle_bg = self.red if self._srv_status in ("online", "starting") else self.green
        toggle_hov = self.red_hover if self._srv_status in ("online", "starting") else self.green_hover
        self.srv_toggle = self._button(head, toggle_txt, self._srv_toggle_server, toggle_bg, toggle_hov, f_btn := ("Segoe UI", self._fs(11), "bold"), w=self._x(130), h=self._x(34), radius=12)
        self.srv_toggle.pack(side="right")
        self.srv_delete_btn = self._button(head, "DELETE", self._srv_delete_server, self.red, self.red_hover, f_btn, w=self._x(110), h=self._x(34), radius=12)
        self.srv_delete_btn.pack(side="right", padx=(0, 8))

        info = RoundedFrame(dash, self.card, self.border, radius=16, surround=self.bg)
        info.pack(fill="x", pady=(10, 8))
        info_inner = tk.Frame(info, bg=self.card)
        info_inner.pack(fill="x", padx=12, pady=8)
        ip = self._lan_ip()
        port = meta.get("port", 25565)
        tk.Label(info_inner, text=f"LOCAL IP  {ip}", font=self.f_small, bg=self.card, fg=self.fg).pack(side="left")
        tk.Label(info_inner, text=f"PORT  {port}", font=self.f_small, bg=self.card, fg=self.fg).pack(side="left", padx=(18, 0))
        tk.Label(info_inner, text=f"{ip}:{port}  —  friends join with this (port-forward {port} for internet play)",
                 font=self.f_tiny, bg=self.card, fg=self.muted).pack(side="right")

        cols = tk.Frame(dash, bg=self.bg)
        cols.pack(fill="both", expand=True)

                 
        con = RoundedFrame(cols, self.card, self.border, radius=16, surround=self.bg)
        con.pack(side="left", fill="both", expand=True, padx=(0, 10))
        tk.Label(con, text="CONSOLE", font=self.f_tiny, bg=self.card, fg=self.muted).pack(anchor="w", padx=8, pady=(6, 0))
        cwrap = tk.Frame(con, bg=self.card)
        cwrap.pack(fill="both", expand=True, padx=8, pady=(2, 6))
        self.srv_console_text = tk.Text(
            cwrap, bg="#0d0f12", fg="#c9d4e3", font=("Consolas", max(7, self._fs(9))),
            state="disabled", bd=0, highlightthickness=0, wrap="word"
        )
        c_scroll = ttk.Scrollbar(cwrap, orient="vertical", command=self.srv_console_text.yview)
        self.srv_console_text.configure(yscrollcommand=c_scroll.set)
        self.srv_console_text.tag_configure("err", foreground="#ff6b6b")
        self.srv_console_text.tag_configure("info", foreground="#6da2ff")
        c_scroll.pack(side="right", fill="y")
        self.srv_console_text.pack(side="left", fill="both", expand=True)
        for line in self._srv_buffer():
            self.srv_console_text.config(state="normal")
            self.srv_console_text.insert("end", line + "\n", self._srv_line_tag(line))
            self.srv_console_text.see("end")
            self.srv_console_text.config(state="disabled")
        send_row = tk.Frame(con, bg=self.card)
        send_row.pack(fill="x", padx=8, pady=(0, 8))
        self.srv_cmd_var = StringVar()
        cmd_entry = self._entry(send_row, textvariable=self.srv_cmd_var, font=self.f_small)
        cmd_entry.pack(side="left", fill="x", expand=True, ipady=4)
        cmd_entry.bind("<Return>", lambda e: self._srv_send())
        send_b = self._button(send_row, "Send", self._srv_send, self.accent, self.accent_hover, self.f_small, w=60, h=28, radius=12)
        send_b.pack(side="left", padx=(6, 0))

                                       
        right = tk.Frame(cols, bg=self.bg, width=self._x(280))
        right.pack(side="right", fill="y")
        right.pack_propagate(False)

        players_card = RoundedFrame(right, self.card, self.border, radius=16, surround=self.bg)
        players_card.pack(fill="x", pady=(0, 10))
        tk.Label(players_card, text=f"PLAYERS ONLINE · {len(self._srv_players)}", font=self.f_tiny, bg=self.card, fg=self.muted).pack(anchor="w", padx=8, pady=(6, 2))
        self.srv_players_list = tk.Listbox(
            players_card, bg=self.input_bg, fg="white", relief="flat", highlightthickness=0,
            selectbackground=self.accent, font=self.f_small, height=5, activestyle="none"
        )
        self.srv_players_list.pack(fill="x", padx=8, pady=(0, 8))
        for p in sorted(self._srv_players):
            self.srv_players_list.insert("end", p)

        files_card = RoundedFrame(right, self.card, self.border, radius=16, surround=self.bg)
        files_card.pack(fill="both", expand=True)
        tk.Label(files_card, text="FILES", font=self.f_tiny, bg=self.card, fg=self.muted).pack(anchor="w", padx=8, pady=(6, 2))
        self._fm_path = self._srv_dir(self._srv_selected)
        self.srv_files_list = tk.Listbox(
            files_card, bg=self.input_bg, fg="white", relief="flat", highlightthickness=0,
            selectbackground=self.accent, font=("Consolas", max(7, self._fs(9))), activestyle="none"
        )
        self.srv_files_list.pack(fill="both", expand=True, padx=8, pady=(0, 4))
        self.srv_files_list.bind("<Double-1>", lambda e: self._fm_navigate())
        frow = tk.Frame(files_card, bg=self.card)
        frow.pack(fill="x", padx=8, pady=(0, 8))
        self._button(frow, "Up", lambda: self._fm_up(), self.gray_btn, self.gray_hover, self.f_tiny, w=self._x(52), h=self._x(24), radius=6).pack(side="left", padx=(0, 4))
        self._button(frow, "Delete", lambda: self._fm_delete(), self.red, self.red_hover, self.f_tiny, w=self._x(62), h=self._x(24), radius=6).pack(side="left", padx=(0, 4))
        self._button(frow, "Open", lambda: self._fm_open(), self.gray_btn, self.gray_hover, self.f_tiny, w=self._x(56), h=self._x(24), radius=6).pack(side="left")
        self._fm_refresh()

    def _srv_buffer(self):
        if not hasattr(self, "_srv_buffers"):
            self._srv_buffers = {}
        return self._srv_buffers.setdefault(self._srv_selected, [])

    @staticmethod
    def _srv_line_tag(line):
        low = line.lower()
        if "error" in low or "exception" in low or "warn" in low and "error" in low:
            return "err"
        if line.startswith("[Spark-Launcher]"):
            return "info"
        return None

    def _srv_on_select(self, event=None):
        sel = self.srv_list.curselection()
        if sel:
            self._srv_selected = self.srv_list.get(sel[0])
            self._srv_render_page()

    def _srv_open_create(self):
        win = tk.Toplevel(self.window)
        win.title("Add Server")
        sw, sh = win.winfo_screenwidth(), win.winfo_screenheight()
        ww, wh = min(480, sw - 40), min(560, sh - 60)
        win.geometry(f"{ww}x{wh}+{(sw - ww) // 2}+{(sh - wh) // 2}")
        win.configure(bg=self.bg)
        win.transient(self.window)
        win.grab_set()
        try:
            win.iconbitmap("icon.ico")
        except Exception:
            pass

        tk.Label(win, text="Add Server", font=self.f_title, bg=self.bg, fg=self.fg).pack(pady=(12, 2), anchor="w", padx=18)
        tk.Frame(win, bg=self.accent, height=3, bd=0, width=self._x(46)).pack(anchor="w", padx=18, pady=(2, 6))

        def card(title):
            outer = tk.Frame(win, bg=self.bg)
            outer.pack(fill="x", padx=18, pady=(8, 0))
            tk.Label(outer, text=title, font=self.f_tiny, bg=self.bg, fg=self.muted).pack(anchor="w", pady=(0, 4))
            box = tk.Frame(outer, bg=self.card, bd=0, highlightthickness=1, highlightbackground=self.border)
            box.pack(fill="x")
            inner = tk.Frame(box, bg=self.card)
            inner.pack(fill="x", padx=12, pady=10)
            return inner

        c1 = card("SERVER NAME")
        name_var = StringVar()
        self._entry(c1, textvariable=name_var, font=self.f_small).pack(fill="x", ipady=5)

        c2 = card("SERVER SOFTWARE")
        soft_var = StringVar(value="Paper")
        soft_cb = ttk.Combobox(c2, textvariable=soft_var,
                               values=tuple(self._SERVER_SOFTWARE.keys()), state="readonly")
        soft_cb.pack(fill="x")
        class_lbl = tk.Label(c2, text="", font=self.f_small, bg=self.card, fg=self.muted)
        class_lbl.pack(anchor="w", pady=(4, 0))

        c2b = card("VERSION")
        ver_var = StringVar(value="Loading...")
        ver_cb = ttk.Combobox(c2b, textvariable=ver_var, values=("Loading...",), state="readonly")
        ver_cb.pack(fill="x")
        jar_row = tk.Frame(c2b, bg=self.card)
        self._srv_custom_jar = None
        jar_lbl = tk.Label(jar_row, text="No jar selected", font=self.f_tiny, bg=self.card, fg=self.muted)
        jar_lbl.pack(side="left", fill="x", expand=True)

        def pick_jar():
            p = filedialog.askopenfilename(title="Pick your server jar",
                                           filetypes=[("Server jar", "*.jar"), ("All files", "*.*")])
            if p:
                self._srv_custom_jar = p
                jar_lbl.config(text=os.path.basename(p), fg=self.fg)
        self._button(jar_row, "Browse jar", pick_jar, self.accent, self.accent_hover, self.f_tiny, w=84, h=26, radius=12).pack(side="left", padx=(6, 0))

        def load_versions():
            sid = self._SERVER_SOFTWARE[soft_var.get()]["id"]
            def work():
                vs = []
                try:
                    if sid in ("vanilla", "fabric"):
                        vs = [v["id"] for v in SESSION.get(
                            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json",
                            timeout=20).json()["versions"] if v.get("type") == "release"]
                    elif sid in ("paper", "velocity"):
                        proj = "paper" if sid == "paper" else "velocity"
                        data = SESSION.get(f"https://fill.papermc.io/v3/projects/{proj}",
                                           timeout=20, headers=self._MODRINTH_UA).json()
                        vs = []
                        for group, lst in (data.get("versions") or {}).items():
                            if isinstance(lst, list):
                                vs.extend(lst)
                            else:
                                vs.append(group)
                        vs = sorted(set(vs), key=self._version_sort_key, reverse=True)
                    elif sid == "purpur":
                        vs = list(reversed(SESSION.get(
                            "https://api.purpurmc.org/v2/purpur", timeout=20).json()["versions"]))
                    elif sid == "forge":
                        entries = minecraft_launcher_lib.forge.list_forge_versions()
                        seen = []
                        for e in entries:
                            mc = e.split("-", 1)[0]
                            if mc not in seen:
                                seen.append(mc)
                        vs = sorted(seen, key=self._version_sort_key, reverse=True)
                    elif sid == "velocity":
                        data = SESSION.get("https://fill.papermc.io/v3/projects/velocity",
                                           timeout=20, headers=self._MODRINTH_UA).json()
                        vs = []
                        for group, lst in (data.get("versions") or {}).items():
                            if isinstance(lst, list):
                                vs.extend(lst)
                            else:
                                vs.append(group)
                        vs = sorted(set(vs), key=self._version_sort_key, reverse=True)
                except Exception:
                    vs = []

                def apply():
                    try:
                        if not win.winfo_exists():
                            return
                        if vs:
                            ver_cb["values"] = vs
                            ver_var.set(vs[0])
                        else:
                            ver_cb["values"] = ["Failed to load"]
                            ver_var.set("Failed to load")
                    except Exception:
                        pass
                self.window.after(0, apply)
            Thread(target=work, daemon=True).start()

        def on_software(evt=None):
            info = self._SERVER_SOFTWARE[soft_var.get()]
            class_lbl.config(text=f"{info['cls'].upper()}  —  {info['desc']}", fg=info["color"])
            if info["id"] == "custom":
                ver_cb.configure(state="disabled")
                ver_var.set("any")
                jar_row.pack(fill="x", pady=(4, 0))
            else:
                ver_cb.configure(state="readonly")
                jar_row.pack_forget()
                load_versions()

        soft_cb.bind("<<ComboboxSelected>>", on_software)
        on_software()

        c3 = card("RAM")
        try:
            total_ram = float(TOTAL_RAM_MB)
        except Exception:
            total_ram = 8192
        ram_var = DoubleVar(value=min(2048, total_ram // 2))
        ram_lbl = tk.Label(c3, text=f"{int(ram_var.get())} MB", font=self.f_small, bg=self.card, fg=self.muted)
        ttk.Scale(c3, from_=512, to=max(1024, total_ram), variable=ram_var,
                  command=lambda v: ram_lbl.config(text=f"{int(float(v))} MB")).pack(fill="x", pady=(4, 0))
        ram_lbl.pack()

        c4 = card("JVM ARGUMENTS")
        jvm_var = StringVar()
        self._entry(c4, textvariable=jvm_var, font=self.f_small).pack(fill="x", ipady=5)

        c5 = card("GAME RULES")
        row5 = tk.Frame(c5, bg=self.card)
        row5.pack(fill="x")
        tk.Label(row5, text="Max players", font=self.f_small, bg=self.card, fg=self.fg).pack(side="left")
        mp_var = StringVar(value="20")
        mp_entry = self._entry(row5, textvariable=mp_var, font=self.f_small, width=6)
        mp_entry.pack(side="left", padx=(8, 18), ipady=3)
        tk.Label(row5, text="Port", font=self.f_small, bg=self.card, fg=self.fg).pack(side="left")
        port_var = StringVar(value="25565")
        self._entry(row5, textvariable=port_var, font=self.f_small, width=7).pack(side="left", padx=(8, 0), ipady=3)
        online_var = StringVar(value="selected")
        ttk.Checkbutton(
            c5, text="Online mode (premium accounts only — untick to allow cracked players)",
            variable=online_var, onvalue="selected", offvalue="deselected"
        ).pack(anchor="w", pady=(8, 0))

        def do_create():
            name = name_var.get().strip().strip('"')
            if not name or name in (".", "..") or name.endswith(".") or any(c in name for c in '\\/:*?"<>|'):
                showerror("Error", "Invalid server name.")
                return
            version = ver_var.get()
            soft_info = self._SERVER_SOFTWARE[soft_var.get()]
            if soft_info["id"] == "custom":
                if not self._srv_custom_jar or not os.path.isfile(self._srv_custom_jar):
                    showerror("Error", "Pick your server jar file first.")
                    return
            elif not version or version in ("Loading...", "Failed to load"):
                showerror("Error", "Pick a version.")
                return
            try:
                max_players = max(1, int(mp_var.get()))
                port = int(port_var.get())
            except Exception:
                showerror("Error", "Max players and port must be numbers.")
                return
            d = self._srv_dir(name)
            if os.path.exists(d):
                showerror("Error", f"A server named '{name}' already exists.")
                return
            soft_info = self._SERVER_SOFTWARE[soft_var.get()]
            meta = {
                "name": name,
                "software": soft_info["id"],
                "software_cls": soft_info["cls"],
                "version": "custom" if soft_info["id"] == "custom" else version,
                "ram": int(float(ram_var.get())),
                "jvm": jvm_var.get().strip(),
                "max_players": max_players,
                "port": port,
                "online_mode": online_var.get() == "selected",
            }
            try:
                os.makedirs(d, exist_ok=True)
                if soft_info["id"] == "custom":
                    shutil.copy2(self._srv_custom_jar, os.path.join(d, "server.jar"))
                self._srv_meta_save(d, meta)
            except Exception as e:
                showerror("Error", str(e))
                return
            self._srv_selected = name
            self._console_write(f"[server] Created server '{name}' ({version})", "info")
            win.destroy()
            self._srv_render_page()
            self._srv_download_jar_async(d, meta)

        self._button(win, "CREATE SERVER", do_create, self.green, self.green_hover, ("Segoe UI", 11, "bold"), h=38, radius=12).pack(fill="x", padx=18, pady=14)

    def _srv_download_jar_async(self, d, meta):
        def work():
            try:
                self._srv_download_jar(d, meta)
            except Exception as e:
                self._console_write(f"[server] Download failed: {e}", "err")
        Thread(target=work, daemon=True).start()

    def _srv_download_jar(self, d, meta):
        import minecraft_launcher_lib._helper as _h
        sid = meta.get("software", "vanilla")
        jar = os.path.join(d, "server.jar")
        if os.path.isfile(jar) and sid != "forge":
            return
        if sid == "forge" and (meta.get("forge_args_file") or meta.get("launch_jar")):
            return
        if sid == "custom":
            return
        cb = {"setStatus": lambda s: self._console_write(f"[server] {s}", "info")}
        if sid == "vanilla":
            version = meta.get("version")
            self._console_write(f"[server] Downloading Vanilla server jar {version}...", "info")
            manifest = SESSION.get("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json", timeout=20).json()
            vurl = None
            for v in manifest.get("versions", []):
                if v.get("id") == version:
                    vurl = v.get("url")
                    break
            if not vurl:
                raise Exception(f"Version {version} not found")
            info = SESSION.get(vurl, timeout=20).json()
            dl = (info.get("downloads") or {}).get("server")
            if not dl:
                raise Exception(f"No server jar available for {version}")
            _h.download_file(dl["url"], jar, callback=cb, sha1=dl.get("sha1"))
        elif sid == "paper":
            v = meta["version"]
            builds = SESSION.get(f"https://fill.papermc.io/v3/projects/paper/versions/{v}/builds",
                                 timeout=20, headers=self._MODRINTH_UA).json()
            b = max(builds, key=lambda x: x.get("id", 0))
            app = b["downloads"].get("server:default") or next(iter(b["downloads"].values()))
            self._console_write(f"[server] Downloading Paper {v} build {b['id']}...", "info")
            _h.download_file(app["url"], jar, callback=cb)
            self._verify_sha256(jar, (app.get("checksums") or {}).get("sha256"))
        elif sid == "purpur":
            v = meta["version"]
            info = SESSION.get(f"https://api.purpurmc.org/v2/purpur/{v}", timeout=20).json()
            b = info["builds"]["latest"]
            self._console_write(f"[server] Downloading Purpur {v} build {b}...", "info")
            _h.download_file(f"https://api.purpurmc.org/v2/purpur/{v}/{b}/download", jar, callback=cb)
        elif sid == "fabric":
            v = meta["version"]
            loaders = SESSION.get(f"https://meta.fabricmc.net/v2/versions/loader/{v}", timeout=20).json()
            loader = loaders[0]["loader"]["version"]
            inst = SESSION.get("https://meta.fabricmc.net/v2/versions/installer", timeout=20).json()
            installer = inst[0]["version"]
            url = f"https://meta.fabricmc.net/v2/versions/loader/{v}/{loader}/{installer}/server/jar"
            self._console_write(f"[server] Downloading Fabric server {v} (loader {loader})...", "info")
            _h.download_file(url, jar, callback=cb)
        elif sid == "velocity":
            v = meta["version"]
            builds = SESSION.get(f"https://fill.papermc.io/v3/projects/velocity/versions/{v}/builds",
                                 timeout=20, headers=self._MODRINTH_UA).json()
            b = max(builds, key=lambda x: x.get("id", 0))
            app = b["downloads"].get("server:default") or next(iter(b["downloads"].values()))
            self._console_write(f"[server] Downloading Velocity {v} build {b['id']}...", "info")
            _h.download_file(app["url"], jar, callback=cb)
            self._verify_sha256(jar, (app.get("checksums") or {}).get("sha256"))
        elif sid == "forge":
            self._srv_install_forge(d, meta, cb)
        self._console_write("[server] Server software ready", "info")

    @staticmethod
    def _verify_sha256(path, expected):
        if not expected:
            return
        import hashlib
        h = hashlib.sha256()
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        if h.hexdigest() != expected:
            os.remove(path)
            raise Exception("Downloaded file failed its sha256 check — it was removed, try again.")

    def _srv_install_forge(self, d, meta, cb):
        import minecraft_launcher_lib._helper as _h
        entry = meta.get("version")
        installer_url = f"https://maven.minecraftforge.net/net/minecraftforge/forge/{entry}/forge-{entry}-installer.jar"
        inst_path = os.path.join(d, "forge-installer.jar")
        self._console_write(f"[server] Downloading Forge installer {entry}...", "info")
        _h.download_file(installer_url, inst_path, callback=cb)
        self._java_preflight(entry.split("-", 1)[0])
        self._console_write("[server] Running Forge installer (this can take a few minutes)...", "info")
        r = subprocess.run(["java", "-jar", inst_path, "--installServer", d], cwd=d,
                           capture_output=True, text=True, timeout=3600)
        out = (r.stdout or "") + (r.stderr or "")
        if r.returncode != 0:
            raise Exception(f"Forge installer failed:\n{out.strip()[-500:]}")
        for line in out.splitlines()[-5:]:
            if line.strip():
                self._console_write(f"[server] {line.strip()}", "info")
        prefer = "win_args.txt" if os.name == "nt" else "unix_args.txt"
        args_file = None
        universal = None
        for root, dirs, files in os.walk(d):
            for f in files:
                if args_file is None and f in (prefer, "win_args.txt", "unix_args.txt"):
                    args_file = os.path.relpath(os.path.join(root, f), d).replace(os.sep, "/")
                if universal is None and f.endswith(".jar") and "universal" in f:
                    universal = f
            if args_file:
                break
        meta["forge_args_file"] = args_file
        meta["launch_jar"] = universal
        self._srv_meta_save(d, meta)
        if not args_file and not universal:
            raise Exception("Forge install finished but no launch method was found")
        self._console_write("[server] Forge installed successfully", "info")

    def _srv_write_props(self, d, meta):
        p = os.path.join(d, "server.properties")
        managed = {
            "online-mode": "true" if meta.get("online_mode") else "false",
            "max-players": str(meta.get("max_players", 20)),
            "server-port": str(meta.get("port", 25565)),
            "motd": f"{meta.get('name', 'Spark-Launcher')} — Spark-Launcher server",
        }
        lines = []
        if os.path.isfile(p):
            with open(p, encoding="utf-8", errors="replace") as f:
                lines = f.read().splitlines()
        seen = set()
        out = []
        for ln in lines:
            k = ln.split("=", 1)[0] if "=" in ln else None
            if k in managed:
                out.append(f"{k}={managed[k]}")
                seen.add(k)
            else:
                out.append(ln)
        for k, v in managed.items():
            if k not in seen:
                out.append(f"{k}={v}")
        with open(p, "w", encoding="utf-8") as f:
            f.write("\n".join(out) + "\n")

    def _srv_toggle_server(self):
        if self._srv_status in ("online", "starting"):
            self._srv_stop()
        else:
            self._srv_start()

    def _srv_delete_server(self):
        name = self._srv_selected
        if not name:
            return
        if self._srv_status in ("online", "starting"):
            showerror("Error", "Stop the server before deleting it.")
            return
        if askquestion("Delete Server", f"Delete server '{name}' and ALL of its files?\n(worlds, configs, mods — this cannot be undone)") != "yes":
            return
        d = self._srv_dir(name)
        try:
            shutil.rmtree(d, ignore_errors=True)
        except Exception as e:
            showerror("Error", str(e))
            return
        self._console_write(f"[server] Deleted server '{name}'", "info")
        self._srv_selected = None
        self._srv_render_page()

    def _srv_set_status(self, state):
        self._srv_status = state
        try:
            self.srv_dot.config(fg={"online": self.green, "starting": "#fbbf24"}.get(state, self.red))
            self.srv_status_lbl.config(text=state.upper())
            running = state in ("online", "starting")
            self.srv_toggle.text = "■  STOP" if running else "▶  START"
            self.srv_toggle.bg = self.red if running else self.green
            self.srv_toggle.hover = self.red_hover if running else self.green_hover
            self.srv_toggle._paint(self.srv_toggle.bg)
        except Exception:
            pass

    def _srv_preload_vanilla(self, d, meta):
\
                                                                            
        import minecraft_launcher_lib._helper as _h
        version = meta.get("version")
        cache_dir = os.path.join(d, "cache")
        target = os.path.join(cache_dir, f"mojang_{version}.jar")
        if os.path.isfile(target):
            return
        manifest = SESSION.get("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json", timeout=20).json()
        vurl = None
        for v in manifest.get("versions", []):
            if v.get("id") == version:
                vurl = v.get("url")
                break
        if not vurl:
            return
        info = SESSION.get(vurl, timeout=20).json()
        dl = (info.get("downloads") or {}).get("server")
        if not dl:
            return
        self._console_write(f"[server] Pre-downloading vanilla jar (needed for {meta.get('software')} patching)...", "info")
        os.makedirs(cache_dir, exist_ok=True)
        _h.download_file(dl["url"], target, callback={"setStatus": lambda s: self._console_write(f"[server] {s}", "info")}, sha1=dl.get("sha1"))

    def _srv_start(self):
        if self._srv_proc or not self._srv_selected:
            return
        d = self._srv_dir(self._srv_selected)
        meta = self._srv_meta_load(d)
        self._srv_set_status("starting")

        def work():
            try:
                sid = meta.get("software", "vanilla")
                jar = os.path.join(d, "server.jar")
                if not os.path.isfile(jar) or sid == "forge":
                    self._srv_download_jar(d, meta)
                if sid in ("paper", "purpur"):
                    self._srv_preload_vanilla(d, meta)
                if sid != "velocity":
                    self._srv_write_props(d, meta)
                    eula = os.path.join(d, "eula.txt")
                    if not os.path.isfile(eula):
                        with open(eula, "w") as f:
                            f.write("eula=true\n")
                        self._console_write("[server] eula.txt accepted automatically", "info")
                ram = int(meta.get("ram") or 2048)
                cmd = ["java", f"-Xmx{ram}M", f"-Xms{max(512, ram // 2)}M"]
                jvm = str(meta.get("jvm") or "").strip()
                if jvm:
                    cmd += jvm.split()
                if sid == "forge" and meta.get("forge_args_file"):
                    cmd += [f"@{meta['forge_args_file']}", "nogui"]
                elif sid == "forge" and meta.get("launch_jar"):
                    cmd += ["-jar", meta["launch_jar"], "nogui"]
                elif sid == "velocity":
                    cmd += ["-jar", "server.jar"]
                elif sid == "custom":
                    cmd += ["-jar", "server.jar"]
                else:
                    cmd += ["-jar", "server.jar", "nogui"]
                creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0) if os.name == "nt" else 0
                proc = subprocess.Popen(
                    cmd, cwd=d, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                    stderr=subprocess.STDOUT, text=True, errors="replace", bufsize=1,
                    creationflags=creationflags
                )
                self._srv_proc = proc
                for line in proc.stdout:
                    self._srv_q.put(line.rstrip("\r\n"))
                code = proc.wait()
                self._srv_proc = None
                self._srv_players.clear()
                self._srv_q.put(f"[Spark-Launcher] Server stopped (exit code {code})")
                self.window.after(0, lambda: self._srv_set_status("offline"))
            except Exception as e:
                self._srv_q.put(f"[Spark-Launcher] Error: {e}")
                self.window.after(0, lambda: self._srv_set_status("offline"))

        Thread(target=work, daemon=True).start()

    def _srv_stop(self):
        if not self._srv_proc:
            return
        try:
            self._srv_proc.stdin.write("stop\n")
            self._srv_proc.stdin.flush()
            self._console_write("[server] Stopping server...", "info")
        except Exception:
            self._srv_proc.kill()

    def _srv_send(self):
        cmd_text = self.srv_cmd_var.get().strip()
        if not cmd_text or not self._srv_proc:
            return
        try:
            self._srv_proc.stdin.write(cmd_text + "\n")
            self._srv_proc.stdin.flush()
            self.srv_cmd_var.set("")
        except Exception as e:
            showerror("Error", f"Could not send command: {e}")

    def _srv_pump(self):
        try:
            while True:
                line = self._srv_q.get_nowait()
                buf = self._srv_buffer()
                buf.append(line)
                if len(buf) > 1500:
                    del buf[:500]
                try:
                    if hasattr(self, "srv_console_text") and self.srv_console_text.winfo_exists():
                        self.srv_console_text.config(state="normal")
                        self.srv_console_text.insert("end", line + "\n", self._srv_line_tag(line))
                        self.srv_console_text.see("end")
                        self.srv_console_text.config(state="disabled")
                except Exception:
                    pass
                m = re.search(r"([A-Za-z0-9_]{3,16}) joined the game", line)
                if m:
                    self._srv_players.add(m.group(1))
                    self._srv_update_players()
                m = re.search(r"\[connected player\] ([A-Za-z0-9_]{3,16})", line)
                if m:
                    self._srv_players.add(m.group(1))
                    self._srv_update_players()
                m = re.search(r"([A-Za-z0-9_]{3,16})(?: left the game| lost connection| has disconnected)", line)
                if m:
                    self._srv_players.discard(m.group(1))
                    self._srv_update_players()
                if "Done (" in line:
                    self._srv_set_status("online")
        except queue.Empty:
            pass
        self.window.after(150, self._srv_pump)

    def _srv_update_players(self):
        try:
            if hasattr(self, "srv_players_list") and self.srv_players_list.winfo_exists():
                self.srv_players_list.delete(0, "end")
                for p in sorted(self._srv_players):
                    self.srv_players_list.insert("end", p)
            if hasattr(self, "srv_players_list") and self.srv_players_list.winfo_exists():
                parent = self.srv_players_list.master
                for w in parent.winfo_children():
                    if isinstance(w, tk.Label) and w.cget("text").startswith("PLAYERS"):
                        w.config(text=f"PLAYERS ONLINE · {len(self._srv_players)}")
        except Exception:
            pass

                                   
    def _fm_refresh(self):
        try:
            if not hasattr(self, "srv_files_list") or not self.srv_files_list.winfo_exists():
                return
            self.srv_files_list.delete(0, "end")
            d = self._fm_path
            if not d or not os.path.isdir(d):
                return
            root = os.path.normpath(self._srv_dir(self._srv_selected))
            if os.path.normpath(d) != root:
                self.srv_files_list.insert("end", "📁  ..")
            entries = sorted(os.listdir(d), key=str.lower)
            for name in entries:
                if name.endswith(".spark-part"):
                    continue
                if os.path.isdir(os.path.join(d, name)):
                    self.srv_files_list.insert("end", f"📁  {name}")
            for name in entries:
                if os.path.isfile(os.path.join(d, name)):
                    self.srv_files_list.insert("end", f"📄  {name}")
        except Exception:
            pass

    def _fm_navigate(self):
        sel = self.srv_files_list.curselection()
        if not sel:
            return
        item = self.srv_files_list.get(sel[0])
        name = item[3:].strip() if item.startswith(("📁", "📄")) else item
        d = self._fm_path
        if name == "..":
            self._fm_up()
            return
        p = os.path.join(d, name)
        if os.path.isdir(p):
            self._fm_path = p
            self._fm_refresh()

    def _fm_up(self):
        root = os.path.normpath(self._srv_dir(self._srv_selected))
        d = os.path.normpath(self._fm_path or root)
        if d != root and d.startswith(root):
            self._fm_path = os.path.dirname(d)
            self._fm_refresh()

    def _fm_delete(self):
        sel = self.srv_files_list.curselection()
        if not sel:
            return
        item = self.srv_files_list.get(sel[0])
        name = item[3:].strip() if item.startswith(("📁", "📄")) else item
        if name == "..":
            return
        p = os.path.join(self._fm_path, name)
        if askquestion("Delete", f"Delete '{name}'?") != "yes":
            return
        try:
            if os.path.isdir(p):
                shutil.rmtree(p, ignore_errors=True)
            else:
                os.remove(p)
            self._fm_refresh()
        except Exception as e:
            showerror("Error", str(e))

    def _fm_open(self):
        try:
            os.startfile(self._fm_path)
        except Exception as e:
            showerror("Error", str(e))

                              
    _LOADER_IDS = {"Forge": "forge", "NeoForge": "neoforge", "Fabric": "fabric", "Quilt": "quilt"}
    _MODRINTH_UA = {"User-Agent": "Spark-Launcher/1.0 (minecraft instance launcher)"}

    def _loader_id(self):
        return self._LOADER_IDS.get(self.download_options.get())

    def _current_mc_version(self):
        v = self.versionsList.get()
        if not v or v.startswith(("Loading", "No ")):
            return None
        t = self.download_options.get()
        if t == "Vanilla":
            return self.versions_map.get(v, v.replace("release ", "").replace("snapshot ", "").strip())
        if t == "Forge":
            return v.split("-", 1)[0]
        return v

    def open_mods_window(self):
        loader = self._loader_id()
        if not loader:
            showerror("Mods", "Vanilla cannot load mods.")
            return
        if getattr(self, "mods_win", None) is not None and self.mods_win.winfo_exists():
            self.mods_win.lift()
            self.mods_win.focus_force()
            return
        mc_ver = self._current_mc_version()
        L = self
        win = tk.Toplevel(self.window)
        self.mods_win = win
        win.title("Mod Downloader")
        sw, sh = win.winfo_screenwidth(), win.winfo_screenheight()
        ww, wh = min(780, sw - 60), min(560, sh - 80)
        win.geometry(f"{ww}x{wh}+{(sw - ww) // 2}+{(sh - wh) // 2}")
        win.configure(bg=L.card)
        win.minsize(680, 480)
        try:
            win.iconbitmap("icon.ico")
        except Exception:
            pass

        head = tk.Frame(win, bg=L.card)
        head.pack(fill="x", padx=14, pady=(10, 2))
        tk.Label(head, text="MOD DOWNLOADER", font=L.f_title, bg=L.card, fg=L.fg).pack(side="left")
        tk.Label(head, text=f"{loader}  ·  {mc_ver or '?'}  ·  {L.instance_var.get()}",
                 font=L.f_small, bg=L.card, fg=L.accent).pack(side="right")

        search_row = tk.Frame(win, bg=L.card)
        search_row.pack(fill="x", padx=14, pady=(8, 4))
        L.mods_search_var = StringVar()
        search_entry = L._entry(search_row, textvariable=L.mods_search_var, font=L.f_small)
        search_entry.pack(side="left", fill="x", expand=True, ipady=5)
        search_entry.bind("<Return>", lambda e: L._mods_search(win))
        search_b = L._button(search_row, "Search", lambda: L._mods_search(win),
                             L.accent, L.accent_hover, L.f_small, w=80, h=30, radius=12)
        search_b.pack(side="left", padx=(8, 0))

        body = tk.Frame(win, bg=L.card)
        body.pack(fill="both", expand=True, padx=14, pady=(4, 8))

                       
        left = tk.Frame(body, bg=L.card)
        left.pack(side="left", fill="both", expand=True, padx=(0, 10))
        tree_wrap = RoundedFrame(left, L.card, L.border, radius=16, surround=L.bg)
        tree_wrap.pack(fill="both", expand=True)
        L.mods_tree = ttk.Treeview(tree_wrap, columns=("name", "author", "dl"), show="headings")
        L.mods_tree.heading("name", text="Mod")
        L.mods_tree.heading("author", text="Author")
        L.mods_tree.heading("dl", text="Downloads")
        L.mods_tree.column("name", width=250)
        L.mods_tree.column("author", width=110, anchor="center")
        L.mods_tree.column("dl", width=90, anchor="center")
        t_scroll = ttk.Scrollbar(tree_wrap, orient="vertical", command=L.mods_tree.yview)
        L.mods_tree.configure(yscrollcommand=t_scroll.set)
        t_scroll.pack(side="right", fill="y")
        L.mods_tree.pack(side="left", fill="both", expand=True)
        L.mods_tree.bind("<Double-1>", lambda e: L._mods_install(win))

        install_row = tk.Frame(left, bg=L.card)
        install_row.pack(fill="x", pady=(8, 0))
        inst_b = L._button(install_row, "INSTALL SELECTED", lambda: L._mods_install(win),
                           L.green, L.green_hover, L.f_btn, w=170, h=32, radius=12)
        inst_b.pack(side="left")
        L.mods_status = tk.Label(install_row, text="", bg=L.card, fg=L.muted, font=L.f_small)
        L.mods_status.pack(side="left", padx=(10, 0))

                               
        right = tk.Frame(body, bg=L.card, width=240)
        right.pack(side="right", fill="y")
        right.pack_propagate(False)
        tk.Label(right, text="INSTALLED MODS", font=L.f_tiny, bg=L.card, fg=L.muted).pack(anchor="w", pady=(0, 4))
        list_wrap = RoundedFrame(right, L.card, L.border, radius=16, surround=L.bg)
        list_wrap.pack(fill="both", expand=True)
        L.mods_installed_list = tk.Listbox(
            list_wrap, bg=L.input_bg, fg="white", relief="flat",
            highlightthickness=0, selectbackground=L.accent, selectforeground="white",
            font=("Segoe UI", 9), activestyle="none"
        )
        lb_scroll = ttk.Scrollbar(list_wrap, orient="vertical", command=L.mods_installed_list.yview)
        L.mods_installed_list.configure(yscrollcommand=lb_scroll.set)
        lb_scroll.pack(side="right", fill="y")
        L.mods_installed_list.pack(side="left", fill="both", expand=True)

        rm_b = L._button(right, "Remove Selected", lambda: L._mods_remove(win),
                         L.red, L.red_hover, L.f_small, h=30, radius=12)
        rm_b.pack(fill="x", pady=(8, 4))
        of_b = L._button(right, "Open Mods Folder", L._open_instance_folder,
                         L.gray_btn, L.gray_hover, L.f_small, h=30, radius=12)
        of_b.pack(fill="x")

        L._set_mods_status(win, "Searching Modrinth...")
        L._mods_search(win)

    def _set_mods_status(self, win, text):
        try:
            if win.winfo_exists():
                self.mods_status.config(text=text)
        except Exception:
            pass

    def _mods_search(self, win):
        loader = self._loader_id()
        mc_ver = self._current_mc_version()
        if not loader or not mc_ver:
            self._set_mods_status(win, "Select a mod-loadable version first.")
            return
        q = self.mods_search_var.get().strip()
        self._set_mods_status(win, "Searching Modrinth...")

        def work():
            try:
                facets = json.dumps([["categories:" + loader], ["versions:" + mc_ver]])
                params = {"facets": facets, "limit": 40}
                if q:
                    params["query"] = q
                else:
                    params["index"] = "downloads"
                r = SESSION.get("https://api.modrinth.com/v2/search", params=params,
                                timeout=20, headers=self._MODRINTH_UA)
                r.raise_for_status()
                hits = r.json().get("hits", [])
            except Exception as e:
                self.window.after(0, lambda: self._set_mods_status(win, f"Search failed: {e}"))
                return

            def apply():
                try:
                    if not win.winfo_exists():
                        return
                    self._mods_hits = hits
                    tree = self.mods_tree
                    tree.delete(*tree.get_children())
                    for h in hits:
                        dl = h.get("downloads", 0)
                        dl_s = f"{dl / 1000:.1f}k" if dl >= 1000 else str(dl)
                        tree.insert("", "end", iid=h.get("project_id"),
                                    values=(h.get("title"), h.get("author"), dl_s))
                    self._set_mods_status(win, f"{len(hits)} mods found" if hits else "No mods found")
                except Exception:
                    pass
            self.window.after(0, apply)

        Thread(target=work, daemon=True).start()

    def _mods_install(self, win):
        sel = self.mods_tree.selection()
        if not sel:
            self._set_mods_status(win, "Select a mod first.")
            return
        pid = sel[0]
        loader = self._loader_id()
        mc_ver = self._current_mc_version()
        if not loader or not mc_ver:
            self._set_mods_status(win, "Select a mod-loadable version first.")
            return
        title = self.mods_tree.item(pid, "values")[0]
        target_mods = os.path.join(self._target_dir(), "mods")
        self._set_mods_status(win, f"Fetching {title}...")

        def work():
            try:
                r = SESSION.get(
                    f"https://api.modrinth.com/v2/project/{pid}/version",
                    params={"game_versions": json.dumps([mc_ver]), "loaders": json.dumps([loader])},
                    timeout=20, headers=self._MODRINTH_UA
                )
                r.raise_for_status()
                versions = r.json()
                if not versions:
                    raise Exception(f"No {loader} build for {mc_ver}")
                v = versions[0]
                files = v.get("files") or []
                if not files:
                    raise Exception("No downloadable file found")
                f = next((x for x in files if x.get("primary")), files[0])
                url, fname = f["url"], f["filename"]
                os.makedirs(target_mods, exist_ok=True)
                path = os.path.join(target_mods, fname)
                self._console_write(f"[mods] Downloading {fname}", "info")
                with SESSION.get(url, stream=True, timeout=120, headers=self._MODRINTH_UA) as d:
                    d.raise_for_status()
                    total = int(d.headers.get("content-length") or 0)
                    with open(path, "wb") as fh:
                        for chunk in d.iter_content(65536):
                            fh.write(chunk)
                            if total:
                                pct = int(100 * fh.tell() / total)

                                def upd(p=pct, n=fname):
                                    if win.winfo_exists():
                                        self._set_mods_status(win, f"Downloading {n} — {p}%")
                                self.window.after(0, upd)
                self._console_write(f"[mods] Installed {fname} into '{os.path.basename(os.path.dirname(target_mods))}'", "info")

                def done():
                    if win.winfo_exists():
                        self._set_mods_status(win, f"Installed {fname}")
                        self._mods_refresh_installed(win)
                self.window.after(0, done)
            except Exception as e:
                try:
                    os.remove(path)
                except Exception:
                    pass
                self.window.after(0, lambda: self._set_mods_status(win, f"Install failed: {e}"))

        Thread(target=work, daemon=True).start()

    def _mods_refresh_installed(self, win):
        mods_dir = os.path.join(self._target_dir(), "mods")
        try:
            files = sorted(f for f in os.listdir(mods_dir) if f.lower().endswith(".jar"))
        except Exception:
            files = []
        try:
            lb = self.mods_installed_list
            lb.delete(0, "end")
            for f in files:
                lb.insert("end", f)
        except Exception:
            pass

    def _mods_remove(self, win):
        lb = self.mods_installed_list
        sel = lb.curselection()
        if not sel:
            return
        fname = lb.get(sel[0])
        p = os.path.join(self._target_dir(), "mods", fname)
        try:
            os.remove(p)
            self._console_write(f"[mods] Removed {fname}", "info")
            self._mods_refresh_installed(win)
        except Exception as e:
            showerror("Error", str(e))

                              
    _AUTH_SHORT = {"mojang login": "mojang", "cracked login": "cracked", "ely_by login": "ely.by"}
    _AVATAR_COLORS = ["#4f8cff", "#22c55e", "#f59e0b", "#a78bfa", "#ec4899", "#14b8a6", "#f97316"]

    def _accounts(self):
        data = load_settings()
        return [a for a in (data.get("User-info") or []) if isinstance(a, dict) and a.get("username")]

    def _accounts_mousewheel(self, event):
        try:
            w = self.window.winfo_containing(event.x_root, event.y_root)
        except Exception:
            return
        if w is not None and str(w).startswith(str(self.accounts_canvas)):
            self.accounts_canvas.yview_scroll(-1 * (event.delta // 120), "units")

    def _refresh_accounts(self, highlight=None):
        def apply():
            self._rebuild_account_rows(highlight)
        self.window.after(0, apply)

    def _rebuild_account_rows(self, highlight=None):
        for w in self.accounts_inner.winfo_children():
            w.destroy()
        accs = self._accounts()
        if hasattr(self, "accounts_count_lbl"):
            self.accounts_count_lbl.config(text=f"ACCOUNTS · {len(accs)}")
        if not accs:
            tk.Label(
                self.accounts_inner, text="No saved accounts yet",
                bg=self.input_bg, fg=self.muted, font=self.f_small
            ).pack(anchor="w", padx=8, pady=8)
            return
        for i, a in enumerate(accs):
            self._account_row(a, highlight, i)

    def _account_row(self, a, highlight=None, index=0):
        uname = a.get("username") or ""
        auth = self._AUTH_SHORT.get(a.get("AUTH_TYPE") or "", a.get("AUTH_TYPE") or "")
        if highlight == uname:
            row_bg = "#2b3a55"
        elif index % 2 == 0:
            row_bg = self.input_bg
        else:
            row_bg = "#2a303b"
        row = tk.Frame(self.accounts_inner, bg=row_bg)
        row.pack(fill="x", padx=3, pady=1)
        av_size = max(14, self._x(20))
        av = tk.Canvas(row, width=av_size, height=av_size, bg=row_bg, highlightthickness=0, bd=0)
        av_color = self._AVATAR_COLORS[sum(map(ord, uname)) % len(self._AVATAR_COLORS)]
        av.create_oval(1, 1, av_size - 2, av_size - 2, fill=av_color, outline="")
        av.create_text(
            av_size // 2, av_size // 2, text=uname[:1].upper(),
            fill="white", font=("Segoe UI", max(6, self._fs(8)), "bold")
        )
        av.pack(side="left", padx=(3, 6), pady=2)
        display = uname if len(uname) <= 12 else uname[:11] + "…"
        name_lbl = tk.Label(
            row, text=display, bg=row_bg, fg="white",
            font=self.f_small, cursor="hand2"
        )
        name_lbl.pack(side="left")
        type_lbl = tk.Label(
            row, text=auth, bg=row_bg, fg=self.muted, font=self.f_tiny
        )
        type_lbl.pack(side="left", padx=(4, 0))

        def login_action(event=None, u=uname):
            self._account_login(u)

        name_lbl.bind("<Button-1>", login_action)
        name_lbl.bind("<Enter>", lambda e: name_lbl.config(fg=self.accent_hover))
        name_lbl.bind("<Leave>", lambda e: name_lbl.config(fg="white"))

        row_widgets = [row, av, name_lbl, type_lbl]

        def row_hover(e, on):
            c = self.row_hover if on else row_bg
            for wdg in row_widgets:
                try:
                    wdg.config(bg=c)
                except Exception:
                    pass

        for wdg in row_widgets:
            wdg.bind("<Enter>", lambda e: row_hover(e, True))
            wdg.bind("<Leave>", lambda e: row_hover(e, False))

        for text, color, hover, cmd in (
            ("Del", self.red, self.red_hover, lambda u=uname: self._account_delete(u)),
            ("Edit", self.accent, self.accent_hover, lambda u=uname: self._account_edit(u)),
            ("Login", self.green, self.green_hover, login_action),
        ):
            b = RoundButton(
                row, self, text, cmd, color, hover, "#ffffff",
                base=8, weight=None, radius=9, surround=row_bg,
                w=self._x(46), h=self._x(20)
            )
            b.pack(side="right", padx=(2, 0))

    def _account_login(self, username):
        acc = next((a for a in self._accounts() if a.get("username") == username), None)
        if not acc:
            return
        self.entry0.delete(0, "end")
        self.entry0.insert(0, username)
        t = acc.get("AUTH_TYPE")
        if t in ("mojang login", "cracked login", "ely_by login"):
            self.acc_options.set(t)
        self.status_lbl.config(text=f"Selected account: {username}")

    def _account_edit(self, username):
        acc = next((a for a in self._accounts() if a.get("username") == username), None)
        if not acc:
            return
        dlg = tk.Toplevel(self.window)
        dlg.title("Edit Account")
        dw, dh = self._x(300), self._y(200)
        dlg.geometry(
            f"{dw}x{dh}+{self.window.winfo_rootx() + self._x(220)}+{self.window.winfo_rooty() + self._y(140)}"
        )
        dlg.configure(bg=self.bg)
        dlg.transient(self.window)
        dlg.grab_set()
        dlg.resizable(False, False)
        try:
            dlg.iconbitmap("icon.ico")
        except Exception:
            pass
        tk.Label(dlg, text="Edit Account", font=self.f_title, bg=self.bg, fg=self.fg).pack(pady=(10, 2))
        tk.Frame(dlg, bg=self.accent, height=3, bd=0, width=self._x(46)).pack(anchor="w", padx=14, pady=(2, 8))
        tk.Label(dlg, text="USERNAME", font=self.f_tiny, bg=self.bg, fg=self.muted).pack(anchor="w", padx=14)
        name_var = StringVar(value=username)
        name_entry = self._entry(dlg, textvariable=name_var, font=self.f_entry)
        name_entry.pack(fill="x", padx=14, pady=(2, 8), ipady=4)
        tk.Label(dlg, text="ACCOUNT TYPE", font=self.f_tiny, bg=self.bg, fg=self.muted).pack(anchor="w", padx=14)
        type_var = StringVar(value=acc.get("AUTH_TYPE") or "cracked login")
        type_cb = ttk.Combobox(
            dlg, textvariable=type_var,
            values=("mojang login", "cracked login", "ely_by login"), state="readonly"
        )
        type_cb.pack(fill="x", padx=14, pady=(2, 12))

        def do_save():
            new_name = name_var.get().strip()
            if not new_name:
                showerror("Error", "Username cannot be empty")
                return
            self._update_account(username, new_name, type_var.get())
            if self.entry0.get() == username:
                self.entry0.delete(0, "end")
                self.entry0.insert(0, new_name)
            dlg.destroy()
            self.status_lbl.config(text=f"Account updated: {new_name}")

        btns = tk.Frame(dlg, bg=self.bg)
        btns.pack(pady=6, fill="x", padx=14)
        save_b = self._button(btns, "Save", do_save, self.green, self.green_hover, self.f_small, h=30, radius=12)
        save_b.pack(side="left", padx=(0, 4), fill="x", expand=True)
        cancel_b = self._button(btns, "Cancel", dlg.destroy, self.gray_btn, self.gray_hover, self.f_small, h=30, radius=12)
        cancel_b.pack(side="left", fill="x", expand=True)

    def _account_delete(self, username):
        data = load_settings()
        accs = [a for a in (data.get("User-info") or []) if isinstance(a, dict) and a.get("username")]
        data["User-info"] = [a for a in accs if a.get("username") != username]
        save_settings(data)
        self._refresh_accounts()
        self.status_lbl.config(text=f"Account removed: {username}")

    def _update_account(self, old_username, new_username, auth_type):
        data = load_settings()
        accs = [a for a in (data.get("User-info") or []) if isinstance(a, dict) and a.get("username")]
        for a in accs:
            if a.get("username") == old_username:
                a["username"] = new_username
                if auth_type:
                    a["AUTH_TYPE"] = auth_type
                break
        data["User-info"] = accs
        save_settings(data)
        self._refresh_accounts(new_username)

    def _upsert_account(self, username, auth_type, uid=None):
        data = load_settings()
        accs = [a for a in (data.get("User-info") or []) if isinstance(a, dict) and a.get("username")]
        for a in accs:
            if a.get("username") == username:
                a["AUTH_TYPE"] = auth_type
                if uid:
                    a["UUID"] = uid
                data["User-info"] = accs
                save_settings(data)
                self._refresh_accounts(username)
                return
        accs.append({"username": username, "AUTH_TYPE": auth_type, "UUID": uid})
        data["User-info"] = accs
        save_settings(data)
        self._refresh_accounts(username)

    def open_settings(self):
        SettingsWindow(self.window, self)

    def printProgressBar(self, iteration, total, prefix="", suffix="", decimals=1, length=40, fill="█", printEnd="\r"):
        percent = ("{0:." + str(decimals) + "f}").format(100 * (iteration / float(total))) if total else "0"
        filledLength = int(length * iteration // total) if total else 0
        bar = fill * filledLength + "-" * (length - filledLength)
        print(f"\r{prefix} |{bar}| {percent}% {suffix}", end=printEnd)
        if iteration == total:
            print()

    def maximum(self, max_value, value):
        max_value[0] = value

    def get_custom_jvm_args(self):
        data = load_settings()
        raw = data.get("jvm-args")
        if not raw:
            return []
                                                                           
        return [
            p.strip() for p in str(raw).split()
            if p.strip() and not p.startswith("-Xmx") and not p.startswith("-Xms")
        ]

    def get_jvm(self):
        self.data = load_settings()
        allocated = self.data.get("allocated_ram") or default_ram
        fps = self.data.get("Fps-Boost") and self.data["setting-info"][0].get("fps_boost_selected")
        ram_gb = max(1, int(allocated // 1000) if allocated >= 1000 else 1)
        if allocated < 1000:
            ram_mb = max(512, int(allocated))
            base = f"-Xmx{ram_mb}M -Xms128M"
        else:
            base = f"-Xmx{ram_gb}G -Xms128M"
                                
        if LOW_END or self.light:
            result = f"{base} -XX:+UseG1GC -XX:MaxGCPauseMillis=50"
        elif fps:
            cpu = os.cpu_count() or 2
            result = f"-XX:+UnlockExperimentalVMOptions {base} -XX:ParallelGCThreads={max(1, cpu)}"
        else:
            result = base
        extra = self.get_custom_jvm_args()
        if extra:
            result = f"{result} {' '.join(extra)}"
        return result

    def _java_major(self):
        if getattr(self, "_java_major_cache", "unset") != "unset":
            return self._java_major_cache
        major = None
        try:
            r = subprocess.run(["java", "-version"], capture_output=True, text=True, timeout=15)
            m = re.search(r'version "(\d+)', (r.stderr or "") + (r.stdout or ""))
            if m:
                major = int(m.group(1))
        except Exception:
            pass
        self._java_major_cache = major
        return major

    def _java_major_of(self, exe):
        try:
            r = subprocess.run([exe, "-version"], capture_output=True, text=True, timeout=15)
            m = re.search(r'version "(\d+)', (r.stderr or "") + (r.stdout or ""))
            return int(m.group(1)) if m else None
        except Exception:
            return None

    def _fix_java_for_version(self, cmd, version_id, game_dir):
\
                                                              
        req_major = None
        component = None
        try:
            data = minecraft_launcher_lib.runtime.get_client_json(version_id, game_dir)
            jv = data.get("javaVersion") or {}
            req_major = jv.get("majorVersion")
            component = jv.get("component")
        except Exception:
            pass
        sys_major = self._java_major()
        if req_major and (sys_major is None or sys_major < req_major):
            exe = None
            try:
                exe = minecraft_launcher_lib.runtime.get_executable_path(component, game_dir)
            except Exception:
                pass
            if not exe or not os.path.isfile(exe):
                try:
                    self._console_write(f"[Spark-Launcher] Installing Java runtime {component} (Java {req_major}) — this may take a minute...", "info")
                    cb = {"setStatus": lambda s: self._console_write(f"[Spark-Launcher] {s}", "info")}
                    minecraft_launcher_lib.runtime.install_jvm_runtime(component, game_dir, callback=cb)
                    exe = minecraft_launcher_lib.runtime.get_executable_path(component, game_dir)
                except Exception as e:
                    self._console_write(f"[Spark-Launcher] Runtime install failed: {e}", "warn")
            if exe and os.path.isfile(exe):
                cmd[0] = exe
                self._console_write(f"[Spark-Launcher] Using Mojang runtime '{component}' (Java {req_major})", "info")
        chosen_major = sys_major if cmd[0] == "java" else self._java_major_of(cmd[0])
        if chosen_major is not None:
            def supported(flag):
                if flag.startswith("--sun-misc-unsafe-memory-access"):
                    return chosen_major >= 23
                if flag.startswith("--enable-native-access"):
                    return chosen_major >= 21
                return True
            cmd[:] = [a for i, a in enumerate(cmd) if i == 0 or supported(a)]
        return cmd

    def run_mc(self):
        login_method = self.acc_options.get()
        runtime_ver = self.download_options.get()
        self.data = load_settings()
        self.mc_dir = self.path_var.get() or self.data.get("Minecraft-home")
        game_dir = self._target_dir()
        self._last_game_dir = game_dir
        j1 = self.get_jvm()
        allocated = self.data.get("allocated_ram") or default_ram
        ram_gb = max(1, int(allocated // 1000) if allocated >= 1000 else 1)

        try:
                                                                    
            if runtime_ver == "Vanilla":
                mc_ver = self.versionsList.get()
                detected = self.versions_map.get(
                    mc_ver, mc_ver.replace("release ", "").replace("snapshot ", "").strip()
                )
            elif runtime_ver == "Forge":
                mc_ver = self.versionsList.get()
                parts = mc_ver.split("-", 1)
                detected = f"{parts[0]}-forge-{parts[1]}" if len(parts) == 2 else mc_ver
            elif runtime_ver == "NeoForge":
                mc_ver = self.versionsList.get()
                nf = mod_loader.Neoforge()
                loaders = nf.get_loader_versions(mc_ver, True)
                if not loaders:
                    raise Exception(f"No NeoForge loader found for {mc_ver}")
                detected = nf.get_installed_version(mc_ver, loaders[0])
            elif runtime_ver == "Fabric":
                lv = get_latest_loader_version()
                mc_ver = self.versionsList.get()
                detected = f"fabric-loader-{lv}-{mc_ver}"
            elif runtime_ver == "Quilt":
                lv = quilt.get_latest_loader_version()
                mc_ver = self.versionsList.get()
                detected = f"quilt-loader-{lv}-{mc_ver}"
            else:
                return

            if login_method == "mojang login":
                showerror("Mojang login", "Mojang login is no longer available (Mojang shut down its login servers). Use cracked login or ely_by login.")
                return

            if login_method == "cracked login":
                usr = self.entry0.get()
                saved_acc = next((a for a in self._accounts() if a.get("username") == usr), None)
                options = {
                    "username": usr,
                    "uuid": (saved_acc or {}).get("UUID") or uuid.uuid4().hex,
                    "token": "",
                    "jvmArguments": j1.split(),
                    "executablePath": "java"
                }
                self._upsert_account(options["username"], login_method, options["uuid"])
            elif login_method == "ely_by login":
                self.ely_authenticate()
                self.data = load_settings()
                j2 = [
                    rf"-javaagent:{currn_dir}/authlib/authlib-injector-1.1.39.jar=ely.by",
                    f"-Xmx{ram_gb}G" if allocated >= 1000 else f"-Xmx{int(allocated)}M",
                    "-Xms128M"
                ] + self.get_custom_jvm_args()
                ely_usr = self.entry0.get()
                ely_acc = next((a for a in self._accounts() if a.get("username") == ely_usr), {})
                options = {
                    "username": ely_usr,
                    "uuid": ely_acc.get("UUID"),
                    "token": self.data.get("accessToken") or "",
                    "jvmArguments": j2,
                    "executablePath": "java"
                }
                self._upsert_account(ely_usr, login_method, ely_acc.get("UUID"))
            else:
                return

            def _show_console():
                if not self.console_visible:
                    self._toggle_console()
            self.window.after(0, _show_console)
            self.window.withdraw()
            options["gameDirectory"] = game_dir
            cmd = minecraft_launcher_lib.command.get_minecraft_command(detected, game_dir, options)
            cmd = self._fix_java_for_version(cmd, detected, game_dir)
            self._launch_time = time.time()
            self._last_dl_pct = -1
            self._save_last_selection()
            self._console_write(f"[Spark-Launcher] Launching {runtime_ver}: {detected}  (instance: {self.instance_var.get()})", "info")
            creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0) if os.name == "nt" else 0
            proc = subprocess.Popen(
                cmd, cwd=game_dir,
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                text=True, errors="replace", bufsize=1,
                creationflags=creationflags
            )
            self._mc_proc = proc
            for line in proc.stdout:
                self._console_write(line.rstrip("\r\n"))
            code = proc.wait()
            self._mc_proc = None
            self._console_write(f"[Spark-Launcher] Game exited with code {code}", "info")
            self.window.after(0, lambda c=code, d=detected: self._after_game_exit(c, d))

        except minecraft_launcher_lib.exceptions.VersionNotFound:
            showerror("Version not installed", f"'{detected}' is not installed in this instance. Select it and press Download first.")
        except Exception as e:
            showerror("Error", str(e))
        finally:
            try:
                self.window.deiconify()
            except Exception:
                pass

    def ely_authenticate(self):
        usr = self.entry0.get()
        pwd = self.entry1.get()
        client_token = str(uuid.uuid4())
        acc_data = {"username": usr, "password": pwd, "clientToken": client_token, "requestUser": True}
        r = requests.get(f"https://authserver.ely.by/api/users/profiles/minecraft/{usr}", timeout=15)
        if r.status_code == 200:
            r1 = requests.post("https://authserver.ely.by/auth/authenticate", data=acc_data, timeout=15)
            if r1.status_code == 200:
                accessToken = r1.json()["accessToken"]
                uid = r1.json()["user"]["id"]
                self.data = load_settings()
                self.data["clientToken"] = client_token
                self.data["accessToken"] = accessToken
                save_settings(self.data)
                self._upsert_account(usr, "ely_by login", uid)
            else:
                showerror("Error", f"Login failed. Code: {r1.status_code}")
                raise Exception("ely auth failed")
        elif r.status_code == 404:
            showerror("User not found", "User does not exist")
            raise Exception("user not found")

    def _required_java(self, mc_ver):
        try:
            parts = [int(x) for x in re.findall(r"\d+", str(mc_ver))[:3]]
            while len(parts) < 3:
                parts.append(0)
            if parts[0] > 1 or (parts[0] == 1 and parts[1] >= 21):
                return 21
            if parts[0] == 1 and parts[1] == 20 and parts[2] >= 5:
                return 21
            return 17
        except Exception:
            return 17

    def _check_java(self, min_major):
                                                                                
        try:
            r = subprocess.run(["java", "-version"], capture_output=True, text=True, timeout=15)
            out = (r.stderr or "") + (r.stdout or "")
            m = re.search(r'version "(\d+)', out)
            if not m:
                return True, None
            major = int(m.group(1))
            return major >= min_major, major
        except FileNotFoundError:
            return False, None
        except Exception:
            return True, None

    def _java_preflight(self, mc_ver):
        need = self._required_java(mc_ver)
        ok, have = self._check_java(need)
        if not ok:
            if have is None:
                raise Exception(f"Java was not found on this PC. This version needs Java {need}+ — install it (e.g. Adoptium Temurin {need}) and make sure 'java' is on PATH.")
            raise Exception(f"This version needs Java {need}+, but Java {have} was found. Install Java {need} (e.g. Adoptium Temurin {need}) and make sure 'java' is on PATH.")

    def download(self):
        dl_opt = self.download_options.get()
        self.mc_dir = self.path_var.get() or self.mc_dir
        target = self._target_dir()
        self._last_game_dir = target
        try:
            os.makedirs(os.path.join(target, "versions"), exist_ok=True)
        except Exception as e:
            showerror("Error", f"Cannot create the target folder:\n{target}\n\n{e}")
            return
        max_value = [0]
        self._dl_value = 0
        self._dl_max = 0

        def _set_max(v):
            self._dl_max = float(v or 0)
            self.maximum(max_value, v)

        def _set_prog(v):
            self._dl_value = float(v or 0)

        callback = {
            "setStatus": lambda text: self._console_write(f"[download] {text}", "info"),
            "setProgress": _set_prog,
            "setMax": _set_max
        }
        try:
            if dl_opt == "Vanilla":
                selected = self.versionsList.get()
                detected = self.versions_map.get(
                    selected, str(selected).replace("release ", "").replace("snapshot ", "").strip()
                )
                self._console_write(f"[download] Installing {selected} into '{os.path.basename(target)}'", "info")
                minecraft_launcher_lib.install.install_minecraft_version(detected, target, callback=callback)
                mods_dir = os.path.join(target, "mods")
                if os.path.isdir(mods_dir):
                    shutil.rmtree(mods_dir, ignore_errors=True)
                    self._console_write("[download] Removed mods folder (vanilla does not load mods)", "info")
            elif dl_opt == "Forge":
                selected = self.versionsList.get()
                self._console_write(f"[download] Installing Forge {selected} into '{os.path.basename(target)}'", "info")
                self._java_preflight(selected.split("-", 1)[0])
                attempt = 0
                while True:
                    try:
                        if supports_automatic_install(selected):
                            install_forge_version(selected, target, callback=callback)
                        else:
                            run_forge_installer(selected)
                        break
                    except Exception as e:
                        attempt += 1
                        if attempt >= 3:
                            if "ssl" in str(e).lower() or "connection" in str(e).lower():
                                raise Exception(f"Forge servers unreachable after retries ({e}). Check your internet connection and try again.")
                            raise
                        self._console_write(f"[download] Forge install failed ({e}) — retrying ({attempt}/2)...", "warn")
                        time.sleep(2)
            elif dl_opt == "NeoForge":
                selected = self.versionsList.get()
                self._console_write(f"[download] Installing NeoForge for {selected} into '{os.path.basename(target)}'", "info")
                nf = mod_loader.Neoforge()
                loaders = nf.get_loader_versions(selected, True)
                if not loaders:
                    raise Exception(f"No NeoForge loader found for {selected}")
                self._java_preflight(selected)
                try:
                    nf.install(selected, target, callback, "java", loaders[0])
                except Exception as e:
                    raise Exception(f"NeoForge installer failed ({e}).")
            elif dl_opt == "Fabric":
                selected = self.versionsList.get()
                self._console_write(f"[download] Installing Fabric {selected} into '{os.path.basename(target)}'", "info")
                install_fabric(selected, target, callback=callback)
            elif dl_opt == "Quilt":
                selected = self.versionsList.get()
                self._console_write(f"[download] Installing Quilt for {selected} into '{os.path.basename(target)}'", "info")
                self._java_preflight(selected)
                try:
                    quilt.install_quilt(selected, target, callback=callback, java="java")
                except Exception as e:
                    raise Exception(f"Quilt installer failed ({e}).")
            self._console_write("[download] Done", "info")
            self._refresh_instances()
            self.window.after(0, self._load_vanilla_versions)
            showinfo("Done", f"Download completed into '{os.path.basename(target)}'")
        except Exception as e:
            showerror("Error", str(e))

    def handle_download(self):
        dl_opt = self.download_options.get()
        size_text = "Unknown"
        version_label = ""
        try:
            if dl_opt == "Vanilla":
                selected = self.versionsList.get()
                version_label = selected
                detected = self.versions_map.get(
                    selected, str(selected).replace("release ", "").replace("snapshot ", "").strip()
                )
                self.status_lbl.config(text="Calculating size...")
                self.window.update_idletasks()
                size_bytes = get_vanilla_download_size(detected)
                size_text = format_size(size_bytes)
            elif dl_opt == "Forge":
                version_label = self.versionsList.get()
                size_text = "Approx (Forge)"
            elif dl_opt == "NeoForge":
                version_label = self.versionsList.get()
                size_text = "Approx (NeoForge)"
            elif dl_opt == "Fabric":
                version_label = self.versionsList.get()
                size_text = "Approx (Fabric)"
            elif dl_opt == "Quilt":
                version_label = self.versionsList.get()
                size_text = "Approx (Quilt)"
        except Exception:
            size_text = "Unknown"

        msg = f"Version: {version_label}\nType: {dl_opt}\nDownload size: {size_text}\n\nStart download?"
        if askquestion("Download", msg) != "yes":
            self.status_lbl.config(text="Ready")
            return
        self._dl_start()
        self.status_lbl.config(text=f"Downloading... ({size_text})")
        t = Thread(target=self.download, daemon=True)
        t.start()
        self._monitor_dl(t)

    def _monitor_dl(self, t):
        if t.is_alive():
            self.window.after(300, lambda: self._monitor_dl(t))
        else:
            self._dl_finish()
            self.status_lbl.config(text="Ready")

    def handle_run(self):
        self.status_dot.config(fg="#fbbf24")
        self.status_lbl.config(text="Launching...")
        t = Thread(target=self.run_mc, daemon=True)
        t.start()
        self._monitor_mc(t)

    def _monitor_mc(self, t):
        if t.is_alive():
            self.window.after(300, lambda: self._monitor_mc(t))
        else:
            self.status_lbl.config(text="Ready")
            try:
                self.window.deiconify()
            except Exception:
                pass

class SettingsWindow:
    def __init__(self, parent, launcher):
        self.launcher = launcher
        L = launcher
        self.data = load_settings()
        self.win = tk.Toplevel(parent)
        self.win.title("Settings")
        sw = self.win.winfo_screenwidth()
        sh = self.win.winfo_screenheight()
        ww, wh = min(500, sw - 40), min(620, sh - 60)
        self.win.geometry(f"{ww}x{wh}+{(sw - ww) // 2}+{(sh - wh) // 2}")
        self.win.configure(bg=L.bg)
        self.win.resizable(True, True)
        try:
            self.win.iconbitmap("icon.ico")
        except Exception:
            pass
        self.win.transient(parent)
        self.win.grab_set()

        tk.Label(self.win, text="Settings", font=L.f_title, bg=L.bg, fg=L.fg).pack(pady=(14, 0), anchor="w", padx=18)
        tk.Frame(self.win, bg=L.accent, height=3, bd=0, width=L._x(46)).pack(anchor="w", padx=18, pady=(4, 0))

        def card(title):
            outer = tk.Frame(self.win, bg=L.bg)
            outer.pack(fill="x", padx=18, pady=(12, 0))
            tk.Label(outer, text=title, font=L.f_tiny, bg=L.bg, fg=L.muted).pack(anchor="w", pady=(0, 4))
            box = RoundedFrame(outer, L.card, L.border, radius=16, surround=L.bg)
            box.pack(fill="x")
            inner = tk.Frame(box, bg=L.card)
            inner.pack(fill="both", expand=True, padx=12, pady=10)
            return inner

                        
        c1 = card("GAME DIRECTORY")
        self.path_var = StringVar(value=self.data.get("Minecraft-home", ""))
        row1 = tk.Frame(c1, bg=L.card)
        row1.pack(fill="x")
        L._entry(row1, textvariable=self.path_var, font=("Segoe UI", 9)).pack(
            side="left", fill="x", expand=True, ipady=5
        )
        browse_s = L._button(row1, "Browse", self.browse, L.accent, L.accent_hover, ("Segoe UI", 9), w=74, h=28)
        browse_s.pack(side="left", padx=(8, 0))

                     
        c2 = card("PERFORMANCE")
        self.fps_var = StringVar(value="selected" if self.data.get("Fps-Boost") else "deselected")
        ttk.Checkbutton(
            c2, text="FPS Boost", onvalue="selected", offvalue="deselected",
            variable=self.fps_var
        ).pack(anchor="w", pady=2)
        self.light_var = StringVar(value="selected" if self.data.get("Light-Mode", LOW_END) else "deselected")
        ttk.Checkbutton(
            c2, text="Low resource mode (no background)",
            onvalue="selected", offvalue="deselected", variable=self.light_var
        ).pack(anchor="w", pady=2)
        tk.Label(c2, text="RAM ALLOCATION", font=L.f_tiny, bg=L.card, fg=L.muted).pack(anchor="w", pady=(8, 0))
        try:
            total_ram = float(str(self.data["PC-info"][0]["Total-Ram"]).replace("GB", "").strip()) * 1000
        except Exception:
            total_ram = float(TOTAL_RAM_MB)
        self.ram_var = DoubleVar(value=self.data.get("allocated_ram") or default_ram)
        self.slider = ttk.Scale(
            c2, from_=512, to=max(1024, total_ram), variable=self.ram_var,
            command=self.on_ram
        )
        self.slider.pack(fill="x", pady=4)
        self.ram_lbl = tk.Label(
            c2, text=f"{int(self.ram_var.get())} MB  /  Total: {int(total_ram)} MB",
            bg=L.card, fg=L.muted, font=L.f_small
        )
        self.ram_lbl.pack()

                       
        c3 = card("JVM ARGUMENTS")
        tk.Label(
            c3, text="Extra flags passed to Java, space separated (e.g. -XX:+UseG1GC)",
            font=L.f_tiny, bg=L.card, fg=L.muted
        ).pack(anchor="w")
        self.jvm_var = StringVar(value=self.data.get("jvm-args") or "")
        L._entry(c3, textvariable=self.jvm_var, font=("Segoe UI", 9)).pack(fill="x", pady=(4, 0), ipady=5)

        save_s = L._button(self.win, "SAVE", self.save, L.green, L.green_hover, ("Segoe UI", 11, "bold"), h=36, radius=12)
        save_s.pack(fill="x", padx=18, pady=16)

    def browse(self):
        path = filedialog.askdirectory(title="Select Minecraft Folder", initialdir=self.path_var.get())
        if path:
            self.path_var.set(path)

    def on_ram(self, v=None):
        self.ram_lbl.config(text=f"{int(float(self.ram_var.get()))} MB")

    def save(self):
        path = self.path_var.get().strip()
        if path:
            self.data["Minecraft-home"] = path
            self.launcher.path_var.set(path)
            self.launcher.mc_dir = path
        fps = self.fps_var.get() == "selected"
        self.data["Fps-Boost"] = fps
        self.data["setting-info"][0]["fps_boost_selected"] = fps
        light = self.light_var.get() == "selected"
        self.data["Light-Mode"] = light
        self.launcher.light = light
        ram = float(self.ram_var.get())
        self.data["allocated_ram"] = ram
        self.data["setting-info"][0]["allocated_ram_selected"] = ram
        jvm = " ".join(self.jvm_var.get().split())
        self.data["jvm-args"] = jvm if jvm else None
        save_settings(self.data)
        showinfo("Saved", "Settings saved. Restart for full effect.")
        self.win.destroy()

if __name__ == "__main__":
                                              
    try:
        if os.name == "nt":
            import ctypes
            hwnd = ctypes.windll.kernel32.GetConsoleWindow()
            if hwnd:
                ctypes.windll.user32.ShowWindow(hwnd, 0)
    except Exception:
        pass
    try:
        Pycraft()
    except KeyboardInterrupt:
        pass

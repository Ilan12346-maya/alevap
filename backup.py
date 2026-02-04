import os
import shutil
import re
import sys
import datetime
import argparse

# ==============================================================================
#   ULTIMATIVES BACKUP & ROLLBACK SYSTEM (V3.4 - ALEV EDITION)
# ==============================================================================

### --- KONFIGURATION (HIER ANPASSEN) --- ###

PROJECT_NAME = "alevjd"
VERSION_FILE_PATH = 'app/build.gradle'

VERSION_NAME_REGEX = r'versionName\s+"(v[\d\.]+)'
VERSION_CODE_REGEX = r'versionCode\s+(\d+)'

BACKUP_ROOT_DIR = os.path.abspath(os.path.join(os.getcwd(), '..', 'backup', PROJECT_NAME))
BUILD_INDEX_FILE = "build.index"

# Diese Dateien werden NIE gesichert
BACKUP_IGNORE = [
    '.git', '.gradle', '.idea', 'build', 'app/build', 'app/.cxx', 'captures', 
    '*.iml', '*.log', '*.keystore', 'local.properties', BUILD_INDEX_FILE,
    '__pycache__', 'rollback.py', 'LAST_BACKUP_NAME',
    'termux-x11/build', 'termux-x11-preference/build', 'shell-loader/build'
]

# Diese Dateien werden beim Rollback NICHT gelöscht (bleiben erhalten)
ROLLBACK_PRESERVE = [
    '.git', '.gradle', '.idea', 'local.properties', BUILD_INDEX_FILE, 'backup.py'
]

### --- KONFIGURATION ENDE --- ###

# Style
GREEN = '\033[0;32m'
BLUE = '\033[0;34m'
YELLOW = '\033[1;33m'
RED = '\033[0;31m'
CYAN = '\033[0;36m'
NC = '\033[0m' 
BOLD = '\033[1m'
LINE = f"{BOLD}{BLUE}" + "-"*75 + f"{NC}"

class BackupManager:
    def __init__(self):
        self.source_dir = os.getcwd()
        self.backup_dir = BACKUP_ROOT_DIR
        self.version_file = os.path.join(self.source_dir, VERSION_FILE_PATH)
        self.build_index = self.load_build_index()
        if not os.path.exists(self.backup_dir): os.makedirs(self.backup_dir)

    def load_build_index(self):
        idx = {}
        if os.path.exists(BUILD_INDEX_FILE):
            try:
                with open(BUILD_INDEX_FILE, "r") as f:
                    for line in f:
                        if ":" in line:
                            parts = line.strip().split(":")
                            b_name = parts[0]
                            status = parts[1]
                            v_code = parts[2] if len(parts) > 2 else "?"
                            idx[b_name] = (status, v_code)
            except: pass
        return idx

    def log(self, msg, color=NC, bold=False):
        style = BOLD if bold else ""
        print(f"{color}{style}{msg}{NC}")

    def get_version_info(self):
        v_name, v_code = "v0.0.0", 0
        if not os.path.exists(self.version_file): return v_name, v_code
        try:
            with open(self.version_file, 'r') as f:
                content = f.read()
                name_match = re.search(VERSION_NAME_REGEX, content)
                code_match = re.search(VERSION_CODE_REGEX, content)
                if name_match: v_name = name_match.group(1)
                if code_match: v_code = int(code_match.group(1))
        except: pass
        return v_name, v_code

    def update_version_code(self, new_code):
        try:
            with open(self.version_file, 'r') as f: c = f.read()
            with open(self.version_file, 'w') as f: 
                f.write(re.sub(VERSION_CODE_REGEX, lambda m: m.group(0).replace(m.group(1), str(new_code)), c))
            return True
        except: return False

    def is_backup_ignored(self, path):
        rel = os.path.relpath(path, self.source_dir)
        if rel == ".": return False
        for p in BACKUP_IGNORE:
            if p.endswith('/') and rel.startswith(p[:-1]): return True
            if rel == p or (p.startswith('*') and rel.endswith(p[1:])): return True
            if p in rel.split(os.sep): return True
        return False

    def is_rollback_preserved(self, path):
        rel = os.path.relpath(path, self.source_dir)
        if rel == "." or rel == "backup.py": return True
        for p in ROLLBACK_PRESERVE:
            if rel == p or rel.startswith(p + os.sep): return True
        return False

    def get_backups_sorted(self):
        if not os.path.exists(self.backup_dir): return []
        return sorted([d for d in os.listdir(self.backup_dir) if os.path.isdir(os.path.join(self.backup_dir, d))])

    def parse_backup_name(self, name):
        match = re.match(r'(v[\d\.]+)_((?:full)|(?:inc_(\d+)))', name)
        if not match: return None
        return match.group(1), int(match.group(3)) if match.group(3) else 0

    def perform_backup(self):
        self.log(f"\n{LINE}", BLUE)
        self.log(f"   BACKUP SYSTEM: {PROJECT_NAME}", CYAN, True)
        self.log(f"{LINE}", BLUE)
        
        v_name, v_code = self.get_version_info()
        backups = self.get_backups_sorted()
        
        # Basis für Vergleich (Zeitpunkt des letzten Backups)
        backups_by_time = sorted(backups, key=lambda x: os.path.getmtime(os.path.join(self.backup_dir, x)))
        last_backup_path = os.path.join(self.backup_dir, backups_by_time[-1]) if backups_by_time else None
        
        # Namen bestimmen
        version_backups = [b for b in backups if b.startswith(v_name + "_")]
        if not version_backups:
            b_name = f"{v_name}_full"
            is_full = True
        else:
            max_inc = -1
            for b in version_backups:
                res = self.parse_backup_name(b)
                if res: max_inc = max(max_inc, res[1])
            b_name = f"{v_name}_inc_{max_inc + 1}"
            is_full = False

        dest = os.path.join(self.backup_dir, b_name)
        if not os.path.exists(dest): os.makedirs(dest)
        
        copied, deleted = 0, []
        self.log(f"Ziel: {b_name} ({'FULL' if is_full else 'INCREMENTAL'})", YELLOW, True)

        # 1. Scannen und Kopieren
        for root, dirs, files in os.walk(self.source_dir):
            dirs[:] = [d for d in dirs if not self.is_backup_ignored(os.path.join(root, d))]
            for file in files:
                src = os.path.join(root, file)
                if self.is_backup_ignored(src): continue
                rel = os.path.relpath(src, self.source_dir)
                
                # Check ob Kopieren nötig
                if is_full or (last_backup_path and os.path.getmtime(src) > os.path.getmtime(last_backup_path)):
                    d_path = os.path.join(dest, rel)
                    os.makedirs(os.path.dirname(d_path), exist_ok=True)
                    shutil.copy2(src, d_path)
                    self.log(f"  {GREEN}+ {rel}{NC}")
                    copied += 1
        
        # 2. Gelöschte finden
        if not is_full and last_backup_path:
            for root, _, files in os.walk(last_backup_path):
                for file in files:
                    if file == 'deleted_files.txt': continue
                    rel = os.path.relpath(os.path.join(root, file), last_backup_path)
                    if not os.path.exists(os.path.join(self.source_dir, rel)) and not self.is_backup_ignored(os.path.join(self.source_dir, rel)):
                        deleted.append(rel)
                        self.log(f"  {RED}- {rel} (gelöscht){NC}")

        if deleted:
            with open(os.path.join(dest, "deleted_files.txt"), "w") as f:
                for d in deleted: f.write(f"{d}\n")
        
        if copied == 0 and len(deleted) == 0:
            self.log("\nKeine Änderungen seit dem letzten Backup gefunden.", YELLOW)
        
        with open("LAST_BACKUP_NAME", "w") as f: f.write(b_name)
        self.log(f"\n{GREEN}{BOLD}[OK] Backup fertig! (+{copied} Dateien / -{len(deleted)} gelöscht){NC}")

    def perform_rollback(self, target_name):
        self.log(f"\n{LINE}", BLUE)
        self.log(f"   ROLLBACK TO: {target_name}", YELLOW, True)
        self.log(f"{LINE}", BLUE)
        
        # 0. Capture current state BEFORE rollback to ensure we can increment it
        pre_rollback_name, pre_rollback_code = self.get_version_info()
        self.log(f"Aktueller Stand im Workspace: {pre_rollback_name} (Code: {pre_rollback_code})", CYAN)

        backups_by_time = sorted([d for d in os.listdir(self.backup_dir) if os.path.isdir(os.path.join(self.backup_dir, d))], 
                                 key=lambda x: os.path.getmtime(os.path.join(self.backup_dir, x)))
        
        try: idx = backups_by_time.index(target_name)
        except: return self.log("Backup nicht gefunden!", RED)

        start = 0
        for i in range(idx, -1, -1):
            if "_full" in backups_by_time[i]: start = i; break
        chain = backups_by_time[start : idx + 1]
        
        self.log(f"Wiederherstellungs-Kette ({len(chain)} Ebenen):", CYAN)
        for c in chain: self.log(f"  -> {c}", CYAN)

        self.log("\n1. Workspace bereinigen...", YELLOW)
        for item in os.listdir(self.source_dir):
            if self.is_rollback_preserved(os.path.join(self.source_dir, item)): continue
            try:
                p = os.path.join(self.source_dir, item)
                if os.path.isfile(p): os.remove(p)
                else: shutil.rmtree(p)
                self.log(f"  [Clean] {item}")
            except: pass

        self.log("\n2. Wende Layer an...", YELLOW)
        for layer in chain:
            l_path = os.path.join(self.backup_dir, layer)
            self.log(f"  Layer: {layer}", BLUE)
            
            # Dateien kopieren
            for root, _, files in os.walk(l_path):
                for f in files:
                    if f == "deleted_files.txt": continue
                    rel = os.path.relpath(os.path.join(root, f), l_path)
                    
                    dst = os.path.join(self.source_dir, rel)
                    os.makedirs(os.path.dirname(dst), exist_ok=True)
                    shutil.copy2(os.path.join(root, f), dst)
                    self.log(f"    + {rel}", GREEN)
            
            # Löschungen anwenden
            d_file = os.path.join(l_path, "deleted_files.txt")
            if os.path.exists(d_file):
                with open(d_file) as f:
                    for line in f:
                        p = os.path.join(self.source_dir, line.strip())
                        if os.path.exists(p):
                            try:
                                if os.path.isfile(p): os.remove(p)
                                else: shutil.rmtree(p)
                                self.log(f"    X {line.strip()}", RED)
                            except: pass

        # 3. Update version code to be higher than what we had BEFORE the rollback
        new_code = pre_rollback_code + 1
        self.update_version_code(new_code)
        self.log(f"\n{GREEN}{BOLD}[OK] Rollback erfolgreich! Neuer Build-Code: {new_code}{NC}")

    def list_all_backups(self):
        groups = {}
        for b in self.get_backups_sorted():
            res = self.parse_backup_name(b)
            v = res[0] if res else "Legacy/Misc"
            if v not in groups: groups[v] = []
            groups[v].append(b)
        
        def version_key(v_str):
            try: return [int(p) for p in v_str.lstrip('v').split('.')]
            except: return [0, 0, 0]
        
        v_list = sorted(groups.keys(), key=version_key, reverse=True)
        self.log(f"\n{LINE}", BLUE)
        self.log(f"   BACKUP TREE: {PROJECT_NAME}", CYAN, True)
        self.log(f"{LINE}", BLUE)
        
        for v in v_list:
            print(f"\n{BOLD}{CYAN}Basis-Version: {v}{NC}")
            backups_with_inc = []
            for b in groups[v]:
                res = self.parse_backup_name(b)
                inc = res[1] if res else -1
                backups_with_inc.append((inc, b))
            backups_with_inc.sort(key=lambda x: x[0], reverse=True)

            for inc, b_name in backups_with_inc:
                status_info = self.build_index.get(b_name, ("UNKNOWN", "?"))
                status, v_code = status_info
                date = datetime.datetime.fromtimestamp(os.path.getmtime(os.path.join(self.backup_dir, b_name))).strftime('%d.%m %H:%M')
                color = GREEN if status == "SUCCESS" else (RED if status == "FAIL" else NC)
                label = "FULL" if inc == 0 else (f"Inc {inc}" if inc > 0 else "Legacy")
                print(f"  {color}{label:<8}{NC} | {b_name:<25} | {date} | {status:<8} | CV: {v_code}")

    def menu_select_version(self):
        groups = {}
        for b in self.get_backups_sorted():
            res = self.parse_backup_name(b)
            v = res[0] if res else "Legacy/Misc"
            if v not in groups: groups[v] = []
            groups[v].append(b)
        def version_key(v_str):
            try: return [int(p) for p in v_str.lstrip('v').split('.')]
            except: return [0, 0, 0]
        
        v_list = sorted(groups.keys(), key=version_key, reverse=True)
        print(f"\n{BOLD}{CYAN}Verfügbare Versionen:{NC}")
        for i, v in enumerate(v_list): print(f" [{i+1}] {v}")
        try:
            sel = int(input(f"\n{YELLOW}Wähle Version (Nummer): {NC}")) - 1
            if 0 <= sel < len(v_list): return groups[v_list[sel]]
        except: pass
        return None

    def menu_select_backup(self, backup_list):
        print(f"\n{BOLD}{CYAN}Verfügbare Backups:{NC}")
        backups_with_inc = []
        for b in backup_list:
            res = self.parse_backup_name(b)
            inc = res[1] if res else -1
            backups_with_inc.append((inc, b))
        backups_with_inc.sort(key=lambda x: x[0], reverse=True)

        for inc, b_name in backups_with_inc:
            status_info = self.build_index.get(b_name, ("UNKNOWN", "?"))
            status, v_code = status_info
            date = datetime.datetime.fromtimestamp(os.path.getmtime(os.path.join(self.backup_dir, b_name))).strftime('%d.%m %H:%M')
            color = GREEN if status == "SUCCESS" else (RED if status == "FAIL" else NC)
            label = "0 (FULL)" if inc == 0 else f"{inc} (Inc)"
            print(f" [{inc}] {color}{label:<10}{NC} | {date} | {status:<8} | CV: {v_code}")

        try:
            sel = int(input(f"\n{YELLOW}Wähle Inkrement-Nummer: {NC}"))
            for inc, b_name in backups_with_inc:
                if inc == sel: return b_name
        except: pass
        return None

    def find_target_by_string(self, target_str):
        if target_str.lower().startswith('v'): target_str = target_str[1:]
        parts = target_str.split('.')
        if len(parts) >= 3:
            v_base = f"v{parts[0]}.{parts[1]}.{parts[2]}"
            inc_target = int(parts[3]) if len(parts) > 3 else 0
            for b in self.get_backups_sorted():
                res = self.parse_backup_name(b)
                if res and res[0] == v_base and res[1] == inc_target: return b
        return None

    def find_latest_stable(self):
        backups_by_time = sorted([d for d in os.listdir(self.backup_dir) if os.path.isdir(os.path.join(self.backup_dir, d))], 
                                 key=lambda x: os.path.getmtime(os.path.join(self.backup_dir, x)))
        for b in reversed(backups_by_time):
            status_info = self.build_index.get(b)
            if status_info and status_info[0] == "SUCCESS": return b
        return None

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('-r', '--rollback', action='store_true')
    parser.add_argument('-a', '--auto', action='store_true')
    parser.add_argument('-l', '--list', action='store_true')
    parser.add_argument('-t', '--target', type=str)
    args = parser.parse_args()
    bm = BackupManager()

    if args.list: bm.list_all_backups()
    elif args.auto:
        target = bm.find_latest_stable()
        if target: bm.perform_rollback(target)
        else: bm.log("Kein SUCCESS-Backup gefunden!", RED)
    elif args.target:
        target = args.target
        if not os.path.exists(os.path.join(bm.backup_dir, target)):
            target = bm.find_target_by_string(args.target) or target
        if os.path.exists(os.path.join(bm.backup_dir, target)): bm.perform_rollback(target)
        else: bm.log(f"Backup {target} nicht gefunden!", RED)
    elif args.rollback:
        selected_backups = bm.menu_select_version()
        if selected_backups:
            target = bm.menu_select_backup(selected_backups)
            if target: bm.perform_rollback(target)
    else: bm.perform_backup()

if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Validate the VBOX Android project without a JDK / Android SDK.

Checks: balanced braces/parens/brackets per .kt file, no TODO/stub
markers, manifest valid XML + required permissions/service, gradle files
present and sane, launcher icon present, no personal name anywhere,
i18n EN/FA key parity, conditional libbox wiring present.
Exit 0 = all pass.
"""
import re, sys, xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
fails, passes = [], []

def check(name, ok, detail=""):
    (passes if ok else fails).append(f"{name} {detail}".strip())

kt = sorted(p for p in ROOT.rglob("*.kt") if ".git" not in str(p))
check("kotlin files", len(kt) >= 15, f"({len(kt)} files)")
for f in kt:
    src = f.read_text(encoding="utf-8")
    for a, b, label in [("{", "}", "braces"), ("(", ")", "parens"), ("[", "]", "brackets")]:
        check(f"balanced {label} {f.name}", src.count(a) == src.count(b),
              f"({src.count(a)} vs {src.count(b)})")
    check(f"no TODO/stub {f.name}", "TODO" not in src and "not implemented" not in src.lower()
          and "NotImplemented" not in src)
    check(f"package {f.name}", src.startswith("package com.amin.vbox"))
    check(f"no personal name {f.name}", "amin talebi" not in src.lower()
          and "mohammad amin" not in src.lower())

man = ROOT / "app/src/main/AndroidManifest.xml"
try:
    ET.parse(man)
    txt = man.read_text()
    for perm in ["INTERNET", "FOREGROUND_SERVICE", "BIND_VPN_SERVICE", "CAMERA"]:
        check(f"manifest permission {perm}", perm in txt)
    check("manifest vpn service", "VboxVpnService" in txt and "android.net.VpnService" in txt)
    check("manifest app label VBOX", 'android:label="@string/app_name"' in txt)
except Exception as e:
    check("manifest parses", False, str(e))

for p in ["settings.gradle.kts", "build.gradle.kts", "app/build.gradle.kts",
          "gradle.properties", "gradle/wrapper/gradle-wrapper.properties",
          "gradlew", ".github/workflows/android.yml", "README.md",
          "scripts/build-libbox.sh", "app/libs/README.md"]:
    check(f"exists {p}", (ROOT / p).exists())
gradle = (ROOT / "app/build.gradle.kts").read_text()
for dep in ["compose-bom", "okhttp", "kotlinx-serialization-json", "navigation-compose",
            "datastore-preferences", "security-crypto", "zxing-android-embedded"]:
    check(f"dependency {dep}", dep in gradle)
check("compileSdk 34", "compileSdk = 34" in gradle)
check("minSdk 26", "minSdk = 26" in gradle)
check("namespace com.amin.vbox", 'namespace = "com.amin.vbox"' in gradle)
check("libbox conditional wiring", "libbox.aar" in gradle and "src/libbox/java" in gradle)
check("no libbox unconditional dep", "io.nekohasekai" not in gradle)

strings = (ROOT / "app/src/main/res/values/strings.xml").read_text()
check("strings app_name VBOX", ">VBOX<" in strings)
check("launcher foreground", (ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml").exists())
check("no screenshot hwid", "1A6CE1CC" not in "".join(p.read_text(errors="ignore") for p in ROOT.rglob("*") if p.is_file() and p.suffix in (".kt", ".md", ".kts", ".xml")))

# i18n parity: count constructor args in Strings.en vs Strings.fa
sk = (ROOT / "app/src/main/java/com/amin/vbox/i18n/Strings.kt").read_text()
en = re.search(r"val en = AppStrings\((.*?)\)\n\n    val fa", sk, re.S)
fa = re.search(r"val fa = AppStrings\((.*?)\)\n\n    fun forLanguage", sk, re.S)
if en and fa:
    en_keys = sorted(set(re.findall(r"(\w+)\s*=", en.group(1))))
    fa_keys = sorted(set(re.findall(r"(\w+)\s*=", fa.group(1))))
    check("i18n EN/FA parity", en_keys == fa_keys,
          f"(en={len(en_keys)} fa={len(fa_keys)} missing={sorted(set(en_keys)-set(fa_keys))} extra={sorted(set(fa_keys)-set(en_keys))})")
else:
    check("i18n blocks found", False)

print(f"PASS {len(passes)}")
for p in passes:
    print("  ok:", p)
if fails:
    print(f"FAIL {len(fails)}")
    for f in fails:
        print("  FAIL:", f)
    sys.exit(1)
print("ALL CHECKS PASSED")

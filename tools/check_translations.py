#!/usr/bin/env python3
"""Kotakula Multiverse: check of faction wording (spec section 14.1).

For every translation key the script computes the text each faction actually sees:
  `Key#Humanist` / `Key#Technocrat` of the mod, else `Key` of the mod, else `Key` of the vanilla Russian file
(the same lookup as com.unciv.models.translations.Translations with a faction viewer), and reports
  - Humanist texts that still contain "наук", "учён(ый...)", "исследова", "технолог";
  - Technocrat texts that still contain "вер(а...)", "религи", "пророк", "миссионер".
Placeholders in [] and {} are skipped: they hold object names, which are checked under their own keys.

"вер" is matched as the word forms of "вера" ("вера", "веры", "верой", "верующий", "верование"...),
otherwise "север", "проверка" or "поверхность" would be reported; "учён" also matches "ученый" written without "ё".
The patterns are the same as FactionWording.forbiddenFor* used by the Kotlin test KotakulaTranslationTests.

Usage (from the fork root):  python3 tools/check_translations.py [mod Russian.properties] [vanilla Russian.properties]
An empty report and exit code 0 mean the check passed.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD_FILE = ROOT / "android/assets/KotakulaMod/Kotakula Multiverse/jsons/translations/Russian.properties"
VANILLA_FILE = ROOT / "android/assets/jsons/translations/Russian.properties"

LETTER = "а-яёА-ЯЁa-zA-Z"
START = f"(?<![{LETTER}])"
END = f"(?![{LETTER}])"
FORBIDDEN = {
    "Humanist": re.compile(r"(наук|уч[её]н(ый|ого|ому|ым|ом|ые|ых|ыми)|исследова|технолог)", re.IGNORECASE),
    "Technocrat": re.compile(
        rf"({START}(вер(а|ы|е|у|ой|ою){END}|верующ|веровани)|религи|пророк|миссионер)", re.IGNORECASE),
}
PLACEHOLDER = re.compile(r"\[[^\]]*\]|\{[^}]*\}")


def read_properties(path: Path) -> dict:
    result = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#") or " = " not in line:
            continue
        key, value = line.split(" = ", 1)
        key = key.replace("\\n", "\n")
        value = value.replace("\\n", "\n")
        if value:
            result[key] = value
    return result


def main() -> int:
    mod_file = Path(sys.argv[1]) if len(sys.argv) > 1 else MOD_FILE
    vanilla_file = Path(sys.argv[2]) if len(sys.argv) > 2 else VANILLA_FILE
    mod = read_properties(mod_file)
    vanilla = read_properties(vanilla_file)

    base_keys = sorted({k for k in list(mod) + list(vanilla) if "#" not in k})
    report = []
    for faction, pattern in FORBIDDEN.items():
        for key in base_keys:
            text = mod.get(f"{key}#{faction}") or mod.get(key) or vanilla.get(key)
            if text is None:
                continue
            found = pattern.search(PLACEHOLDER.sub("", text))
            if found:
                report.append(f"{faction}: «{found.group(0)}» in [{key}] = {text}")

    for line in report:
        print(line)
    return 1 if report else 0


if __name__ == "__main__":
    sys.exit(main())

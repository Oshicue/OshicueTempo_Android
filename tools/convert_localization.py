#!/usr/bin/env python3
"""Converts the iOS Localizable.strings files into Android string resources.

Usage: python3 tools/convert_localization.py <localization_dir> <res_dir> <kotlin_dir>

The iOS project stores every user-facing string in
OshicueTempo/Core/Resources/<language>.lproj/Localizable.strings. This script
generates the matching res/values-* XML files plus the compiled-in key map used
by the app, so both platforms always share the same wording.
"""
import os
import re
import sys

STRING_LINE = re.compile(r'^"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;')

UNESCAPE = {
    'n': '\n',
    't': '\t',
    'r': '\r',
    '"': '"',
    "'": "'",
    '\\': '\\',
}

# Keys that only exist on Android, or whose wording needs the Android flow.
EXTRA = {
    'en': {
        'notifications.channel.defaultName': 'Round alerts',
        'notifications.channel.description': 'Plays when a member\u2019s round ends.',
        'sounds.importHelp': 'Recordings must be shorter than 30 seconds. Record or save the audio with any recorder or Files app, then import it here. M4A, WAV, and other audio files are supported.',
        'storage.unreadableSnapshot': 'The original data cannot be read. Saving has stopped to protect the file. Back up and move OshicueTempo/state.json out of the app folder, then reopen the app.',
    },
    'zh-Hans': {
        'notifications.channel.defaultName': '到时提醒',
        'notifications.channel.description': '成员本轮倒计时结束时播放。',
        'sounds.importHelp': '支持短于 30 秒的录音。可先在系统录音机中录制，或通过「文件」应用选择音频，再从这里导入。支持 M4A、WAV 等音频文件。',
        'storage.unreadableSnapshot': '原数据无法读取。为保护原文件，已停止写入。请先备份并移走应用私有目录中的 OshicueTempo/state.json，再重新打开 App。',
    },
    'zh-Hant': {
        'notifications.channel.defaultName': '時間到提醒',
        'notifications.channel.description': '成員本輪倒數計時結束時播放。',
        'sounds.importHelp': '支援少於 30 秒的錄音。可先在系統錄音機中錄製，或透過「檔案」應用程式選擇音訊，再從這裡匯入。支援 M4A、WAV 等音訊檔案。',
        'storage.unreadableSnapshot': '無法讀取原始資料。為保護原始檔案，已停止寫入。請先備份並移走應用程式私人目錄中的 OshicueTempo/state.json，再重新開啟 App。',
    },
    'ja': {
        'notifications.channel.defaultName': '終了のお知らせ',
        'notifications.channel.description': 'メンバーの今回が終わったときに再生します。',
        'sounds.importHelp': '30秒未満の録音に対応しています。レコーダーアプリで録音するか、「ファイル」アプリで音声を選び、ここから読み込んでください。M4A、WAVなどの音声ファイルに対応しています。',
        'storage.unreadableSnapshot': '元のデータを読み込めません。ファイルを保護するため、保存を停止しました。アプリ専用フォルダー内の OshicueTempo/state.json をバックアップして別の場所に移動し、Appを開き直してください。',
    },
}

# Values shared by every language.
FIXED = {
    'app.name': 'Oshicue Tempo',
    'notifications.channel.group': 'Oshicue Tempo',
}


def unescape(value):
    result = []
    index = 0
    while index < len(value):
        character = value[index]
        if character == '\\' and index + 1 < len(value):
            following = value[index + 1]
            result.append(UNESCAPE.get(following, following))
            index += 2
        else:
            result.append(character)
            index += 1
    return ''.join(result)


def parse_strings(path):
    entries = {}
    with open(path, encoding='utf-8') as handle:
        for line in handle:
            match = STRING_LINE.match(line.strip())
            if not match:
                continue
            entries[unescape(match.group(1))] = unescape(match.group(2))
    return entries


def android_format(value):
    counter = [0]

    def replace(match):
        token = match.group(1)
        if token == '%':
            return '%%'
        counter[0] += 1
        kind = 's' if token == '@' else 'd'
        return '%%%d$%s' % (counter[0], kind)

    def replace_float(match):
        counter[0] += 1
        return '%%%d$%s' % (counter[0], match.group(0).replace('%', ''))

    def replace_int(_match):
        counter[0] += 1
        return '%%%d$d' % counter[0]

    # Handle %ld, %.1f (and any %.Nf) first, then %@, %d and literal %%.
    value = re.sub(r'%\.\d+f', replace_float, value)
    value = re.sub(r'%ld', replace_int, value)
    return re.sub(r'%(@|d|%)', replace, value)


def android_escape(value):
    value = value.replace('\\', '\\\\')
    value = value.replace("'", "\\'")
    value = value.replace('"', '\\"')
    value = value.replace('\n', '\\n')
    value = value.replace('\t', '\\t')
    value = value.replace('\r', '\\r')
    value = value.replace('&', '&amp;')
    value = value.replace('<', '&lt;')
    value = value.replace('>', '&gt;')
    if value.startswith('@') or value.startswith('?'):
        value = '\\' + value
    return value


def resource_name(key):
    return key.replace('.', '_')


def write_strings(path, entries):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as handle:
        handle.write('<?xml version="1.0" encoding="utf-8"?>\n')
        handle.write('<!-- Generated from the iOS Localizable.strings. Do not edit by hand. -->\n')
        handle.write('<resources>\n')
        for key in sorted(entries):
            value = android_escape(android_format(entries[key]))
            attribute = ' translatable="false"' if key in FIXED else ''
            handle.write('    <string name="%s"%s>%s</string>\n' % (resource_name(key), attribute, value))
        handle.write('</resources>\n')


def main():
    localization_dir, res_dir, kotlin_dir = sys.argv[1:4]
    languages = {
        'en': 'values',
        'zh-Hans': 'values-b+zh+Hans',
        'zh-Hant': 'values-b+zh+Hant',
        'ja': 'values-ja',
    }
    reference = None
    keys = []
    for language, folder in languages.items():
        path = os.path.join(localization_dir, language + '.lproj', 'Localizable.strings')
        entries = parse_strings(path)
        entries.update(EXTRA[language])
        if reference is None:
            reference = dict(entries)
            keys = sorted(reference)
        missing = sorted(set(reference) - set(entries))
        if missing:
            raise SystemExit('missing keys in %s: %s' % (language, missing[:5]))
        # Fixed values only belong to the default locale.
        if language == 'en':
            entries = dict(entries)
            entries.update(FIXED)
        write_strings(os.path.join(res_dir, folder, 'strings.xml'), entries)
        print('%s -> %s (%d strings)' % (language, folder, len(entries)))

    os.makedirs(kotlin_dir, exist_ok=True)
    with open(os.path.join(kotlin_dir, 'L10nKeys.kt'), 'w', encoding='utf-8') as handle:
        handle.write('package com.oshicue.tempo.i18n\n\n')
        handle.write('import com.oshicue.tempo.R\n\n')
        handle.write('/** Generated from the iOS Localizable.strings keys. Do not edit by hand. */\n')
        handle.write('internal val KEY_RESOURCES: Map<String, Int> = mapOf(\n')
        for key in keys + sorted(FIXED):
            handle.write('    "%s" to R.string.%s,\n' % (key, resource_name(key)))
        handle.write(')\n')
    print('generated %d key mappings' % (len(keys) + len(FIXED)))


if __name__ == '__main__':
    main()

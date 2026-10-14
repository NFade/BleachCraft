#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""R11 T12: LOCAL tool for the sound clips of the mod (not part of the build, not run by the tests).

The anime clips are copyrighted and the repository is public, so they are never committed; the player puts them in a local folder and this script checks
them against the sound sheet (design/results/r11_sound_sheet.md):

    python -I tools/sounds_check.py                       # check mod/src/main/resources/assets/reiatsu_test/sounds/anime (the default folder)
    python -I tools/sounds_check.py --dir sounds_local    # check another folder laid out the same way (<group>/<event>_N.ogg)
    python -I tools/sounds_check.py --all                 # also list the reserved events (not wired in the code yet) as missing
    python -I tools/sounds_check.py --pack OUT [--zip]    # build the resource pack folder reiatsu_test_anime_sounds from the valid clips
    python -I tools/sounds_check.py --normalize SRC OUT   # (needs ffmpeg) convert any audio files named like the clips to mono 44.1 kHz ogg at one level
    python -I tools/sounds_check.py --selftest            # test of the ogg reader on a synthetic stream

What is checked without anything installed (standard library only): the file is an Ogg Vorbis stream (Minecraft cannot play Opus or mp3), mono, 44100 Hz,
its length against the sheet column "длина", and it is named like a clip of an event. With ffmpeg on the PATH it also measures the peak and the mean level
(volumedetect) and the silence at the start (silencedetect). The report lists what is missing for the events of the sheet (the wired ones, or all with --all).
Exit code 0 when no clip has a PROBLEM, 1 otherwise (missing clips are not a problem: the mod plays its vanilla fallback).
"""
import argparse
import io
import os
import re
import shutil
import struct
import subprocess
import sys
import zipfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
SHEET = os.path.join(ROOT, 'design', 'results', 'r11_sound_sheet.md')
DEFAULT_DIR = os.path.join(ROOT, 'mod', 'src', 'main', 'resources', 'assets', 'reiatsu_test', 'sounds', 'anime')
NAMESPACE = 'reiatsu_test'
PACK_FORMAT = 34  # resource pack format of Minecraft 1.21.1

# one level for all clips (a clip is judged against these; --normalize produces them)
TARGET_PEAK_DB = -1.0       # loudest sample, dBFS: below 0 so that the mixing in the game cannot clip it
PEAK_MAX_DB = -0.3          # a clip above this is clipping
PEAK_MIN_DB = -12.0         # a clip below this is too quiet (raise it, or the volume key of the event in reiatsu_sounds.json)
MEAN_TARGET_DB = -20.0      # mean level the clips are normalised towards (ffmpeg loudnorm -18 LUFS is close to this)
MAX_LEAD_SILENCE_S = 0.05   # silence at the start longer than this delays the sound against the picture
LENGTH_TOLERANCE = 1.35     # a clip may be this much longer than the sheet's maximum (or shorter than the minimum / this)


def short(path, base):
    """Relative path for the report; the path itself when it is on another drive."""
    try:
        return os.path.relpath(path, base)
    except ValueError:
        return path


class Event:
    def __init__(self, id, clip_min, clip_max, mode, status, risk):
        self.id = id
        self.clip_min = clip_min
        self.clip_max = clip_max
        self.mode = mode
        self.status = status
        self.risk = risk
        group, _, rest = id.partition('.')
        self.group = group
        self.base = rest.replace('.', '_')

    @property
    def wired(self):
        return self.status in ('нужен файл', 'есть')


def read_sheet(path=SHEET):
    events = []
    with io.open(path, encoding='utf-8') as f:
        for line in f:
            if not line.startswith('| `'):
                continue
            c = [x.strip() for x in line.split('|')]
            if len(c) < 12:
                continue
            lo, _, hi = c[5].partition('-')
            events.append(Event(c[1].strip('`'), float(lo), float(hi), c[6], c[8], c[9]))
    return events


# ------------------------------------------------------------------------------------------ ogg

class OggInfo:
    def __init__(self):
        self.codec = None
        self.channels = None
        self.rate = None
        self.duration = None
        self.error = None


def read_ogg(data):
    """Reads the first packet (codec, channels, sample rate) and the last granule position (length) of an Ogg stream."""
    info = OggInfo()
    if data[:4] != b'OggS':
        info.error = 'not an Ogg file (wrong header; wav / mp3 / flac must be converted to Ogg Vorbis)'
        return info
    pos = 0
    first = None
    last_granule = None
    n = len(data)
    while pos + 27 <= n:
        if data[pos:pos + 4] != b'OggS':
            info.error = 'broken Ogg page at byte %d' % pos
            return info
        granule = struct.unpack_from('<q', data, pos + 6)[0]
        nseg = data[pos + 26]
        if pos + 27 + nseg > n:
            info.error = 'truncated Ogg page table'
            return info
        seg = data[pos + 27:pos + 27 + nseg]
        body = sum(seg)
        start = pos + 27 + nseg
        if first is None:
            first = data[start:start + body]
        if granule >= 0:
            last_granule = granule
        pos = start + body
    if first is None:
        info.error = 'empty Ogg stream'
        return info
    if first[:7] == b'\x01vorbis' and len(first) >= 16:
        info.codec = 'vorbis'
        info.channels = first[11]
        info.rate = struct.unpack_from('<I', first, 12)[0]
    elif first[:8] == b'OpusHead':
        info.codec = 'opus'
        info.channels = first[9]
        info.rate = 48000
    elif first[:5] == b'\x7fFLAC':
        info.codec = 'flac'
    else:
        info.codec = 'unknown'
    if info.rate and last_granule is not None and last_granule > 0:
        info.duration = last_granule / float(info.rate)
    return info


def synthetic_ogg(channels=1, rate=44100, seconds=1.0, codec='vorbis'):
    """A stream that read_ogg understands (NOT playable audio): an identification page and a last page with the granule position."""
    def page(header_type, granule, serial, seq, payload):
        segs = []
        rest = len(payload)
        while rest >= 255:
            segs.append(255)
            rest -= 255
        segs.append(rest)
        return b'OggS' + struct.pack('<BBqIIIB', 0, header_type, granule, serial, seq, 0, len(segs)) + bytes(segs) + payload
    if codec == 'vorbis':
        ident = b'\x01vorbis' + struct.pack('<IBIiii', 0, channels, rate, 0, 0, 0) + b'\xb8\x01'
    else:
        ident = b'OpusHead' + bytes([1, channels]) + struct.pack('<HIhB', 312, rate, 0, 0)
    return page(2, 0, 7, 0, ident) + page(4, int(seconds * (rate if codec == 'vorbis' else 48000)), 7, 1, b'\x00' * 10)


def selftest():
    ok = True
    for ch, rate, sec, codec in ((1, 44100, 1.5, 'vorbis'), (2, 48000, 0.25, 'vorbis'), (1, 48000, 2.0, 'opus')):
        i = read_ogg(synthetic_ogg(ch, rate, sec, codec))
        good = i.error is None and i.codec == codec and i.channels == ch and abs(i.duration - sec) < 1e-3 and (codec == 'opus' or i.rate == rate)
        print('selftest %-6s %d ch %5d Hz %.2f s -> %s' % (codec, ch, rate, sec, 'ok' if good else 'FAILED %s' % vars(i)))
        ok = ok and good
    bad = read_ogg(b'RIFF....WAVE')
    print('selftest wav header ->', 'ok' if bad.error else 'FAILED')
    ok = ok and bool(bad.error)
    return 0 if ok else 1


# ------------------------------------------------------------------------------------------ ffmpeg

def find_ffmpeg():
    return shutil.which('ffmpeg')


def measure(ffmpeg, path):
    """Peak and mean level (dB) and the silence at the start (s) with ffmpeg; None values when ffmpeg could not tell."""
    out = {'peak': None, 'mean': None, 'lead': 0.0}
    try:
        r = subprocess.run([ffmpeg, '-hide_banner', '-nostats', '-i', path, '-af', 'volumedetect,silencedetect=noise=-50dB:d=0.02', '-f', 'null', '-'],
                           capture_output=True, text=True, errors='replace', timeout=60)
    except (OSError, subprocess.SubprocessError):
        return out
    text = r.stderr
    m = re.search(r'max_volume:\s*(-?[0-9.]+) dB', text)
    if m:
        out['peak'] = float(m.group(1))
    m = re.search(r'mean_volume:\s*(-?[0-9.]+) dB', text)
    if m:
        out['mean'] = float(m.group(1))
    m = re.search(r'silence_start:\s*(-?[0-9.]+)', text)
    if m and abs(float(m.group(1))) < 0.001:
        e = re.search(r'silence_end:\s*([0-9.]+)', text)
        out['lead'] = float(e.group(1)) if e else 9.9
    return out


# ------------------------------------------------------------------------------------------ checking

CLIP_RE = re.compile(r'^(?P<base>.+)_(?P<n>\d+)\.ogg$')


def scan_dir(folder):
    """{(group, base): {n: path}} of the clips on disk, plus the list of files that do not look like a clip."""
    found = {}
    stray = []
    if not os.path.isdir(folder):
        return found, stray
    for group in sorted(os.listdir(folder)):
        gdir = os.path.join(folder, group)
        if not os.path.isdir(gdir):
            stray.append(short(gdir, folder))
            continue
        for name in sorted(os.listdir(gdir)):
            m = CLIP_RE.match(name)
            if not m:
                stray.append(os.path.join(group, name))
                continue
            found.setdefault((group, m.group('base')), {})[int(m.group('n'))] = os.path.join(gdir, name)
    return found, stray


def check_clip(ev, path, ffmpeg):
    """List of (level, text); level is 'PROBLEM' or 'WARN'."""
    msgs = []
    with open(path, 'rb') as f:
        data = f.read()
    info = read_ogg(data)
    if info.error:
        return [('PROBLEM', info.error)], info
    if info.codec != 'vorbis':
        msgs.append(('PROBLEM', 'codec %s: Minecraft plays only Ogg Vorbis (use --normalize or ffmpeg -c:a libvorbis)' % info.codec))
    if info.channels != 1:
        msgs.append(('PROBLEM', '%s channels: positional sounds must be mono (a stereo clip plays without distance attenuation)' % info.channels))
    if info.rate != 44100:
        msgs.append(('WARN', 'sample rate %s Hz (44100 expected; the game resamples, but keep one rate)' % info.rate))
    if info.duration is not None:
        lo, hi = ev.clip_min / LENGTH_TOLERANCE, ev.clip_max * LENGTH_TOLERANCE
        if info.duration > hi:
            msgs.append(('WARN', 'length %.2f s, the sheet says %.2f-%.2f s: trim the tail' % (info.duration, ev.clip_min, ev.clip_max)))
        elif info.duration < lo:
            msgs.append(('WARN', 'length %.2f s, the sheet says %.2f-%.2f s' % (info.duration, ev.clip_min, ev.clip_max)))
    if ffmpeg:
        m = measure(ffmpeg, path)
        if m['peak'] is not None:
            if m['peak'] > PEAK_MAX_DB:
                msgs.append(('PROBLEM', 'peak %.1f dB: clipping (lower it to about %.0f dB)' % (m['peak'], TARGET_PEAK_DB)))
            elif m['peak'] < PEAK_MIN_DB:
                msgs.append(('WARN', 'peak %.1f dB: very quiet (target %.0f dB; or raise events.%s.volume)' % (m['peak'], TARGET_PEAK_DB, ev.id)))
        if m['mean'] is not None and abs(m['mean'] - MEAN_TARGET_DB) > 8:
            msgs.append(('WARN', 'mean level %.1f dB, the common level is about %.0f dB' % (m['mean'], MEAN_TARGET_DB)))
        if m['lead'] > MAX_LEAD_SILENCE_S:
            msgs.append(('WARN', '%.2f s of silence at the start (cut it, the sound would lag behind the picture)' % m['lead']))
    return msgs, info


def run_check(args):
    ffmpeg = find_ffmpeg()
    events = read_sheet()
    found, stray = scan_dir(args.dir)
    print('Sound check of %s' % short(args.dir, ROOT))
    print('ffmpeg: %s' % (ffmpeg or 'not found (levels and silence are not measured; format and length are)'))
    print('Sheet: %d events, %d wired in the code' % (len(events), sum(1 for e in events if e.wired)))
    print()
    problems = 0
    warns = 0
    have = 0
    missing = []
    known = set()
    for ev in events:
        key = (ev.group, ev.base)
        known.add(key)
        clips = found.get(key)
        if not clips:
            if ev.status != 'не используется' and (ev.wired or args.all):
                missing.append(ev)
            continue
        have += 1
        for n in sorted(clips):
            msgs, info = check_clip(ev, clips[n], ffmpeg)
            rel = short(clips[n], args.dir)
            if not msgs:
                dur = '%.2f s' % info.duration if info.duration else '? s'
                print('OK       %-52s %s' % (rel, dur))
            for lvl, text in msgs:
                print('%-8s %-52s %s' % (lvl, rel, text))
                if lvl == 'PROBLEM':
                    problems += 1
                else:
                    warns += 1
        if ev.risk.startswith('голос') or ev.risk.startswith('музыка'):
            print('NOTE     %-52s risk "%s": a clip with a voice or music is the most copyrighted kind; keep it local' % (ev.id, ev.risk))
    for key in sorted(k for k in found if k not in known):
        for n in sorted(found[key]):
            print('STRAY    %s/%s_%d.ogg is not a clip of any event of the sheet (ignored by the game)' % (key[0], key[1], n))
    for s in stray:
        print('STRAY    %s does not look like <group>/<event>_N.ogg' % s)
    print()
    print('Events with a clip: %d; PROBLEM: %d; WARN: %d; missing: %d%s' % (have, problems, warns, len(missing),
          '' if args.all else ' (wired events only; --all adds the reserved ones)'))
    if missing:
        print()
        print('Missing clips (the mod plays its vanilla fallback for these):')
        group = None
        for ev in missing:
            if ev.group != group:
                group = ev.group
                print('  [%s]' % group)
            print('    %s_1.ogg   %.2f-%.2f s, %s%s' % (ev.base, ev.clip_min, ev.clip_max, ev.mode, '' if ev.wired else ' (reserved)'))
    return 1 if problems else 0


# ------------------------------------------------------------------------------------------ resource pack

def build_pack(args):
    events = read_sheet()
    found, _ = scan_dir(args.dir)
    ffmpeg = find_ffmpeg()
    out = args.pack
    if os.path.exists(out):
        shutil.rmtree(out)
    copied = 0
    skipped = 0
    extra = {}
    for ev in events:
        clips = found.get((ev.group, ev.base))
        if not clips:
            continue
        for n in sorted(clips):
            msgs, _ = check_clip(ev, clips[n], ffmpeg)
            if any(l == 'PROBLEM' for l, _t in msgs):
                print('skipped  %s_%d.ogg: %s' % (ev.base, n, '; '.join(t for l, t in msgs if l == 'PROBLEM')))
                skipped += 1
                continue
            dst = os.path.join(out, 'assets', NAMESPACE, 'sounds', 'anime', ev.group, '%s_%d.ogg' % (ev.base, n))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(clips[n], dst)
            copied += 1
            if n > 1:  # the mod's own sounds.json lists variant 1; a pack adds the other variants (the sound lists of the packs are merged)
                extra.setdefault(ev.id, []).append('anime/%s/%s_%d' % (ev.group, ev.base, n))
    os.makedirs(out, exist_ok=True)
    with io.open(os.path.join(out, 'pack.mcmeta'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('{\n  "pack": {\n    "pack_format": %d,\n    "description": "Reiatsu Test: local anime sound clips (not for redistribution)"\n  }\n}\n' % PACK_FORMAT)
    if extra:
        import json
        sj = {eid: {'sounds': [{'name': n, 'attenuation_distance': 32} for n in names]} for eid, names in sorted(extra.items())}
        os.makedirs(os.path.join(out, 'assets', NAMESPACE), exist_ok=True)
        with io.open(os.path.join(out, 'assets', NAMESPACE, 'sounds.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(sj, f, indent=2)
            f.write('\n')
    print('Pack folder %s: %d clips copied, %d skipped, %d events with extra variants' % (out, copied, skipped, len(extra)))
    if args.zip:
        zpath = out.rstrip('/\\') + '.zip'
        with zipfile.ZipFile(zpath, 'w', zipfile.ZIP_DEFLATED) as z:
            for base, _d, files in os.walk(out):
                for fn in files:
                    full = os.path.join(base, fn)
                    z.write(full, short(full, out))
        print('Zip: %s' % zpath)
    print('Put the folder or the zip into .minecraft/resourcepacks/ and enable it (top of the list). Do not publish it: the clips are copyrighted.')
    return 0 if copied or not found else 1


# ------------------------------------------------------------------------------------------ normalize

def normalize(args):
    ffmpeg = find_ffmpeg()
    if not ffmpeg:
        print('ffmpeg is not on the PATH: install it (https://ffmpeg.org) or convert the clips by hand to mono 44.1 kHz Ogg Vorbis.')
        return 2
    events = read_sheet()
    src_found = {}
    exts = ('.ogg', '.wav', '.mp3', '.flac', '.m4a', '.aac', '.opus', '.wma')
    for group in sorted(os.listdir(args.src)) if os.path.isdir(args.src) else []:
        gdir = os.path.join(args.src, group)
        if not os.path.isdir(gdir):
            continue
        for name in sorted(os.listdir(gdir)):
            stem, ext = os.path.splitext(name)
            m = re.match(r'^(?P<base>.+)_(?P<n>\d+)$', stem)
            if ext.lower() in exts and m:
                src_found.setdefault((group, m.group('base')), {})[int(m.group('n'))] = os.path.join(gdir, name)
    done = 0
    for ev in events:
        for n, path in sorted(src_found.get((ev.group, ev.base), {}).items()):
            dst = os.path.join(args.out, ev.group, '%s_%d.ogg' % (ev.base, n))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            # mono, 44.1 kHz, cut the leading silence, loudness about -18 LUFS with a true peak of -1.5 dB, Ogg Vorbis quality 5
            flt = ('silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.01,'
                   'loudnorm=I=-18:TP=-1.5:LRA=11')
            cmd = [ffmpeg, '-y', '-hide_banner', '-loglevel', 'error', '-i', path, '-vn', '-ac', '1', '-ar', '44100', '-af', flt, '-c:a', 'libvorbis', '-q:a', '5', dst]
            r = subprocess.run(cmd, capture_output=True, text=True, errors='replace')
            if r.returncode != 0:
                print('FAILED   %s: %s' % (path, r.stderr.strip()[:200]))
            else:
                print('written  %s' % short(dst, args.out))
                done += 1
    print('%d clips written to %s; check them with: python -I tools/sounds_check.py --dir "%s"' % (done, args.out, args.out))
    return 0


def main(argv=None):
    if hasattr(sys.stdout, 'reconfigure'):
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    ap = argparse.ArgumentParser(description='Check the local anime sound clips of the mod against the sound sheet (see the module docstring).')
    ap.add_argument('--dir', default=DEFAULT_DIR, help='folder with <group>/<event>_N.ogg (default: the ignored folder inside the mod resources)')
    ap.add_argument('--all', action='store_true', help='report the reserved events (not wired in the code yet) as missing too')
    ap.add_argument('--pack', metavar='OUT', help='build the resource pack folder OUT from the valid clips of --dir')
    ap.add_argument('--zip', action='store_true', help='with --pack: also write OUT.zip')
    ap.add_argument('--normalize', nargs=2, metavar=('SRC', 'OUT'), help='convert the audio files in SRC/<group>/<event>_N.* to Ogg clips in OUT (needs ffmpeg)')
    ap.add_argument('--selftest', action='store_true', help='test the Ogg reader on a synthetic stream and exit')
    args = ap.parse_args(argv)
    if args.selftest:
        return selftest()
    if args.normalize:
        args.src, args.out = args.normalize
        return normalize(args)
    if args.pack:
        return build_pack(args)
    return run_check(args)


if __name__ == '__main__':
    sys.exit(main())

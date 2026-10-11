# Synthetic speech fixtures for the voice bridge lab (offline, Windows only).
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\gen_voice_fixtures.ps1 [-Out tests\voice\audio] [-PerCommand 1] [-Neutrals 4]
#
# Reads tests/voice/phrases_fixture.json and speaks, with the installed Windows (System.Speech) voices, the canonical
# phrase of every command and a few neutral phrases per language. Output: 16 kHz, 16 bit, mono WAV files named
# <lang>_<command|neutral>_<n>.wav and manifest.json ({files:[{file,lang,text,expect}]}) for the lab page
# (voice-bridge/index.html, "Lab"). A language without an installed voice is skipped and reported (ja-JP usually has none:
# put your own recordings next to the files and name them ja-JP_*.wav, optionally listing them in manifest.json).
#
# The output folder is git-ignored: the files are generated on the machine that uses them, nothing from the voices is
# redistributed. They are test utterances made from our own phrase list, not recordings of a person.
param(
  [string]$Out = "tests\voice\audio",
  [int]$PerCommand = 1,
  [int]$Neutrals = 4
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Speech

$root = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path $root "tests\voice\phrases_fixture.json"
$outDir = if ([System.IO.Path]::IsPathRooted($Out)) { $Out } else { Join-Path $root $Out }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$data = Get-Content -Raw -Encoding UTF8 $fixture | ConvertFrom-Json
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voices = $synth.GetInstalledVoices() | Where-Object { $_.Enabled }
$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)

$files = @()
$skipped = @()
foreach ($lang in @("en-US", "ru-RU", "ja-JP")) {
  $v = $voices | Where-Object { $_.VoiceInfo.Culture.Name -eq $lang } | Select-Object -First 1
  if (-not $v) { $skipped += $lang; continue }
  $synth.SelectVoice($v.VoiceInfo.Name)
  $synth.Rate = -1
  $jobs = @()
  foreach ($cmd in ($data.positives | Where-Object { $_.lang -eq $lang } | Select-Object -ExpandProperty command -Unique)) {
    $rows = @($data.positives | Where-Object { $_.lang -eq $lang -and $_.command -eq $cmd -and $_.kind -eq "canonical" } | Select-Object -First $PerCommand)
    if ($rows.Count -eq 0) { $rows = @($data.positives | Where-Object { $_.lang -eq $lang -and $_.command -eq $cmd -and $_.kind -eq "variant" } | Select-Object -First $PerCommand) }
    $n = 0
    foreach ($r in $rows) { $n++; $jobs += [pscustomobject]@{ name = "$($lang)_$($cmd)_$n"; text = $r.text; expect = $cmd } }
  }
  $n = 0
  foreach ($r in @($data.neutrals | Where-Object { $_.lang -eq $lang } | Select-Object -First $Neutrals)) {
    $n++; $jobs += [pscustomobject]@{ name = "$($lang)_neutral_$n"; text = $r.text; expect = $null }
  }
  foreach ($j in $jobs) {
    $path = Join-Path $outDir ($j.name + ".wav")
    $synth.SetOutputToWaveFile($path, $fmt)
    $synth.Speak($j.text)
    $synth.SetOutputToNull()
    $files += [ordered]@{ file = ($j.name + ".wav"); lang = $lang; text = $j.text; expect = $j.expect }
  }
  Write-Host ("{0}: {1} files with voice {2}" -f $lang, $jobs.Count, $v.VoiceInfo.Name)
}
$synth.Dispose()

$manifest = [ordered]@{
  generator = "tools/gen_voice_fixtures.ps1"
  format = "16000 Hz, 16 bit, mono WAV"
  note = "expect = command id the game should report for the text, null = must not fire (neutral phrase)"
  files = $files
}
$json = $manifest | ConvertTo-Json -Depth 5
[System.IO.File]::WriteAllText((Join-Path $outDir "manifest.json"), $json, (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("{0} files in {1}" -f $files.Count, $outDir)
if ($skipped.Count) { Write-Host ("No installed voice for: " + ($skipped -join ", ") + " (skipped)") }

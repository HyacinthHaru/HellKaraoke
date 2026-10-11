import argparse
from decimal import Decimal, ROUND_CEILING
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile


ROOT = Path(__file__).resolve().parent
PACK = ROOT / "java"
BEDROCK = ROOT / "bedrock"
TRACKS = (
    ("see_you_again", "See You Again-司凤版.mp4"),
    ("yu_ai", "雨爱-司凤版.mp4"),
    ("dongbei", "大东北我的家乡.mp4"),
    ("ke_jian_pao_cao", "大课间跑操音乐.mp4"),
    ("yun_yuanshen", "云原神.mp4"),
    ("zhong_guo_ren_neng_fei", "中国人能飞.mp4"),
    ("wang_qing_niu_rou_mian", "忘情牛肉面.mp4"),
    ("peng_you_de_jiu", "朋友的酒.mp4"),
)


def write_archive(path, directory, files):
    with zipfile.ZipFile(path, "w") as bundle:
        for source in files:
            entry = zipfile.ZipInfo(source.relative_to(directory).as_posix())
            bundle.writestr(entry, source.read_bytes(),
                            compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    print(f"{path}\nbytes={path.stat().st_size}\nsha1={hashlib.sha1(path.read_bytes()).hexdigest()}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args()
    sounds_dir = PACK / "assets" / "meme" / "sounds"
    sounds_dir.mkdir(parents=True, exist_ok=True)
    bedrock_sounds_dir = BEDROCK / "sounds" / "meme"
    bedrock_sounds_dir.mkdir(parents=True, exist_ok=True)
    definitions = {}
    bedrock_definitions = {}
    metadata = ["songs:"]
    files = [PACK / "pack.mcmeta", PACK / "assets" / "meme" / "sounds.json"]
    bedrock_files = [BEDROCK / "manifest.json", BEDROCK / "sounds" / "sound_definitions.json"]

    for key, filename in TRACKS:
        output = sounds_dir / f"{key}.ogg"
        subprocess.run([
            args.ffmpeg, "-hide_banner", "-loglevel", "error", "-y",
            "-i", str(ROOT.parent / filename), "-map", "0:a:0", "-vn",
            "-ac", "1", "-ar", "24000", "-c:a", "libvorbis", "-q:a", "0",
            "-map_metadata", "-1",
            "-fflags", "+bitexact", "-flags:a", "+bitexact", str(output),
        ], check=True)
        probe = json.loads(subprocess.check_output([
            "ffprobe", "-v", "error", "-show_entries", "format=duration",
            "-of", "json", str(output),
        ], text=True))
        duration_ms = int((Decimal(probe["format"]["duration"]) * 1000)
                          .to_integral_value(rounding=ROUND_CEILING))
        definitions[key] = {"sounds": [{"name": f"meme:{key}", "stream": True}]}
        bedrock_output = bedrock_sounds_dir / output.name
        shutil.copyfile(output, bedrock_output)
        bedrock_definitions[f"meme:{key}"] = {
            "category": "record",
            "sounds": [{"name": f"sounds/meme/{key}", "stream": True,
                        "is3D": False, "load_on_low_memory": True}],
        }
        metadata.extend([f"  - sound: meme:{key}", f"    duration-ms: {duration_ms}"])
        files.append(output)
        bedrock_files.append(bedrock_output)
        print(f"{filename}: duration_ms={duration_ms} wait_ms={duration_ms * 85 / 100:.2f}")

    files[1].write_text(json.dumps(definitions, indent=2) + "\n", encoding="utf-8")
    bedrock_files[1].write_text(json.dumps({
        "format_version": "1.20.20",
        "sound_definitions": bedrock_definitions,
    }, indent=2) + "\n", encoding="utf-8")
    (ROOT / "songs.yml").write_text("\n".join(metadata) + "\n", encoding="utf-8")
    write_archive(ROOT / "the best songs i've heard.zip", PACK, files)
    write_archive(ROOT / "the best songs i've heard.mcpack", BEDROCK, bedrock_files)


if __name__ == "__main__":
    main()

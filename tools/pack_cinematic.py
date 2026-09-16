# -*- coding: utf-8 -*-
"""Pack a video into Eden Protocol's `.ecine` cinematic format.

.ecine = "ECIN1" magic + int32 (width, height, fps, frameCount) followed by
[ int32 length | JPEG bytes ] per frame - an MJPEG stream the client decodes with
the JDK's built-in ImageIO (zero extra natives) and uploads as a dynamic texture.
The audio track is exported separately as .ogg and played by the vanilla sound
engine, so decoding stays tiny and CPU-cheap.

Usage:
  python tools/pack_cinematic.py intro.mp4 intro            # -> assets/eden/cinematics/intro.ecine
                                                              + assets/eden/sounds/cinematic/intro.ogg
Requires ffmpeg on PATH. Recommended source: 1280x720, <=60s, 24-30fps.
"""
import pathlib
import struct
import subprocess
import sys
import tempfile

MOD = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/eden"


def run(cmd):
    subprocess.run(cmd, check=True)


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    src, name = sys.argv[1], sys.argv[2]
    max_w = "1280"
    with tempfile.TemporaryDirectory() as tmp:
        tmp = pathlib.Path(tmp)
        # Video -> numbered JPEG frames (scale to <=1280 wide, 4:2:0, quality ~5).
        run(["ffmpeg", "-y", "-i", src, "-an", "-vf", f"scale='min({max_w},iw)':-2:flags=lanczos",
             "-q:v", "5", str(tmp / "f%06d.jpg")])
        # Audio -> ogg vorbis (mono-friendly, quality 4).
        run(["ffmpeg", "-y", "-i", src, "-vn", "-c:a", "libvorbis", "-q:a", "4",
             str(tmp / "audio.ogg")])
        frames = sorted(tmp.glob("f*.jpg"))
        if not frames:
            print("no frames produced - is the input a video?")
            sys.exit(1)
        # Read fps via ffprobe for the header.
        fps_out = subprocess.check_output(
            ["ffprobe", "-v", "0", "-select_streams", "v:0", "-show_entries",
             "stream=r_frame_rate", "-of", "csv=p=0", src], text=True).strip()
        num, _, den = fps_out.partition("/")
        fps = max(1, round(float(num) / (float(den) or 1)))
        import PIL.Image
        with PIL.Image.open(frames[0]) as im:
            width, height = im.size
        out_dir = MOD / "cinematics"
        out_dir.mkdir(parents=True, exist_ok=True)
        with open(out_dir / f"{name}.ecine", "wb") as f:
            f.write(b"ECIN1")
            f.write(struct.pack("<iiii", width, height, fps, len(frames)))
            for fr in frames:
                data = fr.read_bytes()
                f.write(struct.pack("<i", len(data)))
                f.write(data)
        snd = MOD / "sounds/cinematic"
        snd.mkdir(parents=True, exist_ok=True)
        (snd / f"{name}.ogg").write_bytes((tmp / "audio.ogg").read_bytes())
        size = (out_dir / f"{name}.ecine").stat().st_size
        print(f"packed {len(frames)} frames @ {width}x{height} {fps}fps "
              f"({size/1024:.0f} KiB) + ogg -> assets/eden/{{cinematics/{name}.ecine, sounds/cinematic/{name}.ogg}}")


if __name__ == "__main__":
    main()

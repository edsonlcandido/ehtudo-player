from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path("/home/edson/StudioProjects/ehtudo-player")
SOURCE = ROOT / "shared/design/ehiptv-icon.png"
RES = ROOT / "apps/android/app/src/main/res"
CANVAS_SIZE = 1024
SAFE_SIZE = round(CANVAS_SIZE * 0.66)

with Image.open(SOURCE) as source:
    mark = source.convert("RGBA")
    mark.thumbnail((SAFE_SIZE, SAFE_SIZE), Image.Resampling.LANCZOS)

    canvas = Image.new("RGBA", (CANVAS_SIZE, CANVAS_SIZE), (0, 0, 0, 0))
    offset = ((CANVAS_SIZE - mark.width) // 2, (CANVAS_SIZE - mark.height) // 2)
    canvas.alpha_composite(mark, offset)

foreground_path = RES / "drawable/ic_ehiptv_logo_fg.png"
foreground_path.parent.mkdir(parents=True, exist_ok=True)
canvas.save(foreground_path, format="PNG")

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

for bucket, size in sizes.items():
    directory = RES / bucket
    directory.mkdir(parents=True, exist_ok=True)
    resized = canvas.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(directory / "ic_launcher.png", format="PNG")

    circle_mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(circle_mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    round_icon = resized.copy()
    existing_alpha = round_icon.getchannel("A")
    combined_alpha = Image.new("L", (size, size), 0)
    combined_alpha.paste(existing_alpha, mask=circle_mask)
    round_icon.putalpha(combined_alpha)
    round_icon.save(directory / "ic_launcher_round.png", format="PNG")

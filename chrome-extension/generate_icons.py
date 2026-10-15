import os
from PIL import Image, ImageDraw, ImageFont

ICONS_DIR = os.path.join(os.path.dirname(__file__), "icons")
os.makedirs(ICONS_DIR, exist_ok=True)

SIZES = [16, 32, 48, 128]

def get_font(size):
    # Try common fonts on Windows
    candidates = [
        "C:\\Windows\\Fonts\\segoeui.ttf",
        "C:\\Windows\\Fonts\\segoeuib.ttf",
        "C:\\Windows\\Fonts\\arial.ttf",
        "C:\\Windows\\Fonts\\arialbd.ttf"
    ]
    for font_path in candidates:
        if os.path.exists(font_path):
            try:
                return ImageFont.truetype(font_path, int(size * 0.72))
            except Exception:
                pass
    return ImageFont.load_default()

def create_stealth_default(size):
    # Sleek dark/teal productivity icon (looks like a page reader / speed booster)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    pad = max(1, int(size * 0.08))
    # Rounded background
    bg_color = (24, 28, 36, 255) # Dark graphite
    accent_color = (99, 102, 241, 255) # Indigo
    accent_teal = (20, 184, 166, 255) # Teal
    
    draw.rounded_rectangle([pad, pad, size - pad, size - pad], radius=int(size * 0.28), fill=bg_color)
    
    # Draw lightning/spark or geometric stealth emblem
    cx, cy = size / 2, size / 2
    r = size * 0.3
    # Inner subtle circle or shield
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=accent_color, width=max(1, int(size * 0.08)))
    # Center dot
    dot_r = max(1.5, size * 0.1)
    draw.ellipse([cx - dot_r, cy - dot_r, cx + dot_r, cy + dot_r], fill=accent_teal)
    return img

def create_letter_icon(letter, size, bg_hex="#1e1b4b", text_hex="#38bdf8", border_hex="#6366f1"):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    pad = max(1, int(size * 0.06))
    
    # Background circle or rounded box
    draw.rounded_rectangle(
        [pad, pad, size - pad, size - pad], 
        radius=int(size * 0.28), 
        fill=bg_hex, 
        outline=border_hex, 
        width=max(1, int(size * 0.08))
    )
    
    font = get_font(size)
    # Center letter
    bbox = font.getbbox(letter)
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    x = (size - w) / 2 - bbox[0]
    y = (size - h) / 2 - bbox[1] - (size * 0.02)
    draw.text((x, y), letter, fill=text_hex, font=font)
    return img

def create_loading_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    pad = max(1, int(size * 0.08))
    draw.rounded_rectangle([pad, pad, size - pad, size - pad], radius=int(size * 0.28), fill=(30, 27, 75, 255))
    
    # 3 pulsing dots
    cy = size / 2
    dot_r = max(1.2, size * 0.08)
    spacing = size * 0.24
    for i, offset in enumerate([-spacing, 0, spacing]):
        alpha = int(120 + i * 50)
        draw.ellipse([size/2 + offset - dot_r, cy - dot_r, size/2 + offset + dot_r, cy + dot_r], fill=(234, 179, 8, alpha))
    return img

def create_error_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    pad = max(1, int(size * 0.08))
    draw.rounded_rectangle([pad, pad, size - pad, size - pad], radius=int(size * 0.28), fill=(69, 10, 10, 255), outline=(239, 68, 68, 255), width=max(1, int(size*0.08)))
    font = get_font(size)
    bbox = font.getbbox("!")
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    x = (size - w) / 2 - bbox[0]
    y = (size - h) / 2 - bbox[1]
    draw.text((x, y), "!", fill=(255, 255, 255, 255), font=font)
    return img

# Palette for answers: High contrast, distinct, professional
LETTER_THEMES = {
    "A": {"bg": "#064e3b", "text": "#34d399", "border": "#10b981"}, # Emerald
    "B": {"bg": "#1e3a8a", "text": "#60a5fa", "border": "#3b82f6"}, # Blue
    "C": {"bg": "#581c87", "text": "#c084fc", "border": "#a855f7"}, # Purple
    "D": {"bg": "#7c2d12", "text": "#fb923c", "border": "#f97316"}, # Orange
}

for s in SIZES:
    create_stealth_default(s).save(os.path.join(ICONS_DIR, f"icon_{s}.png"))
    create_loading_icon(s).save(os.path.join(ICONS_DIR, f"loading_{s}.png"))
    create_error_icon(s).save(os.path.join(ICONS_DIR, f"error_{s}.png"))
    for letter, theme in LETTER_THEMES.items():
        create_letter_icon(letter, s, theme["bg"], theme["text"], theme["border"]).save(
            os.path.join(ICONS_DIR, f"choice_{letter.lower()}_{s}.png")
        )

print("Icons successfully generated!")

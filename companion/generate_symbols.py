import os
import math
from PIL import Image, ImageDraw

ICONS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "chrome-extension", "icons"))
os.makedirs(ICONS_DIR, exist_ok=True)
SIZES = [16, 32, 48, 128]

def draw_airplane(draw, size, color):
    cx, cy = size / 2, size / 2
    # Fuselage
    draw.polygon([
        (cx, cy - size * 0.42),
        (cx + size * 0.08, cy + size * 0.25),
        (cx, cy + size * 0.38),
        (cx - size * 0.08, cy + size * 0.25)
    ], fill=color)
    # Wings
    draw.polygon([
        (cx - size * 0.42, cy - size * 0.02),
        (cx + size * 0.42, cy - size * 0.02),
        (cx + size * 0.12, cy + size * 0.12),
        (cx - size * 0.12, cy + size * 0.12)
    ], fill=color)
    # Tail
    draw.polygon([
        (cx - size * 0.20, cy + size * 0.30),
        (cx + size * 0.20, cy + size * 0.30),
        (cx, cy + size * 0.40)
    ], fill=color)

def draw_apple(draw, size, color):
    cx, cy = size / 2, size * 0.54
    r = size * 0.30
    draw.ellipse([cx - r - size*0.04, cy - r, cx + size*0.04, cy + r], fill=color)
    draw.ellipse([cx - size*0.04, cy - r, cx + r + size*0.04, cy + r], fill=color)
    # Leaf
    draw.arc([cx - size*0.1, cy - r - size*0.25, cx + size*0.25, cy - r + size*0.05], start=200, end=350, fill="#10b981", width=max(2, int(size*0.08)))

def draw_anchor(draw, size, color):
    cx, cy = size / 2, size * 0.48
    w = max(2, int(size * 0.08))
    # Top ring
    r_ring = size * 0.12
    draw.ellipse([cx - r_ring, size * 0.1, cx + r_ring, size * 0.1 + r_ring * 2], outline=color, width=w)
    # Center shaft
    draw.line([(cx, size * 0.1 + r_ring * 2), (cx, size * 0.78)], fill=color, width=w)
    # Horizontal crossbar
    draw.line([(cx - size * 0.22, size * 0.38), (cx + size * 0.22, size * 0.38)], fill=color, width=w)
    # Curved bottom arc
    draw.arc([cx - size * 0.35, size * 0.35, cx + size * 0.35, size * 0.85], start=20, end=160, fill=color, width=w)

def draw_triangle(draw, size, color):
    cx, cy = size / 2, size / 2
    pad = size * 0.12
    draw.polygon([
        (cx, pad),
        (size - pad, size - pad),
        (pad, size - pad)
    ], fill=color)

def draw_balloon(draw, size, color):
    cx, cy = size / 2, size * 0.42
    rx, ry = size * 0.32, size * 0.38
    # Balloon body
    draw.ellipse([cx - rx, cy - ry, cx + rx, cy + ry], fill=color)
    # Knot
    draw.polygon([
        (cx - size * 0.08, cy + ry),
        (cx + size * 0.08, cy + ry),
        (cx, cy + ry + size * 0.08)
    ], fill=color)
    # String
    draw.line([(cx, cy + ry + size*0.08), (cx - size*0.05, size * 0.9)], fill="#94a3b8", width=max(1, int(size*0.05)))

def draw_ball(draw, size, color):
    cx, cy = size / 2, size / 2
    r = size * 0.38
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    # Seams / stripes
    w = max(1, int(size * 0.06))
    draw.arc([cx - r, cy - r, cx + r, cy + r], start=30, end=210, fill="#ffffff", width=w)
    draw.line([(cx - r, cy), (cx + r, cy)], fill="#ffffff", width=w)

def draw_book(draw, size, color):
    cx, cy = size / 2, size / 2
    w, h = size * 0.35, size * 0.28
    # Left page
    draw.rounded_rectangle([cx - w, cy - h, cx - size*0.02, cy + h], radius=int(size*0.05), fill=color)
    # Right page
    draw.rounded_rectangle([cx + size*0.02, cy - h, cx + w, cy + h], radius=int(size*0.05), fill=color)
    # Center spine line
    draw.line([(cx, cy - h), (cx, cy + h)], fill="#0f172a", width=max(2, int(size*0.06)))

def draw_square(draw, size, color):
    pad = size * 0.16
    draw.rounded_rectangle([pad, pad, size - pad, size - pad], radius=int(size * 0.2), fill=color)

def draw_cloud(draw, size, color):
    cx, cy = size / 2, size * 0.54
    # Three overlapping circles for cloud puff
    r_center = size * 0.26
    draw.ellipse([cx - r_center, cy - r_center - size*0.08, cx + r_center, cy + r_center - size*0.08], fill=color)
    draw.ellipse([cx - size*0.38, cy - size*0.14, cx - size*0.04, cy + size*0.22], fill=color)
    draw.ellipse([cx + size*0.04, cy - size*0.14, cx + size*0.38, cy + size*0.22], fill=color)
    draw.rounded_rectangle([cx - size*0.32, cy, cx + size*0.32, cy + size*0.22], radius=int(size*0.08), fill=color)

def draw_clock(draw, size, color):
    cx, cy = size / 2, size / 2
    r = size * 0.38
    w = max(2, int(size * 0.08))
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill="#1e293b", outline=color, width=w)
    # Hands (pointing to 3 o'clock)
    draw.line([(cx, cy), (cx, cy - r * 0.6)], fill=color, width=w)
    draw.line([(cx, cy), (cx + r * 0.6, cy)], fill=color, width=w)
    draw.ellipse([cx - w, cy - w, cx + w, cy + w], fill=color)

def draw_coffee(draw, size, color):
    cx, cy = size / 2, size * 0.58
    w, h = size * 0.28, size * 0.28
    # Mug body
    draw.rounded_rectangle([cx - w, cy - h, cx + w * 0.7, cy + h], radius=int(size*0.08), fill=color)
    # Mug handle
    hw = max(2, int(size * 0.07))
    draw.arc([cx + w * 0.4, cy - h * 0.6, cx + w * 1.3, cy + h * 0.6], start=270, end=90, fill=color, width=hw)
    # Steam
    sw = max(1, int(size * 0.05))
    draw.line([(cx - w*0.4, cy - h - size*0.15), (cx - w*0.4, cy - h - size*0.03)], fill="#fde68a", width=sw)
    draw.line([(cx, cy - h - size*0.18), (cx, cy - h - size*0.03)], fill="#fde68a", width=sw)

def draw_circle(draw, size, color):
    cx, cy = size / 2, size / 2
    r = size * 0.38
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)

def draw_diamond(draw, size, color):
    cx, cy = size / 2, size / 2
    w = size * 0.40
    h = size * 0.42
    # Faceted gem diamond
    draw.polygon([
        (cx, cy - h),
        (cx + w, cy),
        (cx, cy + h),
        (cx - w, cy)
    ], fill=color)
    # Inner facet reflection
    draw.polygon([
        (cx, cy - h * 0.5),
        (cx + w * 0.5, cy),
        (cx, cy + h * 0.5),
        (cx - w * 0.5, cy)
    ], outline="#ffffff", width=max(1, int(size * 0.05)))

def draw_document(draw, size, color):
    cx, cy = size / 2, size / 2
    w, h = size * 0.26, size * 0.36
    draw.rounded_rectangle([cx - w, cy - h, cx + w, cy + h], radius=int(size*0.06), fill=color)
    # Lines on document
    lw = max(1, int(size * 0.05))
    draw.line([(cx - w * 0.6, cy - h * 0.4), (cx + w * 0.6, cy - h * 0.4)], fill="#1e293b", width=lw)
    draw.line([(cx - w * 0.6, cy), (cx + w * 0.6, cy)], fill="#1e293b", width=lw)
    draw.line([(cx - w * 0.6, cy + h * 0.4), (cx + w * 0.2, cy + h * 0.4)], fill="#1e293b", width=lw)

def draw_dice(draw, size, color):
    cx, cy = size / 2, size / 2
    pad = size * 0.16
    draw.rounded_rectangle([pad, pad, size - pad, size - pad], radius=int(size * 0.18), fill=color)
    # 5 dots pattern
    dot_r = max(1.2, size * 0.05)
    dot_color = "#ffffff"
    coords = [
        (cx, cy), # center
        (cx - size * 0.18, cy - size * 0.18), # top-left
        (cx + size * 0.18, cy - size * 0.18), # top-right
        (cx - size * 0.18, cy + size * 0.18), # bottom-left
        (cx + size * 0.18, cy + size * 0.18)  # bottom-right
    ]
    for x, y in coords:
        draw.ellipse([x - dot_r, y - dot_r, x + dot_r, y + dot_r], fill=dot_color)

def draw_star(draw, size, color):
    cx, cy = size / 2, size / 2
    points = []
    outer_r = size * 0.40
    inner_r = size * 0.18
    for i in range(10):
        r = outer_r if i % 2 == 0 else inner_r
        angle = i * math.pi / 5 - math.pi / 2
        points.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
    draw.polygon(points, fill=color)

SYMBOLS = {
    # Choice A options
    "airplane": (draw_airplane, "#0284c7"), # Sky Blue
    "apple":    (draw_apple,    "#f43f5e"), # Ruby Red
    "anchor":   (draw_anchor,   "#38bdf8"), # Marine Cyan
    "triangle": (draw_triangle, "#10b981"), # Emerald Green

    # Choice B options
    "balloon":  (draw_balloon,  "#ec4899"), # Vibrant Pink
    "ball":     (draw_ball,     "#f59e0b"), # Amber
    "book":     (draw_book,     "#3b82f6"), # Royal Blue
    "square":   (draw_square,   "#6366f1"), # Indigo

    # Choice C options
    "cloud":    (draw_cloud,    "#38bdf8"), # Cloud Blue
    "clock":    (draw_clock,    "#14b8a6"), # Teal
    "coffee":   (draw_coffee,   "#d97706"), # Coffee Amber
    "circle":   (draw_circle,   "#a855f7"), # Purple

    # Choice D options
    "diamond":  (draw_diamond,  "#06b6d4"), # Cyan Gem
    "document": (draw_document, "#cbd5e1"), # Paper Silver
    "dice":     (draw_dice,     "#8b5cf6"), # Violet
    "star":     (draw_star,     "#eab308"), # Gold
}

print(f"Generating {len(SYMBOLS)} symbols across {len(SIZES)} sizes...")

for name, (draw_fn, color) in SYMBOLS.items():
    for s in SIZES:
        img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        # Subtle dark rounded background tile for visibility on any browser theme
        pad = max(1, int(s * 0.04))
        draw.rounded_rectangle([pad, pad, s - pad, s - pad], radius=int(s * 0.25), fill=(15, 23, 42, 235))
        draw_fn(draw, s, color)
        filename = f"sym_{name}_{s}.png"
        img.save(os.path.join(ICONS_DIR, filename))

print("All 16 symbol icon sets successfully generated!")

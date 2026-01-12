from PIL import Image
import os

img = Image.open("icons/icon.png")
# Windows ICOs typically have these sizes
icon_sizes = [(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)]
img.save("icons/icon.ico", format='ICO', sizes=icon_sizes)
print("Converted icon.png to icon.ico with multiple sizes")

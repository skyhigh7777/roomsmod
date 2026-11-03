import numpy as np
from PIL import Image, ImageDraw, ImageFont
faces = [np.asarray(Image.open(f'out/opt_{k}.png').convert('RGB')).astype(np.float32) for k in range(6)]
N = faces[0].shape[0]
W, H = 1280, 720
fov = 1.4835298; f = 1/np.tan(fov/2)
xs = (np.arange(W)+0.5)/W*2-1; ys = (np.arange(H)+0.5)/H*2-1
X, Y = np.meshgrid(xs*W/H, ys)
# view dir in panorama coords (x right, y down=+y, z forward)
d = np.stack([X, Y, np.full_like(X, f)], -1)
yaw = np.radians(35); pitch = np.radians(10)
cy, sy = np.cos(yaw), np.sin(yaw); cp, sp = np.cos(pitch), np.sin(pitch)
d = d @ np.array([[1,0,0],[0,cp,-sp],[0,sp,cp]]).T
d = d @ np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]]).T
x,y,z = d[...,0], d[...,1], d[...,2]
ax, ay, az = np.abs(x), np.abs(y), np.abs(z)
out = np.zeros((H,W,3), np.float32)
def put(mask, k, u, v):
    px = np.clip((u*N).astype(int),0,N-1); py = np.clip((v*N).astype(int),0,N-1)
    out[mask] = faces[k][py[mask], px[mask]]
# invert face param: P = A + u(D-A) + v(B-A), projected to cube
m = (az>=ax)&(az>=ay)&(z>0); s=x/z; t=y/z; put(m,0,(s+1)/2,(t+1)/2)
m = (az>=ax)&(az>=ay)&(z<=0); s=x/-z; t=y/-z; put(m,2,(1-s)/2,(t+1)/2)
m = (ax>az)&(ax>=ay)&(x>0); s=z/x; t=y/x; put(m,1,(1-s)/2,(t+1)/2)
m = (ax>az)&(ax>=ay)&(x<=0); s=z/-x; t=y/-x; put(m,3,(s+1)/2,(t+1)/2)
m = (ay>ax)&(ay>az)&(y<0); s=x/-y; t=z/-y; put(m,4,(s+1)/2,(t+1)/2)
m = (ay>ax)&(ay>az)&(y>=0); s=x/y; t=z/y; put(m,5,(s+1)/2,(1-t)/2)
img = Image.fromarray(np.clip(out,0,255).astype(np.uint8))
dr = ImageDraw.Draw(img, 'RGBA')
try: font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 16); big = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 44)
except Exception: font = big = ImageFont.load_default()
dr.text((W//2, 150), "MINECRAFT", font=big, fill=(230,230,230,255), anchor='mm')
for i, label in enumerate(["Singleplayer", "Multiplayer", "Minecraft Realms"]):
    y0 = 300 + i*56; dr.rectangle((W//2-200, y0, W//2+200, y0+40), fill=(70,70,70,200), outline=(20,20,20,255), width=2); dr.text((W//2, y0+20), label, font=font, fill=(255,255,255,255), anchor='mm')
dr.text((8, 8), "SRT's Rooms : Deep Corridors", font=font, fill=(255,255,255,140))
dr.text((8, H-24), "Minecraft 1.21.1 (NeoForge)", font=font, fill=(255,255,255,255))
dr.text((W-8, H-24), "(approximate preview; logo/buttons are vanilla)", font=font, fill=(255,255,255,160), anchor='ra')
img.save('/workspace/title_preview.png')
# stitched cross
C = Image.new('RGB', (4*256, 3*256), (16,16,16))
sm = [Image.open(f'out/opt_{k}.png').resize((256,256)) for k in range(6)]
for i,k in enumerate([3,0,1,2]): C.paste(sm[k], (i*256, 256))
C.paste(sm[4], (256,0)); C.paste(sm[5], (256,512)); C.save('/workspace/title_panorama_cross.png')

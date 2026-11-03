import numpy as np
from PIL import Image
import sys
rng = np.random.default_rng(7)
def tex(n, frame=True):
    im = Image.open(f'tex/{n}.png').convert('RGBA')
    a = np.asarray(im).astype(np.float32) / 255.0
    return a[:16] if frame else a
T = {k: tex(k) for k in ['stone','stone_bricks','mossy_stone_bricks','cracked_stone_bricks','oak_door_top','oak_door_bottom','lantern','chain']}
AIR, STONE, BRICK, MOSSY, CRACK, DOOR_B, DOOR_T, LANTERN = 0,1,2,3,4,5,6,7
TEXOF = {STONE:'stone', BRICK:'stone_bricks', MOSSY:'mossy_stone_bricks', CRACK:'cracked_stone_bricks', DOOR_B:'oak_door_bottom', DOOR_T:'oak_door_top'}
# grid
X0, X1, Y0, Y1, Z0, Z1 = -4, 5, -1, 5, -46, 46
G = np.full((X1-X0, Y1-Y0, Z1-Z0), BRICK, np.int8)
def S(x,y,z,v): G[x-X0, y-Y0, z-Z0] = v
# corridor air: x -2..2, y 0..3, z -44..44 ; floor y=-1 stone, ceiling y=4 stone
for x in range(-2,3):
    for z in range(Z0, Z1):
        S(x,-1,z,STONE); S(x,4,z,STONE)
        for y in range(0,4):
            if -44 <= z <= 44: S(x,y,z,AIR)
# wall variety
for x in (-3,3):
    for z in range(Z0,Z1):
        for y in range(0,4):
            r = rng.random()
            S(x,y,z, MOSSY if r<0.10 else CRACK if r<0.22 else BRICK)
# side room opening on -x wall (a dark doorway gap 3 wide) at z 15..17
for z in range(15,18):
    for y in range(0,3): S(-3,y,z,AIR)
    for x in range(-8+4, -3):  pass
# doors
def door(x,z): S(x,0,z,DOOR_B); S(x,1,z,DOOR_T)
for x,z in [(3,2),(3,-6),(3,11),(3,-17),(-3,-2),(-3,7),(-3,-12),(3,24),(-3,30),(3,-29)]: door(x,z)
for x in range(-2,3):  # end walls with door
    for y in range(0,4): S(x,y,45,BRICK); S(x,y,-45,BRICK)
S(0,0,44,AIR)
G[0-X0,0-Y0,45-Z0]=DOOR_B; G[0-X0,1-Y0,45-Z0]=DOOR_T
lanterns = []
for z in range(-41, 45, 9):
    S(0,3,z,LANTERN); lanterns.append((0.5, 3.3, z+0.5))
L = np.array(lanterns)
CAM = np.array([0.5, 1.62, 0.5 + 0.0])

def lantern_box(o, d, vox):
    # hanging lantern body in voxel: x,z in [5,11]/16, y in [2,9]/16 ; chain x,z [7,9]/16 y [9,16]/16
    best_t = np.full(len(o), np.inf); kind = np.zeros(len(o), np.int8); hp = np.zeros_like(o); nrm = np.zeros(len(o), np.int8)
    for k,(lo,hi) in enumerate([((5,2,5),(11,9,11)), ((7,9,7),(9,16,9))]):
        lo = vox + np.array(lo)/16.0; hi = vox + np.array(hi)/16.0
        with np.errstate(divide='ignore', invalid='ignore'):
            t1 = (lo - o)/d; t2 = (hi - o)/d
        tmin = np.minimum(t1,t2); tmax = np.maximum(t1,t2)
        tn = np.nanmax(tmin, axis=1); tf = np.nanmin(tmax, axis=1)
        hit = (tn <= tf) & (tf > 0) & (tn < best_t)
        best_t = np.where(hit, tn, best_t); kind = np.where(hit, k+1, kind)
        nrm = np.where(hit, np.nanargmax(tmin, axis=1), nrm)
    return best_t, kind, nrm

def trace(dirs):
    n = len(dirs)
    d = dirs / np.linalg.norm(dirs, axis=1, keepdims=True)
    o = np.tile(CAM, (n,1))
    vox = np.floor(o).astype(np.int64)
    step = np.sign(d).astype(np.int64); step[step==0] = 1
    with np.errstate(divide='ignore'):
        tdelta = np.abs(1.0/d)
        nxt = np.where(d>0, vox+1-o, o-vox)
        tmax = nxt * tdelta
    col = np.zeros((n,3), np.float32)
    done = np.zeros(n, bool); tcur = np.zeros(n); axis = np.zeros(n, np.int64)
    for it in range(140):
        act = ~done
        if not act.any(): break
        idx = np.nonzero(act)[0]
        a = np.argmin(tmax[idx], axis=1)
        tcur[idx] = tmax[idx, a]
        vox[idx, a] += step[idx, a]
        tmax[idx, a] += tdelta[idx, a]
        axis[idx] = a
        v = vox[idx]
        oob = (v[:,0]<X0)|(v[:,0]>=X1)|(v[:,1]<Y0)|(v[:,1]>=Y1)|(v[:,2]<Z0)|(v[:,2]>=Z1)
        done[idx[oob]] = True  # black
        ok = idx[~oob]; vv = vox[ok]
        b = G[vv[:,0]-X0, vv[:,1]-Y0, vv[:,2]-Z0]
        lan = ok[b==LANTERN]
        if len(lan):
            t, kind, nr = lantern_box(o[lan], d[lan], vox[lan].astype(np.float64))
            h = kind>0
            if h.any():
                li = lan[h]; p = o[li] + d[li]*t[h,None]; f = p - np.floor(p)
                lt = T['lantern']
                if True:
                    uu = np.where(nr[h]==0, f[:,2], f[:,0]); vv2 = 1-f[:,1]
                    # body side uv [0,2]-[6,9] px ; chain region dark metal
                    px = np.clip(((uu*16-5)/6*6).astype(int),0,5); py = np.clip(2+((9-f[:,1]*16)/7*7).astype(int),2,8)
                    c = lt[py, px, :3]
                    chain = kind[h]==2
                    c[chain] = np.array([0.18,0.18,0.22])
                    glow = np.where(chain, 0.5, 1.6)[:,None]
                    col[li] = c*glow*np.array([1.15,1.0,0.75])
                    dist = t[h]
                    col[li] *= np.exp(-np.maximum(dist-6,0)/20)[:,None]
                done[li] = True
        solid = ok[(b!=AIR)&(b!=LANTERN)]
        if len(solid):
            shade(solid, o, d, vox, tcur, axis, step, col, G)
            done[solid] = True
    return col

def lightat(p):
    # block light: 15 - manhattan distance to nearest lantern (continuous), MC-like curve
    dm = np.abs(p[:,None,:] - L[None,:,:]).sum(axis=2)
    lv = np.clip(15 - dm.min(axis=1), 0, 15) / 15.0
    br = lv / (4 - 3*lv)
    return np.maximum(br, 0.025)

def shade(ids, o, d, vox, tcur, axis, step, col, G):
    p = o[ids] + d[ids]*tcur[ids,None]
    a = axis[ids]; vv = vox[ids]
    b = G[vv[:,0]-X0, vv[:,1]-Y0, vv[:,2]-Z0]
    f = p - np.floor(p + 1e-9 * 0)
    f = np.clip(p - vv, 0, 0.99999)
    u = np.where(a==0, f[:,2], f[:,0]); v = np.where(a==1, f[:,2], 1-f[:,1])
    # door: half-block textures; mirror on -x wall faces
    c = np.zeros((len(ids),3)); alpha = np.ones(len(ids))
    for bid, name in TEXOF.items():
        m = b==bid
        if not m.any(): continue
        t = T[name]
        px = np.clip((u[m]*16).astype(int),0,15); py = np.clip((v[m]*16).astype(int),0,15)
        c[m] = t[py, px, :3]; alpha[m] = t[py, px, 3]
    # face normal and light sample point (in the air in front of the face)
    nvec = np.zeros_like(p); nvec[np.arange(len(ids)), a] = -step[ids, a]
    lp = p + nvec*0.5
    br = lightat(lp)
    face = np.select([a==1, a==0, a==2], [np.where(nvec[:,1]>0, 1.0, 0.5), 0.6, 0.8])
    warm = np.array([1.0, 0.86, 0.66])
    out = c * (br*face)[:,None] * warm
    # door windows: dark room behind
    holes = alpha < 0.5
    out[holes] = np.array([0.012,0.01,0.01]) + 0.03*br[holes,None]
    dist = tcur[ids]
    fog = np.exp(-np.maximum(dist-4, 0)/16)
    col[ids] = out * fog[:,None]

def face_dirs(k, N):
    A,D,B = {
      0:((-1,-1,1),(1,-1,1),(-1,1,1)), 1:((1,-1,1),(1,-1,-1),(1,1,1)), 2:((1,-1,-1),(-1,-1,-1),(1,1,-1)),
      3:((-1,-1,-1),(-1,-1,1),(-1,1,-1)), 4:((-1,-1,-1),(1,-1,-1),(-1,-1,1)), 5:((-1,1,1),(1,1,1),(-1,1,-1))}[k]
    A,D,B = map(np.array,(A,D,B))
    s = (np.arange(N)+0.5)/N
    uu, vv = np.meshgrid(s, s)  # vv rows
    P = A[None,None,:] + uu[...,None]*(D-A) + vv[...,None]*(B-A)
    P = P.reshape(-1,3).astype(np.float64)
    # panorama coords: right=+x, up=-y, forward=+z ; scene: X right, Y up, Z forward
    return np.stack([P[:,0], -P[:,1], P[:,2]], axis=1)

def render_face(k, N, ss=2):
    dirs = face_dirs(k, N*ss)
    out = np.zeros((len(dirs),3), np.float32)
    CH = 400000
    for i in range(0, len(dirs), CH): out[i:i+CH] = trace(dirs[i:i+CH])
    img = out.reshape(N, ss, N, ss, 3).mean(axis=(1,3))
    img = np.clip(img, 0, 1) ** (1/1.1)
    return Image.fromarray((img*255).astype(np.uint8))

if __name__ == '__main__':
    N = int(sys.argv[1]) if len(sys.argv)>1 else 256
    ss = int(sys.argv[2]) if len(sys.argv)>2 else 1
    for k in range(6):
        render_face(k, N, ss).save(f'out/panorama_{k}.png'); print('face', k, flush=True)

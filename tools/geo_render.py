"""Render a GeckoLib model (idle pose, textured) into a codex illustration PNG.

    python tools/geo_render.py --geo .../nekomata.geo.json --anim .../nekomata.animation.json \
        --clip main_idle --texture .../entity/nekomata.png --out .../gui/codex/nekomata.png \
        --size 128 --yaw 35 --pitch 22 --alpha 255

Pose: bone rotation/position from the FIRST frame of --clip (the raw geo rest pose is a T-pose).
Shading: texture colour * soft lambert, warm tint.
"""
import argparse
import json
import math
import os

import numpy as np
from PIL import Image


def load_geo(path):
    with open(path, "r", encoding="utf-8") as handle:
        return json.load(handle)["minecraft:geometry"][0]


def euler_matrix(rx, ry, rz):
    rx, ry, rz = math.radians(rx), math.radians(ry), math.radians(rz)
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]], dtype=np.float64)
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]], dtype=np.float64)
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]], dtype=np.float64)
    return mz @ my @ mx


def vec3(value):
    if value is None:
        return [0.0, 0.0, 0.0]
    if isinstance(value, dict):
        value = value.get("vector", [0.0, 0.0, 0.0])
    if not isinstance(value, (list, tuple)):
        return [0.0, 0.0, 0.0]
    return [float(v) for v in value]


def first_frame(prop):
    if not isinstance(prop, dict):
        return None
    if "vector" in prop:
        return prop
    keys = []
    for key in prop.keys():
        try:
            keys.append((float(key), prop[key]))
        except ValueError:
            continue
    if not keys:
        return None
    keys.sort(key=lambda item: item[0])
    return keys[0][1]


def sample_prop(prop, time):
    """Sample an animation property at `time` seconds (linear interpolation between keyframes)."""
    if not isinstance(prop, dict):
        return None
    if "vector" in prop:
        return vec3(prop)
    keys = []
    for key in prop.keys():
        try:
            keys.append((float(key), prop[key]))
        except ValueError:
            continue
    if not keys:
        return None
    keys.sort(key=lambda item: item[0])
    if time <= keys[0][0]:
        return vec3(keys[0][1])
    if time >= keys[-1][0]:
        return vec3(keys[-1][1])
    for i in range(len(keys) - 1):
        t0, v0 = keys[i]
        t1, v1 = keys[i + 1]
        if t0 <= time <= t1:
            a, b = vec3(v0), vec3(v1)
            f = 0.0 if t1 == t0 else (time - t0) / (t1 - t0)
            return [a[k] + (b[k] - a[k]) * f for k in range(3)]
    return vec3(keys[-1][1])


def idle_pose(anim_path, clip, time=0.0):
    if not anim_path:
        return {}
    with open(anim_path, "r", encoding="utf-8") as handle:
        animations = json.load(handle).get("animations", {})
    animation = animations.get(clip)
    if animation is None:
        return {}
    pose = {}
    for bone_name, bone in animation.get("bones", {}).items():
        entry = {}
        for prop_name in ("rotation", "position"):
            value = sample_prop(bone.get(prop_name), time)
            if value is not None:
                entry[prop_name] = value
        pose[bone_name] = entry
    return pose


def bone_matrices(bones, pose):
    by_name = {b["name"]: b for b in bones}
    cache = {}

    def matrix_of(name):
        if name in cache:
            return cache[name]
        bone = by_name[name]
        pivot = np.array(vec3(bone.get("pivot")), dtype=np.float64)
        overrides = pose.get(name, {})
        rot = overrides.get("rotation", vec3(bone.get("rotation")))
        pos = overrides.get("position", [0.0, 0.0, 0.0])

        t1 = np.eye(4)
        t1[:3, 3] = pivot
        t2 = np.eye(4)
        t2[:3, 3] = -pivot
        rot_m = np.eye(4)
        rot_m[:3, :3] = euler_matrix(rot[0], rot[1], rot[2])
        local = t1 @ rot_m @ t2
        local[0, 3] += pos[0]
        local[1, 3] += pos[1]
        local[2, 3] += pos[2]
        parent = bone.get("parent")
        if parent:
            local = matrix_of(parent) @ local
        cache[name] = local
        return local

    return {name: matrix_of(name) for name in by_name}


def face_uv_rects(cube, texture_w, texture_h):
    uv = cube.get("uv")
    if isinstance(uv, dict):
        out = {}
        for face, entry in uv.items():
            u0, v0 = float(entry["uv"][0]), float(entry["uv"][1])
            us, vs = entry.get("uv_size", [0, 0])
            out[face] = (u0, v0, u0 + float(us), v0 + float(vs))
        return out
    u, v = (float(uv[0]), float(uv[1])) if isinstance(uv, (list, tuple)) else (0.0, 0.0)
    size = [abs(float(x)) for x in vec3(cube.get("size"))]
    w, h, d = size
    return {
        "up": (u + d, v, u + d + w, v + d),
        "down": (u + d + w, v, u + d + w + w, v + d),
        "east": (u, v + d, u + d, v + d + h),
        "north": (u + d, v + d, u + d + w, v + d + h),
        "west": (u + d + w, v + d, u + d + w + d, v + d + h),
        "south": (u + d + w + d, v + d, u + d + w + d + w, v + d + h),
    }


def cube_faces(cube, texture_w, texture_h):
    origin = vec3(cube["origin"])
    size = vec3(cube["size"])
    for axis in range(3):
        if size[axis] < 0:
            origin[axis] += size[axis]
            size[axis] = -size[axis]
    inflate = float(cube.get("inflate", 0.0) or 0.0)
    origin = [origin[i] - inflate for i in range(3)]
    size = [size[i] + 2 * inflate for i in range(3)]

    x0, y0, z0 = origin
    x1, y1, z1 = x0 + size[0], y0 + size[1], z0 + size[2]
    p = [
        (x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1),
        (x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1),
    ]
    faces = {
        "up": ([p[4], p[7], p[6], p[5]], (0, 1, 0)),
        "down": ([p[0], p[1], p[2], p[3]], (0, -1, 0)),
        "north": ([p[3], p[7], p[6], p[2]], (0, 0, -1)),
        "south": ([p[0], p[1], p[5], p[4]], (0, 0, 1)),
        "east": ([p[1], p[5], p[6], p[2]], (1, 0, 0)),
        "west": ([p[0], p[4], p[7], p[3]], (-1, 0, 0)),
    }
    rects = face_uv_rects(cube, texture_w, texture_h)
    out = [(corners, np.array(normal, dtype=np.float64), rects.get(face))
           for face, (corners, normal) in faces.items()]
    if cube.get("rotation"):
        pivot_vec = vec3(cube.get("pivot"))
        pivot = np.array(pivot_vec, dtype=np.float64)
        if not any(pivot_vec):
            pivot = np.array([x0 + size[0] / 2, y0 + size[1] / 2, z0 + size[2] / 2], dtype=np.float64)
        rot = vec3(cube["rotation"])
        m = euler_matrix(rot[0], rot[1], rot[2])
        out = [([tuple(m @ (np.array(v, dtype=np.float64) - pivot) + pivot) for v in corners], normal, rect)
               for corners, normal, rect in out]
    return out


def render(geo, pose, texture_path, size, yaw, pitch, alpha, tint, sepia):
    bones = geo["bones"]
    matrices = bone_matrices(bones, pose)
    texture_w = float(geo["description"].get("texture_width", 64))
    texture_h = float(geo["description"].get("texture_height", 64))

    tex = Image.open(texture_path).convert("RGBA") if texture_path else None
    tex_arr = np.asarray(tex, dtype=np.float64) / 255.0 if tex is not None else None

    faces = []
    for bone in bones:
        matrix = matrices[bone["name"]]
        for cube in bone.get("cubes", []):
            for corners, normal, rect in cube_faces(cube, texture_w, texture_h):
                world = [(matrix @ np.array([*c, 1.0]))[:3] for c in corners]
                faces.append((world, matrix[:3, :3] @ normal, rect))
    if not faces:
        raise SystemExit("model has no cubes")

    ry, rp = math.radians(yaw), math.radians(pitch)
    cam = np.array(
        [[1, 0, 0], [0, math.cos(rp), -math.sin(rp)], [0, math.sin(rp), math.cos(rp)]], dtype=np.float64
    ) @ np.array(
        [[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]], dtype=np.float64
    )

    projected = []
    for world, normal, rect in faces:
        verts = [(cam @ np.array(p, dtype=np.float64)) for p in world]
        projected.append((verts, cam @ normal, rect))

    allpts = np.array([p for verts, _, _ in projected for p in verts])
    minx, miny = float(allpts[:, 0].min()), float(allpts[:, 1].min())
    maxx, maxy = float(allpts[:, 0].max()), float(allpts[:, 1].max())
    span = max(maxx - minx, maxy - miny, 1e-6)

    ss = size * 4
    scale = (ss * 0.9) / span
    depth = np.full((ss, ss), -1e18)
    rgb = np.zeros((ss, ss, 3))
    solid = np.zeros((ss, ss), dtype=bool)
    light_dir = np.array([-0.35, 0.8, 0.5])
    light_dir /= np.linalg.norm(light_dir)

    def project(p):
        px = (p[0] - minx) * scale + (ss - (maxx - minx) * scale) / 2.0
        py = ss - ((p[1] - miny) * scale + (ss - (maxy - miny) * scale) / 2.0)
        return px, py

    for verts, normal, rect in projected:
        nl = np.linalg.norm(normal)
        if nl < 1e-9:
            continue
        n = normal / nl
        shade = 0.62 + 0.38 * max(0.0, float(np.dot(n, light_dir)))
        pts = [project(v) for v in verts]
        zs = [v[2] for v in verts]
        uv_rect = rect if rect else (0.0, 0.0, texture_w, texture_h)
        quad_uv = [
            (uv_rect[0], uv_rect[1]),
            (uv_rect[2], uv_rect[1]),
            (uv_rect[2], uv_rect[3]),
            (uv_rect[0], uv_rect[3]),
        ]
        for tri in ((0, 1, 2), (0, 2, 3)):
            i0, i1, i2 = tri
            x0, y0 = pts[i0]
            x1, y1 = pts[i1]
            x2, y2 = pts[i2]
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            bx0 = max(0, int(min(x0, x1, x2)))
            bx1 = min(ss - 1, int(max(x0, x1, x2)) + 1)
            by0 = max(0, int(min(y0, y1, y2)))
            by1 = min(ss - 1, int(max(y0, y1, y2)) + 1)
            if bx1 < bx0 or by1 < by0:
                continue
            z0, z1, z2 = zs[i0], zs[i1], zs[i2]
            for py in range(by0, by1 + 1):
                fy = py + 0.5
                for px in range(bx0, bx1 + 1):
                    fx = px + 0.5
                    w0 = ((x1 - fx) * (y2 - fy) - (x2 - fx) * (y1 - fy)) / area
                    w1 = ((x2 - fx) * (y0 - fy) - (x0 - fx) * (y2 - fy)) / area
                    w2 = 1.0 - w0 - w1
                    if w0 < -1e-6 or w1 < -1e-6 or w2 < -1e-6:
                        continue
                    z = w0 * z0 + w1 * z1 + w2 * z2
                    if z <= depth[py, px]:
                        continue
                    uu = (w0 * quad_uv[i0][0] + w1 * quad_uv[i1][0] + w2 * quad_uv[i2][0]) / texture_w
                    vv = (w0 * quad_uv[i0][1] + w1 * quad_uv[i1][1] + w2 * quad_uv[i2][1]) / texture_h
                    if tex_arr is not None:
                        tx = int(np.clip(uu * tex.width, 0, tex.width - 1))
                        ty = int(np.clip(vv * tex.height, 0, tex.height - 1))
                        sample = tex_arr[ty, tx]
                        if sample[3] < 0.3:
                            continue
                        color = sample[:3] * shade
                    else:
                        color = np.array([0.82, 0.70, 0.52]) * shade
                    depth[py, px] = z
                    if sepia > 0.0:
                        warm = np.array([0.79, 0.66, 0.48])
                        lum = float(np.clip(color.mean(), 0.0, 1.0))
                        paper = warm * (0.42 + 0.58 * lum)
                        color = color * (1.0 - sepia) + paper * sepia
                    rgb[py, px] = np.clip(color * tint, 0.0, 1.0)
                    solid[py, px] = True

    out = np.zeros((ss, ss, 4), dtype=np.uint8)
    out[..., :3] = np.clip(rgb * 255, 0, 255).astype(np.uint8)
    out[..., 3] = np.where(solid, alpha, 0).astype(np.uint8)
    return Image.fromarray(out, mode="RGBA").resize((size, size), Image.LANCZOS)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--geo", required=True)
    parser.add_argument("--anim", default=None)
    parser.add_argument("--clip", default="main_idle")
    parser.add_argument("--texture", default=None)
    parser.add_argument("--out", required=True)
    parser.add_argument("--size", type=int, default=128)
    parser.add_argument("--yaw", type=float, default=35.0)
    parser.add_argument("--pitch", type=float, default=22.0)
    parser.add_argument("--alpha", type=int, default=110)
    parser.add_argument("--time", type=float, default=0.0, help="animation time (seconds) to sample")
    parser.add_argument("--tint", type=float, default=1.0, help="multiply texture colour (1 = as-is)")
    parser.add_argument("--sepia", type=float, default=0.0, help="0 = raw texture, 1 = fully warm-ink")
    args = parser.parse_args()

    geo = load_geo(args.geo)
    pose = idle_pose(args.anim, args.clip, args.time)
    img = render(geo, pose, args.texture, args.size, args.yaw, args.pitch, args.alpha, args.tint, args.sepia)
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    img.save(args.out)
    print("written: %s (%dx%d) posed bones=%d" % (args.out, img.width, img.height, len(pose)))


if __name__ == "__main__":
    main()

# -*- coding: utf-8 -*-
"""v3: 真实浏览器抓必应图片 + 标题相关性校验 + 加载校验重试 + 多候选原图下载"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

OUT_DIR = Path(r"E:/Demo/Chunbo_Wanxiang_Medical/chunbo-medical-backend/uploads/products")
OUT_DIR.mkdir(parents=True, exist_ok=True)
NODE_BIN = r"C:\Users\31665\.workbuddy\binaries\node\versions\22.22.2"
AB_JS = os.path.join(NODE_BIN, "node_modules", "agent-browser", "bin", "agent-browser.js")
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

# (id, [备选搜索词(依次尝试)], 输出名, 标题必须包含任一关键词, 优先分词)
PRODUCTS = [
    (1,  ["美林 布洛芬混悬滴剂", "布洛芬混悬滴剂 美林"],   "prod_01_bulofen_hunxuan", ["布洛芬混悬滴剂", "布洛芬混悬液"], ["美林"]),
    (2,  ["快克 复方氨酚烷胺胶囊", "复方氨酚烷胺胶囊"],     "prod_02_kuaike",          ["复方氨酚烷胺"], ["快克"]),
    (3,  ["连花清瘟胶囊 以岭", "连花清瘟胶囊"],             "prod_03_lianhuaqingwen",  ["连花清瘟"], []),
    (4,  ["京都念慈庵 蜜炼川贝枇杷膏", "念慈庵枇杷膏"],      "prod_04_niancian",        ["枇杷膏", "念慈"], ["念慈"]),
    (5,  ["江中 健胃消食片", "健胃消食片 江中"],            "prod_05_jiangzhong",      ["健胃消食"], ["江中"]),
    (6,  ["吗丁啉 多潘立酮片", "多潘立酮片 吗丁啉"],        "prod_06_madinglin",       ["吗丁啉", "多潘立酮"], ["吗丁啉"]),
    (7,  ["思密达 蒙脱石散", "蒙脱石散 思密达"],            "prod_07_mengtuoshi",      ["蒙脱石"], ["思密达"]),
    (8,  ["云南白药气雾剂", "云南白药 气雾剂"],             "prod_08_yunnanbaiyao",    ["气雾剂"], ["云南白药"]),
    (9,  ["海氏海诺 创口贴", "医用创口贴 海氏海诺"],        "prod_09_chuangkoutie",    ["创口贴", "创可贴"], ["海诺"]),
    (10, ["红霉素软膏 药品", "红霉素软膏"],                 "prod_10_hongmeisu",       ["红霉素软膏"], []),
    (11, ["炉甘石洗剂 药品", "炉甘石洗剂"],                 "prod_11_luganshi",        ["炉甘石"], []),
    (12, ["同仁堂 六味地黄丸", "六味地黄丸 浓缩丸"],        "prod_12_liuwei",          ["六味地黄"], ["同仁堂", "浓缩"]),
    (13, ["东阿阿胶 固元膏", "固元膏"],                     "prod_13_ejiao",           ["固元膏", "阿胶"], ["东阿"]),
    (14, ["999 小儿氨酚黄那敏颗粒", "小儿氨酚黄那敏颗粒"],  "prod_14_xiaoer999",       ["氨酚黄那敏"], ["999", "小儿"]),
    (15, ["鱼跃 臂式电子血压计", "鱼跃电子血压计"],         "prod_15_yuwell",          ["血压计"], ["鱼跃"]),
    (16, ["稳健医疗 医用外科口罩", "医用外科口罩 稳健"],    "prod_16_winner",          ["口罩"], ["稳健", "外科"]),
]

def ab(*args, timeout=90):
    r = subprocess.run([os.path.join(NODE_BIN, "node.exe"), AB_JS, *args],
                       capture_output=True, text=True,
                       encoding="utf-8", errors="ignore", timeout=timeout)
    return r.stdout.strip(), r.stderr.strip(), r.returncode

def http_get(url, referer=None, timeout=20):
    req = urllib.request.Request(url, headers={
        "User-Agent": UA, "Accept": "*/*", "Accept-Language": "zh-CN,zh;q=0.9"})
    if referer:
        req.add_header("Referer", referer)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read()

def is_valid_image(data):
    if len(data) < 8000:
        return False
    return (data[:3] == b"\xff\xd8\xff" or data[:8] == b"\x89PNG\r\n\x1a\n"
            or data[:6] in (b"GIF87a", b"GIF89a")
            or (data[:4] == b"RIFF" and data[8:12] == b"WEBP"))

def pick_ext(data):
    if data[:8] == b"\x89PNG\r\n\x1a\n": return ".png"
    if data[:6] in (b"GIF87a", b"GIF89a"): return ".gif"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP": return ".webp"
    return ".jpg"

def load_bing_images(query, attempts=3):
    q = urllib.parse.quote(query)
    url = f"https://cn.bing.com/images/search?q={q}"
    for a in range(attempts):
        ab("open", url)
        out, _, _ = ab("wait", "--load", "networkidle", timeout=45)
        time.sleep(1.0)
        title, _, _ = ab("get", "title")
        cands = extract_candidates()
        good = [c for c in cands if any(k in c.get("t", "") for k in KEY_HINT)]
        if title and cands and len(good) > 0:
            return cands, title
        print(f"  attempt {a+1}: title={title!r}, cands={len(cands)}, good={len(good)} -> retry")
        time.sleep(1.5)
    return cands, title

KEY_HINT = []

def extract_candidates():
    out, err, rc = ab("eval",
        "JSON.stringify([...document.querySelectorAll('.iusc')].map(e=>{try{const m=JSON.parse(e.getAttribute('m'));return {t:m.t||'',murl:m.murl||'',purl:m.purl||''}}catch(x){return null}}).filter(Boolean))")
    try:
        arr = json.loads(out)
        if isinstance(arr, str):
            arr = json.loads(arr)
        return arr or []
    except Exception:
        return []

def score(title, must, prefer):
    if not title:
        return -1
    if not any(k in title for k in must):
        return -1
    s = 0
    for k in prefer:
        if k in title: s += 10
    for k in ("京东", "价格", "说明书", "药房", "药店", "商城", "药品", "功效"):
        if k in title: s += 2
    for k in ("文档", "PPT", "课件", "论文"):
        if k in title: s -= 5
    return s

def clean_url(u):
    return u.replace("\\u002f", "/").replace("\\/", "/")

def try_download(cands, must, prefer, out_base):
    scored = sorted([(score(c.get("t",""), must, prefer), i, c) for i, c in enumerate(cands)],
                    key=lambda x: -x[0])
    best = [x for x in scored if x[0] >= 0][:6]
    if not best:
        return None
    for s, i, c in best:
        murl = clean_url(c.get("murl", ""))
        if not murl:
            continue
        for ref in (c.get("purl") or None, None):
            try:
                data = http_get(murl, referer=ref)
                if is_valid_image(data):
                    ext = pick_ext(data)
                    final = OUT_DIR / f"{out_base}{ext}"
                    final.write_bytes(data)
                    print(f"  picked [{i}] score={s} ({len(data)//1024}KB) <- {c.get('t','')[:40]}")
                    return final.name
            except Exception as e:
                pass
    return None

def download_product(pid, queries, out_base, must, prefer):
    global KEY_HINT
    KEY_HINT = must
    for q in queries:
        print(f"[{pid}] try query: {q}")
        cands, _ = load_bing_images(q)
        if not cands:
            continue
        name = try_download(cands, must, prefer, out_base)
        if name:
            print(f"[{pid}] OK -> {name}")
            return name
    print(f"[{pid}] FAILED")
    return None

def main():
    only = set(sys.argv[1:]) if len(sys.argv) > 1 else None
    ok, fail = 0, []
    mapping = {}
    for pid, queries, out_base, must, prefer in PRODUCTS:
        if only and str(pid) not in only:
            continue
        try:
            name = download_product(pid, queries, out_base, must, prefer)
            if name:
                ok += 1
                mapping[pid] = name
            else:
                fail.append(pid)
        except Exception as e:
            print(f"[{pid}] ERROR {type(e).__name__}: {e}")
            fail.append(pid)
        time.sleep(1.2)
    print("\nMAPPING = " + json.dumps(mapping, ensure_ascii=False))
    print(f"Done: {ok} ok, failed: {fail}")

if __name__ == "__main__":
    main()

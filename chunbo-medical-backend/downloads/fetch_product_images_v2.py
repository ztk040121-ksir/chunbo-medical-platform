# -*- coding: utf-8 -*-
"""v2: 通过 agent-browser 真实浏览器抓必应图片元数据，按标题相关性下载真实商品图"""
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

# (id, 必应搜索词, 输出名, 标题必须包含任一关键词, 标题优先加分词)
PRODUCTS = [
    (1,  "美林 布洛芬混悬滴剂",              "prod_01_bulofen_hunxuan", ["布洛芬混悬滴剂"], ["美林"]),
    (2,  "快克 复方氨酚烷胺胶囊",            "prod_02_kuaike",          ["复方氨酚烷胺"],  ["快克"]),
    (3,  "连花清瘟胶囊 以岭",                "prod_03_lianhuaqingwen",  ["连花清瘟"],      []),
    (4,  "京都念慈庵 川贝枇杷膏",            "prod_04_niancian",        ["枇杷膏", "念慈"], ["念慈"]),
    (5,  "江中 健胃消食片",                  "prod_05_jiangzhong",      ["健胃消食"],      ["江中"]),
    (6,  "吗丁啉 多潘立酮片",                "prod_06_madinglin",       ["吗丁啉", "多潘立酮"], ["吗丁啉"]),
    (7,  "思密达 蒙脱石散",                  "prod_07_mengtuoshi",      ["蒙脱石"],        ["思密达"]),
    (8,  "云南白药气雾剂",                   "prod_08_yunnanbaiyao",    ["气雾剂"],        ["云南白药"]),
    (9,  "海氏海诺 医用创口贴",              "prod_09_chuangkoutie",    ["创口贴", "创可贴"], ["海诺"]),
    (10, "红霉素软膏",                       "prod_10_hongmeisu",       ["红霉素软膏"],    []),
    (11, "炉甘石洗剂",                       "prod_11_luganshi",        ["炉甘石"],        []),
    (12, "同仁堂 六味地黄丸 浓缩丸",         "prod_12_liuwei",          ["六味地黄"],      ["同仁堂", "浓缩"]),
    (13, "东阿阿胶 固元膏",                  "prod_13_ejiao",           ["固元膏", "阿胶"], ["东阿"]),
    (14, "999 小儿氨酚黄那敏颗粒",           "prod_14_xiaoer999",       ["氨酚黄那敏"],    ["999", "小儿"]),
    (15, "鱼跃 臂式电子血压计",              "prod_15_yuwell",          ["血压计"],        ["鱼跃"]),
    (16, "稳健医疗 医用外科口罩",            "prod_16_winner",          ["口罩"],          ["稳健", "外科"]),
]

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

def ab(*args, timeout=60):
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
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return ".png"
    if data[:6] in (b"GIF87a", b"GIF89a"):
        return ".gif"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return ".webp"
    return ".jpg"

def fetch_candidates(query):
    q = urllib.parse.quote(query)
    ab("open", f"https://cn.bing.com/images/search?q={q}", timeout=90)
    time.sleep(1.5)
    out, err, rc = ab("eval",
        "JSON.stringify([...document.querySelectorAll('.iusc')].map(e=>{try{const m=JSON.parse(e.getAttribute('m'));return {t:m.t||'',murl:m.murl||'',turl:m.turl||''}}catch(x){return null}}).filter(Boolean))",
        timeout=60)
    # eval 返回的是 JSON 字符串（带引号），先解一层
    try:
        arr = json.loads(out)
        if isinstance(arr, str):
            arr = json.loads(arr)
    except Exception:
        m = re.search(r'\[.*\]', out, re.S)
        arr = json.loads(m.group(0)) if m else []
    return arr or []

def score(title, must, prefer):
    if not title:
        return -1
    if not any(k in title for k in must):
        return -1
    s = 0
    for k in prefer:
        if k in title:
            s += 10
    # 药品/药房相关词加分，文档/资讯词降分
    for k in ("京东", "药品", "价格", "说明书", "药房", "药店", "商城"):
        if k in title:
            s += 2
    for k in ("文档", "说明书_", "PPT", "课件"):
        if k in title:
            s -= 3
    return s

def clean_url(u):
    return u.replace("\\u002f", "/").replace("\\/", "/")

def download_product(pid, query, out_base, must, prefer):
    cands = fetch_candidates(query)
    print(f"[{pid}] {query}: {len(cands)} candidates")
    scored = sorted(
        [(score(c.get("t", ""), must, prefer), i, c) for i, c in enumerate(cands)],
        key=lambda x: -x[0])
    best = [(s, i, c) for s, i, c in scored if s >= 0][:4]
    if not best:
        print(f"  no title-matched candidate; top titles:",
              [c.get("t", "")[:30] for c in cands[:3]])
        best = [(0, i, c) for i, c in enumerate(cands[:2])]
    for s, i, c in best:
        murl, turl = clean_url(c.get("murl", "")), clean_url(c.get("turl", ""))
        # 1) 优先原图（带来源页 referer）
        for u, ref in ((murl, c.get("purl")), ):
            if not u:
                continue
            try:
                data = http_get(u, referer=ref or "https://cn.bing.com/")
                if is_valid_image(data):
                    ext = pick_ext(data)
                    (OUT_DIR / f"{out_base}{ext}").write_bytes(data)
                    print(f"  picked [{i}] score={s} via murl ({len(data)//1024}KB) title={c.get('t','')[:36]}")
                    return True
            except Exception as e:
                print(f"  murl fail: {type(e).__name__}")
        # 2) 回退必应缩略图 CDN（放大到 640）
        if turl:
            try:
                big = turl + ("&" if "?" in turl else "?") + "w=640&h=640&c=1"
                data = http_get(big, referer="https://cn.bing.com/")
                if is_valid_image(data):
                    ext = pick_ext(data)
                    (OUT_DIR / f"{out_base}{ext}").write_bytes(data)
                    print(f"  picked [{i}] score={s} via turl ({len(data)//1024}KB) title={c.get('t','')[:36]}")
                    return True
            except Exception as e:
                print(f"  turl fail: {type(e).__name__}")
    print(f"[{pid}] FAILED")
    return False

def main():
    only = set(sys.argv[1:]) if len(sys.argv) > 1 else None
    ok, fail = 0, []
    for pid, query, out_base, must, prefer in PRODUCTS:
        if only and str(pid) not in only:
            continue
        try:
            if download_product(pid, query, out_base, must, prefer):
                ok += 1
            else:
                fail.append(pid)
        except Exception as e:
            print(f"[{pid}] ERROR {type(e).__name__}: {e}")
            fail.append(pid)
        time.sleep(0.8)
    print(f"\nDone: {ok} ok, failed: {fail}")

if __name__ == "__main__":
    main()

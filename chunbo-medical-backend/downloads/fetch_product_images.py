# -*- coding: utf-8 -*-
"""从必应图片搜索为春播商城 16 个商品下载真实商品图，存入后端 uploads/products/"""
import re
import json
import sys
import time
import urllib.request
import urllib.parse
from pathlib import Path

OUT_DIR = Path(r"E:/Demo/Chunbo_Wanxiang_Medical/chunbo-medical-backend/uploads/products")
OUT_DIR.mkdir(parents=True, exist_ok=True)

# (商品ID, 搜索关键词, 输出文件名)
PRODUCTS = [
    (1,  "美林 布洛芬混悬滴剂 包装", "prod_01_bulofen_hunxuan"),
    (2,  "快克 复方氨酚烷胺胶囊 包装", "prod_02_kuaike"),
    (3,  "连花清瘟胶囊 以岭 包装", "prod_03_lianhuaqingwen"),
    (4,  "京都念慈庵 蜜炼川贝枇杷膏 包装", "prod_04_niancian"),
    (5,  "江中 健胃消食片 包装", "prod_05_jiangzhong"),
    (6,  "吗丁啉 多潘立酮片 包装", "prod_06_madinglin"),
    (7,  "思密达 蒙脱石散 包装", "prod_07_mengtuoshi"),
    (8,  "云南白药气雾剂 包装", "prod_08_yunnanbaiyao"),
    (9,  "海氏海诺 医用创口贴 包装", "prod_09_chuangkoutie"),
    (10, "红霉素软膏 药膏 包装", "prod_10_hongmeisu"),
    (11, "炉甘石洗剂 皮肤外用药品", "prod_11_luganshi"),
    (12, "同仁堂 六味地黄丸 浓缩丸 包装", "prod_12_liuwei"),
    (13, "东阿阿胶 固元膏 包装", "prod_13_ejiao"),
    (14, "999小儿氨酚黄那敏颗粒 包装", "prod_14_xiaoer999"),
    (15, "鱼跃 臂式电子血压计", "prod_15_yuwell"),
    (16, "稳健医疗 医用外科口罩 包装", "prod_16_winner"),
]

BLOCKED_DOMAINS = (
    "51wendang.com", "renrendoc.com", "wendangwang.com", "docin.com", "doc88.com",
    "book118.com", "taodocs.com", "mianfeiwendang.com", "wenku.baidu.com",
    "doc.mbalib.com", "max.book118", "yjbys.com", "ruiwen.com", "unjs.com",
    "diyifanwen.com", "cnfla.com", "oh100.com", "8win8.com", "downhot.com",
)

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

def http_get(url, referer=None, timeout=15):
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "*/*",
        "Accept-Language": "zh-CN,zh;q=0.9",
    })
    if referer:
        req.add_header("Referer", referer)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read(), resp.headers.get("Content-Type", "")

def is_valid_image(data):
    if len(data) < 8000:
        return False
    if data[:3] == b"\xff\xd8\xff":          # jpeg
        return True
    if data[:8] == b"\x89PNG\r\n\x1a\n":      # png
        return True
    if data[:6] in (b"GIF87a", b"GIF89a"):   # gif
        return True
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":  # webp
        return True
    return False

def search_bing_images(query, count=12):
    q = urllib.parse.quote(query)
    url = f"https://cn.bing.com/images/search?q={q}&first=1&qft=+filterui:photo-photo"
    try:
        raw, _ = http_get(url)
        html = raw.decode("utf-8", errors="ignore")
    except Exception as e:
        print(f"  [search error] {e}")
        return []
    murls = re.findall(r'murl&quot;:&quot;(.*?)&quot;', html)
    result = []
    for u in murls[:count]:
        u = u.replace("\\u002f", "/")
        low = u.lower()
        if any(d in low for d in BLOCKED_DOMAINS):
            continue
        if not re.search(r"\.(jpg|jpeg|png|webp)(\?|$)", low):
            continue
        result.append(u)
    return result

def download_product(pid, query, out_base):
    out_path = OUT_DIR / f"{out_base}.jpg"
    if out_path.exists() and out_path.stat().st_size > 8000:
        print(f"[{pid}] already exists, skip")
        return True
    urls = search_bing_images(query)
    print(f"[{pid}] {query} -> {len(urls)} candidates")
    for i, u in enumerate(urls[:5]):
        try:
            data, ctype = http_get(u, referer="https://cn.bing.com/")
            if not is_valid_image(data):
                print(f"  candidate {i}: invalid/not image ({len(data)}B {ctype})")
                continue
            # 统一存为原格式对应扩展名（内容魔数判断），数据库与前端只认内容
            ext = ".png" if data[:8] == b"\x89PNG\r\n\x1a\n" else ".jpg"
            final = OUT_DIR / f"{out_base}{ext}"
            final.write_bytes(data)
            # 删除可能冲突的旧 jpg
            if ext == ".png" and out_path.exists():
                out_path.unlink()
            print(f"  candidate {i}: OK -> {final.name} ({len(data)//1024}KB)")
            return True
        except Exception as e:
            print(f"  candidate {i}: fail {type(e).__name__}: {e}")
        time.sleep(0.3)
    print(f"[{pid}] FAILED")
    return False

def main():
    only = sys.argv[1:] if len(sys.argv) > 1 else None
    ok, fail = 0, []
    for pid, query, out_base in PRODUCTS:
        if only and str(pid) not in only:
            continue
        if download_product(pid, query, out_base):
            ok += 1
        else:
            fail.append((pid, query))
        time.sleep(0.6)
    print(f"\nDone: {ok} ok, {len(fail)} failed: {fail}")

if __name__ == "__main__":
    main()

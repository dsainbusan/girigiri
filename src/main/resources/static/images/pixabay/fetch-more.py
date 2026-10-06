"""
Pixabay에서 이미지를 더 받아서 이 폴더 밑에 저장하는 스크립트.
키를 코드에 적지 말고 환경변수로 넘길 것 (레포에 커밋되면 안 됨):

    PIXABAY_KEY=발급받은키 python3 fetch-more.py "검색어" 저장할폴더명 [받을개수]

예: PIXABAY_KEY=xxxx python3 fetch-more.py "bibimbap" korean-food 10
"""
import json
import os
import sys
import urllib.parse
import urllib.request

KEY = os.environ.get("PIXABAY_KEY")
if not KEY:
    print("PIXABAY_KEY 환경변수가 없어요. README.md 참고해서 키부터 발급받으세요.")
    sys.exit(1)

if len(sys.argv) < 3:
    print('사용법: PIXABAY_KEY=키 python3 fetch-more.py "검색어" 폴더명 [개수(기본 10)]')
    sys.exit(1)

query = sys.argv[1]
folder = sys.argv[2]
count = int(sys.argv[3]) if len(sys.argv) > 3 else 10

out_dir = os.path.join(os.path.dirname(__file__), folder)
os.makedirs(out_dir, exist_ok=True)

params = urllib.parse.urlencode({
    "key": KEY,
    "q": query,
    "image_type": "photo",
    "category": "food",
    "per_page": min(count, 200),
    "safesearch": "true",
})
headers = {"User-Agent": "Mozilla/5.0"}
req = urllib.request.Request(f"https://pixabay.com/api/?{params}", headers=headers)
with urllib.request.urlopen(req, timeout=15) as resp:
    data = json.loads(resp.read().decode("utf-8"))

saved = 0
for hit in data.get("hits", []):
    img_url = hit.get("webformatURL")
    if not img_url:
        continue
    fname = f"{folder}-{hit['id']}.jpg"
    req2 = urllib.request.Request(img_url, headers=headers)
    with urllib.request.urlopen(req2, timeout=20) as r2:
        with open(os.path.join(out_dir, fname), "wb") as f:
            f.write(r2.read())
    saved += 1

print(f"{saved}장을 {out_dir}에 저장했어요.")

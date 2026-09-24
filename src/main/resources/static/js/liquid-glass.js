/* ============================================================
   liquid-glass.js — 가장자리 굴절(렌즈) 효과
   - 요소 크기에 맞는 변위 맵(displacement map)을 canvas로 만들고
     SVG feDisplacementMap 필터로 배경을 휘게 한다.
   - 크롬/엣지(Chromium)에서만 굴절을 켜고, 나머지는 CSS 블러만 남는다.
   옵션 (data 속성):
     data-lg-bezel="18"   굴절이 일어나는 가장자리 폭(px)
     data-lg-scale="40"   굴절 세기
     data-lg-chroma="1"   색수차(가장자리 무지개빛) 켜기, 0이면 끔
   ============================================================ */
(function () {
  "use strict";

  const SVG_NS = "http://www.w3.org/2000/svg";
  const isChromium = !!(navigator.userAgentData
    ? navigator.userAgentData.brands.some(b => /Chromium/i.test(b.brand))
    : /Chrome\//.test(navigator.userAgent));
  const reduceTransparency = window.matchMedia("(prefers-reduced-transparency: reduce)").matches;

  let svgRoot = null;
  let uid = 0;

  function ensureSvgRoot() {
    if (svgRoot) return svgRoot;
    svgRoot = document.createElementNS(SVG_NS, "svg");
    svgRoot.setAttribute("width", "0");
    svgRoot.setAttribute("height", "0");
    svgRoot.setAttribute("aria-hidden", "true");
    svgRoot.style.position = "absolute";
    document.body.appendChild(svgRoot);
    return svgRoot;
  }

  /* 둥근 사각형 기준으로, 가장자리에서 안쪽으로 당기는 변위 맵 생성.
     R = x방향, B = y방향, 128이 "움직이지 않음" */
  function buildMap(w, h, radius, bezel) {
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d");
    const img = ctx.createImageData(w, h);
    const d = img.data;
    const r = Math.min(radius, w / 2, h / 2);
    const hw = w / 2, hh = h / 2;

    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const px = x + 0.5 - hw;
        const py = y + 0.5 - hh;
        const qx = Math.abs(px) - (hw - r);
        const qy = Math.abs(py) - (hh - r);
        const ox = Math.max(qx, 0), oy = Math.max(qy, 0);
        const outLen = Math.hypot(ox, oy);
        const sdf = outLen + Math.min(Math.max(qx, qy), 0) - r; // 안쪽이면 음수
        const inside = -sdf;                                     // 가장자리까지 거리

        let dx = 0, dy = 0;
        if (inside > 0 && inside < bezel) {
          // 바깥쪽 법선 방향
          let nx, ny;
          if (qx > 0 && qy > 0) { nx = ox / outLen; ny = oy / outLen; }
          else if (qx > qy)     { nx = Math.sign(px); ny = 0; }
          else                  { nx = 0; ny = Math.sign(py); }
          // 가장자리일수록 강하게 (볼록 렌즈 곡선)
          const t = 1 - inside / bezel;
          const m = t * t * (3 - 2 * t);
          dx = -nx * m;
          dy = -ny * m;
        }
        const i = (y * w + x) * 4;
        d[i]     = 128 + dx * 127;
        d[i + 1] = 128;
        d[i + 2] = 128 + dy * 127;
        d[i + 3] = 255;
      }
    }
    ctx.putImageData(img, 0, 0);
    return canvas.toDataURL("image/png");
  }

  function el(tag, attrs) {
    const n = document.createElementNS(SVG_NS, tag);
    for (const k in attrs) n.setAttribute(k, attrs[k]);
    return n;
  }

  function buildFilter(id, mapUrl, scale, chroma) {
    const f = el("filter", {
      id, x: "0", y: "0", width: "100%", height: "100%",
      "color-interpolation-filters": "sRGB"
    });
    f.appendChild(el("feImage", {
      href: mapUrl, x: "0", y: "0", width: "100%", height: "100%",
      preserveAspectRatio: "none", result: "map"
    }));

    if (!chroma) {
      f.appendChild(el("feDisplacementMap", {
        in: "SourceGraphic", in2: "map", scale,
        xChannelSelector: "R", yChannelSelector: "B"
      }));
      return f;
    }

    // 색수차: R/G/B를 조금씩 다른 세기로 휘게 해서 다시 합침
    const channels = [
      ["r", scale * 1.15, "1 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0"],
      ["g", scale,        "0 0 0 0 0  0 1 0 0 0  0 0 0 0 0  0 0 0 1 0"],
      ["b", scale * 0.85, "0 0 0 0 0  0 0 0 0 0  0 0 1 0 0  0 0 0 1 0"]
    ];
    channels.forEach(([c, s, m]) => {
      f.appendChild(el("feDisplacementMap", {
        in: "SourceGraphic", in2: "map", scale: s,
        xChannelSelector: "R", yChannelSelector: "B", result: c + "d"
      }));
      f.appendChild(el("feColorMatrix", { in: c + "d", type: "matrix", values: m, result: c }));
    });
    f.appendChild(el("feBlend", { in: "g", in2: "b", mode: "screen", result: "gb" }));
    f.appendChild(el("feBlend", { in: "r", in2: "gb", mode: "screen" }));
    return f;
  }

  function setup(host) {
    const layer = host.querySelector(":scope > .lg-glass__layer");
    if (!layer) return;

    const id = "lg-filter-" + (++uid);
    const bezel = parseFloat(host.dataset.lgBezel || 18);
    const scale = parseFloat(host.dataset.lgScale || 40);
    const chroma = host.dataset.lgChroma !== "0";
    let current = null;
    let lastKey = "";

    function render() {
      const w = Math.round(layer.offsetWidth);
      const h = Math.round(layer.offsetHeight);
      if (!w || !h) return;
      const radius = parseFloat(getComputedStyle(layer).borderTopLeftRadius) || 0;
      const key = w + "x" + h + "r" + radius;
      if (key === lastKey) return;       // 크기가 그대로면 다시 만들지 않음
      lastKey = key;

      const filter = buildFilter(id, buildMap(w, h, radius, bezel),
                                 host._lgScale ?? scale, chroma);
      if (current) current.replaceWith(filter);
      else ensureSvgRoot().appendChild(filter);
      current = filter;
      layer.style.filter = "url(#" + id + ")";
    }

    // 세기를 바꾸고 싶을 때: host.lgSetScale(60)
    host.lgSetScale = (s) => {
      host._lgScale = s;
      current && current.querySelectorAll("feDisplacementMap").forEach((n, i, all) => {
        const k = all.length === 3 ? [1.15, 1, 0.85][i] : 1;
        n.setAttribute("scale", s * k);
      });
    };
    host.lgDisable = () => { layer.style.filter = ""; };
    host.lgEnable  = () => { if (current) layer.style.filter = "url(#" + id + ")"; };

    render();
    new ResizeObserver(render).observe(layer);
  }

  function init() {
    if (!isChromium || reduceTransparency) return; // 비크롬: CSS 블러만 사용
    document.querySelectorAll(".lg-glass").forEach(setup);
  }

  window.LiquidGlass = { init, setup, supported: isChromium };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();

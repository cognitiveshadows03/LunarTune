/* ============================================================
   LUNARTUNE // STATIC SITE
   1. One-shot boot: JP -> KR -> RU -> glitch scramble -> EN,
      then blank 0.5s, then one reveal glitch, then static.
   2. Live stats from the GitHub API (stars, total downloads,
      latest stable version). Silent fallback if offline.
   ============================================================ */
(function () {
  "use strict";

  var REPO = "cognitiveshadows03/LunarTune";
  var API = "https://api.github.com/repos/" + REPO;

  /* ---------------- BOOT ---------------- */

  var bootEl = document.getElementById("boot");
  var nameEl = document.getElementById("boot-name");
  var siteEl = document.getElementById("site");
  var GLYPHS = "§∆†Ø₩ŁИΞЖ◊#/\\|<>*+=×";
  var FINAL = "LUNARTUNE";
  var STEPS = ["ルナチューン", "루나튠", "ЛУНАТЮН"];
  var booted = false;

  function sleep(ms) {
    return new Promise(function (resolve) { setTimeout(resolve, ms); });
  }

  function slice() {
    nameEl.classList.remove("slice");
    void nameEl.offsetWidth; // restart the one-shot animation
    nameEl.classList.add("slice");
    return sleep(140);
  }

  function scramble(durationMs) {
    return new Promise(function (resolve) {
      var started = Date.now();
      var tick = setInterval(function () {
        var progress = (Date.now() - started) / durationMs;
        if (progress >= 1) {
          clearInterval(tick);
          nameEl.textContent = FINAL;
          resolve();
          return;
        }
        var revealed = Math.floor(progress * FINAL.length);
        var out = "";
        for (var i = 0; i < FINAL.length; i++) {
          if (i < revealed) {
            out += FINAL[i];
          } else {
            out += GLYPHS[Math.floor(Math.random() * GLYPHS.length)];
          }
        }
        nameEl.textContent = out;
      }, 55);
    });
  }

  function finishBoot() {
    if (booted) return;
    booted = true;
    bootEl.style.display = "none";
    // Blank 0.5s, then ONE glitch as everything appears.
    setTimeout(function () {
      siteEl.hidden = false;
      siteEl.classList.add("reveal");
      setTimeout(function () { siteEl.classList.remove("reveal"); }, 420);
    }, 500);
  }

  // Click anywhere to skip the boot.
  bootEl.addEventListener("click", finishBoot);

  async function runBoot() {
    await sleep(350);
    for (var i = 0; i < STEPS.length; i++) {
      if (booted) return;
      nameEl.textContent = STEPS[i];
      await sleep(430);
      if (booted) return;
      await slice();
    }
    if (booted) return;
    await scramble(650);
    if (booted) return;
    await sleep(450);
    finishBoot();
  }

  /* ---------------- LIVE STATS ---------------- */

  function compact(n) {
    if (typeof n !== "number" || isNaN(n)) return "···";
    if (n < 1000) return String(n);
    var units = ["K", "M", "B"];
    var u = -1;
    var v = n;
    while (v >= 1000 && u < units.length - 1) { v /= 1000; u++; }
    return (v >= 100 ? Math.round(v) : v.toFixed(1).replace(/\.0$/, "")) + units[u];
  }

  function loadStats() {
    var starsEl = document.getElementById("stat-stars");
    var dlEl = document.getElementById("stat-downloads");
    var verEl = document.getElementById("dl-version");
    var btnEl = document.getElementById("dl-btn");

    fetch(API)
      .then(function (r) { if (!r.ok) throw new Error("repo"); return r.json(); })
      .then(function (repo) { starsEl.textContent = compact(repo.stargazers_count); })
      .catch(function () { starsEl.textContent = "—"; });

    fetch(API + "/releases?per_page=100")
      .then(function (r) { if (!r.ok) throw new Error("releases"); return r.json(); })
      .then(function (releases) {
        var total = 0;
        var stable = null;
        for (var i = 0; i < releases.length; i++) {
          var rel = releases[i];
          if (rel.draft) continue;
          var assets = rel.assets || [];
          for (var j = 0; j < assets.length; j++) {
            total += assets[j].download_count || 0;
          }
          // Stable tags look like v5.3.0; nightlies look like N2026...
          if (!stable && /^v\d/.test(rel.tag_name || "")) stable = rel;
        }
        dlEl.textContent = compact(total);
        if (stable) {
          verEl.textContent = stable.tag_name.toUpperCase() + " STABLE";
          btnEl.href = stable.html_url;
        }
      })
      .catch(function () { dlEl.textContent = "—"; });
  }

  /* ---------------- SCREENSHOT CASCADE ----------------
     Mirrors the app's experimental quick-picks pager: cards tuck under
     each other, and every card's scale / fade / tilt / stack order is a
     pure function of its offset from the scroll center. No timers, no
     loops — transforms update only while scrolling. */

  function initCascade() {
    var track = document.querySelector(".shots");
    var posEl = document.getElementById("shot-pos");
    var fillEl = document.getElementById("shots-fill");
    if (!track) return;
    document.documentElement.classList.add("js");
    var cards = Array.prototype.slice.call(track.querySelectorAll("figure"));
    var total = cards.length;
    var ticking = false;
    var voids = Array.prototype.slice.call(track.querySelectorAll(".shots-void"));

    // Size the end spacers so the first/last card sits dead-center.
    function sizeVoids() {
      if (!cards.length) return;
      var w = Math.max(0, track.clientWidth / 2 - cards[0].offsetWidth / 2);
      for (var v = 0; v < voids.length; v++) voids[v].style.width = w + "px";
    }

    function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

    function update() {
      ticking = false;
      var viewCenter = track.scrollLeft + track.clientWidth / 2;
      var best = 0;
      var bestDamp = Infinity;
      for (var i = 0; i < cards.length; i++) {
        var card = cards[i];
        var cardCenter = card.offsetLeft + card.offsetWidth / 2;
        var offset = (cardCenter - viewCenter) / card.offsetWidth;
        var damp = Math.min(1, Math.abs(offset));
        var cl = clamp(offset, -1, 1);
        card.style.transform =
          "scale(" + (1 - 0.08 * damp).toFixed(3) + ")" +
          " rotateY(" + (6 * cl).toFixed(2) + "deg)";
        card.style.opacity = (1 - 0.35 * damp).toFixed(3);
        card.style.zIndex = String(10 - Math.round(damp * 10));
        if (damp < bestDamp) { bestDamp = damp; best = i; }
      }
      if (posEl) {
        posEl.textContent = ("0" + (best + 1)).slice(-2) + " / " + ("0" + total).slice(-2);
      }
      if (fillEl) {
        fillEl.style.width = ((best + 1) / total * 100).toFixed(1) + "%";
      }
    }

    track.addEventListener("scroll", function () {
      if (!ticking) { ticking = true; requestAnimationFrame(update); }
    }, { passive: true });
    window.addEventListener("resize", function () { sizeVoids(); update(); });
    // Images change card widths as they load — re-measure then too,
    // otherwise the spacers are sized while cards are still collapsed.
    window.addEventListener("load", function () { sizeVoids(); update(); });
    var imgs = track.querySelectorAll("img");
    for (var k = 0; k < imgs.length; k++) {
      if (imgs[k].complete) continue;
      imgs[k].addEventListener("load", function () { sizeVoids(); update(); });
    }
    sizeVoids();
    update();
  }

  initCascade();
  runBoot();
  loadStats();
})();

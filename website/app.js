/* ============================================================
   LUNARTUNE // STATIC SITE
   1. One-shot boot: JP -> KR -> RU -> glitch scramble -> EN,
      then blank 0.5s, then one reveal glitch, then static.
   2. Live stats from the GitHub API (stars, total downloads,
      latest stable version). Silent fallback if offline.
   3. Screenshot category filtering (tabs).
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
    var dlBtns = document.querySelectorAll(".dl-card .btn");

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
          // Update all download links to latest stable
          for (var k = 0; k < dlBtns.length; k++) {
            dlBtns[k].href = stable.html_url;
          }
        }
      })
      .catch(function () { dlEl.textContent = "—"; });
  }

  /* ---------------- SCREENSHOT CASCADE + FILTER TABS ----------------
     Mirrors the app's experimental quick-picks pager: cards tuck under
     each other, and every card's scale / fade / tilt / stack order is a
     pure function of its offset from the scroll center. No timers, no
     loops — transforms update only while scrolling. Tab filters hide
     cards and recalculate the cascade on the remaining visible set. */

  function initCascade() {
    var track = document.querySelector(".shots");
    var posEl = document.getElementById("shot-pos");
    var fillEl = document.getElementById("shots-fill");
    if (!track) return;
    document.documentElement.classList.add("js");
    var allCards = Array.prototype.slice.call(track.querySelectorAll("figure"));
    var ticking = false;
    var voids = Array.prototype.slice.call(track.querySelectorAll(".shots-void"));

    function clamp(v, lo, hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** Return only the currently visible (non-hidden) figures. */
    function visibleCards() {
      return allCards.filter(function (c) { return !c.classList.contains("hidden"); });
    }

    // Size the end spacers so the first/last visible card sits dead-center.
    function sizeVoids() {
      var vis = visibleCards();
      if (!vis.length) return;
      var w = Math.max(0, track.clientWidth / 2 - vis[0].offsetWidth / 2);
      for (var v = 0; v < voids.length; v++) voids[v].style.width = w + "px";
    }

    function update() {
      ticking = false;
      var cards = visibleCards();
      var total = cards.length;
      if (!total) return;
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
      // Reset transforms on hidden cards so they don't retain stale state
      for (var h = 0; h < allCards.length; h++) {
        if (allCards[h].classList.contains("hidden")) {
          allCards[h].style.transform = "";
          allCards[h].style.opacity = "";
          allCards[h].style.zIndex = "";
        }
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
    window.addEventListener("load", function () { sizeVoids(); update(); });
    var imgs = track.querySelectorAll("img");
    for (var k = 0; k < imgs.length; k++) {
      if (imgs[k].complete) continue;
      imgs[k].addEventListener("load", function () { sizeVoids(); update(); });
    }

    /* ---- Filter tabs ---- */
    var tabs = document.querySelectorAll(".screen-tab");
    for (var t = 0; t < tabs.length; t++) {
      tabs[t].addEventListener("click", function (e) {
        var btn = e.currentTarget;
        var filter = btn.getAttribute("data-filter");

        // Update active tab
        for (var j = 0; j < tabs.length; j++) {
          tabs[j].classList.remove("active");
          tabs[j].setAttribute("aria-selected", "false");
        }
        btn.classList.add("active");
        btn.setAttribute("aria-selected", "true");

        // Show / hide cards
        for (var k = 0; k < allCards.length; k++) {
          var cat = allCards[k].getAttribute("data-cat");
          if (filter === "all" || cat === filter) {
            allCards[k].classList.remove("hidden");
          } else {
            allCards[k].classList.add("hidden");
          }
        }

        // Recalculate spacers and cascade for new visible set
        sizeVoids();
        track.scrollLeft = 0;
        update();
      });
    }

    sizeVoids();
    update();
  }

  initCascade();
  runBoot();
  loadStats();
})();

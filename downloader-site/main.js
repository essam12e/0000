(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const ICON = { link: '<svg viewBox="0 0 24 24"><path d="M10 13a5 5 0 0 0 7.07 0l3-3a5 5 0 0 0-7.07-7.07l-1.5 1.5M14 11a5 5 0 0 0-7.07 0l-3 3a5 5 0 0 0 7.07 7.07l1.5-1.5"/></svg>',
    paste: '<svg viewBox="0 0 24 24"><rect x="8" y="3" width="8" height="4" rx="1"/><path d="M16 5h2a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h2"/></svg>',
    check: '<svg viewBox="0 0 24 24"><path d="m5 12.5 4.5 4.5L19 7.5"/></svg>' };
  const URL_TEXT = 'youtube.com/watch?v=dQ7x…';
  const head = '<div class="m-head"><img src="assets/icon.png" alt=""><span>داونلودر</span></div>';
  const qualities = (on) => [['4K', '2160p', '1.2 ج.ب'], ['', '1080p', '320 م.ب'], ['', '720p', '140 م.ب']]
    .map(([badge, name, size], i) => `<div class="m-q${i === on ? ' on' : ''}"><u></u><b>${name}${badge ? `<small>${badge}</small>` : ''}</b><span>${size}</span></div>`).join('');
  const thumb = '<div class="m-thumb"><i></i><em>12:48</em></div><div class="m-title">رحلة إلى قمم الجبال — فيلم قصير</div>';

  // ---------- mockups ----------
  $$('[data-phone]').forEach((el) => {
    const live = el.dataset.phone === 'live';
    el.innerHTML = `<div class="scr">${head}
      <div class="m-url">${ICON.link}<span>${live ? `<span class="ph">ألصق الرابط هنا</span><span class="typed">${URL_TEXT}</span>` : URL_TEXT}</span></div>
      ${live ? `<div class="m-paste">${ICON.paste}لصق من الحافظة</div>` : ''}
      <div class="m-result">${thumb}${qualities(live ? 1 : 0)}
        <div class="m-btn"><div class="bar"></div><span class="lbl">تحميل</span></div></div>
      ${live ? `<div class="m-done"><i>${ICON.check}</i>اكتمل التحميل<small>الملف جاهز في «تحميلاتي»</small></div>` : ''}</div>`;
  });
  $$('[data-desk]').forEach((el) => {
    el.innerHTML = `<div class="d-bar"><i></i><i></i><i></i><span>داونلودر</span></div>
      <div class="d-body"><aside class="d-side">${head}<p class="on">الرئيسية</p><p>تحميلاتي</p><p>الإعدادات</p></aside>
      <div class="d-main"><div class="m-url">${ICON.link}<span>${URL_TEXT}</span></div>
        <div class="d-col">${thumb}</div>
        <div class="d-col">${qualities(0)}<div class="m-btn"><span>تحميل</span></div></div></div></div>`;
  });

  // marquee: duplicate content so the loop is seamless
  $$('.marquee .track').forEach((t) => { t.innerHTML += t.innerHTML + t.innerHTML + t.innerHTML; });

  // orbit: place satellites evenly on their ring
  $$('.sats').forEach((ring, r) => {
    const sats = $$('.sat', ring);
    sats.forEach((s, i) => {
      const a = (i / sats.length) * Math.PI * 2 + r * 0.6;
      s.style.left = 50 + 50 * Math.cos(a) + '%';
      s.style.top = 50 + 50 * Math.sin(a) + '%';
    });
  });

  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (!window.gsap || reduce) { // no animation: show the finished state
    $$('[data-count]').forEach((n) => { n.textContent = n.dataset.count; });
    const ph = $('.m-url .ph'); if (ph) ph.remove();
    return;
  }
  gsap.registerPlugin(ScrollTrigger);
  const fine = matchMedia('(hover: hover) and (pointer: fine)').matches;

  // ---------- scroll progress ----------
  gsap.to('.progress', { scaleX: 1, ease: 'none', scrollTrigger: { start: 0, end: 'max', scrub: 0.3 } });

  // ---------- hero intro ----------
  const intro = gsap.timeline({ defaults: { ease: 'expo.out' } });
  intro.from('.nav', { y: -80, opacity: 0, duration: 1 })
    .from('.eyebrow', { y: 20, opacity: 0, duration: .8 }, .15)
    .from('h1 .w', { yPercent: 120, rotate: 6, duration: 1.3, stagger: .09 }, .25)
    .from('.lead', { y: 26, opacity: 0, duration: 1 }, .75)
    .from('.hero .cta .btn', { y: 30, opacity: 0, scale: .92, duration: .9, stagger: .1, ease: 'back.out(1.6)' }, .9)
    .from('.ticks li', { opacity: 0, x: 16, duration: .6, stagger: .08 }, 1.15)
    .from('.core', { scale: 0, rotate: -40, duration: 1.4, ease: 'elastic.out(1, .65)' }, .4)
    .from('.halo', { scale: .3, opacity: 0, duration: 1.6 }, .4)
    .from('.ring', { scale: .4, opacity: 0, duration: 1.4, stagger: .12 }, .55)
    .from('.sat', { scale: 0, opacity: 0, duration: .8, stagger: .07, ease: 'back.out(2.2)' }, .8)
    .from('.scroll-hint', { opacity: 0, duration: 1 }, 1.6);

  // orbit rotation (satellites counter-rotate to stay upright)
  [['.s1', 34, 1], ['.s2', 58, -1]].forEach(([sel, dur, dir]) => {
    gsap.to(sel, { rotate: 360 * dir, duration: dur, ease: 'none', repeat: -1 });
    gsap.to(sel + ' .sat', { rotate: -360 * dir, duration: dur, ease: 'none', repeat: -1 });
  });
  gsap.to('.ring.r1', { rotate: -360, duration: 80, ease: 'none', repeat: -1 });
  gsap.to('.core', { y: -12, duration: 2.6, ease: 'sine.inOut', yoyo: true, repeat: -1, delay: 1.8 });
  gsap.to('.halo', { scale: 1.14, opacity: .75, duration: 3.2, ease: 'sine.inOut', yoyo: true, repeat: -1, delay: 2 });
  // hero parallax on scroll
  gsap.to('.orbit', { yPercent: 18, scale: .9, ease: 'none', scrollTrigger: { trigger: '.hero', start: 'top top', end: 'bottom top', scrub: true } });
  gsap.to('.hero-text', { yPercent: -10, opacity: .25, ease: 'none', scrollTrigger: { trigger: '.hero', start: 'top top', end: 'bottom top', scrub: true } });

  // ---------- pointer effects ----------
  if (fine) {
    const glow = $('.glow');
    const gx = gsap.quickTo(glow, 'x', { duration: .6, ease: 'power3' }), gy = gsap.quickTo(glow, 'y', { duration: .6, ease: 'power3' });
    addEventListener('pointermove', (e) => { gx(e.clientX); gy(e.clientY); gsap.to(glow, { opacity: 1, duration: .4, overwrite: 'auto' }); }, { passive: true });
    // orbit follows the pointer slightly
    const ox = gsap.quickTo('.orbit', 'x', { duration: 1, ease: 'power3' }), oy = gsap.quickTo('.orbit', 'rotationY', { duration: 1, ease: 'power3' });
    $('.hero').addEventListener('pointermove', (e) => { const k = e.clientX / innerWidth - .5; ox(k * -26); oy(k * 14); });
    // magnetic buttons
    $$('.magnet').forEach((b) => {
      const mx = gsap.quickTo(b, 'x', { duration: .45, ease: 'power3' }), my = gsap.quickTo(b, 'y', { duration: .45, ease: 'power3' });
      b.addEventListener('pointermove', (e) => { const r = b.getBoundingClientRect(); mx((e.clientX - r.left - r.width / 2) * .28); my((e.clientY - r.top - r.height / 2) * .4); });
      b.addEventListener('pointerleave', () => { mx(0); my(0); });
    });
    // tilt cards with a light that follows the pointer
    $$('.tilt').forEach((c) => {
      const rx = gsap.quickTo(c, 'rotationX', { duration: .5, ease: 'power3' }), ry = gsap.quickTo(c, 'rotationY', { duration: .5, ease: 'power3' });
      c.addEventListener('pointermove', (e) => {
        const r = c.getBoundingClientRect(), px = (e.clientX - r.left) / r.width, py = (e.clientY - r.top) / r.height;
        c.style.setProperty('--mx', px * 100 + '%'); c.style.setProperty('--my', py * 100 + '%');
        rx((.5 - py) * 9); ry((px - .5) * 9);
      });
      c.addEventListener('pointerleave', () => { rx(0); ry(0); });
    });
  }

  // ---------- gold dust ----------
  (() => {
    const cv = $('.dust'), ctx = cv.getContext('2d');
    let w, h, dpr, parts = [];
    const size = () => {
      dpr = Math.min(devicePixelRatio || 1, 2); w = cv.clientWidth; h = cv.clientHeight;
      cv.width = w * dpr; cv.height = h * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      parts = Array.from({ length: Math.round(Math.min(90, w / 14)) }, () => ({ x: Math.random() * w, y: Math.random() * h, r: Math.random() * 1.6 + .3, v: Math.random() * .28 + .06, d: Math.random() * 6.28, a: Math.random() * .6 + .15 }));
    };
    size(); addEventListener('resize', size);
    let visible = true;
    ScrollTrigger.create({ trigger: '.hero', start: 'top bottom', end: 'bottom top', onToggle: (s) => { visible = s.isActive; } });
    gsap.ticker.add((t) => {
      if (!visible) return;
      ctx.clearRect(0, 0, w, h);
      for (const p of parts) {
        p.y -= p.v; p.x += Math.sin(t * .4 + p.d) * .18;
        if (p.y < -4) { p.y = h + 4; p.x = Math.random() * w; }
        ctx.globalAlpha = p.a * (.6 + .4 * Math.sin(t * 1.3 + p.d));
        ctx.fillStyle = '#e9c987'; ctx.beginPath(); ctx.arc(p.x, p.y, p.r, 0, 6.28); ctx.fill();
      }
    });
  })();

  // ---------- marquee (speeds up with scroll) ----------
  const loops = $$('.marquee').map((m) => {
    const dir = +m.dataset.dir, t = $('.track', m);
    return gsap.fromTo(t, { xPercent: dir > 0 ? -50 : 0 }, { xPercent: dir > 0 ? 0 : -50, duration: 46, ease: 'none', repeat: -1 });
  });
  const boost = { v: 1 };
  ScrollTrigger.create({ onUpdate: (s) => {
    boost.v = 1 + Math.min(Math.abs(s.getVelocity()) / 260, 7);
    loops.forEach((l) => l.timeScale(boost.v));
    gsap.to(boost, { v: 1, duration: 1, overwrite: true, onUpdate: () => loops.forEach((l) => l.timeScale(boost.v)) });
  } });

  // ---------- generic reveals ----------
  $$('.head').forEach((hd) => gsap.from(hd.children, { y: 44, opacity: 0, duration: 1, stagger: .12, ease: 'expo.out', scrollTrigger: { trigger: hd, start: 'top 84%' } }));
  gsap.from('.marquee', { opacity: 0, y: 30, duration: 1, stagger: .15, scrollTrigger: { trigger: '.marquee', start: 'top 90%' } });
  ScrollTrigger.batch('.stat', { start: 'top 88%', once: true, onEnter: (els) => {
    gsap.from(els, { y: 50, opacity: 0, duration: .9, stagger: .1, ease: 'expo.out' });
    els.forEach((el) => { const n = $('[data-count]', el), o = { v: 0 }; gsap.to(o, { v: +n.dataset.count, duration: 1.8, ease: 'power3.out', onUpdate: () => { n.textContent = Math.round(o.v); } }); });
  } });
  ScrollTrigger.batch('.card', { start: 'top 90%', once: true, onEnter: (els) => gsap.from(els, { y: 70, opacity: 0, scale: .94, duration: 1, stagger: .09, ease: 'expo.out', clearProps: 'transform' }) });
  gsap.from('.fi', { scale: 0, rotate: -30, duration: .8, stagger: .06, ease: 'back.out(2)', scrollTrigger: { trigger: '.bento', start: 'top 80%' } });
  gsap.from('.also li', { opacity: 0, y: 16, duration: .5, stagger: .05, scrollTrigger: { trigger: '.also', start: 'top 92%' } });

  // ---------- how it works: scroll-driven demo ----------
  (() => {
    const phone = $('.how .phone'), steps = $$('.how .step');
    const typed = $('.typed', phone), ph = $('.ph', phone), lbl = $('.lbl', phone), pct = { v: 0 };
    const rows = $$('.m-q', phone);
    const setStep = (i) => steps.forEach((s, k) => s.classList.toggle('on', k === i));
    gsap.set(typed, { width: 0 });
    gsap.set($('.m-result', phone), { opacity: 0, y: 30 });
    const tl = gsap.timeline({ defaults: { ease: 'power2.out' }, paused: true });
    tl.call(setStep, [0], 0)
      .to($('.m-paste', phone), { scale: .9, duration: .15, yoyo: true, repeat: 1 }, .2)
      .to(ph, { opacity: 0, width: 0, duration: .1 }, .45)
      .to(typed, { width: 'auto', duration: .9, ease: 'steps(22)' }, .5)
      .to($('.m-paste', phone), { height: 0, opacity: 0, paddingBlock: 0, marginTop: '-.8em', borderWidth: 0, duration: .4 }, 1.4)
      .to($('.m-result', phone), { opacity: 1, y: 0, duration: .6 }, 1.5)
      .from(rows, { x: 30, opacity: 0, duration: .4, stagger: .1 }, 1.7)
      .call(setStep, [1], 2.1).call(setStep, [0], 2.09)
      .call(() => { rows[1].classList.add('on'); rows[0].classList.remove('on'); }, null, 2.39)
      .call(() => { rows[0].classList.add('on'); rows[1].classList.remove('on'); }, null, 2.4)
      .fromTo(rows[0], { scale: 1 }, { scale: 1.05, duration: .2, yoyo: true, repeat: 1 }, 2.4)
      .call(setStep, [2], 3.2).call(setStep, [1], 3.19)
      .to($('.m-btn', phone), { scale: .95, duration: .12, yoyo: true, repeat: 1 }, 3.2)
      .to($('.bar', phone), { scaleX: 1, duration: 1.5, ease: 'power1.inOut' }, 3.45)
      .to(pct, { v: 100, duration: 1.5, ease: 'power1.inOut', onUpdate: () => { lbl.textContent = pct.v < 1 ? 'تحميل' : 'جاري التحميل ' + Math.round(pct.v) + '%'; } }, 3.45)
      .to($('.m-done', phone), { autoAlpha: 1, duration: .4 }, 5.05)
      .from($('.m-done i', phone), { scale: 0, rotate: -90, duration: .7, ease: 'back.out(2.4)' }, 5.1)
      .to({}, { duration: .5 });
    const mm = gsap.matchMedia();
    mm.add('(min-width: 981px)', () => { // desktop: pin the section and scrub the demo with the scroll
      const st = ScrollTrigger.create({ trigger: '.how', start: 'top top', end: '+=260%', pin: true, scrub: .6, animation: tl });
      return () => st.kill();
    });
    mm.add('(max-width: 980px)', () => { // mobile: play the demo once it is in view, and loop it
      tl.repeat(-1).repeatDelay(1.6);
      const st = ScrollTrigger.create({ trigger: phone, start: 'top 75%', end: 'bottom 10%', onToggle: (s) => (s.isActive ? tl.play() : tl.pause()) });
      return () => { st.kill(); tl.repeat(0); };
    });
    gsap.from(phone, { y: 80, opacity: 0, rotate: 5, duration: 1.2, ease: 'expo.out', scrollTrigger: { trigger: '.how', start: 'top 70%' } });
    gsap.from('.steps', { y: 50, opacity: 0, duration: 1, ease: 'expo.out', scrollTrigger: { trigger: '.how', start: 'top 70%' } });
  })();

  // ---------- versions ----------
  $$('.vcard').forEach((c, i) => {
    gsap.from(c, { y: 90, opacity: 0, duration: 1.1, delay: i * .12, ease: 'expo.out', scrollTrigger: { trigger: c, start: 'top 86%' } });
    const shot = $('.phone, .desk', c);
    gsap.fromTo(shot, { y: 70 }, { y: -10, ease: 'none', scrollTrigger: { trigger: c, start: 'top bottom', end: 'bottom top', scrub: .8 } });
    gsap.from($$('.vbody > *', c), { y: 24, opacity: 0, duration: .7, stagger: .07, ease: 'power3.out', scrollTrigger: { trigger: $('.vbody', c), start: 'top 88%' } });
  });

  // ---------- final ----------
  const fin = gsap.timeline({ scrollTrigger: { trigger: '.final', start: 'top 72%' }, defaults: { ease: 'expo.out' } });
  fin.from('.final-icon', { scale: 0, rotate: 180, duration: 1.2, ease: 'elastic.out(1, .6)' })
    .from('.final h2', { y: 60, opacity: 0, duration: 1 }, .15)
    .from('.final .sub', { y: 30, opacity: 0, duration: .8 }, .3)
    .from('.final .btn', { y: 30, opacity: 0, scale: .9, duration: .8, stagger: .1, ease: 'back.out(1.6)' }, .4);
  gsap.to('.final-icon', { y: -10, duration: 2.4, ease: 'sine.inOut', yoyo: true, repeat: -1, delay: 1.4 });

  addEventListener('load', () => ScrollTrigger.refresh());
})();

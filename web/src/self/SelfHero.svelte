<script>
  // Slideshow on the start page of the player area: planned or running maintenance, a planned restart and the
  // team's posts (news, changelogs). Moves on by itself (player-web.news.seconds), stops while the pointer or the
  // keyboard is on it, can be swiped on the phone. Nothing to show = not shown at all.
  import { api } from '../lib/api.js';
  import { t, lang } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';

  let data = $state(null);
  let index = $state(0);
  let paused = $state(false);
  let drag = $state(0);
  let startX = $state(null);
  let now = $state(Date.now());

  async function load() {
    try {
      data = await api('GET', '/self/news?lang=' + lang);
      if (index >= data.slides.length) index = 0;
    } catch {
      data = null;
    }
  }

  // Again every minute - a maintenance planned meanwhile appears without reloading the page.
  $effect(() => {
    load();
    const timer = setInterval(load, 60_000);
    const clock = setInterval(() => (now = Date.now()), 1000);
    return () => {
      clearInterval(timer);
      clearInterval(clock);
    };
  });

  const slides = $derived(data?.slides ?? []);
  const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

  $effect(() => {
    if (!data?.seconds || slides.length < 2 || paused || reduced) return;
    const timer = setInterval(() => {
      if (!document.hidden) index = (index + 1) % slides.length;
    }, data.seconds * 1000);
    return () => clearInterval(timer);
  });

  const show = (i) => (index = (i + slides.length) % slides.length);

  // Swiping: follow the finger, change the slide past a sixth of the width.
  let track = $state();
  function down(e) {
    if (slides.length < 2 || e.pointerType === 'mouse') return;
    startX = e.clientX;
  }
  function move(e) {
    if (startX !== null) drag = e.clientX - startX;
  }
  function up() {
    if (startX === null) return;
    const width = track?.offsetWidth || 1;
    if (Math.abs(drag) > width / 6) show(index + (drag < 0 ? 1 : -1));
    startX = null;
    drag = 0;
  }

  function key(e) {
    if (e.key === 'ArrowRight') show(index + 1);
    else if (e.key === 'ArrowLeft') show(index - 1);
  }

  // "in 2 h 5 min" / "in 4 min" until a time
  function until(time) {
    const m = Math.max(0, Math.ceil((time - now) / 60000));
    const h = Math.floor(m / 60);
    return h ? t('self.hero.inHours', { h, m: m % 60 }) : t('self.hero.inMinutes', { m });
  }

  const tone = { maintenance: 'warn', restart: 'warn', news: 'accent', changelog: 'good' };
  const icon = { maintenance: '🛠️', restart: '🔄', news: '📣', changelog: '📝' };
</script>

{#if slides.length}
  <section class="hero card" aria-roledescription="carousel" aria-label={t('self.hero.label')}
    onmouseenter={() => (paused = true)} onmouseleave={() => (paused = false)}
    onfocusin={() => (paused = true)} onfocusout={() => (paused = false)}>
    <div class="viewport" bind:this={track} onpointerdown={down} onpointermove={move} onpointerup={up} onpointercancel={up}
      onkeydown={key} tabindex="-1" role="presentation">
      <div class="track" class:dragging={startX !== null} style="transform: translateX(calc({-index * 100}% + {drag}px))">
        {#each slides as s, i (s.type + (s.id ?? ''))}
          {@const kind = s.type === 'maintenance' ? 'maintenance-' + s.phase : s.type}
          <article class="slide tone-{s.type === 'maintenance' && s.phase === 'active' ? 'bad' : tone[s.type]}"
            aria-roledescription="slide" aria-label={t('self.hero.of', { n: i + 1, total: slides.length })} aria-hidden={i !== index}
            inert={i !== index}>
            <div class="kind">
              <span aria-hidden="true">{icon[s.type]}</span>
              {t('self.hero.kind.' + kind)}
              {#if s.pinned}<span class="pin" title={t('self.hero.pinned')}>📌</span>{/if}
              {#if s.created}<span class="when">{dateTime(s.created)}</span>{/if}
            </div>
            {#if s.type === 'maintenance'}
              <h2>{s.title || t('self.hero.maintenanceTitle')}</h2>
              <p>
                {#if s.phase === 'active'}
                  {s.end ? t('self.hero.maintenanceUntil', { time: dateTime(s.end) }) : t('self.hero.maintenanceOpen')}
                {:else}
                  {t('self.hero.maintenanceFrom', { time: dateTime(s.start), in: until(s.start) })}
                  {#if s.end}<br />{t('self.hero.maintenanceUntil', { time: dateTime(s.end) })}{/if}
                {/if}
              </p>
            {:else if s.type === 'restart'}
              <h2>{t('self.hero.restartTitle')}</h2>
              <p>{t('self.hero.restartText', { time: dateTime(s.time), in: until(s.time) })}</p>
            {:else}
              <h2>{s.title}</h2>
              <p class="text">{s.text}</p>
              <button class="more" tabindex={i === index ? 0 : -1} onclick={() => go('/me/news#post-' + s.id)}>{t('self.hero.readMore')} →</button>
            {/if}
          </article>
        {/each}
      </div>
    </div>
    {#if slides.length > 1}
      <div class="controls">
        <button class="arrow" aria-label={t('self.hero.prev')} onclick={() => show(index - 1)}>‹</button>
        <div class="dots">
          {#each slides as _, i}
            <button class="dot" class:active={i === index} aria-label={t('self.hero.of', { n: i + 1, total: slides.length })}
              aria-current={i === index} onclick={() => show(i)}></button>
          {/each}
        </div>
        <button class="arrow" aria-label={t('self.hero.next')} onclick={() => show(index + 1)}>›</button>
      </div>
    {/if}
  </section>
{/if}

<style>
  .hero {
    padding: 0;
    overflow: hidden;
    position: relative;
  }

  .viewport {
    overflow: hidden;
    touch-action: pan-y;
    outline: none;
  }

  .track {
    display: flex;
    transition: transform 0.45s cubic-bezier(0.22, 0.8, 0.3, 1);
  }

  .track.dragging {
    transition: none;
  }

  @media (prefers-reduced-motion: reduce) {
    .track {
      transition: none;
    }
  }

  .slide {
    flex: 0 0 100%;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 22px 24px 46px;
    min-height: 170px;
    --tone: var(--accent);
    background:
      radial-gradient(120% 140% at 100% 0%, color-mix(in srgb, var(--tone) 22%, transparent), transparent 60%),
      linear-gradient(135deg, color-mix(in srgb, var(--tone) 10%, var(--surface)), var(--surface));
    border-left: 4px solid var(--tone);
  }

  .slide.tone-good {
    --tone: var(--good);
  }

  .slide.tone-warn {
    --tone: var(--warn);
  }

  .slide.tone-bad {
    --tone: var(--bad);
  }

  .kind {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    font-size: 0.78rem;
    font-weight: 700;
    letter-spacing: 0.04em;
    text-transform: uppercase;
    color: var(--tone);
  }

  .when {
    color: var(--muted);
    font-weight: 600;
    letter-spacing: 0;
    text-transform: none;
    margin-left: auto;
  }

  h2 {
    margin: 0;
    font-size: 1.35rem;
    line-height: 1.25;
    overflow-wrap: anywhere;
  }

  p {
    margin: 0;
    color: var(--muted);
    overflow-wrap: anywhere;
  }

  .text {
    white-space: pre-line;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .more {
    align-self: flex-start;
    margin-top: auto;
    background: transparent;
    color: var(--tone);
    padding: 4px 0;
  }

  .controls {
    position: absolute;
    right: 12px;
    bottom: 10px;
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .arrow {
    width: 28px;
    height: 28px;
    padding: 0;
    border-radius: 50%;
    background: color-mix(in srgb, var(--surface-2) 80%, transparent);
    color: var(--text);
    border-color: var(--border);
    font-size: 1.1rem;
    line-height: 1;
  }

  .dots {
    display: flex;
    gap: 2px;
  }

  .dot {
    width: 18px;
    height: 18px;
    padding: 0;
    background: transparent;
    position: relative;
  }

  .dot::after {
    content: '';
    position: absolute;
    inset: 6px;
    border-radius: 50%;
    background: var(--border);
    transition: background 0.2s, transform 0.2s;
  }

  .dot.active::after {
    background: var(--text);
    transform: scale(1.25);
  }

  @media (max-width: 560px) {
    .slide {
      padding: 18px 16px 48px;
    }

    .controls {
      left: 0;
      right: 0;
      justify-content: center;
    }
  }
</style>

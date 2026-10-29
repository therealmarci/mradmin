<script>
  // Slideshow on the start page of the player area: planned or running maintenance, a planned restart, warnings of
  // the plugins (e.g. chunk loaders locked for lag), new answers in own tickets and the team's posts (news,
  // changelogs). Moves on by itself (player-web.news.seconds) - a bar between the arrows runs down until the next one -,
  // stops while the pointer or the keyboard is on it, can be swiped on the phone. Nothing to show = not shown at all.
  import { api } from '../lib/api.js';
  import { poll } from '../lib/poll.js';
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

  // Again every 20 seconds while the tab is visible - a warning or a maintenance planned meanwhile appears without
  // reloading the page.
  $effect(() => {
    load();
    const stop = poll(load, 20_000);
    const clock = setInterval(() => (now = Date.now()), 1000);
    return () => {
      stop();
      clearInterval(clock);
    };
  });

  const slides = $derived(data?.slides ?? []);
  const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

  // Moving on is driven by the bar between the arrows: when the current segment has filled up, the next slide comes.
  // Pausing (pointer or keyboard on it, swiping) simply holds the animation.
  const auto = $derived(!!data?.seconds && slides.length > 1 && !reduced);

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

  const tone = { maintenance: 'warn', restart: 'warn', news: 'accent', changelog: 'good', ticket: 'accent', alert: 'bad', incident: 'bad' };
  const icon = { maintenance: '🛠️', restart: '🔄', news: '📣', changelog: '📝', ticket: '🎫', alert: '⚠️', incident: '⚠️' };
</script>

{#if slides.length}
  <section class="hero card" aria-roledescription="carousel" aria-label={t('self.hero.label')}
    onmouseenter={() => (paused = true)} onmouseleave={() => (paused = false)}
    onfocusin={() => (paused = true)} onfocusout={() => (paused = false)}>
    <div class="viewport" bind:this={track} onpointerdown={down} onpointermove={move} onpointerup={up} onpointercancel={up}
      onkeydown={key} tabindex="-1" role="presentation">
      <div class="track" class:dragging={startX !== null} style="transform: translateX(calc({-index * 100}% + {drag}px))">
        {#each slides as s, i (s.type + ':' + (s.id ?? s.page + s.text))}
          {@const kind = s.type === 'maintenance' ? 'maintenance-' + s.phase : s.type === 'incident' && s.level === 'info' ? 'incident-info' : s.type}
          <article class="slide tone-{s.type === 'maintenance' && s.phase === 'active' ? 'bad' : s.type === 'notice' ? s.tone : s.type === 'incident' ? (s.level === 'critical' ? 'bad' : s.level === 'warning' ? 'warn' : 'accent') : tone[s.type]}"
            aria-roledescription="slide" aria-label={t('self.hero.of', { n: i + 1, total: slides.length })} aria-hidden={i !== index}
            inert={i !== index}>
            <div class="kind">
              <span aria-hidden="true">{s.icon ?? (s.level === 'info' ? 'ℹ️' : icon[s.type])}</span>
              {s.type === 'notice' ? s.title : t('self.hero.kind.' + kind)}
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
            {:else if s.type === 'notice'}
              <h2>{s.text}</h2>
              <button class="more" tabindex={i === index ? 0 : -1} onclick={() => go(s.page)}>{t('self.hero.toArea', { area: s.title })} →</button>
            {:else if s.type === 'alert' || s.type === 'incident'}
              <h2>{s.title}</h2>
              {#if s.text}<p class="text">{s.text}</p>{/if}
              {#if s.type === 'incident'}
                <a class="more" tabindex={i === index ? 0 : -1} href={s.link} target="_blank" rel="noopener noreferrer">{t('self.hero.toStatus')} ↗</a>
              {:else}
                <button class="more" tabindex={i === index ? 0 : -1} onclick={() => go('/me/news')}>{t('self.hero.readMore')} →</button>
              {/if}
            {:else if s.type === 'ticket'}
              <h2>{s.title}</h2>
              <p>{t(s.open ? 'self.hero.ticketAnswer' : 'self.hero.ticketClosed', { time: dateTime(s.time) })}</p>
              <button class="more" tabindex={i === index ? 0 : -1} onclick={() => go('/me/tickets/' + s.id)}>{t('self.hero.toTicket')} →</button>
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
        <div class="segments">
          {#each slides as _, i}
            <button class="segment" class:done={i < index} aria-label={t('self.hero.of', { n: i + 1, total: slides.length })}
              aria-current={i === index} onclick={() => show(i)}>
              {#if i === index}
                {#key index}
                  <span class="fill" class:running={auto} class:held={paused || startX !== null}
                    style="animation-duration: {data.seconds}s" onanimationend={() => show(index + 1)}></span>
                {/key}
              {/if}
            </button>
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

  a.more {
    font-weight: 600;
    text-decoration: none;
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

  /* One segment per slide between the arrows: earlier ones full, the current one fills up until the next slide
     comes (the animation's end switches), later ones empty. A click jumps there. */
  .segments {
    display: flex;
    gap: 4px;
    align-items: center;
  }

  .segment {
    position: relative;
    width: 26px;
    height: 18px;
    padding: 0;
    background: transparent;
    border: 0;
  }

  .segment::before {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    top: 7px;
    height: 4px;
    border-radius: 2px;
    background: color-mix(in srgb, var(--border) 85%, transparent);
  }

  .segment.done::before {
    background: color-mix(in srgb, var(--text) 45%, transparent);
  }

  .fill {
    position: absolute;
    left: 0;
    top: 7px;
    height: 4px;
    width: 100%;
    border-radius: 2px;
    background: var(--text);
  }

  .fill.running {
    width: 0;
    animation-name: run;
    animation-timing-function: linear;
    animation-fill-mode: forwards;
  }

  .fill.held {
    animation-play-state: paused;
  }

  @keyframes run {
    from {
      width: 0;
    }

    to {
      width: 100%;
    }
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

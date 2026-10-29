<script>
  // Slideshow of the start pages (player area and team panel): one slide at a time, moves on by itself - a bar between
  // the arrows runs down until the next one -, stops while the pointer or the keyboard is on it, can be swiped on the
  // phone. The content of a slide comes from the page (snippet `slide`); its colour from `tone` (accent, good, warn, bad).
  // The texts inside a slide use the classes kind, when, text and more for the common look.
  import { t } from '../lib/i18n.js';

  /** slides: [{key, tone, ...}]; seconds: 0 = no moving on; index can be bound (to jump to a slide from outside). */
  let { slides, seconds = 0, label = '', index = $bindable(0), slide } = $props();

  let paused = $state(false);
  let drag = $state(0);
  let startX = $state(null);

  const reduced = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

  // Moving on is driven by the bar between the arrows: when the current segment has filled up, the next slide comes.
  // Pausing (pointer or keyboard on it, swiping) simply holds the animation.
  const auto = $derived(!!seconds && slides.length > 1 && !reduced);

  // Fewer slides than before (a warning is gone): stay in range.
  $effect(() => {
    if (index >= slides.length) index = 0;
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
</script>

{#if slides.length}
  <section class="hero card" aria-roledescription="carousel" aria-label={label}
    onmouseenter={() => (paused = true)} onmouseleave={() => (paused = false)}
    onfocusin={() => (paused = true)} onfocusout={() => (paused = false)}>
    <div class="viewport" bind:this={track} onpointerdown={down} onpointermove={move} onpointerup={up} onpointercancel={up}
      onkeydown={key} tabindex="-1" role="presentation">
      <div class="track" class:dragging={startX !== null} style="transform: translateX(calc({-index * 100}% + {drag}px))">
        {#each slides as s, i (s.key)}
          <article class="slide tone-{s.tone}" aria-roledescription="slide" aria-label={t('self.hero.of', { n: i + 1, total: slides.length })}
            aria-hidden={i !== index} inert={i !== index}>
            {@render slide(s, i, i === index)}
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
                    style="animation-duration: {seconds}s" onanimationend={() => show(index + 1)}></span>
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

  /* Common texts of a slide (the content comes from the page). */
  .slide :global(.kind) {
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

  .slide :global(.when) {
    color: var(--muted);
    font-weight: 600;
    letter-spacing: 0;
    text-transform: none;
    margin-left: auto;
  }

  .slide :global(h2) {
    margin: 0;
    font-size: 1.35rem;
    line-height: 1.25;
    overflow-wrap: anywhere;
  }

  .slide :global(p) {
    margin: 0;
    color: var(--muted);
    overflow-wrap: anywhere;
  }

  .slide :global(.text) {
    white-space: pre-line;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .slide :global(.more) {
    align-self: flex-start;
    margin-top: auto;
    background: transparent;
    color: var(--tone);
    padding: 4px 0;
  }

  .slide :global(a.more) {
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

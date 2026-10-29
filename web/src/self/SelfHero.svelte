<script>
  // Slideshow on the start page of the player area: planned or running maintenance, a planned restart, warnings of
  // the plugins (e.g. chunk loaders locked for lag), new answers in own tickets and the team's posts (news,
  // changelogs). Moves on by itself (player-web.news.seconds), see Carousel. Nothing to show = not shown at all.
  import { api } from '../lib/api.js';
  import { poll } from '../lib/poll.js';
  import { t, lang } from '../lib/i18n.js';
  import { dateTime } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import Carousel from '../components/Carousel.svelte';

  let data = $state(null);
  let now = $state(Date.now());

  async function load() {
    try {
      data = await api('GET', '/self/news?lang=' + lang);
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

  // "in 2 h 5 min" / "in 4 min" until a time
  function until(time) {
    const m = Math.max(0, Math.ceil((time - now) / 60000));
    const h = Math.floor(m / 60);
    return h ? t('self.hero.inHours', { h, m: m % 60 }) : t('self.hero.inMinutes', { m });
  }

  const tones = { maintenance: 'warn', restart: 'warn', news: 'accent', changelog: 'good', ticket: 'accent', alert: 'bad', incident: 'bad' };
  const icon = { maintenance: '🛠️', restart: '🔄', news: '📣', changelog: '📝', ticket: '🎫', alert: '⚠️', incident: '⚠️' };

  function toneOf(s) {
    if (s.type === 'maintenance' && s.phase === 'active') return 'bad';
    if (s.type === 'notice') return s.tone;
    if (s.type === 'incident') return s.level === 'critical' ? 'bad' : s.level === 'warning' ? 'warn' : 'accent';
    return tones[s.type];
  }

  const slides = $derived((data?.slides ?? []).map((s) => ({ ...s, key: s.type + ':' + (s.id ?? s.page + s.text), tone: toneOf(s) })));
</script>

<Carousel {slides} seconds={data?.seconds ?? 0} label={t('self.hero.label')}>
  {#snippet slide(s, i, active)}
    {@const kind = s.type === 'maintenance' ? 'maintenance-' + s.phase : s.type === 'incident' && s.level === 'info' ? 'incident-info' : s.type}
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
      <button class="more" tabindex={active ? 0 : -1} onclick={() => go(s.page)}>{t('self.hero.toArea', { area: s.title })} →</button>
    {:else if s.type === 'alert' || s.type === 'incident'}
      <h2>{s.title}</h2>
      {#if s.text}<p class="text">{s.text}</p>{/if}
      {#if s.type === 'incident'}
        <a class="more" tabindex={active ? 0 : -1} href={s.link} target="_blank" rel="noopener noreferrer">{t('self.hero.toStatus')} ↗</a>
      {:else}
        <button class="more" tabindex={active ? 0 : -1} onclick={() => go('/me/news')}>{t('self.hero.readMore')} →</button>
      {/if}
    {:else if s.type === 'ticket'}
      <h2>{s.title}</h2>
      <p>{t(s.open ? 'self.hero.ticketAnswer' : 'self.hero.ticketClosed', { time: dateTime(s.time) })}</p>
      <button class="more" tabindex={active ? 0 : -1} onclick={() => go('/me/tickets/' + s.id)}>{t('self.hero.toTicket')} →</button>
    {:else}
      <h2>{s.title}</h2>
      <p class="text">{s.text}</p>
      <button class="more" tabindex={active ? 0 : -1} onclick={() => go('/me/news#post-' + s.id)}>{t('self.hero.readMore')} →</button>
    {/if}
  {/snippet}
</Carousel>

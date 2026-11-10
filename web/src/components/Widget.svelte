<script>
  // One widget of the start page (since 1.5), drawn by its type. ctx: {player (null = visitor), news, online,
  // editing, onsignin}. Widgets without anything to show draw a quiet placeholder while editing, otherwise nothing
  // (the grid hides them through `empty`).
  import { t } from '../lib/i18n.js';
  import { markdown } from '../lib/markdown.js';
  import { head } from '../lib/format.js';
  import { go } from '../lib/router.svelte.js';
  import { brand, picture } from '../lib/branding.svelte.js';
  import SelfHero from '../self/SelfHero.svelte';
  import SelfTile from '../self/SelfTile.svelte';
  import Apply from '../pages/Apply.svelte';

  let { widget, ctx } = $props();

  const d = $derived(widget.data);
  let copied = $state(false);
  let applying = $state(false);

  async function copy(text) {
    try {
      await navigator.clipboard.writeText(text);
      copied = true;
      setTimeout(() => (copied = false), 1500);
    } catch {
      // no clipboard (http) - the address is visible anyway
    }
  }

  const personal = $derived(ctx.player?.personal?.length ? {
    module: 'mradmin', id: 'personal', title: t('self.personal.title'), icon: '📊',
    summary: [{ type: 'stats', stats: ctx.player.personal.map((p) => ({ label: t('self.stats.' + p.key), value: p.value, tone: '' })) }],
  } : null);
  const links = $derived(widget.type === 'links' ? (d.items?.length ? d.items : brand.links) : []);
  const date = (ms) => new Date(ms).toLocaleDateString([], { day: 'numeric', month: 'short' });
  const signIn = () => !ctx.editing && ctx.onsignin();
</script>

{#snippet placeholder(text)}
  <div class="placeholder">{text}</div>
{/snippet}

{#if widget.type === 'banner'}
  <div class="banner" class:picture={!!d.image} style={d.image ? `--bg: url('/branding/landing/${d.image}')` : ''}>
    <img class="logo" src={picture('logo')} alt="" />
    <div class="who">
      {#if ctx.player}
        <span class="hello"><img src={head(ctx.player.uuid, 32)} alt="" width="22" height="22" />{t('self.hello', { name: ctx.player.name })}</span>
      {/if}
      <h1>{brand.name}</h1>
      {#if brand.tagline}<p class="tagline">{brand.tagline}</p>{/if}
      <div class="facts">
        {#if d.address}
          <button class="pill" onclick={() => copy(d.address)}>
            <span class="mono">{d.address}</span><span class="hint">{copied ? t('landing.copied') : t('landing.copy')}</span>
          </button>
        {/if}
        {#if d.online && ctx.online}
          <span class="pill"><span class="dot"></span>{t('landing.online', { n: ctx.online.count, max: ctx.online.max })}</span>
        {/if}
        {#if !ctx.player}<button class="join" onclick={signIn}>{t('nav.login')}</button>{/if}
      </div>
    </div>
  </div>
{:else if widget.type === 'hints'}
  <div class="flush">
    {#if ctx.player}{#if ctx.player.news}<SelfHero />{/if}{:else}<SelfHero guest />{/if}
  </div>
  {#if ctx.editing}<span class="edit-note">{t('widget.hintsNote')}</span>{/if}
{:else if widget.type === 'address'}
  <div class="box center">
    <span class="label">{d.title || t('widget.type.address')}</span>
    {#if d.address}
      <button class="big-copy" onclick={() => copy(d.address)}>
        <span class="mono">{d.address}</span><span class="hint">{copied ? t('landing.copied') : t('landing.copy')}</span>
      </button>
    {:else}{@render placeholder(t('widget.noAddress'))}{/if}
  </div>
{:else if widget.type === 'online'}
  <div class="box center">
    <span class="label">{d.title || t('widget.type.online')}</span>
    {#if ctx.online}
      <span class="number"><span class="dot"></span>{ctx.online.count}<small>/ {ctx.online.max}</small></span>
    {/if}
  </div>
{:else if widget.type === 'greeting'}
  <div class="box greeting">
    {#if ctx.player}
      <img src={head(ctx.player.uuid, 64)} alt="" width="56" height="56" />
      <div>
        <h2>{d.title || t('self.hello', { name: ctx.player.name })}</h2>
        <p class="muted">{t('self.helloSub')}</p>
      </div>
    {:else}{@render placeholder(t('widget.membersOnly'))}{/if}
  </div>
{:else if widget.type === 'tiles'}
  {#if ctx.player?.areas?.length}
    <div class="tiles">
      {#each ctx.player.areas as a (a.module + '/' + a.id)}
        <SelfTile area={a} onclick={() => !ctx.editing && go(`/game/${a.module}/${a.id}`)} />
      {/each}
    </div>
  {:else}<div class="box">{@render placeholder(t(ctx.player ? 'self.noAreas' : 'widget.membersOnly'))}</div>{/if}
{:else if widget.type === 'stats'}
  {#if personal}
    <SelfTile area={personal} onclick={ctx.player?.stats && !ctx.editing ? () => go('/stats') : null} />
  {:else}<div class="box">{@render placeholder(t('widget.membersOnly'))}</div>{/if}
{:else if widget.type === 'news'}
  <div class="box">
    <h2>{d.title || t('landing.news')}</h2>
    {#if ctx.news?.length}
      <div class="feed">
        {#each ctx.news.slice(0, d.count) as post (post.created + post.title)}
          <article class="post">
            <span class="date">{date(post.created)}</span>
            <div><strong>{post.title}</strong><p>{post.text}</p></div>
          </article>
        {/each}
      </div>
    {:else}<p class="muted small">{t('landing.noNews')}</p>{/if}
  </div>
{:else if widget.type === 'text'}
  <div class="box">
    {#if d.title}<h2>{d.title}</h2>{/if}
    {#if d.text}<div class="md">{@html markdown(d.text)}</div>{:else}{@render placeholder(t('landing.emptyText'))}{/if}
  </div>
{:else if widget.type === 'image'}
  {#if d.image}
    <img class="image" class:contain={d.fit === 'contain'} src={'/branding/landing/' + d.image} alt={d.alt} />
  {:else}<div class="box">{@render placeholder(t('widget.noImage'))}</div>{/if}
{:else if widget.type === 'links'}
  <div class="box">
    <h2>{d.title || t('landing.links')}</h2>
    {#if links.length}
      <div class="links">
        {#each links as item (item.url)}
          <a href={item.url} target="_blank" rel="noopener noreferrer">{item.label}<span aria-hidden="true">↗</span></a>
        {/each}
      </div>
    {:else}{@render placeholder(t('widget.noLinks'))}{/if}
  </div>
{:else if widget.type === 'discord'}
  <div class="box discord">
    <svg viewBox="0 0 24 24" width="34" height="34" aria-hidden="true"><path fill="currentColor" d="M20.3 4.4A19.6 19.6 0 0 0 15.4 3l-.6 1.2a18 18 0 0 0-5.6 0L8.6 3a19.6 19.6 0 0 0-4.9 1.5C.6 9 .1 13.4.3 17.8a19.8 19.8 0 0 0 6 3l1.3-2a12.7 12.7 0 0 1-2-1l.5-.4a14.1 14.1 0 0 0 12 0l.5.4c-.6.4-1.3.7-2 1l1.3 2a19.7 19.7 0 0 0 6-3c.5-5.1-.8-9.5-3.6-13.4ZM8.5 15.1c-1.2 0-2.1-1.1-2.1-2.4s.9-2.4 2.1-2.4 2.2 1.1 2.1 2.4c0 1.3-.9 2.4-2.1 2.4Zm7 0c-1.2 0-2.1-1.1-2.1-2.4s.9-2.4 2.1-2.4 2.2 1.1 2.1 2.4c0 1.3-.9 2.4-2.1 2.4Z"/></svg>
    <div>
      <h2>Discord</h2>
      <p>{d.text || t('widget.discordText')}</p>
    </div>
    {#if d.url}<a class="discord-btn" href={d.url} target="_blank" rel="noopener noreferrer">{t('widget.discordJoin')}</a>{/if}
  </div>
{:else if widget.type === 'login'}
  <div class="box login">
    <h2>{d.title || t('landing.signinTitle')}</h2>
    <p class="muted">{d.text || t('landing.signinText')}</p>
    <button onclick={signIn}>{t('nav.login')}</button>
  </div>
{:else if widget.type === 'apply'}
  <!-- Whitelist application: the form opens in a window (also at /bewerben) -->
  <div class="box login">
    <h2>{d.title || t('apply.widgetTitle')}</h2>
    <p class="muted">{d.text || t('apply.widgetText')}</p>
    <button onclick={() => !ctx.editing && (applying = true)}>{t('apply.widgetButton')}</button>
  </div>
  {#if applying}
    <div class="apply-overlay" role="presentation" onclick={(e) => e.target === e.currentTarget && (applying = false)}
      onkeydown={(e) => e.key === 'Escape' && (applying = false)}>
      <div class="apply-dialog" role="dialog" aria-modal="true" aria-label={t('apply.title')}>
        <Apply modal onclose={() => (applying = false)} />
      </div>
    </div>
  {/if}
{/if}

<style>
  .box {
    display: grid;
    align-content: start;
    gap: 10px;
    height: 100%;
    padding: 18px 20px;
    border-radius: var(--radius);
    background: var(--surface);
    border: 1px solid var(--border);
    box-shadow: var(--shadow);
    box-sizing: border-box;
  }

  .box.center {
    align-content: center;
    justify-items: center;
    text-align: center;
  }

  h1 {
    margin: 0;
    font-size: clamp(1.6rem, 3.6vw, 2.4rem);
    line-height: 1.1;
  }

  h2 {
    margin: 0;
    font-size: 1.08rem;
  }

  .label {
    font-size: 0.8rem;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--muted);
    font-weight: 700;
  }

  .placeholder {
    display: grid;
    place-items: center;
    min-height: 60px;
    color: var(--muted);
    font-size: 0.86rem;
    text-align: center;
  }

  .flush {
    height: 100%;
  }

  .edit-note {
    position: absolute;
    left: 14px;
    bottom: 8px;
    font-size: 0.75rem;
    color: var(--muted);
  }

  /* banner */
  .banner {
    display: flex;
    align-items: center;
    gap: 22px;
    height: 100%;
    padding: 26px 28px;
    box-sizing: border-box;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background:
      radial-gradient(120% 140% at 0% 0%, color-mix(in srgb, var(--accent) 55%, transparent), transparent 60%),
      linear-gradient(135deg, color-mix(in srgb, var(--accent) 30%, var(--surface)), var(--surface));
  }

  .banner.picture {
    background: linear-gradient(90deg, rgb(0 0 0 / 72%), rgb(0 0 0 / 30%)), var(--bg) center / cover no-repeat;
    color: #fff;
  }

  .logo {
    width: 88px;
    height: 88px;
    flex: none;
    object-fit: contain;
    filter: drop-shadow(0 4px 14px rgb(0 0 0 / 30%));
  }

  .who {
    display: grid;
    gap: 6px;
    min-width: 0;
  }

  .hello {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    font-size: 0.9rem;
    opacity: 0.9;
  }

  .hello img,
  .greeting img {
    border-radius: 6px;
    image-rendering: pixelated;
  }

  .tagline {
    margin: 0;
    opacity: 0.85;
  }

  .facts {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 4px;
  }

  .pill {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    padding: 7px 12px;
    border-radius: 10px;
    background: rgb(0 0 0 / 22%);
    color: inherit;
    border: 1px solid rgb(255 255 255 / 14%);
    font-size: 0.9rem;
  }

  .hint {
    font-size: 0.78rem;
    opacity: 0.8;
  }

  .dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: var(--good);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--good) 30%, transparent);
  }

  .join {
    padding: 8px 18px;
  }

  /* address / online */
  .big-copy {
    display: grid;
    gap: 4px;
    padding: 10px 18px;
    border-radius: 12px;
    background: var(--surface-2);
    color: var(--text);
    border: 1px solid var(--border);
    font-size: 1.1rem;
  }

  .number {
    display: inline-flex;
    align-items: baseline;
    gap: 10px;
    font-size: 2.2rem;
    font-weight: 800;
  }

  .number .dot {
    align-self: center;
  }

  .number small {
    font-size: 1rem;
    color: var(--muted);
    font-weight: 600;
  }

  /* greeting */
  .greeting {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .greeting p {
    margin: 2px 0 0;
  }

  /* tiles */
  .tiles {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 280px), 1fr));
  }

  /* news */
  .post {
    display: grid;
    grid-template-columns: 58px minmax(0, 1fr);
    gap: 12px;
    padding: 9px 0;
    border-top: 1px solid var(--border);
  }

  .post:first-child {
    border-top: 0;
    padding-top: 0;
  }

  .date {
    font-size: 0.8rem;
    color: var(--accent);
    font-weight: 600;
  }

  .post p {
    margin: 3px 0 0;
    color: var(--muted);
    font-size: 0.9rem;
  }

  .md :global(p) {
    margin: 0 0 8px;
  }

  .md :global(ul),
  .md :global(ol) {
    margin: 0 0 8px;
    padding-left: 20px;
  }

  /* image */
  .image {
    display: block;
    width: 100%;
    height: 100%;
    min-height: 120px;
    object-fit: cover;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    box-sizing: border-box;
  }

  .image.contain {
    object-fit: contain;
    background: var(--surface);
  }

  /* links */
  .links {
    display: grid;
    gap: 6px;
  }

  .links a {
    display: flex;
    justify-content: space-between;
    padding: 9px 12px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    color: var(--text);
    text-decoration: none;
    font-weight: 600;
  }

  .links a:hover {
    border-color: var(--accent);
  }

  /* discord */
  .discord {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 14px;
    background: linear-gradient(135deg, #5865f2, #4752c4);
    color: #fff;
    border-color: #5865f2;
  }

  .discord div {
    flex: 1;
    min-width: 160px;
  }

  .discord p {
    margin: 2px 0 0;
    opacity: 0.9;
  }

  .discord-btn {
    padding: 9px 16px;
    border-radius: 10px;
    background: #fff;
    color: #4752c4;
    font-weight: 700;
    text-decoration: none;
  }

  .apply-overlay {
    position: fixed;
    inset: 0;
    z-index: 50;
    display: grid;
    place-items: center;
    padding: 16px;
    background: rgb(0 0 0 / 50%);
    overflow-y: auto;
  }

  .apply-dialog {
    width: min(100%, 520px);
  }

  /* login */
  .login {
    border-top: 4px solid var(--accent);
  }

  .login p {
    margin: 0;
  }

  .login button {
    justify-self: start;
  }

  @media (max-width: 760px) {
    .banner {
      flex-direction: column;
      align-items: flex-start;
      padding: 20px;
    }

    .logo {
      width: 60px;
      height: 60px;
    }
  }
</style>

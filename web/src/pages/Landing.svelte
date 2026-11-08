<script>
  // The start page in the "server portal" layout (since 1.5, the same for everyone): one header with a background
  // picture (or a gradient in the accent colour), logo, name, tagline, server address and players online - signed in
  // also "Hello <name>"; below it what the caller puts in between (the hint slideshow); then the blocks in two columns
  // (main: news, texts, pictures; side: rules, links, sign-in). Data: GET /api/landing (BrandingRoutes.java).
  // preview: the editor passes {header, blocks} - then only news and players online are loaded and buttons do nothing.
  import { api } from '../lib/api.js';
  import { t, lang } from '../lib/i18n.js';
  import { markdown } from '../lib/markdown.js';
  import { head } from '../lib/format.js';
  import { brand, picture } from '../lib/branding.svelte.js';

  let { preview = null, player = null, onsignin = () => {}, between } = $props();

  let data = $state(null);
  let copied = $state(false);

  async function load() {
    try {
      data = await api('GET', '/landing?lang=' + lang);
    } catch {
      data = { enabled: false, blocks: [], news: [], header: { image: '', address: '', online: false } };
    }
  }
  load();

  const header = $derived(preview ? preview.header : (data?.header ?? { image: '', address: '', online: false }));
  const blocks = $derived((preview ? preview.blocks : data?.enabled ? data.blocks : []).filter((b) => b.enabled && visible(b)));
  const main = $derived(blocks.filter((b) => b.column !== 'side'));
  const side = $derived(blocks.filter((b) => b.column === 'side'));
  const signedIn = $derived(!!player);
  const signIn = () => !preview && onsignin();

  // Blocks without anything to show stay away (empty texts, no posts, no picture).
  function visible(b) {
    if (b.type === 'signin') return !signedIn;
    if (b.type === 'text') return !!b.data.text || !!preview;
    if (b.type === 'news') return !!data?.news?.length || !!preview;
    if (b.type === 'image') return !!b.data.image;
    if (b.type === 'buttons') return b.data.items.length > 0 || brand.links.length > 0;
    return true;
  }

  const defaultTitle = (b) => ({
    news: t('landing.news'), signin: t('landing.signinTitle'), buttons: t('landing.links'),
    text: b.data.role === 'about' ? t('landing.about') : b.data.role === 'rules' ? t('landing.rules') : '',
  })[b.type] ?? '';
  const title = (b) => b.data.title || defaultTitle(b);

  async function copy(address) {
    try {
      await navigator.clipboard.writeText(address);
      copied = true;
      setTimeout(() => (copied = false), 1500);
    } catch {
      // no clipboard (http) - the address is visible anyway
    }
  }

  const date = (ms) => new Date(ms).toLocaleDateString([], { day: 'numeric', month: 'short' });
</script>

{#snippet block(b)}
  {#if b.type === 'news'}
    <section class="card box">
      <h2>{title(b)}</h2>
      {#if data?.news?.length}
        <div class="feed">
          {#each data.news.slice(0, b.data.count) as post (post.created + post.title)}
            <article class="post">
              <span class="date">{date(post.created)}</span>
              <div>
                <strong>{post.title}</strong>
                <p>{post.text}</p>
              </div>
            </article>
          {/each}
        </div>
      {:else}
        <p class="muted small">{t('landing.noNews')}</p>
      {/if}
    </section>
  {:else if b.type === 'text'}
    <section class="card box">
      {#if title(b)}<h2>{title(b)}</h2>{/if}
      {#if b.data.text}
        <div class="md">{@html markdown(b.data.text)}</div>
      {:else}
        <p class="muted small">{t('landing.emptyText')}</p>
      {/if}
    </section>
  {:else if b.type === 'image'}
    <figure class="card picture" class:wide={b.data.wide}>
      <img src={'/branding/landing/' + b.data.image} alt={b.data.alt} />
      {#if b.data.title}<figcaption>{b.data.title}</figcaption>{/if}
    </figure>
  {:else if b.type === 'buttons'}
    {@const items = b.data.items.length ? b.data.items : brand.links}
    <section class="card box">
      <h2>{title(b)}</h2>
      <div class="links">
        {#each items as item (item.url)}
          <a href={item.url} target="_blank" rel="noopener noreferrer">{item.label}<span aria-hidden="true">↗</span></a>
        {/each}
      </div>
    </section>
  {:else if b.type === 'columns'}
    <section class="box plain">
      {#if b.data.title}<h2>{b.data.title}</h2>{/if}
      <div class="columns" style="--n: {Math.max(1, b.data.items.length)}">
        {#each b.data.items as col, i (i)}
          <div class="card col">
            {#if col.title}<h3>{col.title}</h3>{/if}
            <div class="md">{@html markdown(col.text)}</div>
          </div>
        {/each}
      </div>
    </section>
  {:else if b.type === 'signin'}
    <section class="card box join">
      <h2>{title(b)}</h2>
      <p>{b.data.text || t('landing.signinText')}</p>
      <button onclick={signIn}>{t('landing.signIn')}</button>
    </section>
  {/if}
{/snippet}

<div class="portal" class:in-preview={!!preview}>
  <!-- The header: one place for name, address and status -->
  <header class="banner" style={header.image ? `--bg: url('/branding/landing/${header.image}')` : ''} class:picture={!!header.image}>
    <div class="banner-inner">
      <img class="logo" src={picture('logo')} alt="" />
      <div class="who">
        {#if player}
          <span class="hello"><img src={head(player.uuid, 32)} alt="" width="22" height="22" />{t('self.hello', { name: player.name })}</span>
        {/if}
        <h1>{brand.name}</h1>
        {#if brand.tagline}<p class="tagline">{brand.tagline}</p>{/if}
        <div class="facts">
          {#if header.address}
            <button class="address" onclick={() => copy(header.address)} title={t('landing.copy')}>
              <span class="mono">{header.address}</span>
              <span class="hint">{copied ? t('landing.copied') : t('landing.copy')}</span>
            </button>
          {/if}
          {#if header.online && data?.online}
            <span class="online"><span class="dot"></span>{t('landing.online', { n: data.online.count, max: data.online.max })}</span>
          {/if}
          {#if !signedIn}<button class="join-btn" onclick={signIn}>{t('landing.signIn')}</button>{/if}
        </div>
      </div>
    </div>
  </header>

  {#if between}{@render between()}{/if}

  {#if main.length || side.length}
    <div class="grid" class:single={!side.length || !main.length}>
      {#if main.length}
        <div class="main">{#each main as b (b.id)}{@render block(b)}{/each}</div>
      {/if}
      {#if side.length}
        <aside class="side">{#each side as b (b.id)}{@render block(b)}{/each}</aside>
      {/if}
    </div>
  {/if}
</div>

<style>
  .portal {
    display: grid;
    gap: 16px;
  }

  .portal.in-preview {
    font-size: 0.9em;
    padding: 10px;
  }

  /* Header: background picture (darkened for readable text) or a gradient in the accent colour */
  .banner {
    position: relative;
    border-radius: var(--radius);
    overflow: hidden;
    border: 1px solid var(--border);
    background:
      radial-gradient(120% 140% at 0% 0%, color-mix(in srgb, var(--accent) 55%, transparent), transparent 60%),
      linear-gradient(135deg, color-mix(in srgb, var(--accent) 30%, var(--surface)), var(--surface));
    color: var(--text);
  }

  .banner.picture {
    background: linear-gradient(90deg, rgb(0 0 0 / 72%), rgb(0 0 0 / 35%)), var(--bg) center / cover no-repeat;
    color: #fff;
  }

  .banner-inner {
    display: flex;
    align-items: center;
    gap: 22px;
    padding: 30px 30px;
    min-height: 170px;
  }

  .logo {
    width: 96px;
    height: 96px;
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

  .hello img {
    border-radius: 5px;
    image-rendering: pixelated;
  }

  h1 {
    margin: 0;
    font-size: clamp(1.7rem, 4vw, 2.5rem);
    line-height: 1.1;
  }

  .tagline {
    margin: 0;
    opacity: 0.85;
    font-size: 1.02rem;
  }

  .facts {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    margin-top: 6px;
  }

  .address,
  .online {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    padding: 7px 12px;
    border-radius: 10px;
    background: rgb(0 0 0 / 22%);
    color: inherit;
    border: 1px solid rgb(255 255 255 / 14%);
    font-size: 0.9rem;
    backdrop-filter: blur(4px);
  }

  .address .hint {
    font-size: 0.78rem;
    opacity: 0.8;
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--good);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--good) 30%, transparent);
  }

  .join-btn {
    padding: 8px 18px;
  }

  /* Two columns: main and a narrow side */
  .grid {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 320px;
    gap: 16px;
    align-items: start;
  }

  .grid.single {
    grid-template-columns: minmax(0, 1fr);
  }

  .main,
  .side {
    display: grid;
    gap: 16px;
  }

  .box {
    display: grid;
    gap: 10px;
    padding: 18px 20px;
  }

  .box.plain {
    padding: 0;
  }

  h2 {
    margin: 0;
    font-size: 1.08rem;
  }

  h3 {
    margin: 0 0 6px;
    font-size: 1rem;
  }

  .feed {
    display: grid;
  }

  .post {
    display: grid;
    grid-template-columns: 58px minmax(0, 1fr);
    gap: 12px;
    padding: 10px 0;
    border-top: 1px solid var(--border);
  }

  .post:first-child {
    border-top: 0;
    padding-top: 2px;
  }

  .date {
    font-size: 0.8rem;
    color: var(--accent);
    font-weight: 600;
    padding-top: 2px;
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

  .picture {
    margin: 0;
    overflow: hidden;
    padding: 0;
  }

  .picture img {
    display: block;
    width: 100%;
  }

  figcaption {
    padding: 8px 14px;
    font-size: 0.85rem;
    color: var(--muted);
  }

  .links {
    display: grid;
    gap: 6px;
  }

  .links a {
    display: flex;
    justify-content: space-between;
    align-items: center;
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

  .columns {
    display: grid;
    grid-template-columns: repeat(var(--n), minmax(0, 1fr));
    gap: 16px;
  }

  .col {
    padding: 16px 18px;
  }

  .join {
    border-top: 4px solid var(--accent);
  }

  .join p {
    margin: 0;
    color: var(--muted);
  }

  .join button {
    justify-self: start;
  }

  @media (max-width: 860px) {
    .grid {
      grid-template-columns: minmax(0, 1fr);
    }

    .columns {
      grid-template-columns: minmax(0, 1fr);
    }

    .banner-inner {
      flex-direction: column;
      align-items: flex-start;
      padding: 22px;
    }

    .logo {
      width: 64px;
      height: 64px;
    }
  }
</style>

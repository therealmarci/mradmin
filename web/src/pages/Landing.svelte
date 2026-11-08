<script>
  // The public start page in front of the sign-in (since 1.5): the blocks the team arranged under Server -> Appearance
  // (GET /api/landing, BrandingRoutes.java). Switched off: the sign-in page right away. preview: the editor passes its
  // draft (blocks) - then nothing is loaded except news and players online, and buttons do nothing.
  import { api } from '../lib/api.js';
  import { t, lang } from '../lib/i18n.js';
  import { markdown } from '../lib/markdown.js';
  import { brand, picture } from '../lib/branding.svelte.js';
  import Footer from '../components/Footer.svelte';
  import LangFlags from '../components/LangFlags.svelte';
  import SignIn from '../components/SignIn.svelte';

  // embedded: part of the start page (no own bar and footer); signedIn: no sign-in buttons and calls to sign in
  let { preview = null, onsignin = () => {}, onSignedIn = () => {}, embedded = false, signedIn = false } = $props();

  let data = $state(null);
  let copied = $state(false);

  async function load() {
    try {
      data = await api('GET', '/landing?lang=' + lang);
    } catch {
      data = { enabled: false, blocks: [], news: [] };
    }
  }
  load();

  const blocks = $derived(preview ? preview.filter((b) => b.enabled) : (data?.blocks ?? []));
  const signIn = () => !preview && onsignin();

  const defaultTitle = (b) => ({
    news: t('landing.news'), signin: t('landing.signinTitle'),
    text: b.data.role === 'about' ? t('landing.about') : b.data.role === 'rules' ? t('landing.rules') : '',
    buttons: '', hero: brand.name,
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

  const date = (ms) => new Date(ms).toLocaleDateString([], { day: 'numeric', month: 'short', year: 'numeric' });
</script>

{#if embedded && !preview && data && !data.enabled}
  <!-- switched off: the start page shows only the hero -->
{:else if !preview && data && !data.enabled}
  <SignIn {onSignedIn} />
{:else if preview || data}
  <div class="landing" class:in-preview={!!preview} class:embedded>
    {#if !preview && !embedded}
      <div class="top">
        <span class="mark"><img src={picture('icon')} alt="" width="26" height="26" />{brand.name}</span>
        <span class="right"><LangFlags /><button onclick={signIn}>{t('landing.signIn')}</button></span>
      </div>
    {/if}

    {#each blocks as b (b.id)}
      {#if b.type === 'hero'}
        <section class="hero">
          <img class="logo" src={picture('logo')} alt="" />
          <h1>{title(b)}</h1>
          {#if b.data.text || brand.tagline}<p class="lead">{b.data.text || brand.tagline}</p>{/if}
          <div class="hero-row">
            {#if b.data.address}
              <button class="address" onclick={() => copy(b.data.address)} title={t('landing.copy')}>
                <span class="mono">{b.data.address}</span>
                <span class="hint">{copied ? t('landing.copied') : t('landing.copy')}</span>
              </button>
            {/if}
            {#if b.data.online && data?.online}
              <span class="online"><span class="dot"></span>{t('landing.online', { n: data.online.count, max: data.online.max })}</span>
            {/if}
          </div>
          {#if !signedIn}<button class="cta" onclick={signIn}>{t('landing.signIn')}</button>{/if}
        </section>
      {:else if b.type === 'news'}
        {#if data?.news?.length}
          <section class="block">
            <h2>{title(b)}</h2>
            <div class="news">
              {#each data.news.slice(0, b.data.count) as post (post.created + post.title)}
                <article class="card post">
                  <span class="muted small">{date(post.created)}</span>
                  <strong>{post.title}</strong>
                  <p class="small">{post.text}</p>
                </article>
              {/each}
            </div>
          </section>
        {/if}
      {:else if b.type === 'text'}
        {#if b.data.text || preview}
          <section class="block text">
            {#if title(b)}<h2>{title(b)}</h2>{/if}
            {#if b.data.text}
              <div class="md">{@html markdown(b.data.text)}</div>
            {:else}
              <p class="muted small">{t('landing.emptyText')}</p>
            {/if}
          </section>
        {/if}
      {:else if b.type === 'image'}
        {#if b.data.image}
          <section class="block image" class:wide={b.data.wide}>
            {#if b.data.title}<h2>{b.data.title}</h2>{/if}
            <img src={'/branding/landing/' + b.data.image} alt={b.data.alt} />
          </section>
        {/if}
      {:else if b.type === 'buttons'}
        {@const items = b.data.items.length ? b.data.items : brand.links}
        {#if items.length}
          <section class="block buttons">
            {#if b.data.title}<h2>{b.data.title}</h2>{/if}
            <div class="row">
              {#each items as item (item.url)}
                <a class="button" href={item.url} target="_blank" rel="noopener noreferrer">{item.label}</a>
              {/each}
            </div>
          </section>
        {/if}
      {:else if b.type === 'columns'}
        <section class="block">
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
      {:else if b.type === 'signin' && !signedIn}
        <section class="block">
          <div class="card join">
            <div>
              <h2>{title(b)}</h2>
              <p class="muted">{b.data.text || t('landing.signinText')}</p>
            </div>
            <button onclick={signIn}>{t('landing.signIn')}</button>
          </div>
        </section>
      {/if}
    {/each}

    {#if !preview && !embedded}<Footer />{/if}
  </div>
{/if}

<style>
  .landing {
    min-height: 100vh;
    display: grid;
    align-content: start;
    gap: 34px;
    padding: 0 16px 24px;
    background:
      radial-gradient(70% 40% at 50% 0%, color-mix(in srgb, var(--accent) 20%, transparent), transparent 70%),
      var(--bg);
  }

  .landing.embedded {
    min-height: 0;
    padding: 0;
    gap: 26px;
    background: none;
  }

  .landing.embedded .hero {
    padding-top: 10px;
  }

  .landing.in-preview {
    min-height: 0;
    padding: 0 12px 12px;
    gap: 22px;
    font-size: 0.9em;
  }

  .top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    max-width: 1000px;
    width: 100%;
    margin: 0 auto;
    padding: 14px 0;
  }

  .mark {
    display: flex;
    align-items: center;
    gap: 10px;
    font-weight: 700;
  }

  .mark img {
    border-radius: 6px;
    object-fit: contain;
  }

  .right {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .hero {
    display: grid;
    justify-items: center;
    gap: 14px;
    text-align: center;
    padding: 30px 0 10px;
  }

  .logo {
    max-height: 96px;
    max-width: 260px;
    object-fit: contain;
  }

  h1 {
    margin: 0;
    font-size: clamp(1.8rem, 5vw, 2.8rem);
  }

  .lead {
    margin: 0;
    max-width: 620px;
    color: var(--muted);
    font-size: 1.1rem;
  }

  .hero-row {
    display: flex;
    flex-wrap: wrap;
    justify-content: center;
    gap: 10px;
  }

  .address {
    display: inline-flex;
    align-items: center;
    gap: 10px;
    padding: 9px 14px;
    background: var(--surface);
    color: var(--text);
    border: 1px solid var(--border);
    border-radius: 10px;
  }

  .address .hint {
    font-size: 0.8rem;
    color: var(--accent);
  }

  .online {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 9px 14px;
    border-radius: 10px;
    background: var(--surface);
    border: 1px solid var(--border);
  }

  .dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
    background: var(--good);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--good) 25%, transparent);
  }

  .cta {
    padding: 11px 26px;
    font-size: 1rem;
  }

  .block {
    width: 100%;
    max-width: 1000px;
    margin: 0 auto;
    display: grid;
    gap: 12px;
  }

  h2 {
    margin: 0;
    font-size: 1.35rem;
  }

  h3 {
    margin: 0 0 6px;
    font-size: 1.05rem;
  }

  .news {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: 12px;
  }

  .post {
    display: grid;
    gap: 4px;
    align-content: start;
    padding: 14px 16px;
  }

  .post p {
    margin: 2px 0 0;
    color: var(--muted);
  }

  .md :global(p) {
    margin: 0 0 10px;
  }

  .md :global(ul),
  .md :global(ol) {
    margin: 0 0 10px;
    padding-left: 22px;
  }

  .image img {
    width: 100%;
    max-width: 640px;
    border-radius: 12px;
    justify-self: center;
  }

  .image.wide img {
    max-width: none;
  }

  .buttons .row {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
  }

  .button {
    display: inline-flex;
    padding: 9px 18px;
    border-radius: 10px;
    background: var(--surface);
    color: var(--text);
    border: 1px solid var(--border);
    text-decoration: none;
    font-weight: 600;
  }

  .button:hover {
    border-color: var(--accent);
  }

  .columns {
    display: grid;
    grid-template-columns: repeat(var(--n), minmax(0, 1fr));
    gap: 12px;
  }

  .col {
    padding: 16px;
  }

  .join {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: center;
    gap: 14px;
    padding: 20px 22px;
    border-left: 5px solid var(--accent);
  }

  .join h2 {
    font-size: 1.15rem;
  }

  .join p {
    margin: 4px 0 0;
  }

  @media (max-width: 700px) {
    .columns {
      grid-template-columns: minmax(0, 1fr);
    }
  }
</style>

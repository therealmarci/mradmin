<script>
  // The start page for visitors who are not signed in: who are you - a player (-> the player page /me, signed in with
  // the code from /web in game) or in the team (-> the team sign-in /login). Without the player page (player-web off)
  // it goes straight to the team sign-in.
  import { api } from '../lib/api.js';
  import { t } from '../lib/i18n.js';
  import { go } from '../lib/router.svelte.js';
  import { ICONS } from '../lib/icons.js';
  import LangFlags from '../components/LangFlags.svelte';
  import Footer from '../components/Footer.svelte';

  let ready = $state(false);

  api('GET', '/login/options')
    .then((o) => {
      if (!o.playerWeb) go('/login', true);
      else ready = true;
    })
    .catch(() => (ready = true));
</script>

<div class="page">
  <div class="flags"><LangFlags /></div>
  {#if ready}
    <main class="choose">
      <div class="brand">
        <img src="/icon.svg" alt="" width="56" height="56" />
        <h1>{t('landing.title')}</h1>
        <p class="muted">{t('landing.subtitle')}</p>
      </div>
      <div class="options">
        <button class="option player" onclick={() => go('/me')}>
          <span class="o-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round"><path d={ICONS.users} /></svg>
          </span>
          <strong>{t('landing.player')}</strong>
          <span class="muted small">{t('landing.playerHint')}</span>
          <span class="go">{t('landing.playerGo')} →</span>
        </button>
        <button class="option team" onclick={() => go('/login')}>
          <span class="o-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="30" height="30" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
              stroke-linejoin="round"><path d={ICONS.shield} /></svg>
          </span>
          <strong>{t('landing.team')}</strong>
          <span class="muted small">{t('landing.teamHint')}</span>
          <span class="go">{t('landing.teamGo')} →</span>
        </button>
      </div>
    </main>
  {/if}
  <Footer />
</div>

<style>
  .page {
    min-height: 100vh;
    display: grid;
    grid-template-rows: auto 1fr auto;
    padding: 16px;
    background:
      radial-gradient(80% 60% at 20% 0%, color-mix(in srgb, #8b5cf6 14%, transparent), transparent 60%),
      radial-gradient(80% 60% at 80% 0%, color-mix(in srgb, var(--accent) 16%, transparent), transparent 60%),
      var(--bg);
  }

  .flags {
    justify-self: end;
  }

  .choose {
    align-self: center;
    justify-self: center;
    width: 100%;
    max-width: 760px;
    display: grid;
    gap: 28px;
    padding: 20px 0;
  }

  .brand {
    display: grid;
    justify-items: center;
    gap: 6px;
    text-align: center;
  }

  .brand h1 {
    margin: 8px 0 0;
    font-size: 1.7rem;
  }

  .brand p {
    margin: 0;
  }

  .options {
    display: grid;
    gap: 16px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
  }

  /* Player violet (like the player page), team in the accent colour of the team area */
  .option {
    --oc: var(--accent);
    display: grid;
    justify-items: start;
    align-content: start;
    gap: 8px;
    padding: 24px;
    text-align: left;
    font: inherit;
    font-weight: 400;
    color: var(--text);
    border-radius: 18px;
    border: 1px solid color-mix(in srgb, var(--oc) 30%, var(--border));
    background:
      radial-gradient(120% 120% at 100% 0%, color-mix(in srgb, var(--oc) 16%, transparent), transparent 60%),
      var(--surface);
    box-shadow: var(--shadow);
    transition: transform 0.15s, border-color 0.15s;
  }

  .option.player {
    --oc: #8b5cf6;
  }

  .option:hover {
    filter: none;
    transform: translateY(-3px);
    border-color: var(--oc);
  }

  .o-icon {
    display: grid;
    place-items: center;
    width: 58px;
    height: 58px;
    margin-bottom: 6px;
    border-radius: 16px;
    color: var(--oc);
    background: color-mix(in srgb, var(--oc) 16%, var(--surface-2));
    border: 1px solid color-mix(in srgb, var(--oc) 35%, transparent);
  }

  .option strong {
    font-size: 1.25rem;
  }

  .go {
    margin-top: 8px;
    font-weight: 700;
    color: var(--oc);
  }

  @media (prefers-reduced-motion: reduce) {
    .option {
      transition: none;
    }
  }
</style>

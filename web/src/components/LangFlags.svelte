<script>
  // Language as two small flags (drawn, not emoji - Windows shows flag emoji as letters). The active one is clear,
  // a click on the other switches the page's language.
  import { t, lang, setLang } from '../lib/i18n.js';
</script>

<div class="flags" role="group" aria-label={t('self.language')}>
  <button class:active={lang === 'de'} aria-pressed={lang === 'de'} title="Deutsch" aria-label="Deutsch"
    onclick={() => lang !== 'de' && setLang('de')}>
    <svg viewBox="0 0 5 3" width="24" height="16" aria-hidden="true">
      <rect width="5" height="1" y="0" fill="#000" />
      <rect width="5" height="1" y="1" fill="#DD0000" />
      <rect width="5" height="1" y="2" fill="#FFCE00" />
    </svg>
  </button>
  <button class:active={lang === 'en'} aria-pressed={lang === 'en'} title="English" aria-label="English"
    onclick={() => lang !== 'en' && setLang('en')}>
    <svg viewBox="0 0 60 30" width="24" height="16" aria-hidden="true">
      <clipPath id="uk-clip"><path d="M30,15 h30 v15 z v15 h-30 z h-30 v-15 z v-15 h30 z" /></clipPath>
      <path d="M0,0 v30 h60 v-30 z" fill="#012169" />
      <path d="M0,0 L60,30 M60,0 L0,30" stroke="#fff" stroke-width="6" />
      <path d="M0,0 L60,30 M60,0 L0,30" clip-path="url(#uk-clip)" stroke="#C8102E" stroke-width="4" />
      <path d="M30,0 v30 M0,15 h60" stroke="#fff" stroke-width="10" />
      <path d="M30,0 v30 M0,15 h60" stroke="#C8102E" stroke-width="6" />
    </svg>
  </button>
</div>

<style>
  .flags {
    display: inline-flex;
    gap: 4px;
    padding: 3px;
    border-radius: 10px;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }

  button {
    display: grid;
    place-items: center;
    padding: 4px 5px;
    border-radius: 7px;
    background: transparent;
    border: 0;
    opacity: 0.45;
    filter: grayscale(0.6);
    transition: opacity 0.15s, filter 0.15s;
  }

  button:hover {
    opacity: 0.85;
    filter: none;
  }

  button.active {
    opacity: 1;
    filter: none;
    background: var(--surface);
    box-shadow: var(--shadow);
    cursor: default;
  }

  svg {
    display: block;
    border-radius: 2px;
    box-shadow: 0 0 0 1px rgb(0 0 0 / 12%);
  }
</style>

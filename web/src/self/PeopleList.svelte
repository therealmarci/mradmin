<script>
  // Players of a plugin (SelfBlock.People), e.g. invited players: head, name, a line about them and their buttons.
  import { head } from '../lib/format.js';

  let { block, onButton, busy = false, flat = false } = $props();
</script>

<section class="card people-block" class:flat>
  {#if block.title}<h2>{block.title}</h2>{/if}
  {#if block.text}<p class="muted small intro">{block.text}</p>{/if}
  {#if block.people.length === 0}
    <p class="muted empty">{block.empty}</p>
  {:else}
    <ul>
      {#each block.people as p, i (p.uuid + i)}
        <li>
          <img src={head(p.uuid)} alt="" width="36" height="36" />
          <div class="who">
            <span class="name">{p.name}{#if p.badge} <span class="badge {p.tone}">{p.badge}</span>{/if}</span>
            {#if p.detail}<span class="detail">{p.detail}</span>{/if}
          </div>
          {#if p.buttons.length}
            <div class="buttons">
              {#each p.buttons as b}
                <button class={b.tone === 'bad' ? 'danger small' : b.tone === 'good' ? 'small' : 'secondary small'} disabled={busy}
                  onclick={() => onButton(b)}>{b.label}</button>
              {/each}
            </div>
          {/if}
        </li>
      {/each}
    </ul>
  {/if}
</section>

<style>
  h2 {
    margin: 0 0 6px;
    font-size: 1.05rem;
  }

  .intro {
    margin: 0 0 12px;
  }

  .empty {
    margin: 0;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: grid;
    gap: 8px;
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
  }

  li {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    border-radius: 12px;
    background: var(--surface-2);
    border: 1px solid color-mix(in srgb, var(--border) 70%, transparent);
    min-width: 0;
  }

  img {
    border-radius: 8px;
    image-rendering: pixelated;
    flex-shrink: 0;
  }

  .who {
    display: grid;
    gap: 2px;
    flex: 1;
    min-width: 0;
  }

  .name {
    font-weight: 700;
    overflow-wrap: anywhere;
  }

  .detail {
    font-size: 0.8rem;
    color: var(--muted);
    overflow-wrap: anywhere;
  }

  .badge {
    font-size: 0.7rem;
    font-weight: 700;
    padding: 1px 7px;
    border-radius: 999px;
    color: var(--muted);
    background: color-mix(in srgb, var(--muted) 14%, transparent);
  }

  .badge.good {
    color: var(--good);
    background: color-mix(in srgb, var(--good) 15%, transparent);
  }

  .badge.warn {
    color: var(--warn);
    background: color-mix(in srgb, var(--warn) 16%, transparent);
  }

  .badge.bad {
    color: var(--bad);
    background: color-mix(in srgb, var(--bad) 15%, transparent);
  }

  .buttons {
    display: flex;
    gap: 6px;
    flex-shrink: 0;
  }

  /* Inside an opened card: no frame of its own, a line above instead. */
  .flat {
    padding: 16px 0 0;
    border: 0;
    border-top: 1px solid var(--border);
    border-radius: 0;
    box-shadow: none;
    background: transparent;
  }
</style>

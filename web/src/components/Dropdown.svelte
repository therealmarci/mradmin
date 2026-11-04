<script>
  // A selection in the look of the web interface instead of the browser's <select>: a button with the chosen entry
  // and an arrow, a click opens the list below (or above, near the bottom of the screen). Entries may have a colour
  // dot (e.g. a waypoint category). Esc, a click elsewhere or a choice closes it.
  //   options: [{ value, label, color? }]   value: the chosen value   onchange(value)
  //   placeholder: shown while nothing is chosen   size: "" or "small"   label: for screen readers
  let { options = [], value = '', onchange = null, placeholder = '', size = '', label = '', disabled = false,
    width = '' } = $props();

  let open = $state(false);
  let up = $state(false);
  let box = $state(null);

  const current = $derived(options.find((o) => o.value === value) ?? null);

  function toggle() {
    if (disabled) return;
    if (!open && box) up = box.getBoundingClientRect().bottom > window.innerHeight - 260;
    open = !open;
  }

  function pick(o) {
    open = false;
    if (o.value !== value) onchange?.(o.value);
  }

  function outside(e) {
    if (open && box && !box.contains(e.target)) open = false;
  }
</script>

<svelte:window onclick={outside} onkeydown={(e) => open && e.key === 'Escape' && (open = false)} />

<div class="dd {size}" class:open bind:this={box} style={width ? `width:${width}` : ''}>
  <button type="button" class="trigger" aria-haspopup="listbox" aria-expanded={open} aria-label={label || undefined}
    {disabled} onclick={toggle}>
    {#if current?.color}<span class="dot" style="--c:{current.color}" aria-hidden="true"></span>{/if}
    <span class="text" class:placeholder={!current}>{current ? current.label : placeholder}</span>
    <svg class="chev" viewBox="0 0 12 12" width="10" height="10" aria-hidden="true"><path d="M2.5 4.5 6 8l3.5-3.5" /></svg>
  </button>
  {#if open}
    <ul class="list" class:up role="listbox">
      {#each options as o (o.value)}
        <li>
          <button type="button" role="option" aria-selected={o.value === value} class:active={o.value === value}
            onclick={() => pick(o)}>
            {#if o.color}<span class="dot" style="--c:{o.color}" aria-hidden="true"></span>{/if}
            <span class="text">{o.label}</span>
            {#if o.value === value}<span class="tick" aria-hidden="true">✓</span>{/if}
          </button>
        </li>
      {/each}
    </ul>
  {/if}
</div>

<style>
  .dd {
    position: relative;
    display: inline-block;
    min-width: 0;
  }

  .trigger {
    width: 100%;
    justify-content: flex-start;
    gap: 8px;
    padding: 9px 12px;
    border-radius: 10px;
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text);
    font-weight: 600;
    font-size: 0.88rem;
    text-align: left;
  }

  .trigger:hover:not(:disabled) {
    filter: none;
    border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
  }

  .open .trigger {
    border-color: var(--accent);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 20%, transparent);
  }

  .small .trigger {
    padding: 5px 9px;
    border-radius: 8px;
    font-size: 0.8rem;
  }

  .text {
    flex: 1;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .placeholder {
    color: var(--muted);
    font-weight: 500;
  }

  .chev {
    flex-shrink: 0;
    fill: none;
    stroke: var(--muted);
    stroke-width: 1.8;
    stroke-linecap: round;
    stroke-linejoin: round;
    transition: transform 0.15s;
  }

  .open .chev {
    transform: rotate(180deg);
  }

  .dot {
    width: 9px;
    height: 9px;
    flex-shrink: 0;
    border-radius: 3px;
    background: var(--c);
  }

  .list {
    position: absolute;
    top: calc(100% + 6px);
    left: 0;
    z-index: 50;
    min-width: 100%;
    max-width: min(320px, 90vw);
    max-height: 260px;
    overflow-y: auto;
    margin: 0;
    padding: 5px;
    list-style: none;
    border-radius: 12px;
    border: 1px solid var(--border);
    background: var(--surface);
    box-shadow: 0 12px 32px rgb(0 0 0 / 0.22);
  }

  .list.up {
    top: auto;
    bottom: calc(100% + 6px);
  }

  .list button {
    width: 100%;
    justify-content: flex-start;
    gap: 8px;
    padding: 7px 10px;
    border-radius: 8px;
    background: transparent;
    color: var(--text);
    font-weight: 500;
    font-size: 0.86rem;
    text-align: left;
    white-space: nowrap;
  }

  .list button:hover {
    filter: none;
    background: var(--surface-2);
  }

  .list button.active {
    font-weight: 700;
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .tick {
    color: var(--accent);
    font-size: 0.8rem;
  }
</style>

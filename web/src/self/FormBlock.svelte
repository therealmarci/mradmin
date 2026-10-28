<script>
  // A form of a plugin (SelfBlock.Form), e.g. "invite a player": text fields and choices, sent as one action with
  // every field as an argument. Text fields are emptied after it went through.
  import { untrack } from 'svelte';

  let { block, onSubmit, busy = false } = $props();

  let values = $state({});

  // Start values (and again when the plugin changes the form, e.g. a new loader in a choice).
  $effect(() => {
    const fields = block.fields;
    untrack(() => {
      const next = {};
      for (const f of fields) {
        const kept = values[f.key];
        // A kept choice only if it still exists.
        const valid = f.type !== 'select' || f.options.some((o) => o.value === kept);
        next[f.key] = kept != null && valid ? kept : (f.value || f.options[0]?.value || '');
      }
      values = next;
    });
  });

  async function submit(event) {
    event.preventDefault();
    const ok = await onSubmit(block.action, { ...block.args, ...values });
    if (ok) for (const f of block.fields) if (f.type === 'text') values[f.key] = f.value ?? '';
  }

  const filled = $derived(block.fields.every((f) => f.type !== 'text' || (values[f.key] ?? '').trim()));
</script>

<form class="card form-block" onsubmit={submit}>
  {#if block.title}<h2>{block.title}</h2>{/if}
  {#if block.text}<p class="muted small intro">{block.text}</p>{/if}
  <div class="fields">
    {#each block.fields as f (f.key)}
      <label class="field">
        <span>{f.label}</span>
        {#if f.type === 'select'}
          <select bind:value={values[f.key]}>
            {#each f.options as o (o.value)}<option value={o.value}>{o.label}</option>{/each}
          </select>
        {:else}
          <input type="text" bind:value={values[f.key]} maxlength={f.maxLength} autocomplete="off" />
        {/if}
      </label>
    {/each}
    <button type="submit" disabled={busy || !filled}>{block.submit}</button>
  </div>
</form>

<style>
  h2 {
    margin: 0 0 6px;
    font-size: 1.05rem;
  }

  .intro {
    margin: 0 0 12px;
  }

  .fields {
    display: grid;
    gap: 10px;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 170px), 1fr));
    align-items: end;
  }

  .field {
    display: grid;
    gap: 5px;
    margin: 0;
  }

  .field span {
    color: var(--muted);
    font-size: 0.8rem;
    font-weight: 600;
  }

  select {
    width: 100%;
    font: inherit;
    color: var(--text);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 10px;
    padding: 10px 12px;
  }
</style>

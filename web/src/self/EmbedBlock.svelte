<script>
  // Another page of a plugin in a frame (SelfBlock.Embed), e.g. the live map. Sandboxed (no top navigation, no forms
  // to this panel). If the browser refuses it (e.g. an http page in an https panel), the link still opens it.
  let { block } = $props();
</script>

<section class="card embed-block">
  <div class="head">
    {#if block.title}<h2>{block.title}</h2>{/if}
    {#if block.linkLabel}<a class="open" href={block.url} target="_blank" rel="noopener noreferrer">{block.linkLabel} ↗</a>{/if}
  </div>
  <iframe src={block.url} title={block.title} height={block.height} loading="lazy" referrerpolicy="no-referrer"
    sandbox="allow-scripts allow-same-origin allow-popups"></iframe>
</section>

<style>
  .embed-block {
    padding: 14px;
    display: grid;
    gap: 10px;
  }

  .head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    padding: 0 4px;
  }

  h2 {
    margin: 0;
    font-size: 1.05rem;
  }

  .open {
    font-size: 0.85rem;
    font-weight: 700;
    color: var(--tint, var(--accent));
    text-decoration: none;
  }

  iframe {
    width: 100%;
    border: 1px solid var(--border);
    border-radius: 10px;
    background: var(--surface-2);
  }
</style>

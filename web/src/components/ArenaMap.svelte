<script>
  // Top view of an arena (MRpvp sends the map colour of the highest block per column) with the two players as dots
  // and a short line where they look. Positions are blocks from the arena's lowest corner. A marker with "hit"
  // (just attacked) gets a yellow glow, one with "hurt" (just hit) a red one.
  let { map, markers = [] } = $props();

  let canvas = $state();
  const COLORS = { 1: '#e5484d', 2: '#3b82f6' };

  $effect(() => {
    if (!canvas || !map) return;
    const scale = Math.max(2, Math.floor(Math.min(480, canvas.parentElement.clientWidth || 480) / Math.max(map.sizeX, map.sizeZ)));
    canvas.width = map.sizeX * scale;
    canvas.height = map.sizeZ * scale;
    const ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    for (let z = 0; z < map.sizeZ; z++) {
      for (let x = 0; x < map.sizeX; x++) {
        const color = map.colors[z * map.sizeX + x];
        if (color) {
          ctx.fillStyle = color;
          ctx.fillRect(x * scale, z * scale, scale, scale);
        }
      }
    }
    for (const m of markers) {
      const px = m.x * scale;
      const pz = m.z * scale;
      // Minecraft: yaw 0 = south (+z), 90 = west (-x)
      const rad = (m.yaw * Math.PI) / 180;
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = Math.max(2, scale / 2);
      ctx.beginPath();
      ctx.moveTo(px, pz);
      ctx.lineTo(px - Math.sin(rad) * scale * 3, pz + Math.cos(rad) * scale * 3);
      ctx.stroke();
      if (m.hit || m.hurt) {
        ctx.fillStyle = m.hit ? 'rgba(250, 204, 21, 0.55)' : 'rgba(239, 68, 68, 0.5)';
        ctx.beginPath();
        ctx.arc(px, pz, Math.max(10, scale * 3), 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.fillStyle = COLORS[m.side] ?? '#f59e0b';
      ctx.beginPath();
      ctx.arc(px, pz, Math.max(4, scale * 1.2), 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 2;
      ctx.stroke();
    }
  });
</script>

<div class="wrap">
  {#if map}
    <canvas bind:this={canvas}></canvas>
  {:else}
    <div class="none muted small">–</div>
  {/if}
</div>

<style>
  .wrap {
    width: 100%;
    max-width: 480px;
  }

  canvas {
    display: block;
    max-width: 100%;
    image-rendering: pixelated;
    border-radius: 8px;
    background: var(--surface-2);
  }

  .none {
    padding: 20px;
    text-align: center;
  }
</style>

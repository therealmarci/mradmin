<script>
  // Top view of an arena (MRpvp sends the map colour of the highest block per column) with the two players as dots
  // and a short line where they look. Positions are blocks from the arena's lowest corner. A marker with "hit"
  // (just attacked) gets a yellow glow, one with "hurt" (just hit) a red one.
  // Optional "effects" with "clock" (a function returning the current time of the round in ms): what happens in
  // the fight, animated on a layer above the map for a moment after its time t -
  //   { t, kind: 'hit' | 'damage' | 'shot' | 'pearl' | 'totem' | 'border' | 'end', x, z, fx?, fz?, side, label }
  // (x/z where it happened, fx/fz where the attacker stood, label e.g. "-2.5❤").
  let { map, markers = [], effects = [], clock = null } = $props();

  let canvas = $state();
  let overlay = $state();
  const COLORS = { 1: '#e5484d', 2: '#3b82f6' };
  const DURATION = { hit: 1100, damage: 1100, shot: 900, pearl: 1200, totem: 1600, border: 1000, end: 2500 };

  let scale = $state(2);

  // The map is drawn once per map into a canvas of its own; markers are drawn over a copy of it.
  const background = $derived.by(() => {
    if (!map) return null;
    const c = document.createElement('canvas');
    c.width = map.sizeX;
    c.height = map.sizeZ;
    const ctx = c.getContext('2d');
    for (let z = 0; z < map.sizeZ; z++) {
      for (let x = 0; x < map.sizeX; x++) {
        const color = map.colors[z * map.sizeX + x];
        if (color) {
          ctx.fillStyle = color;
          ctx.fillRect(x, z, 1, 1);
        }
      }
    }
    return c;
  });

  $effect(() => {
    if (!canvas || !map || !background) return;
    scale = Math.max(2, Math.floor(Math.min(480, canvas.parentElement.clientWidth || 480) / Math.max(map.sizeX, map.sizeZ)));
    for (const c of [canvas, overlay]) {
      if (!c) continue;
      c.width = map.sizeX * scale;
      c.height = map.sizeZ * scale;
    }
    const ctx = canvas.getContext('2d');
    ctx.imageSmoothingEnabled = false;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.drawImage(background, 0, 0, canvas.width, canvas.height);
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

  // The effects layer: redrawn every frame while there are effects.
  $effect(() => {
    if (!overlay || !clock || !effects.length) {
      overlay?.getContext('2d').clearRect(0, 0, overlay.width, overlay.height);
      return;
    }
    let frame;
    const draw = () => {
      paint(overlay.getContext('2d'), clock());
      frame = requestAnimationFrame(draw);
    };
    frame = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(frame);
  });

  function ring(ctx, x, z, radius, color, alpha, width = 3) {
    ctx.globalAlpha = alpha;
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.beginPath();
    ctx.arc(x, z, radius, 0, Math.PI * 2);
    ctx.stroke();
  }

  function label(ctx, text, x, z, color, alpha) {
    if (!text) return;
    ctx.globalAlpha = alpha;
    ctx.font = `800 ${Math.max(12, scale * 2.6)}px system-ui, sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'bottom';
    ctx.lineWidth = 4;
    ctx.strokeStyle = 'rgba(0, 0, 0, 0.75)';
    ctx.strokeText(text, x, z);
    ctx.fillStyle = color;
    ctx.fillText(text, x, z);
  }

  function paint(ctx, now) {
    ctx.clearRect(0, 0, overlay.width, overlay.height);
    const s = scale;
    const r0 = Math.max(6, s * 1.6);
    for (const e of effects) {
      const duration = DURATION[e.kind] ?? 1000;
      const age = now - e.t;
      if (age < 0 || age > duration || e.x == null) continue;
      const p = age / duration;
      const fade = 1 - p;
      const x = e.x * s;
      const z = e.z * s;
      const own = COLORS[e.side] ?? '#f59e0b';
      ctx.save();
      if (e.kind === 'hit') {
        // The strike: a line from the attacker that shoots in, then fades; an impact ring and a cross on the target.
        if (e.fx != null) {
          const fx = e.fx * s;
          const fz = e.fz * s;
          const reach = Math.min(1, p * 4);
          ctx.globalAlpha = fade;
          ctx.strokeStyle = own;
          ctx.lineWidth = Math.max(3, s * 0.9);
          ctx.lineCap = 'round';
          ctx.beginPath();
          ctx.moveTo(fx, fz);
          ctx.lineTo(fx + (x - fx) * reach, fz + (z - fz) * reach);
          ctx.stroke();
        }
        ring(ctx, x, z, r0 + p * s * 5, '#ffffff', fade, 3);
        ring(ctx, x, z, r0 + p * s * 3, own, fade, 2);
        ctx.globalAlpha = fade;
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 3;
        const k = r0 * 0.9;
        ctx.beginPath();
        ctx.moveTo(x - k, z - k);
        ctx.lineTo(x + k, z + k);
        ctx.moveTo(x + k, z - k);
        ctx.lineTo(x - k, z + k);
        ctx.stroke();
        label(ctx, e.label, x, z - r0 - p * s * 6, '#ffd2d2', Math.min(1, fade * 1.6));
      } else if (e.kind === 'damage') {
        ring(ctx, x, z, r0 + p * s * 4, '#ef4444', fade, 3);
        label(ctx, e.label, x, z - r0 - p * s * 6, '#fca5a5', Math.min(1, fade * 1.6));
      } else if (e.kind === 'shot') {
        ring(ctx, x, z, r0 + p * s * 4, own, fade, 2);
        label(ctx, e.label, x, z - r0 - p * s * 3, '#ffffff', fade);
      } else if (e.kind === 'pearl') {
        ring(ctx, x, z, r0 + p * s * 4, '#a855f7', fade, 3);
        ring(ctx, x, z, r0 + p * s * 2, '#2dd4bf', fade, 2);
        label(ctx, e.label, x, z - r0 - p * s * 3, '#e9d5ff', fade);
      } else if (e.kind === 'totem') {
        // A golden burst
        ctx.globalAlpha = fade;
        ctx.strokeStyle = '#facc15';
        ctx.lineWidth = 3;
        const inner = r0 + p * s * 2;
        const outer = r0 + p * s * 7;
        ctx.beginPath();
        for (let i = 0; i < 10; i++) {
          const a = (i / 10) * Math.PI * 2;
          ctx.moveTo(x + Math.cos(a) * inner, z + Math.sin(a) * inner);
          ctx.lineTo(x + Math.cos(a) * outer, z + Math.sin(a) * outer);
        }
        ctx.stroke();
        ring(ctx, x, z, r0 + p * s * 4, '#fde047', fade, 3);
        label(ctx, e.label, x, z - r0 - p * s * 5, '#fde047', Math.min(1, fade * 1.6));
      } else if (e.kind === 'border') {
        ring(ctx, x, z, r0 + p * s * 3, '#f97316', fade, 3);
        label(ctx, e.label, x, z - r0, '#fdba74', fade);
      } else if (e.kind === 'end') {
        ring(ctx, x, z, r0 + Math.min(1, p * 3) * s * 8, '#ef4444', fade, 4);
        ring(ctx, x, z, r0 + Math.min(1, p * 3) * s * 5, '#ffffff', fade, 2);
        label(ctx, e.label, x, z - r0 - s * 2, '#ffffff', Math.min(1, fade * 2));
      }
      ctx.restore();
    }
  }
</script>

<div class="wrap">
  {#if map}
    <div class="stage">
      <canvas bind:this={canvas}></canvas>
      <canvas class="fx" bind:this={overlay} aria-hidden="true"></canvas>
    </div>
  {:else}
    <div class="none muted small">–</div>
  {/if}
</div>

<style>
  .wrap {
    width: 100%;
    max-width: 480px;
  }

  .stage {
    position: relative;
    width: fit-content;
    max-width: 100%;
  }

  canvas {
    display: block;
    max-width: 100%;
    image-rendering: pixelated;
    border-radius: 8px;
    background: var(--surface-2);
  }

  .fx {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    background: transparent;
    pointer-events: none;
    image-rendering: auto;
  }

  .none {
    padding: 20px;
    text-align: center;
  }
</style>

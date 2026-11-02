// What makes a chart flow although new values only come now and then: the time keeps moving in the browser, and a new
// value glides in instead of jumping. For one chart page: run() in an $effect, sync() with the server time of an
// answer, glideFrom() when a new newest point came, shown() for the newest point on its way.
const GLIDE_MS = 800;

export class LiveClock {
  /** The server's time right now. */
  now = $state(Date.now());
  #blend = $state(1);
  #start = 0;
  #offset = 0;
  #before = null;
  #calm = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

  sync(serverTime) {
    this.#offset = serverTime - Date.now();
  }

  /** The newest point changes: it comes from this one. */
  glideFrom(point) {
    if (this.#calm || !point) return;
    this.#before = point;
    this.#start = performance.now();
    this.#blend = 0;
  }

  /** The newest point as it stands right now, on its way from the one before. */
  shown(point) {
    if (!point || !this.#before || this.#blend >= 1) return point ?? null;
    const eased = this.#blend * (2 - this.#blend);
    const mixed = { ...point };
    for (const key of Object.keys(point)) {
      const from = this.#before[key];
      if (key !== 'time' && typeof point[key] === 'number' && typeof from === 'number') {
        mixed[key] = from + (point[key] - from) * eased;
      }
    }
    return mixed;
  }

  /** Moves the time every stepMillis, every frame while something glides. For an $effect: returns the stop function. */
  run(stepMillis) {
    const step = this.#calm ? Math.max(1000, stepMillis) : stepMillis;
    let frame;
    let last = 0;
    const loop = (time) => {
      if (this.#blend < 1) this.#blend = Math.min(1, (time - this.#start) / GLIDE_MS);
      if (this.#blend < 1 || time - last >= step) {
        last = time;
        this.now = Date.now() + this.#offset;
      }
      frame = requestAnimationFrame(loop);
    };
    frame = requestAnimationFrame(loop);
    return () => cancelAnimationFrame(frame);
  }
}

/** How often the time has to move: one pixel of a wide chart, moving more often would not show. */
export const clockStep = (rangeMillis) => Math.min(5000, Math.max(50, rangeMillis / 1200));

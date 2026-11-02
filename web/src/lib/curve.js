// Soft lines for charts: a monotone cubic curve goes through every point and never overshoots it, so a chart shows no
// value that was not measured.

/** The curve through pts [[x, y], ...] as one SVG "C" command per stretch, n points give n - 1 commands. */
export function curve(pts) {
  const n = pts.length;
  if (n < 2) return [];
  const slopes = [];
  for (let i = 0; i < n - 1; i++) {
    const dx = pts[i + 1][0] - pts[i][0];
    slopes.push(dx > 0 ? (pts[i + 1][1] - pts[i][1]) / dx : 0);
  }
  // Flat at a peak or a dip, otherwise the harmonic mean of both sides: it stays below the steeper one.
  const tangents = pts.map((_, i) => {
    if (i === 0) return slopes[0];
    if (i === n - 1) return slopes[n - 2];
    const a = slopes[i - 1];
    const b = slopes[i];
    return a * b <= 0 ? 0 : (2 * a * b) / (a + b);
  });
  const result = [];
  for (let i = 0; i < n - 1; i++) {
    const [x0, y0] = pts[i];
    const [x1, y1] = pts[i + 1];
    const h = (x1 - x0) / 3;
    result.push(
      `C${(x0 + h).toFixed(1)},${(y0 + tangents[i] * h).toFixed(1)} ${(x1 - h).toFixed(1)},${(y1 - tangents[i + 1] * h).toFixed(1)} ${x1.toFixed(1)},${y1.toFixed(1)}`,
    );
  }
  return result;
}

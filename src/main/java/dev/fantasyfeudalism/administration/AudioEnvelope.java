package dev.fantasyfeudalism.administration;

/** A reversible fade uses its current gain as the next starting point. Time is monotonic. */
public final class AudioEnvelope {
  private float from, target;
  private long start, duration;

  public float value(long now) {
    if (duration == 0 || now >= start + duration) return target;
    double fraction = Math.max(0, (double) (now - start) / duration);
    return (float) (from + (target - from) * fraction);
  }

  public void to(float gain, int ticks, long now) {
    if (!Float.isFinite(gain) || gain < 0 || gain > 1 || ticks < 0 || ticks > 600)
      throw new IllegalArgumentException("Invalid audio fade.");
    from = value(now);
    target = gain;
    start = now;
    duration = ticks * 50_000_000L;
  }

  public boolean silent(long now) {
    return target == 0 && value(now) == 0;
  }
}

package dev.fantasyfeudalism.administration;

import java.nio.*;
import java.util.*;

/** Bounded, single-stream Ogg/Vorbis metadata validation; never decodes audio on the server. */
public record OggInfo(int channels, int sampleRate, long samples) {
  private static final int[] CRC = crcTable();

  private static int[] crcTable() {
    int[] table = new int[256];
    for (int i = 0; i < table.length; i++) {
      int value = i << 24;
      for (int bit = 0; bit < 8; bit++)
        value = (value << 1) ^ ((value & 0x80000000) != 0 ? 0x04c11db7 : 0);
      table[i] = value;
    }
    return table;
  }

  public double seconds() {
    return (double) samples / sampleRate;
  }

  public static OggInfo read(byte[] bytes) {
    int offset = 0, serial = 0, sequence = 0, channels = 0, rate = 0;
    long samples = -1;
    boolean ended = false;
    while (offset < bytes.length) {
      if (ended || bytes.length - offset < 27) throw invalid();
      var b =
          ByteBuffer.wrap(bytes, offset, bytes.length - offset)
              .slice()
              .order(ByteOrder.LITTLE_ENDIAN);
      if (b.getInt(0) != 0x5367674f || b.get(4) != 0) throw invalid();
      int flags = Byte.toUnsignedInt(b.get(5)), segments = Byte.toUnsignedInt(b.get(26));
      if (bytes.length - offset < 27 + segments) throw invalid();
      int length = 27 + segments;
      for (int i = 0; i < segments; i++) length += Byte.toUnsignedInt(b.get(27 + i));
      if (length > bytes.length - offset) throw invalid();
      if (offset == 0) {
        if ((flags & 2) == 0
            || segments == 0
            || Byte.toUnsignedInt(b.get(27)) < 30
            || length < 27 + segments + 30) throw invalid();
        int p = 27 + segments;
        if (b.get(p) != 1
            || b.get(p + 1) != 'v'
            || b.get(p + 2) != 'o'
            || b.get(p + 3) != 'r'
            || b.get(p + 4) != 'b'
            || b.get(p + 5) != 'i'
            || b.get(p + 6) != 's'
            || b.getInt(p + 7) != 0) throw invalid();
        channels = Byte.toUnsignedInt(b.get(p + 11));
        rate = b.getInt(p + 12);
        serial = b.getInt(14);
        if (channels < 1 || channels > 2 || rate < 8000 || rate > 192000) throw invalid();
      } else if (b.getInt(14) != serial || (flags & 2) != 0) throw invalid();
      if (b.getInt(18) != sequence++) throw invalid();
      long granule = b.getLong(6);
      if (granule >= 0) {
        if (granule < samples) throw invalid();
        samples = granule;
      }
      int crc = 0;
      for (int i = 0; i < length; i++) {
        int value = i >= 22 && i < 26 ? 0 : Byte.toUnsignedInt(b.get(i));
        crc = (crc << 8) ^ CRC[((crc >>> 24) ^ value) & 255];
      }
      if (crc != b.getInt(22)) throw invalid();
      ended = (flags & 4) != 0;
      offset += length;
    }
    if (!ended || channels == 0 || samples <= 0) throw invalid();
    return new OggInfo(channels, rate, samples);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Use a complete mono or stereo Ogg/Vorbis .ogg file.");
  }
}
